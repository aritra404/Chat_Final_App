package aritra.seal.new_chat.ui.auth

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
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
import aritra.seal.new_chat.viewmodel.AuthViewModel
import com.bumptech.glide.Glide







class SignUpActivity : AppCompatActivity() {

    private val viewModel: AuthViewModel by viewModels()
    private var imageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_up)

        window.statusBarColor = Color.parseColor("#121212")

        val emailEditText = findViewById<EditText>(R.id.emailEditText)
        val passwordEditText = findViewById<EditText>(R.id.passwordEditText)
        val usernameEditText = findViewById<EditText>(R.id.usernameEditText)
        val signUpButton = findViewById<Button>(R.id.signUpButton)
        val profileImageButton = findViewById<ImageView>(R.id.profile_register_button)
        val signInTextView = findViewById<TextView>(R.id.signInTextView)

        signInTextView.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }

        val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                Log.d("PhotoPicker", "Selected URI: $uri")
                imageUri = uri
                Glide.with(this).load(uri).into(profileImageButton)
            } else {
                Log.d("PhotoPicker", "No media selected")
            }
        }

        profileImageButton.setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        signUpButton.setOnClickListener {
            val email = emailEditText.text.toString()
            val password = passwordEditText.text.toString()
            val username = usernameEditText.text.toString()

            if (email.isNotEmpty() && password.isNotEmpty() && username.isNotEmpty()) {
                // Show loading state here if desired
                findViewById<android.widget.ProgressBar>(R.id.progressBar).visibility = android.view.View.VISIBLE
                signUpButton.isEnabled = false
                viewModel.signUpUser(email, password, username, imageUri)
            } else {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            }
        }

        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.signUpState.observe(this) { result ->
            val signUpButton = findViewById<Button>(R.id.signUpButton)
            val progressBar = findViewById<android.widget.ProgressBar>(R.id.progressBar)
            
            if (result.isSuccess) {
                Log.d("SignUp", "Sign up success")
                progressBar.visibility = android.view.View.GONE
                signUpButton.isEnabled = true
                Toast.makeText(this, "Sign up successful!", Toast.LENGTH_SHORT).show()
                navigateToMain()
            } else {
                Log.e("SignUp", "Sign up failed", result.exceptionOrNull())
                progressBar.visibility = android.view.View.GONE
                signUpButton.isEnabled = true
                Toast.makeText(this, "Sign up failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}



