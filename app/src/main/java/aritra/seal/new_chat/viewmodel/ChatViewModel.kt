package aritra.seal.new_chat.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
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
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch






class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChatRepository()
    private val auth = FirebaseAuth.getInstance()

    // We maintain the list of messages in the ViewModel
    private val _messages = MutableLiveData<List<Message>>()
    val messages: LiveData<List<Message>> = _messages

    // Local list to manipulate
    private val currentMessageList = mutableListOf<Message>()

    fun loadMessages(receiverUserId: String) {
        viewModelScope.launch {
            repository.getMessagesFlow(receiverUserId).collect { change ->
                when (change) {
                    is MessageChange.Added -> {
                        // Check if we already have it (e.g. from local send) or duplicates via unique ID
                        if (currentMessageList.none { it.id == change.message.id }) {
                            // Deduplicate based on content/timestamp if we have optimistic messages?
                            // Or simpler: If we have an optimistic message (sent locally) that matches, remove it?
                            // Since optimistic messages have random IDs vs Server IDs, we might have duplicates momentarily.
                            // BUT, previously we ignored DB messages. Now we accept them.
                            // If we accept them, we will see 2 messages: "Optimistic" and "DB".
                            
                            // Strategy: When "sendMessage" succeeds, it returns the real message ID? No, it returns Message obj.
                            // We should handle that in sendMessage.
                            
                            // For here: We just ADD incoming messages.
                            // If the user re-loads, they will get these DB messages (now decrypted).
                            // The "Vanishing" bug happens because we prevented loading these.
                            
                            currentMessageList.add(change.message)
                            _messages.postValue(currentMessageList.toList())
                        }
                    }
                    is MessageChange.Modified -> {
                        val index = currentMessageList.indexOfFirst { it.id == change.message.id }
                        if (index != -1) {
                            // Only update status fields like 'seen', don't overwrite text if we have plain and incoming is encrypted
                            val oldMsg = currentMessageList[index]
                            val newMsg = oldMsg.copy(seen = change.message.seen)
                            currentMessageList[index] = newMsg
                            _messages.postValue(currentMessageList.toList())
                        }
                    }
                    is MessageChange.Removed -> {
                        val index = currentMessageList.indexOfFirst { it.id == change.message.id }
                        if (index != -1) {
                            currentMessageList.removeAt(index)
                            _messages.postValue(currentMessageList.toList())
                        }
                    }
                }
            }
        }
    }

    fun sendMessage(receiverUserId: String, text: String) {
        val currentUser = auth.currentUser?.uid ?: return
        
        // Optimistic Update: Add to UI immediately
        val tempId = System.currentTimeMillis().toString()
        val tempMessage = Message(
            id = tempId,
            senderId = currentUser,
            receiverId = receiverUserId,
            text = text,
            timestamp = System.currentTimeMillis(),
            seen = false,
            isEncrypted = false
        )
        currentMessageList.add(tempMessage)
        _messages.value = currentMessageList.toList()

        viewModelScope.launch {
            val result = repository.sendMessage(getApplication(), receiverUserId, text, tempId)
            if (result.isSuccess) {
                // Success - replace invalid temp message with real one from server
                val realMessage = result.getOrNull()
                if (realMessage != null) {
                    val index = currentMessageList.indexOfFirst { it.id == tempId }
                    if (index != -1) {
                        // We replace the optimistic message with the real one.
                        // The real one (from sendMessage) has the correct ID and usually encrypted text? 
                        // Wait, sendMessage returns the object we created to SAVE.
                        // That object has ENCRYPTED text. We don't want to show encrypted text.
                        // We want to show the PLAIN text we entered.
                        
                        // So we take realMessage ID, but keep our plain text?
                        // OR we rely on flow to give us the decrypted update?
                        // If we rely on flow, we should remove the temp message?
                        // If we remove temp message, there might be a flicker before Flow adds it.
                        
                        // Best approach: Update temp message with real ID. Keep text plain.
                        currentMessageList[index] = realMessage.copy(
                            text = text, // Keep plain text
                            isEncrypted = false
                        )
                         _messages.postValue(currentMessageList.toList())
                    }
                }
            } else {
                // Handle failure - maybe remove message or show error
                // For now, simple.
            }
        }
    }
}


