package aritra.seal.new_chat.service

import android.content.Context
import android.util.Log
import aritra.seal.new_chat.R
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.auth.oauth2.GoogleCredentials
import java.io.InputStream
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

class FcmNotificationsSender(
    private val userFcmToken: String,
    private val title: String, // This will be the sender name
    private val body: String,
    private val context: Context,
    private val senderId: String,
    private val senderName: String,
    private val senderProfileImage: String
) {

    private var requestQueue: RequestQueue? = null

    // V1 API URL requires Project ID. We can extract it from the JSON or user can supply it.
    // For simplicity, we'll try to extract "project_id" from the JSON raw file manually or just parse it.
    // But sending happens in background so we need to get token async.

    fun sendNotifications() {
        requestQueue = Volley.newRequestQueue(context)
        
        // OAuth2 token generation must be done on background thread
        GlobalScope.launch(Dispatchers.IO) {
            val accessToken = getAccessToken(context)
            val projectId = getProjectId(context)
            
            if (accessToken == null || projectId == null) {
                Log.e("FCM_SENDER", "Failed to get access token or project ID. Check service_account_key.json")
                return@launch
            }

            val postUrl = "https://fcm.googleapis.com/v1/projects/$projectId/messages:send"

            val mainObj = JSONObject()
            try {
                // V1 Payload Structure
                val messageObj = JSONObject()
                val notificationObj = JSONObject() // Optional: for system tray
                val dataObj = JSONObject()

                // Token (Target)
                messageObj.put("token", userFcmToken)

                // Data Payload (handled by onMessageReceived)
                dataObj.put("title", title)
                dataObj.put("body", body)
                dataObj.put("senderId", senderId)
                dataObj.put("senderName", senderName)
                dataObj.put("messageText", body)
                dataObj.put("senderProfileImage", senderProfileImage)
                
                // Android Config for High Priority
                val androidConfig = JSONObject()
                androidConfig.put("priority", "high")
                messageObj.put("android", androidConfig)

                messageObj.put("data", dataObj)
                mainObj.put("message", messageObj)

                withContext(Dispatchers.Main) {
                    val request = object : JsonObjectRequest(
                        Request.Method.POST,
                        postUrl,
                        mainObj,
                        { response ->
                            Log.d("FCM_SENDER", "Notification sent successfully: $response")
                        },
                        { error ->
                            Log.e("FCM_SENDER", "Notification send failed", error)
                            if (error.networkResponse != null) {
                                Log.e("FCM_SENDER", "Error Body: " + String(error.networkResponse.data))
                            }
                        }
                    ) {
                        override fun getHeaders(): MutableMap<String, String> {
                            val header: MutableMap<String, String> = HashMap()
                            header["content-type"] = "application/json"
                            header["authorization"] = "Bearer $accessToken"
                            return header
                        }
                    }
                    requestQueue!!.add(request)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getAccessToken(context: Context): String? {
        return try {
            val inputStream: InputStream = context.resources.openRawResource(R.raw.service_account_key)
            val googleCredentials = GoogleCredentials.fromStream(inputStream)
                .createScoped(Collections.singleton("https://www.googleapis.com/auth/firebase.messaging"))
            googleCredentials.refreshIfExpired()
            googleCredentials.accessToken.tokenValue
        } catch (e: Exception) {
            Log.e("FCM_SENDER", "Error getting access token", e)
            null
        }
    }

    private fun getProjectId(context: Context): String? {
        return try {
            val inputStream: InputStream = context.resources.openRawResource(R.raw.service_account_key)
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            val jsonStr = String(buffer)
            val jsonObject = JSONObject(jsonStr)
            jsonObject.getString("project_id")
        } catch (e: Exception) {
            Log.e("FCM_SENDER", "Error reading project ID", e)
            null
        }
    }
}
