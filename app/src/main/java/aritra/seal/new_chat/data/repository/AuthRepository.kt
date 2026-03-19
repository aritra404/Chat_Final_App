package aritra.seal.new_chat.data.repository

import android.net.Uri
import android.util.Log
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
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID
import kotlinx.coroutines.tasks.await







class AuthRepository {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
    private val TAG = "AuthRepository"

    // Sign in with email and password
    suspend fun loginUser(email: String, password: String): Result<AuthResult> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Sign up with email and password
    suspend fun signUpUser(email: String, password: String): Result<AuthResult> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Upload profile image
    suspend fun uploadProfileImage(imageUri: Uri): Result<String> {
        return try {
            val storageRef = storage.reference.child("profile_images/${UUID.randomUUID()}")
            storageRef.putFile(imageUri).await()
            val downloadUrl = storageRef.downloadUrl.await().toString()
            Result.success(downloadUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Update Firebase User Profile
    suspend fun updateUserProfile(username: String, profileImageUrl: String?) {
        try {
            val user = auth.currentUser
            val builder = UserProfileChangeRequest.Builder().setDisplayName(username)
            if (profileImageUrl != null) {
                builder.setPhotoUri(Uri.parse(profileImageUrl))
            }
            user?.updateProfile(builder.build())?.await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update user profile", e)
        }
    }

    // Save User to Realtime Database
    suspend fun saveUserToDatabase(
        username: String,
        email: String,
        profileImageUrl: String?
    ): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: throw Exception("User not found")
            
            // Generate Key Pair
            val keyPair = EncryptionUtils.generateRSAKeyPair()
            val publicKeyString = EncryptionUtils.publicKeyToString(keyPair.public)
                ?: throw Exception("Failed to generate public key")

            val user = User(
                email = email,
                imageUri = profileImageUrl,
                uid = userId,
                username = username,
                publicKey = publicKeyString
            )

            database.getReference("users").child(userId).setValue(user).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getCurrentUser() = auth.currentUser
}



