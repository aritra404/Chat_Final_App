package aritra.seal.new_chat.ui.chat

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
import aritra.seal.new_chat.viewmodel.ChatViewModel
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth







class ChatScreen : AppCompatActivity() {

    private val viewModel: ChatViewModel by viewModels()
    private lateinit var messageAdapter: MessageAdapter
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

        window.statusBarColor = Color.parseColor("#121212")

        receiverUserId = intent.getStringExtra("RECEIVER_USER_ID") ?: ""
        if (receiverUserId.isEmpty()) {
            Toast.makeText(this, "Invalid chat recipient", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializeViews()
        setupRecyclerView()
        setupListeners()
        
        // Start observing functionality
        viewModel.loadMessages(receiverUserId)
        observeViewModel()
    }

    private fun initializeViews() {
        username = findViewById(R.id.user_name_screen)
        profileImage = findViewById(R.id.user_profile_image)
        recyclerView = findViewById(R.id.chat_recycler_view)
        sendMessageButton = findViewById(R.id.send_button)
        messageInput = findViewById(R.id.message_input)

        username.text = intent.getStringExtra("RECEIVER_USERNAME")
        val profileImageUrl = intent.getStringExtra("RECEIVER_PROFILE_IMAGE")
        Glide.with(this).load(profileImageUrl).into(profileImage)
    }

    private fun setupRecyclerView() {
        // Initialize with empty list
        messageAdapter = MessageAdapter(mutableListOf(), currentUserId)
        recyclerView.adapter = messageAdapter
        recyclerView.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
    }

    private fun setupListeners() {
        sendMessageButton.setOnClickListener {
            val messageText = messageInput.text.toString()
            if (messageText.isNotEmpty()) {
                viewModel.sendMessage(receiverUserId, messageText)
                messageInput.text.clear()
            }
        }
    }

    private fun observeViewModel() {
        viewModel.messages.observe(this) { messages ->
            // Re-create adapter with new list since we don't have update methods
            val mutableList = messages.toMutableList()
            messageAdapter = MessageAdapter(mutableList, currentUserId)
            recyclerView.adapter = messageAdapter
            recyclerView.scrollToPosition(messages.size - 1)
        }
    }
}



