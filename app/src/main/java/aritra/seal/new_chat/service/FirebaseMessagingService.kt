package aritra.seal.new_chat.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import aritra.seal.new_chat.data.model.*
import aritra.seal.new_chat.data.repository.*
import aritra.seal.new_chat.R
import aritra.seal.new_chat.service.*
import android.app.ActivityManager
import aritra.seal.new_chat.ui.adapter.*
import aritra.seal.new_chat.ui.auth.*
import aritra.seal.new_chat.ui.chat.*
import aritra.seal.new_chat.ui.main.*
import aritra.seal.new_chat.ui.profile.*
import aritra.seal.new_chat.ui.splash.*
import aritra.seal.new_chat.ui.story.*
import aritra.seal.new_chat.utils.*
import aritra.seal.new_chat.viewmodel.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage








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

            val senderProfileImage = remoteMessage.data["senderProfileImage"]

            // Check if app is in foreground
            if (isAppForeground()) {
                Log.d(TAG, "App is in foreground, suppressing notification")
                return
            }

            // Only show notification if this isn't the sender
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
            if (currentUserId != senderId && senderId != null && messageText != null) {
                sendNotification(senderName ?: "New message", messageText, senderId, senderProfileImage)
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

    private fun sendNotification(title: String, messageBody: String, senderId: String, senderImage: String?) {
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

            // Get Bitmap from URL using Glide (Synchronous call is okay-ish here or ideally in background but onMessageReceived is slightly time critical)
            // But Glide.submit().get() MUST remain on background thread. onMessageReceived IS on background thread usually.
            // Let's wrap in try-catch.
            
            var personBitmap: android.graphics.Bitmap? = null
            
            try {
                if (!senderImage.isNullOrEmpty()) {
                     personBitmap = com.bumptech.glide.Glide.with(applicationContext)
                        .asBitmap()
                        .load(senderImage)
                        .circleCrop() 
                        .submit()
                        .get()
                } else {
                    // Fallback to App Icon if sender image is missing
                    personBitmap = com.bumptech.glide.Glide.with(applicationContext)
                        .asBitmap()
                        .load(R.drawable.ic_app_new) // Use the new app icon
                        .circleCrop()
                        .submit()
                        .get()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Define "Me" (current user)
            val me = androidx.core.app.Person.Builder().setName("Me").build()

            // Define Sender
            val senderBuilder = androidx.core.app.Person.Builder().setName(title)
            if (personBitmap != null) {
                senderBuilder.setIcon(androidx.core.graphics.drawable.IconCompat.createWithBitmap(personBitmap))
            }
            val senderPerson = senderBuilder.build()

            val messagingStyle = NotificationCompat.MessagingStyle(me)
                .addMessage(messageBody, System.currentTimeMillis(), senderPerson) // Message from Sender

            val notificationBuilder = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_app_new)
                .setStyle(messagingStyle) 
                .setContentTitle(title) 
                .setContentText(messageBody) 
                .setAutoCancel(true)
                .setSound(defaultSoundUri)
                .setContentIntent(pendingIntent)
                .setColor(resources.getColor(R.color.primary_brand, theme))
                //.setLargeIcon(personBitmap) // Removed to prevent duplicate profile pictures

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

    private fun isAppForeground(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningAppProcesses = activityManager.runningAppProcesses ?: return false
        for (processInfo in runningAppProcesses) {
            if (processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                for (activeProcess in processInfo.pkgList) {
                    if (activeProcess == packageName) {
                        return true
                    }
                }
            }
        }
        return false
    }
}



