package aritra.seal.new_chat.Story

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import aritra.seal.new_chat.R
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID

class StoryUploadActivity : AppCompatActivity() {
    private lateinit var selectedImageView: ImageView
    private lateinit var captionEditText: EditText
    private lateinit var uploadButton: Button
    private var selectedMediaUri: Uri? = null
    private val currentUser = FirebaseAuth.getInstance().currentUser

    private val pickMedia = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedMediaUri = it
            Glide.with(this)
                .load(it)
                .into(selectedImageView)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_story_upload)

        selectedImageView = findViewById(R.id.selected_image_view)
        captionEditText = findViewById(R.id.caption_edit_text)
        uploadButton = findViewById(R.id.upload_button)

        findViewById<Button>(R.id.select_media_button).setOnClickListener {
            pickMedia.launch("image/*")
        }

        uploadButton.setOnClickListener {
            uploadStory()
        }
    }

    private fun uploadStory() {
        val mediaUri = selectedMediaUri ?: run {
            Toast.makeText(this, "Please select a media", Toast.LENGTH_SHORT).show()
            return
        }

        currentUser?.let { user ->
            val storyId = UUID.randomUUID().toString()
            val storageRef = FirebaseStorage.getInstance().reference
                .child("stories/${user.uid}/$storyId.jpg")

            storageRef.putFile(mediaUri)
                .addOnSuccessListener { taskSnapshot ->
                    taskSnapshot.storage.downloadUrl.addOnSuccessListener { uri ->
                        val currentTime = System.currentTimeMillis()
                        val story = Story(
                            id = storyId,
                            userId = user.uid,
                            username = user.displayName ?: "Unknown",
                            profilePic = user.photoUrl?.toString() ?: "",
                            mediaType = "image",
                            mediaUrl = uri.toString(),
                            timestamp = currentTime,
                            expiresAt = currentTime + (24 * 60 * 60 * 1000), // 24 hours
                            caption = captionEditText.text.toString()
                        )

                        // Save story to Firebase
                        FirebaseDatabase.getInstance().reference
                            .child("stories")
                            .child(user.uid)
                            .child(storyId)
                            .setValue(story)
                            .addOnSuccessListener {
                                Toast.makeText(this, "Story uploaded", Toast.LENGTH_SHORT).show()
                                finish()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
}