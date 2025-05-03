package aritra.seal.new_chat.Story


import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import aritra.seal.new_chat.R
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase



class StoryViewerActivity : AppCompatActivity(), StoriesProgressView.StoriesListener {
    private lateinit var storiesProgressView: StoriesProgressView
    private lateinit var imageView: ImageView
    private lateinit var usernameTextView: TextView
    private lateinit var captionTextView: TextView


    private var currentUserStory: UserStory? = null
    private var currentStoryIndex = 0
    private val currentUser = FirebaseAuth.getInstance().currentUser

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_story_viewer)

        // Initialize views
        storiesProgressView = findViewById(R.id.stories_progress)
        imageView = findViewById(R.id.story_image_view)
        usernameTextView = findViewById(R.id.username_text_view)
        captionTextView = findViewById(R.id.caption_text_view)


        // Get UserStory from intent
        currentUserStory = intent.getSerializableExtra("USER_STORY") as? UserStory

        // Setup stories progress view and load first story
        currentUserStory?.let { userStory ->
            // Ensure stories count is set
            storiesProgressView.setStoriesCount(userStory.stories.size)

            // Set listener before starting
            storiesProgressView.setStoriesListener(this)

            // Sort stories to show unviewed first
            val sortedStories = userStory.stories.sortedBy { it.viewedByCurrentUser }
            currentStoryIndex = sortedStories.indexOfFirst { !it.viewedByCurrentUser }
                .takeIf { it != -1 } ?: 0

            // Load first story and start progress
            loadStory(sortedStories[currentStoryIndex])
            storiesProgressView.startStories(currentStoryIndex)
        }

        // Setup navigation click listeners
        findViewById<View>(R.id.prev_click_area).setOnClickListener {
            storiesProgressView.pause()
            onPrev()
        }

        findViewById<View>(R.id.next_click_area).setOnClickListener {
            storiesProgressView.pause()
            onNext()
        }

    }

    private fun loadStory(story: Story) {
        // Load story image
        Glide.with(this)
            .load(story.mediaUrl)
            .into(imageView)

        // Set username and caption
        usernameTextView.text = story.username
        captionTextView.text = story.caption

        // Mark story as viewed
        markStoryAsViewed(story)
    }

    // Implement StoriesProgressView.StoriesListener methods
    override fun onNext() {
        currentUserStory?.let { userStory ->
            if (currentStoryIndex < userStory.stories.size - 1) {
                currentStoryIndex++
                loadStory(userStory.stories[currentStoryIndex])
            } else {
                finish()
            }
        }
    }

    override fun onPrev() {
        if (currentStoryIndex > 0) {
            currentStoryIndex--
            currentUserStory?.let {
                loadStory(it.stories[currentStoryIndex])
            }
        }
    }

    override fun onComplete() {
        finish()
    }

    private fun markStoryAsViewed(story: Story) {
        currentUser?.let { user ->
            val viewRef = FirebaseDatabase.getInstance().reference
                .child("stories")
                .child(story.userId)
                .child(story.id)
                .child("views")
                .child(user.uid)

            viewRef.setValue(
                StoryView(
                    userId = user.uid,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }
}