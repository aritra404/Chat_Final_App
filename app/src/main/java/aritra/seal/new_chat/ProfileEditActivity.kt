package aritra.seal.new_chat

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID

class ProfileEditActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var storage: FirebaseStorage
    private lateinit var database: FirebaseDatabase

    private lateinit var profileImageView: ImageView
    private lateinit var profileCard: MaterialCardView
    private lateinit var editPhotoIcon: ImageView
    private lateinit var usernameEditText: TextInputEditText
    private lateinit var saveButton: MaterialButton
    private lateinit var cancelButton: MaterialButton

    private var imageUri: Uri? = null
    private var currentImageUrl: String? = null
    private var currentUsername: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_edit)

        auth = FirebaseAuth.getInstance()
        storage = FirebaseStorage.getInstance()
        database = FirebaseDatabase.getInstance()

        // Initialize views
        profileImageView = findViewById(R.id.profile_image)
        profileCard = findViewById(R.id.profile_card)
        editPhotoIcon = findViewById(R.id.edit_photo_icon)
        usernameEditText = findViewById(R.id.username_edit_text)
        saveButton = findViewById(R.id.save_button)
        cancelButton = findViewById(R.id.cancel_button)

        // Image picker setup
        val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                Log.d("PhotoPicker", "Selected URI: $uri")
                imageUri = uri
                Glide.with(this).load(uri).into(profileImageView)
            } else {
                Log.d("PhotoPicker", "No media selected")
            }
        }

        // Set click listeners for profile image
        profileCard.setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        editPhotoIcon.setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        // Load current user data
        loadUserData()

        // Set click listeners for buttons
        saveButton.setOnClickListener {
            saveProfileChanges()
        }

        cancelButton.setOnClickListener {
            finish()
        }
    }

    private fun loadUserData() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // Load data from Firebase Database
            val userRef = database.getReference("users").child(currentUser.uid)
            userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val user = snapshot.getValue(User::class.java)
                    if (user != null) {
                        // Set current values
                        currentUsername = user.username
                        currentImageUrl = user.imageUri

                        // Display current values
                        usernameEditText.setText(currentUsername)
                        if (!currentImageUrl.isNullOrEmpty()) {
                            Glide.with(this@ProfileEditActivity)
                                .load(currentImageUrl)
                                .placeholder(R.drawable.user)
                                .into(profileImageView)
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ProfileEdit", "Failed to load user data", error.toException())
                    Toast.makeText(this@ProfileEditActivity, "Failed to load profile data", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    private fun saveProfileChanges() {
        val newUsername = usernameEditText.text.toString().trim()

        if (newUsername.isEmpty()) {
            Toast.makeText(this, "Username cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        val currentUser = auth.currentUser ?: return

        // If new image is selected, upload it
        if (imageUri != null) {
            uploadProfileImage(imageUri!!) { downloadUrl ->
                if (downloadUrl != null) {
                    updateProfile(currentUser.uid, newUsername, downloadUrl)
                } else {
                    Toast.makeText(this, "Failed to upload image", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            // No new image, update only username
            updateProfile(currentUser.uid, newUsername, currentImageUrl)
        }
    }

    private fun uploadProfileImage(imageUri: Uri, onComplete: (String?) -> Unit) {
        val storageRef = storage.reference.child("profile_images/${UUID.randomUUID()}")

        storageRef.putFile(imageUri)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                    Log.d("Firebase", "Image uploaded successfully. URL: $downloadUrl")
                    onComplete(downloadUrl.toString())
                }
            }
            .addOnFailureListener { exception ->
                Log.e("Firebase", "Image upload failed", exception)
                onComplete(null)
            }
    }

    private fun updateProfile(userId: String, username: String, profileImageUrl: String?) {
        // Update Auth Profile
        val profileUpdates = UserProfileChangeRequest.Builder()
            .setDisplayName(username)
            .setPhotoUri(profileImageUrl?.let { Uri.parse(it) })
            .build()

        auth.currentUser?.updateProfile(profileUpdates)
            ?.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("ProfileEdit", "User profile updated in Auth")

                    // Update Realtime Database
                    val userRef = database.getReference("users").child(userId)
                    userRef.child("username").setValue(username)
                    if (profileImageUrl != null) {
                        userRef.child("imageUri").setValue(profileImageUrl)
                    }

                    Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Log.e("ProfileEdit", "Failed to update profile", task.exception)
                    Toast.makeText(this, "Failed to update profile", Toast.LENGTH_SHORT).show()
                }
            }
    }
}