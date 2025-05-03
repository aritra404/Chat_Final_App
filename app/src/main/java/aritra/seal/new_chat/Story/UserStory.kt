package aritra.seal.new_chat.Story

import java.io.Serializable

data class UserStory(
    val userId: String = "",
    val username: String = "",
    val profilePic: String = "",
    val stories: List<Story> = emptyList(),
    val hasUnviewedStory: Boolean = false
) : Serializable