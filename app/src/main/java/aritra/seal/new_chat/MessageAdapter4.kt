package aritra.seal.new_chat

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessageAdapter(private val messages: List<Message>, private val currentUserId: String) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val VIEW_TYPE_SENT = 1
    private val VIEW_TYPE_RECEIVED = 2
    private val TAG = "MessageAdapter"

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_SENT) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_messgage_sent, parent, false)
            SentMessageViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_messgage_received, parent, false)
            ReceivedMessageViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]

        try {
            if (holder is SentMessageViewHolder) {
                holder.bind(message)
            } else if (holder is ReceivedMessageViewHolder) {
                holder.bind(message)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error binding message at position $position: ${e.message}")
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].senderId == currentUserId) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
    }

    override fun getItemCount(): Int = messages.size

    inner class SentMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val seenIndicator: TextView = itemView.findViewById(R.id.seenIndicator)
        private val messageTime: TextView = itemView.findViewById(R.id.messageTimestamp)

        fun bind(message: Message) {
            seenIndicator.visibility = if (message.seen) View.VISIBLE else View.GONE

            // Format timestamp to a readable time
            val formattedTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(message.timestamp))
            messageTime.text = formattedTime

            // Display message text (should be plaintext for sent messages)
            messageText.text = message.text
        }
    }

    inner class ReceivedMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val messageTime: TextView? = itemView.findViewById(R.id.messageTimestamp)

        fun bind(message: Message) {
            // Display message text (should be decrypted for received messages)
            messageText.text = message.text

            // Set timestamp if available in the layout
            messageTime?.let {
                val formattedTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(message.timestamp))
                it.text = formattedTime
            }
        }
    }
}