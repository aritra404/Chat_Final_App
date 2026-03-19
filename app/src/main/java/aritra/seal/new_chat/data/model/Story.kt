package aritra.seal.new_chat.data.model

import android.net.Uri
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



