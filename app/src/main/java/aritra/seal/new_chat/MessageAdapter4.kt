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

class MessageAdapter(private val messages: List<aritra.seal.new_chat.Message>, private val currentUserId: String) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val VIEW_TYPE_SENT = 1
    private val VIEW_TYPE_RECEIVED = 2

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

        // Step 1: If the message text is encrypted (check for some flag or condition), decrypt it
        if (message.text.startsWith("ENCRYPTED_")) { // Use your condition here to check if it's encrypted
            // Step 2: Retrieve the private key from the Keystore
            val privateKey = EncryptionUtils.getPrivateKeyFromKeystore()

            // Step 3: Decrypt the AES key (if encrypted) using RSA and the private key
            val decryptedAESKey = EncryptionUtils.decryptAESKeyWithRSA(message.encryptedAESKey, privateKey)

            // Step 4: Decrypt the message using the decrypted AES key
            val decryptedMessage = EncryptionUtils.decryptMessageAES(message.text, decryptedAESKey)
            Log.d("MessageAdapter", "Decrypted message: $decryptedMessage")

            // Step 5: Set the decrypted message as the original message text
            message.text = decryptedMessage
        }

        if (holder is SentMessageViewHolder) {
            holder.bind(message)
        } else if (holder is ReceivedMessageViewHolder) {
            holder.bind(message)
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

        fun bind(message: aritra.seal.new_chat.Message) {
            seenIndicator.visibility = if (message.seen) View.VISIBLE else View.GONE

            // Format timestamp to a readable time, e.g., "12:45 PM"
            val formattedTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(message.timestamp))
            messageTime.text = formattedTime
            messageText.text = message.text
        }
    }

    inner class ReceivedMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageText)

        fun bind(message: aritra.seal.new_chat.Message) {
            messageText.text = message.text
        }
    }
}
