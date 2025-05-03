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

    // Modify the generateRSAKeyPair to return the KeyPair
    fun generateRSAKeyPair(): KeyPair {
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE)
            keyStore.load(null)

            if (keyStore.containsAlias(KEY_ALIAS)) {
                Log.d(TAG, "RSA Key Pair already exists in the Keystore.")
                // Load the existing key pair if it exists
                val privateKey = getPrivateKeyFromKeystore()
                val publicKey = getPublicKey()
                return KeyPair(publicKey, privateKey)
            }

            val keyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA, KEYSTORE
            )
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                .build()

            keyPairGenerator.initialize(keyGenParameterSpec)
            val keyPair = keyPairGenerator.generateKeyPair()
            Log.d(TAG, "RSA Key Pair successfully generated and stored in Keystore.")
            return keyPair
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate RSA Key Pair: ${e.message}")
            throw e
        }
    }


    // Add this function to EncryptionUtils class
    fun publicKeyToString(publicKey: PublicKey): String {
        return Base64.encodeToString(publicKey.encoded, Base64.DEFAULT)
    }


    // Convert PrivateKey to Base64 String
    fun privateKeyToString(privateKey: PrivateKey): String {
        return Base64.encodeToString(privateKey.encoded, Base64.DEFAULT)
    }

    // Convert Base64 String to PublicKey
    fun stringToPublicKey(keyString: String): PublicKey {
        val keyBytes = Base64.decode(keyString, Base64.DEFAULT)
        val keySpec = X509EncodedKeySpec(keyBytes)
        val keyFactory = java.security.KeyFactory.getInstance("RSA")
        return keyFactory.generatePublic(keySpec)
    }

    fun getPrivateKeyFromKeystore(): PrivateKey {
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE)
            keyStore.load(null) // Load the keystore
            val key = keyStore.getKey("ChatAppKeyAlias", null)
            if (key is PrivateKey) {
                return key
            } else {
                throw KeyStoreException("Key not found or not a PrivateKey")
            }
        } catch (e: Exception) {
            Log.e("EncryptionUtils", "Error retrieving private key: ${e.message}")
            throw e
        }
    }

    // Retrieve RSA Public Key from Keystore
    fun getPublicKey(): PublicKey {
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE)
            keyStore.load(null)
            val certificate = keyStore.getCertificate(KEY_ALIAS)
            if (certificate != null) {
                return certificate.publicKey
            } else {
                throw KeyStoreException("Certificate not found or public key is missing")
            }
        } catch (e: Exception) {
            Log.e("EncryptionUtils", "Error retrieving public key: ${e.message}")
            throw e
        }
    }

    // Generate AES Secret Key
    fun generateAESKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256) // Ensure your Android version supports 256-bit keys
        return keyGen.generateKey()
    }

    // Encrypt message using AES
    fun encryptMessageAES(message: String, secretKey: SecretKey): String {
        val cipher = Cipher.getInstance("AES")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val encryptedBytes = cipher.doFinal(message.toByteArray())
        return Base64.encodeToString(encryptedBytes, Base64.DEFAULT)
    }

    // Modify the decryptMessageAES function to accept SecretKey instead of String
    fun decryptMessageAES(encryptedMessage: String, secretKey: SecretKey): String {
        try {
            // Initialize the cipher for decryption
            val cipher = Cipher.getInstance("AES")
            cipher.init(Cipher.DECRYPT_MODE, secretKey)

            // Decode the encrypted message and decrypt it
            val decodedBytes = Base64.decode(encryptedMessage, Base64.DEFAULT)
            val decryptedBytes = cipher.doFinal(decodedBytes)

            // Return the decrypted message as a string
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            return ""  // Return an empty string in case of an error
        }
    }


    // Encrypt AES key using RSA
    fun encryptAESKeyWithRSA(secretKey: SecretKey, publicKey: PublicKey): String {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val encryptedKey = cipher.doFinal(secretKey.encoded)
        return Base64.encodeToString(encryptedKey, Base64.DEFAULT)
    }

    // Decrypt AES key using RSA
    fun decryptAESKeyWithRSA(encryptedKey: String, privateKey: PrivateKey): SecretKey {
        // Initialize the cipher for RSA decryption with the correct padding
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.DECRYPT_MODE, privateKey)

        // Decode the encrypted key (which was Base64 encoded) and decrypt it
        val decodedKey = Base64.decode(encryptedKey, Base64.DEFAULT)
        val decryptedKey = cipher.doFinal(decodedKey)

        // Return the decrypted key as a SecretKey for AES
        return SecretKeySpec(decryptedKey, "AES")
    }




    // Generate HMAC for integrity
    fun generateHMAC(message: String, secretKey: SecretKey): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(secretKey)
        val hmacBytes = mac.doFinal(message.toByteArray())
        return Base64.encodeToString(hmacBytes, Base64.DEFAULT)
    }

    fun verifyHMAC(message: String, providedHMAC: String, secretKey: SecretKey): Boolean {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(secretKey)
        val calculatedHMAC = mac.doFinal(message.toByteArray())
        return calculatedHMAC.contentEquals(Base64.decode(providedHMAC, Base64.DEFAULT))
    }

}