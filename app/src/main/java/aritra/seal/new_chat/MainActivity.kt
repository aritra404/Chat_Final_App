package aritra.seal.new_chat

import aritra.seal.new_chat.R
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import aritra.seal.new_chat.Story.Story
import aritra.seal.new_chat.Story.StoryAdapter
import aritra.seal.new_chat.Story.StoryUploadActivity
import aritra.seal.new_chat.Story.StoryViewerActivity
import aritra.seal.new_chat.Story.UserStory
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.messaging.FirebaseMessaging


class MainActivity : AppCompatActivity() {
    private lateinit var database: DatabaseReference
    private lateinit var activeChatsRecyclerView: RecyclerView
    private lateinit var storiesRecyclerView: RecyclerView
    private lateinit var userAdapter: UserAdapter
    private lateinit var storiesAdapter: StoryAdapter
    private lateinit var userList: ArrayList<User>
    private lateinit var userStories: MutableList<UserStory>
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var addStoryButton: ShapeableImageView
    private lateinit var auth: FirebaseAuth
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize SwipeRefreshLayout
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        swipeRefreshLayout.setOnRefreshListener {
            // Refresh your data here
            fetchStories()
            fetchUsersWithConversations()
        }
        val searchBar = findViewById<EditText>(R.id.search_bar)
        searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterUsers(s.toString())
            }

            override fun afterTextChanged(s: Editable?) {}
        })


        // Initialize Firebase components
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference

        // Initialize Stories RecyclerView
        storiesRecyclerView = findViewById(R.id.storiesRecyclerView)
        storiesRecyclerView.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        // Initialize user stories list
        userStories = mutableListOf()

        // Setup Stories Adapter
        storiesAdapter = StoryAdapter(this, userStories) { userStory ->
            val intent = Intent(this, StoryViewerActivity::class.java).apply {
                putExtra("USER_STORY", userStory)
            }
            startActivity(intent)
        }
        storiesRecyclerView.adapter = storiesAdapter

        // Add Story Button
        addStoryButton = findViewById(R.id.add_story_button)
        addStoryButton.setOnClickListener {
            startActivity(Intent(this, StoryUploadActivity::class.java))
        }

        // Initialize Active Chats RecyclerView
        activeChatsRecyclerView = findViewById(R.id.usersRecyclerView)
        activeChatsRecyclerView.layoutManager = LinearLayoutManager(this)
        userList = ArrayList()
        userAdapter = UserAdapter(userList) { selectedUser ->
            val intent = Intent(this, ChatScreen::class.java)
            intent.putExtra("RECEIVER_USER_ID", selectedUser.uid)
            intent.putExtra("RECEIVER_USERNAME", selectedUser.username)
            intent.putExtra("RECEIVER_PROFILE_IMAGE", selectedUser.imageUri)
            startActivity(intent)

            // Mark messages as read when opening the chat
            markMessagesAsRead(selectedUser.uid)
        }
        activeChatsRecyclerView.adapter = userAdapter

        // Bottom Navigation
        bottomNav = findViewById(R.id.bottomNav)
        bottomNav.setOnNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.home -> {
                    true
                }
                R.id.message -> {
                    startActivity(Intent(this, ShowUsersActivity::class.java))
                    true
                }
                R.id.settings -> {
                    startActivity(Intent(this, ProfileEditActivity::class.java))
                    true
                }
                else -> false
            }
        }

        // Fetch stories and chats
        fetchStories()
        fetchUsersWithConversations()

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("FCM", "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }

            // Get the token
            val token = task.result

            // Save the token in Firebase
            val currentUser = auth.currentUser
            if (currentUser != null) {
                val userRef = database.child("users").child(currentUser.uid)
                userRef.child("fcmToken").setValue(token)
            }
        }
    }

    private fun filterUsers(query: String) {
        if (query.isEmpty()) {
            // If query is empty, show original list
            userAdapter.updateList(userList)
            return
        }

        // Filter users by username or lastMessage
        val filteredList = userList.filter { user ->
            user.username.contains(query, ignoreCase = true) ||
                    user.lastMessage.contains(query, ignoreCase = true)
        }

        userAdapter.updateList(filteredList)
    }

    private fun fetchStories() {
        val currentUserId = auth.currentUser?.uid ?: return
        val storiesRef = FirebaseDatabase.getInstance().reference.child("stories")

        storiesRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                userStories.clear()
                val currentTime = System.currentTimeMillis()

                for (userSnapshot in snapshot.children) {
                    val userStoriesList = mutableListOf<Story>()
                    var hasUnviewedStory = false

                    for (storySnapshot in userSnapshot.children) {
                        val story = storySnapshot.getValue(Story::class.java)
                        story?.let {
                            // Check story expiration
                            if (currentTime < it.expiresAt) {
                                // Check if story is viewed by current user
                                val isViewedByCurrentUser =
                                    it.views.containsKey(currentUserId)

                                val modifiedStory = it.copy(
                                    viewedByCurrentUser = isViewedByCurrentUser
                                )
                                userStoriesList.add(modifiedStory)

                                // Track if any story is unviewed
                                if (!isViewedByCurrentUser) {
                                    hasUnviewedStory = true
                                }
                            }
                        }
                    }

                    // Only add user story if they have active stories
                    if (userStoriesList.isNotEmpty()) {
                        val userStory = UserStory(
                            userId = userSnapshot.key ?: "",
                            username = userStoriesList.first().username,
                            profilePic = userStoriesList.first().profilePic,
                            stories = userStoriesList,
                            hasUnviewedStory = hasUnviewedStory
                        )
                        userStories.add(userStory)
                    }
                }

                // Sort stories to prioritize unviewed stories and most recent
                userStories.sortWith(compareBy({ !it.hasUnviewedStory }, { -(it.stories.maxOfOrNull { s -> s.timestamp } ?: 0) }))

                // Notify adapter of data changes
                storiesAdapter.notifyDataSetChanged()

                // Stop the refresh indicator
                swipeRefreshLayout.isRefreshing = false
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("StoryFetch", "Failed to fetch stories", error.toException())
                // Stop the refresh indicator on error
                swipeRefreshLayout.isRefreshing = false
            }
        })
    }

    private fun fetchUsersWithConversations() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val messagesRef = FirebaseDatabase.getInstance().getReference("messages")

        // Map to track users and their conversation info
        val userConversations = HashMap<String, UserConversationInfo>()

        messagesRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                userConversations.clear()

                for (conversationSnapshot in snapshot.children) {
                    val conversationId = conversationSnapshot.key ?: continue

                    // Find the last message in the conversation
                    var lastMessage: Message? = null
                    var lastMessageTimestamp: Long = 0
                    var unreadCount = 0

                    for (messageSnapshot in conversationSnapshot.children) {
                        val message = messageSnapshot.getValue(Message::class.java) ?: continue

                        // Determine if this message involves the current user
                        val isCurrentUserInvolved = message.senderId == currentUserId || message.receiverId == currentUserId
                        if (!isCurrentUserInvolved) continue

                        // Count unread messages (received by current user and not read)
                        if (message.receiverId == currentUserId && !message.seen) {
                            unreadCount++
                        }

                        // Update last message if this is more recent
                        if (message.timestamp > lastMessageTimestamp) {
                            lastMessage = message
                            lastMessageTimestamp = message.timestamp
                        }
                    }

                    // If we found messages involving the current user in this conversation
                    if (lastMessage != null) {
                        // Determine the other user in the conversation
                        val otherUserId = if (lastMessage.senderId == currentUserId)
                            lastMessage.receiverId
                        else
                            lastMessage.senderId

                        // Update or create the conversation info for this user
                        val conversationInfo = userConversations.getOrPut(otherUserId) {
                            UserConversationInfo(otherUserId, null, 0, 0)
                        }

                        // Update conversation info with the latest data
                        if (lastMessageTimestamp > conversationInfo.lastMessageTimestamp) {
                            conversationInfo.lastMessage = lastMessage
                            conversationInfo.lastMessageTimestamp = lastMessageTimestamp
                        }
                        conversationInfo.unreadCount += unreadCount
                    }
                }

                // Now fetch user details for these users
                fetchUsersDetails(userConversations)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Failed to fetch messages", error.toException())
                // Stop the refresh indicator on error
                swipeRefreshLayout.isRefreshing = false
            }
        })
    }

    private fun fetchUsersDetails(userConversations: HashMap<String, UserConversationInfo>) {
        if (userConversations.isEmpty()) {
            userList.clear()
            userAdapter.notifyDataSetChanged()

            // Stop the refresh indicator if no conversations
            swipeRefreshLayout.isRefreshing = false
            return
        }

        val usersRef = FirebaseDatabase.getInstance().getReference("users")
        usersRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                userList.clear()

                for (userSnapshot in snapshot.children) {
                    val userId = userSnapshot.key ?: continue
                    val conversationInfo = userConversations[userId] ?: continue

                    val user = userSnapshot.getValue(User::class.java) ?: continue

                    // Add last message info to the user object
                    val encryptedMessage = conversationInfo.lastMessage?.text ?: ""
                    val encryptedAESKey = conversationInfo.lastMessage?.encryptedAESKey ?: ""
                    val lastMessageSenderId = conversationInfo.lastMessage?.senderId ?: ""
                    val isMessageFromCurrentUser = lastMessageSenderId == auth.currentUser?.uid

                    // Decrypt the message
                    var decryptedMessage = ""
                    try {
                        if (encryptedMessage.isNotEmpty() && encryptedAESKey.isNotEmpty()) {
                            // Get private key and decrypt
                            val privateKey = EncryptionUtils.getPrivateKeyFromKeystore()
                            val decryptedAESKey = EncryptionUtils.decryptAESKeyWithRSA(encryptedAESKey, privateKey)
                            decryptedMessage = EncryptionUtils.decryptMessageAES(encryptedMessage, decryptedAESKey)
                        }
                    } catch (e: Exception) {
                        Log.e("Decryption", "Failed to decrypt last message: ${e.message}")
                        decryptedMessage = "Encrypted message" // Fallback text if decryption fails
                    }

                    // Format the last message to show prefix based on sender
                    val formattedLastMessage = if (isMessageFromCurrentUser) {
                        "You: $decryptedMessage"
                    } else {
                        decryptedMessage
                    }

                    // Create enhanced user object with conversation details
                    val enhancedUser = user.copy(
                        lastMessage = formattedLastMessage,
                        unreadCount = conversationInfo.unreadCount,
                        lastMessageTimestamp = conversationInfo.lastMessageTimestamp
                    )

                    userList.add(enhancedUser)
                }

                // Sort users by last message timestamp (most recent first)
                userList.sortByDescending { it.lastMessageTimestamp }
                userAdapter.updateList(userList)

                userAdapter.notifyDataSetChanged()

                // Stop the refresh indicator after data is loaded
                swipeRefreshLayout.isRefreshing = false
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Failed to fetch user details", error.toException())
                // Stop the refresh indicator on error
                swipeRefreshLayout.isRefreshing = false
            }
        })
    }

    private fun markMessagesAsRead(otherUserId: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        val messagesRef = FirebaseDatabase.getInstance().getReference("messages")

        // Create conversation ID (use consistent format regardless of who started conversation)
        val conversationId = if (currentUserId < otherUserId) {
            "$currentUserId-$otherUserId"
        } else {
            "$otherUserId-$currentUserId"
        }

        messagesRef.child(conversationId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (messageSnapshot in snapshot.children) {
                    val message = messageSnapshot.getValue(Message::class.java) ?: continue

                    // Only mark messages sent to current user as read
                    if (message.receiverId == currentUserId && !message.seen) {
                        messageSnapshot.ref.child("seen").setValue(true)
                    }
                }

                // Refresh the UI after marking messages as read
                fetchUsersWithConversations()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Failed to mark messages as read", error.toException())
            }
        })
    }

    // Data class to track conversation info per user
    private data class UserConversationInfo(
        val userId: String,
        var lastMessage: Message? = null,
        var lastMessageTimestamp: Long = 0,
        var unreadCount: Int = 0
    )

    // Optional: Add onResume to refresh data when returning to the activity
    override fun onResume() {
        super.onResume()
        fetchStories()
        fetchUsersWithConversations()
    }
}