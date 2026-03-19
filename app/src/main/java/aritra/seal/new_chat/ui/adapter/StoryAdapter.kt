package aritra.seal.new_chat.ui.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
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
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import de.hdodenhof.circleimageview.CircleImageView






class StoryAdapter(
    private val context: Context,
    private val userStories: List<UserStory>,
    private val onStoryClickListener: (UserStory) -> Unit
) : RecyclerView.Adapter<StoryAdapter.StoryViewHolder>() {

    inner class StoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profileImage: CircleImageView = itemView.findViewById(R.id.story_profile_image)
        val storyRing: View = itemView.findViewById(R.id.story_ring)
        val usernameTextView: TextView = itemView.findViewById(R.id.story_username)

        fun bind(userStory: UserStory) {
            // Load profile image
            Glide.with(context)
                .load(userStory.profilePic)
                .placeholder(R.drawable.user)
                .into(profileImage)

            usernameTextView.text = userStory.username

            // Set ring color based on viewed status
            val ringColor = if (userStory.hasUnviewedStory) {
                context.getColor(R.color.story_unviewed_ring)
            } else {
                context.getColor(R.color.story_viewed_ring)
            }

            // Use CardView's setCardBackgroundColor instead of setBackgroundColor
            (storyRing as androidx.cardview.widget.CardView).setCardBackgroundColor(ringColor)

            // Set click listener
            itemView.setOnClickListener { onStoryClickListener(userStory) }
        }

        }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StoryViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_story, parent, false)
        return StoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: StoryViewHolder, position: Int) {
        holder.bind(userStories[position])
    }

    override fun getItemCount(): Int = userStories.size
}


