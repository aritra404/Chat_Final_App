package aritra.seal.new_chat

import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import android.util.Base64
import java.util.Collections
import javax.crypto.SecretKey

class ChatScreen : AppCompatActivity() {

    private lateinit var messageAdapter: MessageAdapter
    private lateinit var messageList: MutableList<Message>
    private lateinit var recyclerView: RecyclerView
    private lateinit var sendMessageButton: AppCompatImageButton
    private lateinit var messageInput: EditText
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    private lateinit var receiverUserId: String
    private lateinit var username: TextView
    private lateinit var profileImage: ImageView
    private val LOG_TAG = "ChatScreen"

    // Set to track processed message IDs - make it thread-safe
    private val processedMessageIds = Collections.synchronizedSet(HashSet<String>())

    // Map to store decrypted AES keys for reuse
    private val decryptedAESKeys = HashMap<String, SecretKey>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_screen)

        // Ensure RSA key pair is generated/retrieved during app startup
        initializeEncryption()

        receiverUserId = intent.getStringExtra("RECEIVER_USER_ID") ?: ""
        if (receiverUserId.isEmpty()) {
            Log.e(LOG_TAG, "Receiver user ID is empty!")
            Toast.makeText(this, "Invalid chat recipient", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        username = findViewById(R.id.user_name_screen)
        profileImage = findViewById(R.id.user_profile_image)

        // Initialize views
        recyclerView = findViewById(R.id.chat_recycler_view)
        sendMessageButton = findViewById(R.id.send_button)
        messageInput = findViewById(R.id.message_input)

        // Set up RecyclerView
        messageList = mutableListOf()
        messageAdapter = MessageAdapter(messageList, currentUserId)
        recyclerView.adapter = messageAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        sendMessageButton.setOnClickListener {
            val messageText = messageInput.text.toString()
            if (messageText.isNotEmpty()) {
                sendMessage(messageText)
                messageInput.text.clear()
            }
        }

        username.text = intent.getStringExtra("RECEIVER_USERNAME")
        val profileImageUrl = intent.getStringExtra("RECEIVER_PROFILE_IMAGE")
        Glide.with(this).load(profileImageUrl).into(profileImage)

        receiveMessages()
    }

    private fun initializeEncryption() {
        try {
            val keyPair = EncryptionUtils.generateRSAKeyPair()
            Log.d(LOG_TAG, "RSA KeyPair successfully initialized")

            // Debug: Print public key to verify it exists
            val publicKey = EncryptionUtils.getPublicKey()
            Log.d(LOG_TAG, "Public key available: ${publicKey != null}")

            if (publicKey == null) {
                Log.e(LOG_TAG, "Public key is null! Key generation may have failed")
                showErrorAndRetry("Encryption setup incomplete. Please restart the app.")
                return
            }

            // Store public key in Firebase for other users to encrypt messages to this user
            val userRef = FirebaseDatabase.getInstance().getReference("users")
                .child(currentUserId)
                .child("publicKey")

            val publicKeyString = EncryptionUtils.publicKeyToString(publicKey)
            if (publicKeyString != null) {
                userRef.setValue(publicKeyString)
                    .addOnSuccessListener {
                        Log.d(LOG_TAG, "Public key successfully stored in Firebase")
                    }
                    .addOnFailureListener { e ->
                        Log.e(LOG_TAG, "Failed to store public key: ${e.message}")
                    }
            } else {
                Log.e(LOG_TAG, "Failed to convert public key to string")
                showErrorAndRetry("Failed to prepare encryption keys. Please restart the app.")
            }

        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to initialize RSA KeyPair: ${e.message}")
            e.printStackTrace()
            showErrorAndRetry("Encryption setup failed. Please restart the app.")
        }
    }

    private fun showErrorAndRetry(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        // Consider adding a retry button or automatic retry after delay
    }

    private fun sendMessage(messageText: String) {
        // Step 1: Fetch receiver's public key asynchronously
        getReceiverPublicKey(receiverUserId) { receiverPublicKey ->
            if (receiverPublicKey == null) {
                Log.e(LOG_TAG, "Public key for receiver not found. Cannot send message.")
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Encryption error. Cannot send message.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return@getReceiverPublicKey
            }

            try {
                // Debug: Log that we're using a valid public key
                Log.d(
                    LOG_TAG,
                    "Using valid receiver public key: ${receiverPublicKey.algorithm}, format: ${receiverPublicKey.format}"
                )

                // Step 2: Generate AES key and encrypt the message
                val aesKey = EncryptionUtils.generateAESKey()
                Log.d(LOG_TAG, "Generated AES key: ${aesKey.algorithm}, format: ${aesKey.format}")

                val encryptedMessage = EncryptionUtils.encryptMessageAES(messageText, aesKey)
                val encryptedAESKey =
                    EncryptionUtils.encryptAESKeyWithRSA(aesKey, receiverPublicKey)

                if (encryptedAESKey == null) {
                    Log.e(LOG_TAG, "Failed to encrypt AES key")
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            "Encryption error. Cannot send message.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@getReceiverPublicKey
                }

                // Debug: Log success in encrypting AES key
                Log.d(LOG_TAG, "Successfully encrypted AES key, length: ${encryptedAESKey.length}")

                // Step 3: Generate HMAC for integrity verification
                val hmac = EncryptionUtils.generateHMAC(messageText, aesKey)

                // Add a unique identifier for the message
                val messageId = System.currentTimeMillis().toString()

                // Step 4: Create a temporary message object for immediate display
                val tempMessage = Message(
                    id = messageId,
                    senderId = currentUserId,
                    receiverId = receiverUserId,
                    text = messageText,  // Store original text for sent messages
                    encryptedAESKey = encryptedAESKey,
                    timestamp = System.currentTimeMillis(),
                    hmac = hmac,
                    seen = false,
                    isEncrypted = false  // Mark as not encrypted for display purposes
                )

                // Track this message as processed
                synchronized(processedMessageIds) {
                    processedMessageIds.add(messageId)
                }

                // Add temp message to UI
                messageList.add(tempMessage)
                runOnUiThread {
                    messageAdapter.notifyItemInserted(messageList.size - 1)
                    recyclerView.scrollToPosition(messageList.size - 1)
                }

                // Step 5: Create actual encrypted message for Firebase
                val message = Message(
                    id = messageId,
                    senderId = currentUserId,
                    receiverId = receiverUserId,
                    text = encryptedMessage,  // Store encrypted text for Firebase
                    encryptedAESKey = encryptedAESKey,
                    timestamp = System.currentTimeMillis(),
                    hmac = hmac,
                    seen = false,
                    isEncrypted = true  // Mark as encrypted
                )

                // Step 6: Save the message to Firebase
                val conversationId = getConversationId(currentUserId, receiverUserId)
                val messageRef = FirebaseDatabase.getInstance()
                    .getReference("messages/$conversationId")

                messageRef.child(messageId)
                    .setValue(message)
                    .addOnSuccessListener {
                        Log.d(LOG_TAG, "Message sent successfully to Firebase.")

                        // Step 7: Send FCM notification to the receiver
                        sendNotificationToReceiver(receiverUserId, messageText)
                    }
                    .addOnFailureListener { error ->
                        Log.e(LOG_TAG, "Failed to send message to Firebase: ${error.message}")
                    }

            } catch (e: Exception) {
                Log.e(LOG_TAG, "Error during encryption or message preparation: ${e.message}")
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Failed to send message. Please try again.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun receiveMessages() {
        val conversationId = getConversationId(currentUserId, receiverUserId)
        val messagesRef = FirebaseDatabase.getInstance().getReference("messages/$conversationId")

        messagesRef.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                try {
                    val message = snapshot.getValue(Message::class.java)

                    if (message != null) {
                        Log.d(
                            LOG_TAG,
                            "Received message: ID=${message.id}, encrypted: ${message.isEncrypted}"
                        )

                        // Check if we've already processed this message - use synchronized to be thread-safe
                        var alreadyProcessed = false
                        synchronized(processedMessageIds) {
                            alreadyProcessed = message.id in processedMessageIds
                            if (!alreadyProcessed) {
                                // Mark this message as processed if it's not already processed
                                processedMessageIds.add(message.id)
                            }
                        }

                        if (alreadyProcessed) {
                            Log.d(LOG_TAG, "Message ${message.id} already processed - skipping")
                            return
                        }

                        // Handle sent messages (messages from current user)
                        if (message.senderId == currentUserId) {
                            // For sent messages, we already have the plaintext version in the UI
                            // So we don't need to do anything else
                            return
                        }

                        // Process received messages that need decryption
                        if (message.isEncrypted) {
                            decryptAndDisplayMessage(message, snapshot.ref)
                        } else {
                            // Unencrypted messages (rare case)
                            messageList.add(message)
                            runOnUiThread {
                                messageAdapter.notifyItemInserted(messageList.size - 1)
                                recyclerView.scrollToPosition(messageList.size - 1)
                            }
                        }
                    } else {
                        Log.e(LOG_TAG, "Received null message from Firebase")
                    }
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "Error processing message in onChildAdded: ${e.message}")
                    e.printStackTrace()
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                val updatedMessage = snapshot.getValue(Message::class.java)

                if (updatedMessage != null) {
                    // Find the message in the list and update only status-related fields (like 'seen')
                    val index = messageList.indexOfFirst { it.id == updatedMessage.id }
                    if (index != -1) {
                        // Update only the 'seen' status, not the decrypted text
                        messageList[index].seen = updatedMessage.seen
                        runOnUiThread {
                            messageAdapter.notifyItemChanged(index)
                        }
                    }
                }
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                val deletedMessage = snapshot.getValue(Message::class.java)
                if (deletedMessage != null) {
                    val index = messageList.indexOfFirst { it.id == deletedMessage.id }
                    if (index != -1) {
                        messageList.removeAt(index)
                        runOnUiThread {
                            messageAdapter.notifyItemRemoved(index)
                        }
                        synchronized(processedMessageIds) {
                            processedMessageIds.remove(deletedMessage.id)
                        }
                    }
                }
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {
                // Not needed for chat messaging
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(LOG_TAG, "Error loading messages: ${error.message}")
                runOnUiThread {
                    Toast.makeText(
                        this@ChatScreen,
                        "Failed to load messages: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })

        // Mark messages as seen when the user opens the chat
        markMessagesAsSeen(conversationId)
    }

    private fun decryptAndDisplayMessage(message: Message, messageRef: DatabaseReference) {
        try {
            // Skip if message is already decrypted or has no encrypted key
            if (!message.isEncrypted || message.encryptedAESKey.isNullOrEmpty()) {
                Log.d(LOG_TAG, "Message is not encrypted or has no key. Skipping decryption.")
                return
            }

            // Check if we've already decrypted this AES key before
            val cachedAESKey = decryptedAESKeys[message.id]
            val aesKey = if (cachedAESKey != null) {
                Log.d(LOG_TAG, "Using cached AES key for message ${message.id}")
                cachedAESKey
            } else {
                try {
                    // Get private key from the EncryptionUtils helper
                    val privateKey = EncryptionUtils.getPrivateKeyFromKeystore()
                    if (privateKey == null) {
                        Log.e(LOG_TAG, "Private key not available. Cannot decrypt message.")
                        return
                    }

                    // Decrypt the AES key with the private RSA key
                    val decryptedAESKey = EncryptionUtils.decryptAESKeyWithRSA(
                        message.encryptedAESKey,
                        privateKey
                    )

                    if (decryptedAESKey != null) {
                        // Cache the decrypted key
                        decryptedAESKeys[message.id] = decryptedAESKey
                    }

                    decryptedAESKey
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "Error decrypting AES key: ${e.message}")
                    e.printStackTrace()
                    null
                }
            }

            if (aesKey == null) {
                Log.e(LOG_TAG, "Failed to decrypt AES key for message ${message.id}")
                return
            }

            // Decrypt the message text
            val decryptedText = EncryptionUtils.decryptMessageAES(message.text, aesKey)

            // Verify message integrity using HMAC
            if (!message.hmac.isNullOrEmpty()) {
                val calculatedHmac = EncryptionUtils.generateHMAC(decryptedText, aesKey)

                if (calculatedHmac != message.hmac) {
                    Log.e(LOG_TAG, "HMAC verification failed! Message may have been tampered with.")
                    return
                }
            }

            // Replace the encrypted message with decrypted text for display
            val decryptedMessage = Message(
                id = message.id,
                senderId = message.senderId,
                receiverId = message.receiverId,
                text = decryptedText,
                encryptedAESKey = message.encryptedAESKey,
                timestamp = message.timestamp,
                hmac = message.hmac,
                seen = message.seen,
                isEncrypted = false  // Mark as decrypted for UI
            )

            // Add to UI
            messageList.add(decryptedMessage)
            runOnUiThread {
                messageAdapter.notifyItemInserted(messageList.size - 1)
                recyclerView.scrollToPosition(messageList.size - 1)

                // Mark message as seen once it's displayed
                messageRef.child("seen").setValue(true)
            }

        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error decrypting message: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun getReceiverPublicKey(userId: String, callback: (PublicKey?) -> Unit) {
        val userRef = FirebaseDatabase.getInstance().getReference("users").child(userId)

        userRef.child("publicKey").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val publicKeyString = snapshot.getValue(String::class.java)

                if (publicKeyString != null) {
                    try {
                        // Convert string to PublicKey object
                        val publicKey = EncryptionUtils.stringToPublicKey(publicKeyString)
                        callback(publicKey)
                    } catch (e: Exception) {
                        Log.e(LOG_TAG, "Failed to convert public key string: ${e.message}")
                        e.printStackTrace()
                        callback(null)
                    }
                } else {
                    Log.e(LOG_TAG, "Public key not found for user: $userId")
                    callback(null)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(LOG_TAG, "Failed to retrieve public key: ${error.message}")
                callback(null)
            }
        })
    }

    private fun getConversationId(userId1: String, userId2: String): String {
        // Create a unique conversation ID by sorting and concatenating user IDs
        return if (userId1 < userId2) {
            "${userId1}_${userId2}"
        } else {
            "${userId2}_${userId1}"
        }
    }

    private fun markMessagesAsSeen(conversationId: String) {
        val messagesRef = FirebaseDatabase.getInstance().getReference("messages/$conversationId")

        messagesRef.orderByChild("receiverId").equalTo(currentUserId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (messageSnapshot in snapshot.children) {
                        val message = messageSnapshot.getValue(Message::class.java)

                        if (message != null && !message.seen && message.senderId == receiverUserId) {
                            messageSnapshot.ref.child("seen").setValue(true)
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(LOG_TAG, "Failed to mark messages as seen: ${error.message}")
                }
            })
    }

    private fun sendNotificationToReceiver(receiverId: String, messageText: String) {
        // This would typically integrate with Firebase Cloud Messaging (FCM)
        // Implementation depends on your FCM setup
        val notificationData = hashMapOf(
            "title" to "New Message",
            "body" to messageText,
            "senderId" to currentUserId,
            "type" to "chat"
        )

        // Example: Use a cloud function to send the notification
        // This is just placeholder code - the actual implementation would depend on your FCM setup
        val notificationsRef = FirebaseDatabase.getInstance().getReference("notifications/$receiverId")
        notificationsRef.push().setValue(notificationData)
    }

    override fun onPause() {
        super.onPause()
        // Clean up any listeners if needed
    }

    override fun onDestroy() {
        super.onDestroy()
        // Perform any necessary cleanup
        decryptedAESKeys.clear()
        processedMessageIds.clear()
    }
}