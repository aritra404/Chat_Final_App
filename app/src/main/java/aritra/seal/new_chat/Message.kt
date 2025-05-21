package aritra.seal.new_chat

data class Message(
    val id: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    var text: String = "",
    var encryptedAESKey: String? = null,
    val timestamp: Long = 0,
    var hmac: String = "",
    var seen: Boolean = false,
    var isEncrypted: Boolean = false
) {
    // Empty constructor for Firebase
    constructor() : this("", "", "", "", null, 0, "", false, false)

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