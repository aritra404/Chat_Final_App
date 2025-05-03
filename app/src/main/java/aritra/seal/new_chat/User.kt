package aritra.seal.new_chat

data class User(
    val email: String,
    val imageUri: String?,
    val uid: String,
    val username: String,
    val publicKey: String,
    val lastMessage: String = "",
    val unreadCount: Int = 0,
    val lastMessageTimestamp: Long = 0,
    val fcmToken: String = "",  // Add this field
)

{
constructor() : this(null.toString(), null, null.toString(), null.toString(),null.toString(),null.toString(),0,0,null.toString())
}