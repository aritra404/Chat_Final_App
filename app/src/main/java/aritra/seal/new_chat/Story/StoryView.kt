package aritra.seal.new_chat.Story

import java.io.Serializable

data class StoryView(
    val userId: String = "",
    val timestamp: Long = 0
) : Serializable
