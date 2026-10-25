package dev.freeranger.range.notes.data

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG
import android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import java.util.concurrent.CopyOnWriteArrayList
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Locks and unlocks locked notes. Their content is encrypted with a random note key, which is kept
 * wrapped by a key in the Android Keystore that only works right after the fingerprint or phone PIN
 * prompt. Unlocking unwraps the note key into memory until [lock], when the app is left.
 *
 * Removing the phone's screen lock destroys the Keystore key, and with it any way to read locked notes:
 * backups, which unlock first, are the way back.
 */
class NoteLock(context: Context, private val scope: CoroutineScope) : NoteCipher {
    private val prefs = context.getSharedPreferences("note_lock", Context.MODE_PRIVATE)
    private val keyguard = context.getSystemService(KeyguardManager::class.java)

    @Volatile private var noteKey: SecretKey? = null
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()
    override val isUnlocked: Boolean get() = noteKey != null

    /** Whether the phone has a screen lock to lock notes with. */
    val canLock: Boolean get() = keyguard.isDeviceSecure

    private val beforeLocking = CopyOnWriteArrayList<suspend () -> Unit>()

    /** Leaving the app for a picker that comes straight back, like a backup file's, doesn't lock. */
    @Volatile private var keepThroughLeave = false

    enum class Result { Unlocked, Cancelled, NoScreenLock, Lost }

    /** Runs [save] before every lock, so edits to an open locked note are encrypted while that's still possible. */
    fun beforeLocking(save: suspend () -> Unit): () -> Unit {
        beforeLocking += save
        return { beforeLocking -= save }
    }

    fun keepUnlockedThroughNextLeave() {
        keepThroughLeave = true
    }

    /** The app was left: locked notes lock again. */
    fun appLeft() {
        if (keepThroughLeave) return
        if (noteKey == null) return
        scope.launch {
            beforeLocking.forEach { it() }
            noteKey = null
            _unlocked.value = false
        }
    }

    fun appReturned() {
        keepThroughLeave = false
    }

    /** Asks for the fingerprint or phone PIN, then unlocks, setting up the note key the first time. */
    fun unlock(activity: Activity, title: String, onResult: (Result) -> Unit) {
        if (noteKey != null) return onResult(Result.Unlocked)
        if (!canLock) return onResult(Result.NoScreenLock)
        val wrapped = prefs.getString(WRAPPED_KEY, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        try {
            if (wrapped == null) {
                cipher.init(Cipher.ENCRYPT_MODE, keystoreKey() ?: createKeystoreKey())
            } else {
                val key = keystoreKey() ?: return onResult(Result.Lost)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, wrapped, 0, IV_BYTES))
            }
        } catch (e: KeyPermanentlyInvalidatedException) {
            return onResult(Result.Lost)
        }
        BiometricPrompt.Builder(activity)
            .setTitle(title)
            .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            .build()
            .authenticate(
                BiometricPrompt.CryptoObject(cipher),
                CancellationSignal(),
                activity.mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        val unlocked = result.cryptoObject?.cipher ?: return onResult(Result.Cancelled)
                        val raw = try {
                            if (wrapped == null) {
                                ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }.also { raw ->
                                    val stored = unlocked.iv + unlocked.doFinal(raw)
                                    prefs.edit().putString(WRAPPED_KEY, Base64.encodeToString(stored, Base64.NO_WRAP)).apply()
                                }
                            } else {
                                unlocked.doFinal(wrapped, IV_BYTES, wrapped.size - IV_BYTES)
                            }
                        } catch (e: GeneralSecurityException) {
                            return onResult(Result.Lost)
                        }
                        noteKey = SecretKeySpec(raw, "AES")
                        _unlocked.value = true
                        onResult(Result.Unlocked)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(Result.Cancelled)
                },
            )
    }

    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, checkNotNull(noteKey) { "Notes are locked" })
        return Base64.encodeToString(cipher.iv + cipher.doFinal(plain.encodeToByteArray()), Base64.NO_WRAP)
    }

    override fun decrypt(secret: String): String {
        val bytes = Base64.decode(secret, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, checkNotNull(noteKey) { "Notes are locked" }, GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
        return cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES).decodeToString()
    }

    private fun keystoreKey(): SecretKey? =
        KeyStore.getInstance(KEYSTORE).apply { load(null) }.getKey(ALIAS, null) as SecretKey?

    private fun createKeystoreKey(): SecretKey =
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setUserAuthenticationRequired(true)
                    // Every use needs the prompt just before it.
                    .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
                    // A new fingerprint mustn't lock the owner out of their notes; the PIN guards adding one.
                    .setInvalidatedByBiometricEnrollment(false)
                    .build(),
            )
        }.generateKey()

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "locked_notes"
        const val WRAPPED_KEY = "wrapped_note_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val KEY_BYTES = 32
    }
}
