package aritra.seal.new_chat.viewmodel

import android.net.Uri
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
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch






class AuthViewModel : ViewModel() {

    private val repository = AuthRepository()

    private val _loginState = MutableLiveData<Result<FirebaseUser?>>()
    val loginState: LiveData<Result<FirebaseUser?>> = _loginState

    private val _signUpState = MutableLiveData<Result<Unit>>()
    val signUpState: LiveData<Result<Unit>> = _signUpState

    fun loginUser(email: String, password: String) {
        viewModelScope.launch {
            val result = repository.loginUser(email, password)
            if (result.isSuccess) {
                _loginState.value = Result.success(result.getOrNull()?.user)
            } else {
                _loginState.value = Result.failure(result.exceptionOrNull() ?: Exception("Unknown error"))
            }
        }
    }

    fun signUpUser(email: String, password: String, username: String, imageUri: Uri?) {
        viewModelScope.launch {
            // 1. Create Auth User
            val authResult = repository.signUpUser(email, password)
            if (authResult.isFailure) {
                _signUpState.value = Result.failure(authResult.exceptionOrNull()!!)
                return@launch
            }

            // 2. Upload Image (if exists)
            var downloadUrl: String? = null
            if (imageUri != null) {
                val uploadResult = repository.uploadProfileImage(imageUri)
                if (uploadResult.isSuccess) {
                    downloadUrl = uploadResult.getOrNull()
                } else {
                    // Decide if we fail the whole signup or just continue without image.
                    // For now, let's log and continue, or fail. Let's fail for safety.
                    _signUpState.value = Result.failure(uploadResult.exceptionOrNull()!!)
                    return@launch
                }
            }

            // 3. Update Profile & Save to DB
            repository.updateUserProfile(username, downloadUrl)
            val dbResult = repository.saveUserToDatabase(username, email, downloadUrl)
            
            _signUpState.value = dbResult
        }
    }

    fun isUserLoggedIn(): Boolean {
        return repository.getCurrentUser() != null
    }
}


