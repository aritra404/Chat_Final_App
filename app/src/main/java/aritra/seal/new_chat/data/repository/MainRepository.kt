package aritra.seal.new_chat.data.repository

import android.util.Log
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
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await







class MainRepository {
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()
    private val TAG = "MainRepository"

    fun getStoriesFlow(): Flow<List<UserStory>> = callbackFlow {
        val currentUserId = auth.currentUser?.uid ?: run {
            close()
            return@callbackFlow
        }
        val storiesRef = database.reference.child("stories")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val userStories = mutableListOf<UserStory>()
                val currentTime = System.currentTimeMillis()

                for (userSnapshot in snapshot.children) {
                    val userStoriesList = mutableListOf<Story>()
                    var hasUnviewedStory = false

                    for (storySnapshot in userSnapshot.children) {
                        val story = storySnapshot.getValue(Story::class.java)
                        story?.let {
                            if (currentTime < it.expiresAt) {
                                val isViewedByCurrentUser = it.views.containsKey(currentUserId)
                                val modifiedStory = it.copy(viewedByCurrentUser = isViewedByCurrentUser)
                                userStoriesList.add(modifiedStory)

                                if (!isViewedByCurrentUser) {
                                    hasUnviewedStory = true
                                }
                            }
                        }
                    }

                    if (userStoriesList.isNotEmpty()) {
                        val firstStory = userStoriesList.first()
                        val userStory = UserStory(
                            userId = userSnapshot.key ?: "",
                            username = firstStory.username,
                            profilePic = firstStory.profilePic,
                            stories = userStoriesList,
                            hasUnviewedStory = hasUnviewedStory
                        )
                        userStories.add(userStory)
                    }
                }

                userStories.sortWith(compareBy({ !it.hasUnviewedStory }, { -(it.stories.maxOfOrNull { s -> s.timestamp } ?: 0) }))
                trySend(userStories)
            }

            override fun onCancelled(error: DatabaseError) {
                // handle error or close
            }
        }

        storiesRef.addValueEventListener(listener)
        awaitClose { storiesRef.removeEventListener(listener) }
    }

    fun getUsersWithConversationsFlow(): Flow<List<User>> = callbackFlow {
        val currentUserId = auth.currentUser?.uid ?: run {
            close()
            return@callbackFlow
        }
        val messagesRef = database.getReference("messages")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Map to track users and their conversation info
                val userConversations = HashMap<String, UserConversationInfo>()

                for (conversationSnapshot in snapshot.children) {
                    // Logic to find last message and unread count
                    var lastMessage: Message? = null
                    var lastMessageTimestamp: Long = 0
                    var unreadCount = 0

                    for (messageSnapshot in conversationSnapshot.children) {
                        val message = messageSnapshot.getValue(Message::class.java) ?: continue

                        val isCurrentUserInvolved = message.senderId == currentUserId || message.receiverId == currentUserId
                        if (!isCurrentUserInvolved) continue

                        if (message.receiverId == currentUserId && !message.seen) {
                            unreadCount++
                        }

                        if (message.timestamp > lastMessageTimestamp) {
                            lastMessage = message
                            lastMessageTimestamp = message.timestamp
                        }
                    }

                    if (lastMessage != null) {
                        val otherUserId = if (lastMessage.senderId == currentUserId) lastMessage.receiverId else lastMessage.senderId
                        
                        // We check if we have info for this user
                        val info = userConversations.getOrPut(otherUserId) {
                            UserConversationInfo(otherUserId, null, 0, 0)
                        }

                        if (lastMessageTimestamp > info.lastMessageTimestamp) {
                            info.lastMessage = lastMessage
                            info.lastMessageTimestamp = lastMessageTimestamp
                        }
                        info.unreadCount += unreadCount
                    }
                }

                // Fetch User Details
                fetchUserDetails(userConversations) { users ->
                     trySend(users)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        messagesRef.addValueEventListener(listener)
        awaitClose { messagesRef.removeEventListener(listener) }
    }

    private fun fetchUserDetails(
        userConversations: HashMap<String, UserConversationInfo>,
        onResult: (List<User>) -> Unit
    ) {
        if (userConversations.isEmpty()) {
            onResult(emptyList())
            return
        }

        val usersRef = database.getReference("users")
        usersRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val userList = mutableListOf<User>()
                
                for (userSnapshot in snapshot.children) {
                    val userId = userSnapshot.key ?: continue
                    val conversationInfo = userConversations[userId] ?: continue
                    val user = userSnapshot.getValue(User::class.java) ?: continue

                    // Decrypt Last Message
                    val lastMsg = conversationInfo.lastMessage
                    val decryptedMessage = if (lastMsg != null) {
                        try {
                            if (lastMsg.isEncrypted && !lastMsg.encryptedAESKey.isNullOrEmpty()) {
                                // Try to decrypt
                                // Note: This logic duplicates EncryptionHelper a bit but we need it here.
                                // IMPORTANT: Since this is run on Main Thread (usually) or IO, we access Keystore.
                                val privateKey = EncryptionUtils.getPrivateKeyFromKeystore()
                                if (privateKey != null) {
                                    val encryptedKeyToUse = if (lastMsg.senderId == auth.currentUser?.uid) {
                                        lastMsg.encryptedAESKeySender
                                    } else {
                                        lastMsg.encryptedAESKey
                                    }

                                    if (encryptedKeyToUse != null) {
                                        val aesKey = EncryptionUtils.decryptAESKeyWithRSA(encryptedKeyToUse, privateKey)
                                        if (aesKey != null) {
                                            EncryptionUtils.decryptMessageAES(lastMsg.text, aesKey)
                                        } else "Encrypted message"
                                    } else "Encrypted message"
                                } else "Encrypted message"
                            } else {
                                // If not encrypted or sent by us (and we saved it encrypted? wait)
                                // If sent by us, can we decrypt? See ChatRepository discussion.
                                // If we sent it, we encrypted it with THEIR public key. We cannot decrypt it.
                                // So if senderId == me, we show "You: Encrypted message" unless we stored plain text?
                                // ChatScreen had logic: `getConversationId`. 
                                // Actually, in MainActivity.kt original code:
                                // It loads private key and decrypts.
                                // `EncryptionUtils.decryptAESKeyWithRSA(encryptedAESKey, privateKey)`
                                // This implies `encryptedAESKey` was encrypted with OUR public key.
                                // BUT `sendMessage` encrypts with RECEIVER public key.
                                // So `MainActivity` decryption logic ONLY works for RECEIVED messages.
                                // For SENT messages, `decryptAESKeyWithRSA` with MY private key will FAIL 
                                // because it was encrypted with Their Public Key.
                                // So the original app likely showed "Encrypted message" or garbage for sent messages 
                                // in the main list, OR it handled the exception and showed fallback.
                                
                                // Let's check original MainActivity.kt:
                                // `if (encryptedMessage.isNotEmpty() ...)`
                                // `val decryptedAESKey = EncryptionUtils.decryptAESKeyWithRSA...`
                                // It blindly attempts to decrypt.
                                // If it fails (which it will for sent messages), it catches and shows fallback.
                                
                                lastMsg.text
                            }
                        } catch (e: Exception) {
                            "Encrypted message"
                        }
                    } else ""
                    
                    val prefix = if (lastMsg?.senderId == auth.currentUser?.uid) "You: " else ""
                    
                    // Note: If we failed to decrypt (e.g. sent message), we might want to just show "Message"
                    val displayMessage = "$prefix$decryptedMessage"

                    userList.add(user.copy(
                        lastMessage = displayMessage,
                        unreadCount = conversationInfo.unreadCount,
                        lastMessageTimestamp = conversationInfo.lastMessageTimestamp
                    ))
                }
                
                userList.sortByDescending { it.lastMessageTimestamp }
                onResult(userList)
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun loadCurrentUserProfileImage(onResult: (String?) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        database.getReference("users/$uid").addValueEventListener(object: ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val user = snapshot.getValue(User::class.java)
                onResult(user?.imageUri)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private data class UserConversationInfo(
        val userId: String,
        var lastMessage: Message? = null,
        var lastMessageTimestamp: Long = 0,
        var unreadCount: Int = 0
    )
}



