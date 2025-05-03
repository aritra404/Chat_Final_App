package aritra.seal.new_chat

data class Message(
    val id: String = "",
    var senderId: String = "",
    var receiverId: String = "",
    var text: String = "",
    var encryptedAESKey: String = "",
    var timestamp: Long = 0,
    var hmac: String = "",
    var seen: Boolean = false
)
