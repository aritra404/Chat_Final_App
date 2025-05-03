package aritra.seal.new_chat.Story

import android.net.Uri
import java.io.Serializable

data class Story(
    val id: String = "",
    val userId: String = "",
    val username: String = "",
    val profilePic: String = "",
    val mediaType: String = "", // "image" or "video"
    val mediaUrl: String = "",
    val timestamp: Long = 0,
    val expiresAt: Long = 0,
    val caption: String = "",
    val views: Map<String, StoryView> = emptyMap(),
    val viewCount: Int = 0,
    val viewedByCurrentUser: Boolean = false
) : Serializable
