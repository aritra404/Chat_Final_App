package aritra.seal.new_chat

import android.content.ContentValues.TAG
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.*
import java.security.spec.X509EncodedKeySpec
import javax.crypto.*
import javax.crypto.spec.SecretKeySpec

object EncryptionUtils {

    private const val KEY_ALIAS = "ChatAppKeyAlias"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val LOG_TAG = "EncryptionUtils" // More specific tag for debugging

    // Generate or retrieve existing RSA KeyPair from the Android KeyStore
    fun generateRSAKeyPair(): KeyPair {
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE)
            keyStore.load(null)

            if (keyStore.containsAlias(KEY_ALIAS)) {
                Log.d(LOG_TAG, "RSA Key Pair exists in the Keystore, retrieving...")
                try {
                    val privateKey = getPrivateKeyFromKeystore()
                    val publicKey = getPublicKey()

                    // Validate that both keys are not null
                    if (privateKey != null && publicKey != null) {
                        // Verify the key pair is valid
                        if (isKeyPairValid(publicKey, privateKey)) {
                            Log.d(LOG_TAG, "Successfully retrieved existing key pair")
                            return KeyPair(publicKey, privateKey)
                        } else {
                            Log.w(LOG_TAG, "Retrieved key pair is invalid - will generate new pair")
                            keyStore.deleteEntry(KEY_ALIAS)
                        }
                    } else {
                        Log.w(LOG_TAG, "Retrieved key pair contains null key(s)")
                        // If either key is null, we'll delete and recreate the keys
                        keyStore.deleteEntry(KEY_ALIAS)
                        Log.d(LOG_TAG, "Deleted existing key entry due to null key(s)")
                    }
                } catch (e: Exception) {
                    Log.w(LOG_TAG, "Failed to retrieve existing key pair: ${e.message}")
                    e.printStackTrace()
                    // If retrieval fails, we'll delete and recreate the keys
                    keyStore.deleteEntry(KEY_ALIAS)
                    Log.d(LOG_TAG, "Deleted existing key entry due to retrieval failure")
                }
            }

            // Generate a new key pair if one doesn't exist or couldn't be retrieved
            Log.d(LOG_TAG, "Generating new RSA key pair...")
            val keyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA, KEYSTORE
            )
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                .setKeySize(2048) // Ensure adequate key size
                .build()

            keyPairGenerator.initialize(keyGenParameterSpec)
            val keyPair = keyPairGenerator.generateKeyPair()
            Log.d(LOG_TAG, "RSA Key Pair successfully generated and stored in Keystore.")
            return keyPair
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to generate RSA Key Pair: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    // Verifies a key pair is valid
    private fun isKeyPairValid(publicKey: PublicKey, privateKey: PrivateKey): Boolean {
        try {
            // Try a simple encrypt/decrypt operation to verify the key pair works
            val testData = "test".toByteArray()

            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)
            val encrypted = cipher.doFinal(testData)

            cipher.init(Cipher.DECRYPT_MODE, privateKey)
            val decrypted = cipher.doFinal(encrypted)

            return testData.contentEquals(decrypted)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Key pair validation failed: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    // Convert PublicKey to Base64 String
    fun publicKeyToString(publicKey: PublicKey?): String? {
        if (publicKey == null) {
            Log.e(LOG_TAG, "Cannot convert null PublicKey to string")
            return null
        }
        return Base64.encodeToString(publicKey.encoded, Base64.DEFAULT)
    }

    // Convert PrivateKey to Base64 String
    fun privateKeyToString(privateKey: PrivateKey?): String? {
        if (privateKey == null) {
            Log.e(LOG_TAG, "Cannot convert null PrivateKey to string")
            return null
        }
        return Base64.encodeToString(privateKey.encoded, Base64.DEFAULT)
    }

    // Convert Base64 String to PublicKey
    fun stringToPublicKey(keyString: String?): PublicKey? {
        if (keyString == null) {
            Log.e(LOG_TAG, "Cannot convert null string to PublicKey")
            return null
        }

        try {
            val keyBytes = Base64.decode(keyString, Base64.DEFAULT)
            val keySpec = X509EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            return keyFactory.generatePublic(keySpec)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error converting string to PublicKey: ${e.message}")
            e.printStackTrace()
            return null
        }
    }

    // Get Private Key from Android KeyStore
    fun getPrivateKeyFromKeystore(): PrivateKey? {
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE)
            keyStore.load(null) // Load the keystore

            if (!keyStore.containsAlias(KEY_ALIAS)) {
                Log.e(LOG_TAG, "Key alias $KEY_ALIAS not found in keystore")
                return null
            }

            val entry = keyStore.getEntry(KEY_ALIAS, null)
            if (entry !is KeyStore.PrivateKeyEntry) {
                Log.e(LOG_TAG, "Not an instance of PrivateKeyEntry")
                return null
            }

            return entry.privateKey
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error retrieving private key: ${e.message}")
            e.printStackTrace()
            return null
        }
    }

    // Retrieve RSA Public Key from Keystore
    fun getPublicKey(): PublicKey? {
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE)
            keyStore.load(null)

            if (!keyStore.containsAlias(KEY_ALIAS)) {
                Log.e(LOG_TAG, "Key alias $KEY_ALIAS not found in keystore")
                return null
            }

            val certificate = keyStore.getCertificate(KEY_ALIAS)
            if (certificate != null) {
                return certificate.publicKey
            } else {
                Log.e(LOG_TAG, "Certificate not found or public key is missing")
                return null
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error retrieving public key: ${e.message}")
            e.printStackTrace()
            return null
        }
    }

    // Generate AES Secret Key
    fun generateAESKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256) // 256-bit AES key
        return keyGen.generateKey()
    }

    // Encrypt message using AES
    fun encryptMessageAES(message: String, secretKey: SecretKey): String {
        try {
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding") // Added more secure mode and padding
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val encryptedBytes = cipher.doFinal(message.toByteArray())
            return Base64.encodeToString(encryptedBytes, Base64.DEFAULT)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to encrypt message: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    // Decrypt message using AES
    fun decryptMessageAES(encryptedMessage: String?, secretKey: SecretKey?): String {
        try {
            if (encryptedMessage == null) {
                throw IllegalArgumentException("Encrypted message cannot be null")
            }
            if (secretKey == null) {
                throw IllegalArgumentException("Secret key cannot be null")
            }

            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding") // Match the encryption mode and padding
            cipher.init(Cipher.DECRYPT_MODE, secretKey)
            val decodedBytes = Base64.decode(encryptedMessage, Base64.DEFAULT)
            val decryptedBytes = cipher.doFinal(decodedBytes)
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to decrypt message: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    // Encrypt AES key using RSA public key
    fun encryptAESKeyWithRSA(secretKey: SecretKey?, publicKey: PublicKey?): String? {
        try {
            if (secretKey == null) {
                Log.e(LOG_TAG, "Cannot encrypt null SecretKey")
                return null
            }

            if (publicKey == null) {
                Log.e(LOG_TAG, "Cannot encrypt with null PublicKey")
                return null
            }

            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)
            val encryptedKey = cipher.doFinal(secretKey.encoded)
            return Base64.encodeToString(encryptedKey, Base64.DEFAULT)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to encrypt AES key: ${e.message}")
            e.printStackTrace()
            return null
        }
    }

    fun decryptAESKeyWithRSA(encryptedKey: String?, privateKey: PrivateKey?): SecretKey? {
        try {
            if (encryptedKey == null) {
                Log.e(LOG_TAG, "Cannot decrypt null encryptedKey")
                return null
            }

            if (privateKey == null) {
                Log.e(LOG_TAG, "Cannot decrypt with null PrivateKey")
                return null
            }

            // Log detailed information about the encryption process
            Log.d(LOG_TAG, "Attempting to decrypt AES key with RSA")
            Log.d(LOG_TAG, "PrivateKey algorithm: ${privateKey.algorithm}, format: ${privateKey.format}")

            try {
                // First attempt with standard implementation
                return attemptDecryptAESKey(encryptedKey, privateKey)
            } catch (e: Exception) {
                Log.w(LOG_TAG, "First decryption attempt failed: ${e.message}")

                // If we hit keystore issues, try to regenerate the key pair and retry
                if (e.message?.contains("KeyStoreException") == true ||
                    e.cause?.message?.contains("KeyStoreException") == true) {

                    Log.w(LOG_TAG, "Keystore issue detected, attempting recovery by regenerating keys")
                    val keyStore = KeyStore.getInstance(KEYSTORE)
                    keyStore.load(null)

                    if (keyStore.containsAlias(KEY_ALIAS)) {
                        keyStore.deleteEntry(KEY_ALIAS)
                        Log.d(LOG_TAG, "Deleted problematic key entry")
                    }

                    // Generate fresh keys
                    generateRSAKeyPair()

                    // Return null - the app will need to handle this recovery
                    Log.d(LOG_TAG, "Keys regenerated, but cannot decrypt this message - need to re-establish secure channel")
                    return null
                }

                // If it's not a keystore issue, just throw the exception
                throw e
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Unexpected error in decryptAESKeyWithRSA: ${e.message}")
            e.printStackTrace()
            return null
        }
    }

    private fun attemptDecryptAESKey(encryptedKey: String, privateKey: PrivateKey): SecretKey? {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.DECRYPT_MODE, privateKey)

        // Decode the Base64 string to binary
        val decodedKey = try {
            Base64.decode(encryptedKey, Base64.DEFAULT)
        } catch (e: IllegalArgumentException) {
            Log.e(LOG_TAG, "Failed to decode Base64 encrypted key: ${e.message}")
            e.printStackTrace()
            return null
        }

        Log.d(LOG_TAG, "Decoded key length: ${decodedKey.size} bytes")

        // Actually decrypt the key
        val decryptedKey = try {
            cipher.doFinal(decodedKey)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "RSA decryption failed: ${e.message}")
            e.printStackTrace()
            throw e  // Let the caller handle this
        }

        Log.d(LOG_TAG, "Decrypted key length: ${decryptedKey.size} bytes")

        // Create and return the AES key
        return SecretKeySpec(decryptedKey, "AES")
    }

    // Generate HMAC for message integrity
    fun generateHMAC(message: String, secretKey: SecretKey): String {
        try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(secretKey)
            val hmacBytes = mac.doFinal(message.toByteArray())
            return Base64.encodeToString(hmacBytes, Base64.DEFAULT)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to generate HMAC: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    // Verify HMAC for message integrity
    fun verifyHMAC(message: String, providedHMAC: String, secretKey: SecretKey): Boolean {
        try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(secretKey)
            val calculatedHMAC = mac.doFinal(message.toByteArray())
            return calculatedHMAC.contentEquals(Base64.decode(providedHMAC, Base64.DEFAULT))
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to verify HMAC: ${e.message}")
            e.printStackTrace()
            return false
        }
    }
}