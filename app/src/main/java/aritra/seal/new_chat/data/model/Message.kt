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






data class Message(
    val id: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    var text: String = "",
    var encryptedAESKey: String? = null,
    var encryptedAESKeySender: String? = null, // Key encrypted with Sender's Public Key
    val timestamp: Long = 0,
    var hmac: String = "",
    var seen: Boolean = false,
    var isEncrypted: Boolean = false
) {
    // Empty constructor for Firebase
    constructor() : this("", "", "", "", null, null, 0, "", false, false)

    // Deep copy function
    fun copy(): Message {
        return Message(
            id = this.id,
            senderId = this.senderId,
            receiverId = this.receiverId,
            text = this.text,
            encryptedAESKey = this.encryptedAESKey,
            timestamp = this.timestamp,
            hmac = this.hmac,
            seen = this.seen,
            isEncrypted = this.isEncrypted
        )
    }
}



