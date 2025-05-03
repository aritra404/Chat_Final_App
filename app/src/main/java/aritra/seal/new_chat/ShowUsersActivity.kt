package aritra.seal.new_chat

import android.content.Intent
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
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class ShowUsersActivity : AppCompatActivity() {

    private lateinit var database: DatabaseReference
    private lateinit var usersRecyclerView: RecyclerView
    private lateinit var userAdapter: UserAdapter
    private lateinit var userList: ArrayList<User>
    private lateinit var allUsersList: ArrayList<User>
    private lateinit var searchEditText: EditText
    private lateinit var backButton: ImageView
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_users)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Initialize UI components
        usersRecyclerView = findViewById(R.id.usersRecyclerView)
        searchEditText = findViewById(R.id.search_bar)
        backButton = findViewById(R.id.backButton)

        // Set up RecyclerView
        usersRecyclerView.layoutManager = LinearLayoutManager(this)
        userList = ArrayList()
        allUsersList = ArrayList() // Keep a copy of all users for filtering
        userAdapter = UserAdapter(userList){ selectedUser ->
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
                userList.clear() // Clear the list before adding new data
                allUsersList.clear() // Clear the backup list as well

                val currentUserId = auth.currentUser?.uid

                for (userSnapshot in snapshot.children) {
                    val user = userSnapshot.getValue(User::class.java)
                    if (user != null && user.uid != currentUserId) {
                        // Don't add current user to the list
                        userList.add(user)
                        allUsersList.add(user)
                    }
                }
                userAdapter.notifyDataSetChanged() // Notify adapter about data changes
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Failed to fetch users", error.toException())
            }
        })
    }

    private fun filterUsers(searchText: String) {
        userList.clear()

        if (searchText.isEmpty()) {
            // If search field is empty, show all users
            userList.addAll(allUsersList)
        } else {
            // Filter users based on username or display name
            val lowercaseQuery = searchText.lowercase()

            for (user in allUsersList) {
                if (user.username.lowercase().contains(lowercaseQuery)  == true) {
                    userList.add(user)
                }
            }
        }

        // Update the RecyclerView
        userAdapter.notifyDataSetChanged()
    }
}