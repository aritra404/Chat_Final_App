package aritra.seal.new_chat.ui.main

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.EditText
import android.widget.ImageView
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
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
import aritra.seal.new_chat.viewmodel.MainViewModel
import com.bumptech.glide.Glide
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging







class MainActivity : AppCompatActivity() {
    
    private val viewModel: MainViewModel by viewModels()
    
    private lateinit var activeChatsRecyclerView: RecyclerView
    private lateinit var storiesRecyclerView: RecyclerView
    private lateinit var userAdapter: UserAdapter
    private lateinit var storiesAdapter: StoryAdapter
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var addStoryButton: ShapeableImageView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var profPic: ImageView
    
    // Identify full list for filtering
    private var allUsersList: List<User> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.statusBarColor = Color.parseColor("#121212")

        initializeViews()
        setupAdapters()
        setupListeners()
        setupFCM()
        checkNotificationPermission()
        
        observeViewModel()
    }
    
    private fun initializeViews() {
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        profPic = findViewById(R.id.profile_image)
        storiesRecyclerView = findViewById(R.id.storiesRecyclerView)
        activeChatsRecyclerView = findViewById(R.id.usersRecyclerView)
        addStoryButton = findViewById(R.id.add_story_button)
        bottomNav = findViewById(R.id.bottomNav)
    }

    private fun setupAdapters() {
        // Stories
        storiesRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        storiesAdapter = StoryAdapter(this, mutableListOf()) { userStory ->
            val intent = Intent(this, StoryViewerActivity::class.java).apply {
                putExtra("USER_STORY", userStory)
            }
            startActivity(intent)
        }
        storiesRecyclerView.adapter = storiesAdapter
        
        // Chats
        activeChatsRecyclerView.layoutManager = LinearLayoutManager(this)
        userAdapter = UserAdapter { selectedUser ->
            val intent = Intent(this, ChatScreen::class.java)
            intent.putExtra("RECEIVER_USER_ID", selectedUser.uid)
            intent.putExtra("RECEIVER_USERNAME", selectedUser.username)
            intent.putExtra("RECEIVER_PROFILE_IMAGE", selectedUser.imageUri)
            startActivity(intent)
        }
        activeChatsRecyclerView.adapter = userAdapter
    }
    
    private fun setupListeners() {
        swipeRefreshLayout.setOnRefreshListener {
            viewModel.refresh()
            swipeRefreshLayout.isRefreshing = false // Immediate since using LiveData updates
        }
        
        findViewById<EditText>(R.id.search_bar).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterUsers(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        
        addStoryButton.setOnClickListener {
            startActivity(Intent(this, StoryUploadActivity::class.java))
        }
        
        bottomNav.setOnNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.home -> true
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
    }
    
    private fun observeViewModel() {
        viewModel.stories.observe(this) { stories ->
            // Use specific method if available, or recreate/clear-add
            // Assuming we must modify the list reference or adapter supports it.
            // Simplified:
            storiesAdapter = StoryAdapter(this, stories.toMutableList()) { userStory ->
                 val intent = Intent(this, StoryViewerActivity::class.java).apply {
                    putExtra("USER_STORY", userStory)
                }
                startActivity(intent)
            }
            storiesRecyclerView.adapter = storiesAdapter
        }
        
        viewModel.users.observe(this) { users ->
            allUsersList = users
            userAdapter.updateList(users)
        }
        
        viewModel.currentUserImage.observe(this) { url ->
            if (url != null) {
                Glide.with(this).load(url).circleCrop().into(profPic)
            }
        }
    }
    
    private fun filterUsers(query: String) {
        if (query.isEmpty()) {
            userAdapter.updateList(allUsersList)
            return
        }
        val filtered = allUsersList.filter { 
            it.username.contains(query, ignoreCase = true) || 
            it.lastMessage.contains(query, ignoreCase = true) 
        }
        userAdapter.updateList(filtered)
    }

    private fun setupFCM() {
         FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                val currentUser = FirebaseAuth.getInstance().currentUser
                if (currentUser != null) {
                    FirebaseDatabase.getInstance().getReference("users")
                        .child(currentUser.uid)
                        .child("fcmToken").setValue(token)
                }
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }
}



