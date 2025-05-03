package aritra.seal.new_chat


import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val TAG = "FCM_Service"

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "From: ${remoteMessage.from}")

        // Check if message contains a data payload
        remoteMessage.data.isNotEmpty().let {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")

            // Get message details from data
            val senderId = remoteMessage.data["senderId"]
            val senderName = remoteMessage.data["senderName"]
            val messageText = remoteMessage.data["messageText"]

            // Only show notification if this isn't the sender
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
            if (currentUserId != senderId && senderId != null && messageText != null) {
                sendNotification(senderName ?: "New message", messageText, senderId)
            }
        }

        // Check if message contains a notification payload
        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
            // You can also handle notification payload if needed
        }
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "Refreshed token: $token")

        // Update the token in Firebase database for this user
        sendRegistrationToServer(token)
    }

    private fun sendRegistrationToServer(token: String) {
        // Save the FCM token to the user's database entry
        val currentUser = FirebaseAuth.getInstance().currentUser
        currentUser?.let {
            val userRef = FirebaseDatabase.getInstance().getReference("users/${it.uid}")
            userRef.child("fcmToken").setValue(token)
                .addOnSuccessListener {
                    Log.d(TAG, "FCM Token updated successfully")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to update FCM Token: ${e.message}")
                }
        }
    }

    private fun sendNotification(title: String, messageBody: String, senderId: String) {
        // Create an intent to open the chat with this sender when notification is tapped
        val intent = Intent(this, ChatScreen::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("RECEIVER_USER_ID", senderId)
        }

        // Get additional sender info to pass to the chat activity
        val usersRef = FirebaseDatabase.getInstance().getReference("users/$senderId")
        usersRef.get().addOnSuccessListener { snapshot ->
            val senderUsername = snapshot.child("username").getValue(String::class.java) ?: "User"
            val senderProfileImage = snapshot.child("imageUri").getValue(String::class.java) ?: ""

            intent.putExtra("RECEIVER_USERNAME", senderUsername)
            intent.putExtra("RECEIVER_PROFILE_IMAGE", senderProfileImage)

            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )

            val channelId = getString(R.string.default_notification_channel_id)
            val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val notificationBuilder = NotificationCompat.Builder(this, channelId)
                //.setSmallIcon(R.drawable.ic_notification) // Add a notification icon to your drawable resources
                .setContentTitle(title)
                .setContentText(messageBody)
                .setAutoCancel(true)
                .setSound(defaultSoundUri)
                .setContentIntent(pendingIntent)

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Create the notification channel for Android O and above
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Chat Notifications",
                    NotificationManager.IMPORTANCE_HIGH
                )
                notificationManager.createNotificationChannel(channel)
            }

            // Use a random ID to show multiple notifications
            val notificationId = (System.currentTimeMillis() / 1000).toInt()
            notificationManager.notify(notificationId, notificationBuilder.build())
        }
    }
}
