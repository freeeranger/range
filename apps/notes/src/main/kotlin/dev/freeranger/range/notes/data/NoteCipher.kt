package dev.freeranger.range.notes.data

import android.util.Base64

/** Encrypts locked notes' content. [NoteLock] does it with a key only the phone's screen lock releases; tests fake it. */
interface NoteCipher {
    /** Whether locked notes can be read and written right now. */
    val isUnlocked: Boolean

    fun encrypt(plain: ByteArray): ByteArray

    fun decrypt(sealed: ByteArray): ByteArray

    fun encrypt(plain: String): String = Base64.encodeToString(encrypt(plain.encodeToByteArray()), Base64.NO_WRAP)

    fun decrypt(secret: String): String = decrypt(Base64.decode(secret, Base64.NO_WRAP)).decodeToString()
}
