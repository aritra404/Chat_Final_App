package aritra.seal.new_chat.ui.main

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
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
import com.google.android.material.textfield.TextInputEditText // Keep if still used elsewhere, otherwise remove
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*








class ShowUsersActivity : AppCompatActivity() {

    private lateinit var database: DatabaseReference
    private lateinit var usersRecyclerView: RecyclerView
    private lateinit var userAdapter: UserAdapter
    // Renamed to emphasize their role as internal data holders for the Activity
    private lateinit var currentDisplayedUsers: ArrayList<User> // Holds users currently shown (filtered)
    private lateinit var allFetchedUsers: ArrayList<User>       // Holds all users fetched from Firebase
    private lateinit var searchEditText: EditText
    private lateinit var backButton: ImageView
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_users)

        window.statusBarColor = Color.parseColor("#121212")
        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Initialize UI components
        usersRecyclerView = findViewById(R.id.usersRecyclerView)
        searchEditText = findViewById(R.id.search_bar)
        backButton = findViewById(R.id.backButton)

        // Set up RecyclerView
        usersRecyclerView.layoutManager = LinearLayoutManager(this)

        // Initialize internal data lists
        currentDisplayedUsers = ArrayList()
        allFetchedUsers = ArrayList()

        // Initialize adapter WITHOUT passing an initial list to its constructor,
        // as the adapter now manages its own list internally via updateList().
        userAdapter = UserAdapter { selectedUser ->
            // Handle user click and navigate to ChatScreen
            val intent = Intent(this, ChatScreen::class.java)
            intent.putExtra("RECEIVER_USER_ID", selectedUser.uid) // Pass selected user ID
            intent.putExtra("RECEIVER_USERNAME", selectedUser.username)
            intent.putExtra("RECEIVER_PROFILE_IMAGE", selectedUser.imageUri)
            startActivity(intent)
        }
        usersRecyclerView.adapter = userAdapter

        // Set up back button
        backButton.setOnClickListener {
            finish()
        }

        // Set up search functionality
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // Not needed
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Filter the user list based on the search text
                filterUsers(s.toString())
            }

            override fun afterTextChanged(s: Editable?) {
                // Not needed
            }
        })

        // Initialize Firebase database reference
        database = FirebaseDatabase.getInstance().getReference("users")

        // Fetch all users from Firebase
        fetchUsersFromDatabase()
    }

    private fun fetchUsersFromDatabase() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Clear the internal master list and current display list
                allFetchedUsers.clear()
                currentDisplayedUsers.clear()

                val currentUserId = auth.currentUser?.uid

                for (userSnapshot in snapshot.children) {
                    try {
                        val user = userSnapshot.getValue(User::class.java)
                        // Only add user if not null and not the current user
                        if (user != null && user.uid != currentUserId) {
                            allFetchedUsers.add(user) // Add to the master list of all fetched users
                        }
                    } catch (e: Exception) {
                        Log.e("ShowUsersActivity", "Error deserializing user data for key: ${userSnapshot.key}. Error: ${e.message}", e)
                        // Log the raw value of the problematic snapshot for debugging
                        Log.e("ShowUsersActivity", "Problematic raw data: ${userSnapshot.value}")
                    }
                }
                // Initially, display all fetched users
                currentDisplayedUsers.addAll(allFetchedUsers)

                // IMPORTANT: Tell the adapter to update its internal list and refresh the UI
                userAdapter.updateList(currentDisplayedUsers)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Failed to fetch users", error.toException())
            }
        })
    }

    private fun filterUsers(searchText: String) {
        currentDisplayedUsers.clear() // Clear the list before adding filtered results

        if (searchText.isEmpty()) {
            // If search field is empty, display all original users
            currentDisplayedUsers.addAll(allFetchedUsers)
        } else {
            // Filter users based on username (case-insensitive)
            val lowercaseQuery = searchText.lowercase()

            for (user in allFetchedUsers) {
                if (user.username.lowercase().contains(lowercaseQuery)) {
                    currentDisplayedUsers.add(user)
                }
            }
        }

        // IMPORTANT: Tell the adapter to update its internal list with the filtered results
        userAdapter.updateList(currentDisplayedUsers)
    }
}



