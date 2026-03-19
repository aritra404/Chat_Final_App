package aritra.seal.new_chat.ui.adapter

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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale







// Removed 'UserList' from primary constructor and instead only use 'displayedUserList'
// Initialized 'displayedUserList' as a mutable list directly.
class UserAdapter(private val onUserClick: (User) -> Unit) :
    RecyclerView.Adapter<UserAdapter.ViewHolder>() {

    // This list will hold the actual data displayed by the RecyclerView.
    // It's initialized as empty.
    private var displayedUserList: List<User> = emptyList()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val usernameTextView: TextView = itemView.findViewById(R.id.usernameTextView)
        val profileImageView: ImageView = itemView.findViewById(R.id.userProfileImageView)
        val lastMessage: TextView = itemView.findViewById(R.id.lastMessageTextView)
        val unreadCountBadge: TextView = itemView.findViewById(R.id.unreadCountBadge)
        val lastMessageTime: TextView = itemView.findViewById(R.id.messageTimeTextView)
    }

    // This crucial method updates the adapter's data and refreshes the RecyclerView.
    fun updateList(newList: List<User>) {
        displayedUserList = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserAdapter.ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_user, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserAdapter.ViewHolder, position: Int) {
        // This is now safe as getItemCount() will correctly reflect displayedUserList.size
        val user = displayedUserList[position]

        holder.usernameTextView.text = user.username
        if (user.imageUri != null && user.imageUri.isNotEmpty()) { // Added .isNotEmpty() check
            Glide.with(holder.itemView.context)
                .load(user.imageUri)
                .placeholder(R.drawable.ic_profile_placeholder)
                .circleCrop()
                .into(holder.profileImageView)
        } else {
            holder.profileImageView.setImageResource(R.drawable.ic_profile_placeholder)
        }
        // Display last message (if any)
        if (user.lastMessage.isNotEmpty()) {
            holder.lastMessage.text = user.lastMessage
            holder.lastMessage.visibility = View.VISIBLE

            // Format and display timestamp
            val formattedTime = formatTimestamp(user.lastMessageTimestamp)
            holder.lastMessageTime.text = formattedTime
            holder.lastMessageTime.visibility = View.VISIBLE
        } else {
            holder.lastMessage.visibility = View.GONE
            holder.lastMessageTime.visibility = View.GONE
        }

        // Show unread count badge if there are unread messages
        if (user.unreadCount > 0) {
            holder.unreadCountBadge.visibility = View.VISIBLE
            holder.unreadCountBadge.text = if (user.unreadCount > 99) "99+" else user.unreadCount.toString()
        } else {
            holder.unreadCountBadge.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            onUserClick.invoke(user)
        }
    }

    override fun getItemCount(): Int {
        // CRUCIAL FIX: Return the size of the list that is actually displayed
        return displayedUserList.size
    }

    private fun formatTimestamp(timestamp: Long): String {
        if (timestamp == 0L) return ""

        val calendar = Calendar.getInstance()
        val currentDay = calendar.get(Calendar.DAY_OF_YEAR)
        val currentYear = calendar.get(Calendar.YEAR)

        calendar.timeInMillis = timestamp
        val messageDay = calendar.get(Calendar.DAY_OF_YEAR)
        val messageYear = calendar.get(Calendar.YEAR)

        return when {
            // Today
            currentDay == messageDay && currentYear == messageYear -> {
                SimpleDateFormat("h:mm a", Locale.getDefault()).format(timestamp)
            }
            // Yesterday
            currentDay - messageDay == 1 && currentYear == messageYear -> {
                "Yesterday"
            }
            // Within a week
            currentDay - messageDay < 7 && currentYear == messageYear -> {
                SimpleDateFormat("EEE", Locale.getDefault()).format(timestamp)
            }
            // Same year
            currentYear == messageYear -> {
                SimpleDateFormat("MMM d", Locale.getDefault()).format(timestamp)
            }
            // Different year
            else -> {
                SimpleDateFormat("MM/dd/yy", Locale.getDefault()).format(timestamp)
            }
        }
    }
}



