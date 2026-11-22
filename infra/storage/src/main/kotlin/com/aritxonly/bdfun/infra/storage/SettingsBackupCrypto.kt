package com.aritxonly.bdfun.infra.storage

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 备份文件的口令加密：PBKDF2-HMAC-SHA256 派生 256 位密钥 + AES/GCM 认证加密。
 *
 * 用 GCM 而不是 CBC 是为了让「口令错误」和「文件被改过」都表现为解密失败，
 * 不需要额外的 HMAC，也不会把错误口令解出半截明文。
 */
internal class SettingsBackupCrypto(private val random: SecureRandom = SecureRandom()) {
    data class Sealed(
        val iterations: Int,
        val salt: String,
        val iv: String,
        val data: String,
    )

    fun seal(plain: String, password: String): Sealed {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt, ITERATIONS), GCMParameterSpec(TAG_BITS, iv))
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Sealed(ITERATIONS, salt.b64(), iv.b64(), encrypted.b64())
    }

    /** 口令错误或文件被篡改返回 null。 */
    fun open(sealed: Sealed, password: String): String? {
        if (!iterationsValid(sealed.iterations)) return null
        val salt = sealed.salt.unB64() ?: return null
        val iv = sealed.iv.unB64() ?: return null
        val data = sealed.data.unB64() ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(password, salt, sealed.iterations), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(data), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun key(password: String, salt: ByteArray, iterations: Int) =
        SecretKeySpec(
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)).encoded,
            "AES",
        )

    private fun ByteArray.b64() = Base64.getEncoder().encodeToString(this)
    private fun String.unB64(): ByteArray? = runCatching { Base64.getDecoder().decode(this) }.getOrNull()

    companion object {
        const val ITERATIONS = 150_000
        private const val KEY_BITS = 256
        private const val IV_BYTES = 12
        private const val SALT_BYTES = 16
        private const val TAG_BITS = 128
        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        /** 迭代次数来自文件，防止有人写个 0 次迭代拖慢/削弱；只接受合理区间。 */
        fun iterationsValid(value: Int) = value in 10_000..1_000_000
    }
}
