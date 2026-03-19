package aritra.seal.new_chat.data.repository

import android.util.Log
import android.content.Context
import aritra.seal.new_chat.data.model.*
import aritra.seal.new_chat.data.repository.*
import aritra.seal.new_chat.R
import aritra.seal.new_chat.service.*
import aritra.seal.new_chat.ui.adapter.*
import aritra.seal.new_chat.ui.auth.*
import aritra.seal.new_chat.ui.chat.*
import aritra.seal.new_chat.ui.main.*
import aritra.seal.new_chat.ui.profile.*
import aritra.seal.new_chat.ui.splash.*
import aritra.seal.new_chat.ui.story.*
import aritra.seal.new_chat.utils.*
import aritra.seal.new_chat.viewmodel.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.Collections
import javax.crypto.SecretKey
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await








class ChatRepository {
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()
    private val TAG = "ChatRepository"

    private val decryptedAESKeys = HashMap<String, SecretKey>()

    suspend fun sendMessage(
        context: Context,
        receiverUserId: String,
        messageText: String,
        messageId: String
    ): Result<Message> {
        return try {
            val currentUserId = auth.currentUser?.uid ?: throw Exception("User not logged in")

            // 1. Get Receiver Public Key
            val userRef = database.getReference("users").child(receiverUserId)
            val snapshot = userRef.child("publicKey").get().await()
            val publicKeyString = snapshot.getValue(String::class.java) 
                ?: throw Exception("Receiver public key not found")
            
            // Fetch FCM Token as well
            val fcmToken = userRef.child("fcmToken").get().await().getValue(String::class.java)

            // Fetch Current User Details (for notification sender)
            val currentUserRef = database.getReference("users").child(currentUserId)
            val currentUserSnapshot = currentUserRef.get().await()
            val currentUsername = currentUserSnapshot.child("username").getValue(String::class.java) ?: "New Message"
            val currentUserImage = currentUserSnapshot.child("imageUri").getValue(String::class.java) ?: ""

            val receiverPublicKey = EncryptionUtils.stringToPublicKey(publicKeyString)
                ?: throw Exception("Invalid receiver public key")

            // 2. Generate AES Key and Encrypt Message
            val aesKey = EncryptionUtils.generateAESKey()
            val encryptedMessageText = EncryptionUtils.encryptMessageAES(messageText, aesKey)

            // 3. Encrypt AES Key with Receiver's RSA Public Key
            val encryptedAESKey = EncryptionUtils.encryptAESKeyWithRSA(aesKey, receiverPublicKey)
                ?: throw Exception("Failed to encrypt AES key for receiver")

            // 3b. Encrypt AES Key with Sender's RSA Public Key (for self-decryption)
            // We need to fetch our own public key. 
            // Ideally it's in Keystore, but we need the PublicKey object.
            // EncryptionUtils.getPublicKey() retrieves it from Keystore.
            val senderPublicKey = EncryptionUtils.getPublicKey()
                ?: throw Exception("Failed to retrieve sender public key")
                
            val encryptedAESKeySender = EncryptionUtils.encryptAESKeyWithRSA(aesKey, senderPublicKey)


            // 4. Generate HMAC
            val hmac = EncryptionUtils.generateHMAC(messageText, aesKey)
            // Message ID is passed in param


            // 5. Create Message Objects
            // Actual encrypted message for Firebase
            val message = Message(
                id = messageId,
                senderId = currentUserId,
                receiverId = receiverUserId,
                text = encryptedMessageText,
                encryptedAESKey = encryptedAESKey,
                encryptedAESKeySender = encryptedAESKeySender,
                timestamp = System.currentTimeMillis(),
                hmac = hmac,
                seen = false,
                isEncrypted = true
            )

            // 6. Save to Firebase
            val conversationId = getConversationId(currentUserId, receiverUserId)
            database.getReference("messages/$conversationId/$messageId").setValue(message).await()
            
            // Send Notification
            if (!fcmToken.isNullOrEmpty()) {
                // Get current user name for title (assumed fetched or passed, but we'll use generic or fetch)
                // Efficiency: We could pass it in, but for now let's just use "New Message" or try to get it from cache if possible.
                // Or better, fetch our own name quickly or assume the receiver will fetch it.
                // The FcmNotificationsSender needs senderName.
                // Let's fetch it or use placeholder.
                
                val senderName = currentUsername 
                val senderClass = FcmNotificationsSender(
                    fcmToken,
                    senderName,
                    messageText, 
                    context,
                    currentUserId,
                    senderName,
                    currentUserImage
                )
                senderClass.sendNotifications()
                senderClass.sendNotifications()
            }
            
            Result.success(message)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getMessagesFlow(receiverUserId: String): Flow<MessageChange> = callbackFlow {
        val currentUserId = auth.currentUser?.uid ?: return@callbackFlow
        val conversationId = getConversationId(currentUserId, receiverUserId)
        val messagesRef = database.getReference("messages/$conversationId")
        
        // Mark messages as seen when we start listening
        markMessagesAsSeen(conversationId, receiverUserId, currentUserId)

        val childEventListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val message = snapshot.getValue(Message::class.java) ?: return
                // Decrypt if needed
                // Decrypt if needed - try to decrypt ALL messages if they are encrypted
                // The decryptMessage function handles "my message" vs "other message" key logic
                val finalMessage = if (message.isEncrypted) {
                    decryptMessage(message)
                } else {
                    message
                }
                
                // If it's a sent message, we normally can't decrypt it.
                // However, the UI needs to display "You: <Plain Text>".
                // Since we can't decrypt it, we must return the message with encrypted text 
                // and hope the UI has a way to handle it, OR we acknowledge the limitation.
                // Re-reading ChatScreen.kt: It adds a `tempMessage` with PLAIN text to the list LOCALLY.
                // It ignores the childAdded for own messages.
                // We will emit it anyway. The ViewModel/UI can filter or match against local state.
                
                trySend(MessageChange.Added(finalMessage))
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                val message = snapshot.getValue(Message::class.java) ?: return
                trySend(MessageChange.Modified(message))
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                val message = snapshot.getValue(Message::class.java) ?: return
                trySend(MessageChange.Removed(message))
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        messagesRef.addChildEventListener(childEventListener)
        awaitClose { messagesRef.removeEventListener(childEventListener) }
    }

    private fun decryptMessage(message: Message): Message {
        try {
            if (!message.isEncrypted || message.encryptedAESKey.isNullOrEmpty()) return message

            // Check cache
            var aesKey = decryptedAESKeys[message.id]
            if (aesKey == null) {
                val privateKey = EncryptionUtils.getPrivateKeyFromKeystore() ?: return message
                
                // Choose which key to decrypt
                val currentUserId = auth.currentUser?.uid
                val encryptedKeyToUse = if (message.senderId == currentUserId) {
                    message.encryptedAESKeySender
                } else {
                    message.encryptedAESKey
                }

                if (encryptedKeyToUse == null) {
                    // Cannot decrypt (old message or missing field)
                    return message
                }

                aesKey = EncryptionUtils.decryptAESKeyWithRSA(encryptedKeyToUse, privateKey)
                if (aesKey != null) {
                    decryptedAESKeys[message.id] = aesKey
                }
            }

            if (aesKey == null) return message

            val decryptedText = EncryptionUtils.decryptMessageAES(message.text, aesKey)
            
            // Verify HMAC
            if (!message.hmac.isNullOrEmpty()) {
                val calculatedHmac = EncryptionUtils.generateHMAC(decryptedText, aesKey)
                if (calculatedHmac != message.hmac) {
                    Log.e(TAG, "HMAC verification failed")
                    return message // Return encrypted if verification fails?
                }
            }

            return message.copy(
                text = decryptedText,
                isEncrypted = false // Mark as decrypt so UI shows it
            )

        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed", e)
            return message
        }
    }

    private fun markMessagesAsSeen(conversationId: String, receiverId: String, currentUserId: String) {
        val messagesRef = database.getReference("messages/$conversationId")
        messagesRef.orderByChild("receiverId").equalTo(currentUserId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (child in snapshot.children) {
                        val msg = child.getValue(Message::class.java)
                        if (msg != null && !msg.seen && msg.senderId == receiverId) {
                            child.ref.child("seen").setValue(true)
                        }
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }
    
    // Helper to match ChatScreen logic
    fun getConversationId(userId1: String, userId2: String): String {
        return if (userId1 < userId2) "${userId1}_${userId2}" else "${userId2}_${userId1}"
    }
}

sealed class MessageChange {
    data class Added(val message: Message) : MessageChange()
    data class Modified(val message: Message) : MessageChange()
    data class Removed(val message: Message) : MessageChange()
}



