package aritra.seal.new_chat

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import aritra.seal.new_chat.EncryptionUtils.generateRSAKeyPair
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.UUID

class SignUpActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var storage: FirebaseStorage

    private var imageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_up)

        auth = FirebaseAuth.getInstance()
        storage = FirebaseStorage.getInstance()

        val emailEditText = findViewById<EditText>(R.id.emailEditText)
        val passwordEditText = findViewById<EditText>(R.id.passwordEditText)
        val usernameEditText = findViewById<EditText>(R.id.usernameEditText)
        val signUpButton = findViewById<Button>(R.id.signUpButton)
        val profileImageButton = findViewById<ImageView>(R.id.profile_register_button)

        val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                Log.d("PhotoPicker", "Selected URI: $uri")
                imageUri = uri
                Glide.with(this).load(uri).into(profileImageButton) // Display selected image
            } else {
                Log.d("PhotoPicker", "No media selected")
            }
        }

        profileImageButton.setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        signUpButton.setOnClickListener {
            val email = emailEditText.text.toString()
            val password = passwordEditText.text.toString()
            val username = usernameEditText.text.toString()

            if (email.isNotEmpty() && password.isNotEmpty() && username.isNotEmpty()) {
                signUpUser(email, password, username)
            } else {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun signUpUser(email: String, password: String, username: String) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val userId = auth.currentUser?.uid
                    userId?.let {
                        // Generate RSA Key Pair
                        val keyPair = generateRSAKeyPair()
                        val publicKeyString = EncryptionUtils.publicKeyToString(keyPair.public)

                        // Upload the profile image if selected
                        if (imageUri != null) {
                            uploadProfileImage(imageUri!!) { downloadUrl ->
                                if (downloadUrl != null) {
                                    updateUserProfile(username, downloadUrl)
                                    saveUserToDatabase(username, userId, email, downloadUrl, publicKeyString)
                                }
                            }
                        } else {
                            updateUserProfile(username, null)
                            saveUserToDatabase(username, userId, email, null, publicKeyString)
                        }
                    }
                } else {
                    Log.e("SignUp", "Authentication failed", task.exception)
                    Toast.makeText(this, "Authentication failed.", Toast.LENGTH_SHORT).show()
                }
            }
    }


    private fun uploadProfileImage(imageUri: Uri, onComplete: (String?) -> Unit) {
        val storageRef = storage.reference.child("profile_images/${UUID.randomUUID()}")

        storageRef.putFile(imageUri)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                    Log.d("Firebase", "Image uploaded successfully. URL: $downloadUrl")
                    onComplete(downloadUrl.toString()) // Return the URL to the caller
                }
            }
            .addOnFailureListener { exception ->
                Log.e("Firebase", "Image upload failed", exception)
                Toast.makeText(this, "Failed to upload profile image", Toast.LENGTH_SHORT).show()
                onComplete(null)
            }
    }

    private fun updateUserProfile(username: String, profileImageUrl: String?) {
        val user = auth.currentUser
        val profileUpdates = UserProfileChangeRequest.Builder()
            .setDisplayName(username)
            .setPhotoUri(profileImageUrl?.let { Uri.parse(it) }) // Set photo URI if available
            .build()

        user?.updateProfile(profileUpdates)
            ?.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("Firebase", "User profile updated.")
                }
            }
            ?.addOnFailureListener { exception ->
                Log.e("Firebase", "Failed to update user profile", exception)
            }
    }

    private fun saveUserToDatabase(
        username: String,
        userId: String,
        email: String,
        profileImageUrl: String?,
        publicKey: String
    ) {
        val databaseRef = FirebaseDatabase.getInstance().getReference("users").child(userId)

        val user = User(
            email = email,
            imageUri = profileImageUrl,
            uid = userId,
            username = username,
            publicKey = publicKey
        )

        databaseRef.setValue(user)
            .addOnSuccessListener {
                Log.d("Firebase", "User added to Realtime Database successfully")
                Toast.makeText(this, "Sign up successful!", Toast.LENGTH_SHORT).show()
                navigateToMainActivity()
            }
            .addOnFailureListener { exception ->
                Log.e("Firebase", "Error adding user to Realtime Database", exception)
                Toast.makeText(this, "Failed to save user to database", Toast.LENGTH_SHORT).show()
            }
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
