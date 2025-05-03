package aritra.seal.new_chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class UserAdapter(private val UserList: List<User>, private val onUserClick: (User) -> Unit): RecyclerView.Adapter<UserAdapter.ViewHolder>() {
    private var displayedUserList: List<User> = UserList.toList()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val usernameTextView: TextView = itemView.findViewById(R.id.usernameTextView)
        val profileImageView: ImageView = itemView.findViewById(R.id.userProfileImageView)
        val lastMessage: TextView = itemView.findViewById(R.id.lastMessageTextView)
        val unreadCountBadge: TextView = itemView.findViewById(R.id.unreadCountBadge)
        val lastMessageTime: TextView = itemView.findViewById(R.id.messageTimeTextView)

    }

    fun updateList(newList: List<User>) {
        displayedUserList = newList
        notifyDataSetChanged()
    }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserAdapter.ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_user, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: UserAdapter.ViewHolder, position: Int) {
            val user = displayedUserList[position]


            holder.usernameTextView.text = user.username
            if (user.imageUri != null) {
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
            return UserList.size
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
