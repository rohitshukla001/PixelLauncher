package dev.rohitshukla.pixellauncher.data

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** A drawn pattern: the dots in order, 0..8 on a 3 x 3 pad, left to right and top to bottom. */
typealias Pattern = List<Int>

/**
 * Setting a new privacy pattern, as in Yomo: draw it (at least [MIN_DOTS] dots), then draw it again.
 * A mismatch starts over from the first drawing.
 */
class PatternSetup(private var first: Pattern? = null) {

    sealed interface Result {
        data object TooShort : Result
        data object DrawAgain : Result
        data object Mismatch : Result
        data class Done(val pattern: Pattern) : Result
    }

    val confirming: Boolean get() = first != null

    fun submit(pattern: Pattern): Result {
        val firstPattern = first
        return when {
            firstPattern == null && pattern.size < MIN_DOTS -> Result.TooShort
            firstPattern == null -> {
                first = pattern
                Result.DrawAgain
            }
            pattern != firstPattern -> {
                first = null
                Result.Mismatch
            }
            else -> Result.Done(pattern)
        }
    }

    companion object {
        const val MIN_DOTS = 4
    }
}

/**
 * Stores the pattern as a salted PBKDF2 hash, never as the dots themselves (Yomo keeps them as
 * plain text). Format: `v1:<salt>:<hash>`, both Base64.
 */
object PatternHash {

    private const val VERSION = "v1"
    private const val ITERATIONS = 600_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16

    fun create(pattern: Pattern, random: SecureRandom = SecureRandom()): String {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        return listOf(VERSION, encode(salt), encode(hash(pattern, salt))).joinToString(":")
    }

    /** False for a different pattern or a stored value it cannot read. */
    fun matches(stored: String, pattern: Pattern): Boolean {
        val parts = stored.split(':')
        if (parts.size != 3 || parts[0] != VERSION) return false
        val salt = decode(parts[1]) ?: return false
        val expected = decode(parts[2]) ?: return false
        return MessageDigest.isEqual(expected, hash(pattern, salt))
    }

    private fun hash(pattern: Pattern, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pattern.joinToString(",").toCharArray(), salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encode(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)

    private fun decode(text: String) = try {
        Base64.getDecoder().decode(text)
    } catch (e: IllegalArgumentException) {
        null
    }
}
