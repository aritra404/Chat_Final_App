package aritra.seal.new_chat.data.model

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






data class User(
    val email: String = "", // Provide a default empty string
    val imageUri: String? = null, // Null is fine for nullable String
    val uid: String = "", // Provide a default empty string
    val username: String = "", // Provide a default empty string
    val publicKey: String = "", // Provide a default empty string
    val lastMessage: String = "", // Provide a default empty string
    val unreadCount: Int = 0,
    val lastMessageTimestamp: Long = 0,
    val fcmToken: String = "", // Provide a default empty string
) {
    // Firebase needs a public no-argument constructor for deserialization.
    // When all properties have default values in the primary constructor,
    // Kotlin automatically generates a synthetic no-argument constructor.
    // So, you often don't need to explicitly declare it like this if all fields have defaults.
    // However, if you choose to keep it, make sure it's correct:
    constructor() : this("", null, "", "", "", "", 0, 0, "")
}



