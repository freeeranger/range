package dev.freeranger.range.notes.data

/** Encrypts locked notes' content. [NoteLock] does it with a key only the phone's screen lock releases; tests fake it. */
interface NoteCipher {
    /** Whether locked notes can be read and written right now. */
    val isUnlocked: Boolean

    fun encrypt(plain: String): String

    fun decrypt(secret: String): String
}
