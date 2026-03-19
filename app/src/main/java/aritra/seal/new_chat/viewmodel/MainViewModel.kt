package aritra.seal.new_chat.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
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
import kotlinx.coroutines.launch






class MainViewModel : ViewModel() {

    private val repository = MainRepository()

    private val _stories = MutableLiveData<List<UserStory>>()
    val stories: LiveData<List<UserStory>> = _stories

    private val _users = MutableLiveData<List<User>>()
    val users: LiveData<List<User>> = _users

    private val _currentUserImage = MutableLiveData<String?>()
    val currentUserImage: LiveData<String?> = _currentUserImage
    
    // Loading state could be added here

    init {
        fetchStories()
        fetchConversations()
        fetchUserProfile()
    }
    
    fun refresh() {
        // Since we are using Flows/Listeners that stay active, 
        // "refresh" might just verify connection or re-trigger if needed.
        // But with Firebase listeners, updates are real-time.
        // We can re-fetch if we really want to reset listeners, 
        // but typically not needed for RTDB.
        // However, if the user pulls to refresh, we might just re-subscribe.
        // For simplicity, we assume listeners handle it, but allow public call.
    }

    private fun fetchStories() {
        viewModelScope.launch {
            repository.getStoriesFlow().collect { list ->
                _stories.postValue(list)
            }
        }
    }

    private fun fetchConversations() {
        viewModelScope.launch {
            repository.getUsersWithConversationsFlow().collect { list ->
                _users.postValue(list)
            }
        }
    }
    
    private fun fetchUserProfile() {
        repository.loadCurrentUserProfileImage { url ->
            _currentUserImage.postValue(url)
        }
    }
}


