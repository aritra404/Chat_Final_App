package aritra.seal.new_chat

import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_screen)

        receiverUserId = intent.getStringExtra("RECEIVER_USER_ID") ?: ""
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

    private fun sendMessage(messageText: String) {
        // Step 1: Fetch receiver's public key asynchronously
        getReceiverPublicKey(receiverUserId) { receiverPublicKey ->
            if (receiverPublicKey == null) {
                Log.e("SendMessage", "Public key for receiver not found. Cannot send message.")
                return@getReceiverPublicKey
            }

            try {
                // Step 2: Generate AES key and encrypt the message
                val aesKey = EncryptionUtils.generateAESKey()
                val encryptedMessage = EncryptionUtils.encryptMessageAES(messageText, aesKey)
                val encryptedAESKey = EncryptionUtils.encryptAESKeyWithRSA(aesKey, receiverPublicKey)

                // Step 3: Generate HMAC for integrity verification
                val hmac = EncryptionUtils.generateHMAC(messageText, aesKey)

                // Add a unique identifier for the message
                val messageId = System.currentTimeMillis().toString()

                // Step 4: Construct the Message object
                val message = Message(
                    id = messageId,  // Add a unique ID
                    senderId = currentUserId,
                    receiverId = receiverUserId,
                    text = encryptedMessage,
                    encryptedAESKey = encryptedAESKey,
                    timestamp = System.currentTimeMillis(),
                    hmac = hmac,
                    seen = false
                )

                // Step 5: Save the message to Firebase
                val conversationId = getConversationId(currentUserId, receiverUserId)
                val messageRef = FirebaseDatabase.getInstance()
                    .getReference("messages/$conversationId")

                messageRef.child(messageId)  // Use the unique ID as the key
                    .setValue(message)
                    .addOnSuccessListener {
                        Log.d("SendMessage", "Message sent successfully.")

                        // Step 6: Send FCM notification to the receiver
                        sendNotificationToReceiver(receiverUserId, messageText)
                    }
                    .addOnFailureListener { error ->
                        Log.e("SendMessage", "Failed to send message: ${error.message}")
                    }

            } catch (e: Exception) {
                Log.e("SendMessage", "Error during encryption or message preparation: ${e.message}")
            }
        }
    }

    // Add this function to send FCM notification
    private fun sendNotificationToReceiver(receiverId: String, messageText: String) {
        // Get the FCM token of the receiver and the current user name
        val userRef = FirebaseDatabase.getInstance().getReference("users")

        // First get sender info
        userRef.child(currentUserId).get().addOnSuccessListener { senderSnapshot ->
            val senderName = senderSnapshot.child("username").getValue(String::class.java) ?: "User"

            // Then get receiver's FCM token
            userRef.child(receiverId).get().addOnSuccessListener { receiverSnapshot ->
                val receiverToken = receiverSnapshot.child("fcmToken").getValue(String::class.java)

                if (!receiverToken.isNullOrEmpty()) {
                    // Send to your Firebase Cloud Function or server that will send the notification
                    // Here's the data we need to send
                    val notificationData = hashMapOf(
                        "to" to receiverToken,
                        "data" to hashMapOf(
                            "senderId" to currentUserId,
                            "senderName" to senderName,
                            "messageText" to messageText
                        )
                    )

                    // Call your server or Cloud Function to send the notification
                 //   sendFCMNotification(notificationData)
                }
            }
        }
    }




    private fun receiveMessages() {
        val conversationId = getConversationId(currentUserId, receiverUserId)
        val messagesRef = FirebaseDatabase.getInstance().getReference("messages/$conversationId")

        messagesRef.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val message = snapshot.getValue(Message::class.java)

                if (message != null) {
                    // Check if the message is already in the list to prevent duplicates
                    val isDuplicate = messageList.any { it.id == message.id }
                    if (!isDuplicate) {
                        try {
                            // Step 1: Decrypt the AES key using the private RSA key
                            val privateKey = EncryptionUtils.getPrivateKeyFromKeystore()
                            val decryptedAESKey = EncryptionUtils.decryptAESKeyWithRSA(message.encryptedAESKey, privateKey)

                            // Step 2: Decrypt the message using the decrypted AES key
                            val decryptedMessage = EncryptionUtils.decryptMessageAES(message.text, decryptedAESKey)
                            message.text = decryptedMessage // Replace with decrypted message text

                            // Step 3: Mark the message as seen if the recipient is the current user
                            if (message.receiverId == currentUserId && !message.seen) {
                                snapshot.ref.child("seen").setValue(true)
                            }

                            // Step 4: Add the message to the list and update the UI
                            messageList.add(message)
                            messageAdapter.notifyItemInserted(messageList.size - 1)

                            // Scroll to the bottom
                            recyclerView.scrollToPosition(messageList.size - 1)

                        } catch (e: Exception) {
                            Log.e("MessageDecrypt", "Error decrypting message: ${e.message}")
                        }
                    }
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                val updatedMessage = snapshot.getValue(Message::class.java)

                if (updatedMessage != null) {
                    // Find the message in the list and update it
                    val index = messageList.indexOfFirst { it.timestamp == updatedMessage.timestamp }
                    if (index != -1) {
                        messageList[index] = updatedMessage
                        messageAdapter.notifyItemChanged(index)
                    }
                }
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                // Optional: Handle message deletions (if needed)
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {
                // Optional: Handle child moves (not common for messages)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseError", "Error loading messages: ${error.message}")
            }
        })
    }




    private fun getConversationId(userId1: String, userId2: String): String {
        return if (userId1 < userId2) "$userId1$userId2" else "$userId2$userId1"
    }

    fun getReceiverPublicKey(receiverUserId: String, callback: (PublicKey?) -> Unit) {
        val databaseRef = FirebaseDatabase.getInstance().getReference("users/$receiverUserId")
        databaseRef.child("publicKey").get()
            .addOnSuccessListener { snapshot ->
                val publicKeyString = snapshot.getValue(String::class.java)
                if (publicKeyString != null) {
                    try {
                        val keyBytes = Base64.decode(publicKeyString, Base64.DEFAULT)
                        val keySpec = X509EncodedKeySpec(keyBytes)
                        val keyFactory = KeyFactory.getInstance("RSA")
                        val publicKey = keyFactory.generatePublic(keySpec)
                        callback(publicKey)
                    } catch (e: Exception) {
                        Log.e("PublicKeyDecode", "Error decoding public key: ${e.message}")
                        callback(null)
                    }
                } else {
                    Log.e("PublicKeyFetch", "Public key is null in Firebase for user: $receiverUserId")
                    callback(null)
                }
            }
            .addOnFailureListener { error ->
                Log.e("PublicKeyFetch", "Failed to fetch public key: ${error.message}")
                callback(null)
            }
    }

}
