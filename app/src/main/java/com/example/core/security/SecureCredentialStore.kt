package com.example.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class SecureCredentialStore {

    fun encrypt(plaintext: String): String {
        val trimmed = plaintext.trim()
        if (trimmed.isEmpty()) return ""
        return try {
            val secretKey = getOrCreateKeystoreKey()
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(trimmed.toByteArray(StandardCharsets.UTF_8))
            val combined = ByteBuffer.allocate(1 + iv.size + ciphertext.size)
                .put(iv.size.toByte())
                .put(iv)
                .put(ciphertext)
                .array()
            PREFIX_KEYSTORE_V1 + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            if (!isAndroidKeyStoreAvailable()) {
                // Strictly restricted to headless JVM/Robolectric unit test environments without AndroidKeyStore provider
                val fallbackKey = deriveHostFallbackKey()
                val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, fallbackKey)
                val iv = cipher.iv
                val ciphertext = cipher.doFinal(trimmed.toByteArray(StandardCharsets.UTF_8))
                val combined = ByteBuffer.allocate(1 + iv.size + ciphertext.size)
                    .put(iv.size.toByte())
                    .put(iv)
                    .put(ciphertext)
                    .array()
                PREFIX_FALLBACK_V1 + Base64.encodeToString(combined, Base64.NO_WRAP)
            } else {
                ""
            }
        }
    }

    private fun isAndroidKeyStoreAvailable(): Boolean = runCatching {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
        ks.load(null)
        true
    }.getOrDefault(false)

    fun decrypt(storedValue: String): String {
        if (storedValue.isBlank()) return ""
        return when {
            storedValue.startsWith(PREFIX_KEYSTORE_V1) -> {
                runCatching {
                    val raw = Base64.decode(storedValue.removePrefix(PREFIX_KEYSTORE_V1), Base64.NO_WRAP)
                    val buffer = ByteBuffer.wrap(raw)
                    val ivLen = buffer.get().toInt() and 0xFF
                    val iv = ByteArray(ivLen).also { buffer.get(it) }
                    val ciphertext = ByteArray(buffer.remaining()).also { buffer.get(it) }
                    val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
                    cipher.init(Cipher.DECRYPT_MODE, getOrCreateKeystoreKey(), GCMParameterSpec(128, iv))
                    String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
                }.getOrDefault("")
            }
            storedValue.startsWith(PREFIX_FALLBACK_V1) -> {
                runCatching {
                    val raw = Base64.decode(storedValue.removePrefix(PREFIX_FALLBACK_V1), Base64.NO_WRAP)
                    val buffer = ByteBuffer.wrap(raw)
                    val ivLen = buffer.get().toInt() and 0xFF
                    val iv = ByteArray(ivLen).also { buffer.get(it) }
                    val ciphertext = ByteArray(buffer.remaining()).also { buffer.get(it) }
                    val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
                    cipher.init(Cipher.DECRYPT_MODE, deriveHostFallbackKey(), GCMParameterSpec(128, iv))
                    String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
                }.getOrDefault("")
            }
            else -> ""
        }
    }

    private fun getOrCreateKeystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun deriveHostFallbackKey(): SecretKey {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("jarvis_local_cred_fallback_v1".toByteArray(StandardCharsets.UTF_8))
        return SecretKeySpec(digest, "AES")
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "jarvis_hf_credential_aes_key"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PREFIX_KEYSTORE_V1 = "enc_ks_v1:"
        private const val PREFIX_FALLBACK_V1 = "enc_fb_v1:"

        private val REPO_ID_REGEX = Regex("^[a-zA-Z0-9._-]+/[a-zA-Z0-9._-]+$")
        private val BLOCKED_EXECUTABLE_EXTENSIONS = setOf(
            "py", "pyc", "pyo", "so", "dex", "jar", "apk", "aab",
            "sh", "bash", "zsh", "bat", "cmd", "exe", "dll", "dylib",
            "js", "mjs", "ts", "wasm", "class", "elf", "msi"
        )

        fun normalizeAndValidateRepoId(rawInput: String): Result<String> {
            val trimmed = rawInput.trim()
                .removePrefix("https://huggingface.co/")
                .removePrefix("http://huggingface.co/")
                .removePrefix("huggingface.co/")
                .trim('/')

            if (trimmed.isEmpty()) {
                return Result.failure(IllegalArgumentException("Repository ID cannot be empty."))
            }
            if (trimmed.contains("..") || trimmed.contains('?') || trimmed.contains('#') || trimmed.contains('\\')) {
                return Result.failure(IllegalArgumentException("Invalid repository identifier characters."))
            }
            if (!REPO_ID_REGEX.matches(trimmed)) {
                return Result.failure(
                    IllegalArgumentException("Repository ID must be in 'owner/model-name' format (e.g. litert-community/Gemma3-1B-IT).")
                )
            }
            val segments = trimmed.split('/')
            if (segments.size != 2 || segments[0] == "." || segments[1] == ".") {
                return Result.failure(IllegalArgumentException("Invalid owner/repository segments."))
            }
            return Result.success(trimmed)
        }

        fun isSafeModelFilename(fileName: String): Boolean {
            val clean = fileName.trim()
            if (clean.isEmpty() || clean.contains("..") || clean.contains('\\')) return false
            val leaf = clean.substringAfterLast('/')
            if (leaf.isEmpty() || leaf.startsWith('.')) return false
            val ext = leaf.substringAfterLast('.', "").lowercase()
            if (ext.isEmpty() || ext in BLOCKED_EXECUTABLE_EXTENSIONS) return false
            return true
        }

        fun sanitizeLocalFileLeaf(repoId: String, fileName: String): String {
            val combined = "${repoId.replace('/', '_')}_${fileName.substringAfterLast('/')}"
            return combined.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        }

        fun maskToken(token: String): String {
            val trimmed = token.trim()
            if (trimmed.isEmpty()) return "Not configured"
            if (trimmed.length <= 8) return "Configured (••••)"
            return "${trimmed.take(4)}••••${trimmed.takeLast(4)}"
        }
    }
}
