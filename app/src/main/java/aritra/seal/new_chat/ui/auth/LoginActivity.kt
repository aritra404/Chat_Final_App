package aritra.seal.new_chat.ui.auth

import android.content.ContentValues.TAG
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
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







class LoginActivity : AppCompatActivity() {

    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.parseColor("#121212")

        // Check if user is signed in
        if (viewModel.isUserLoggedIn()) {
            navigateToMain()
            return
        }

        setContentView(R.layout.activity_login)

        val email = findViewById<EditText>(R.id.emailEditText)
        val password = findViewById<EditText>(R.id.passwordEditText)
        val loginButton = findViewById<Button>(R.id.loginButton)
        val signUpButton = findViewById<TextView>(R.id.signUpTextView)

        signUpButton.setOnClickListener {
            val intent = Intent(this, SignUpActivity::class.java)
            startActivity(intent)
        }

        loginButton.setOnClickListener {
            val emailText = email.text.toString()
            val passwordText = password.text.toString()

            if (emailText.isNotEmpty() && passwordText.isNotEmpty()) {
                findViewById<android.widget.ProgressBar>(R.id.progressBar).visibility = android.view.View.VISIBLE
                loginButton.isEnabled = false
                viewModel.loginUser(emailText, passwordText)
            } else {
                Toast.makeText(this, "Empty Fields Are not Allowed !!", Toast.LENGTH_SHORT).show()
            }
        }

        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.loginState.observe(this) { result ->
            val loginButton = findViewById<Button>(R.id.loginButton)
            val progressBar = findViewById<android.widget.ProgressBar>(R.id.progressBar)
            
            if (result.isSuccess) {
                Log.d(TAG, "signInWithEmail:success")
                progressBar.visibility = android.view.View.GONE
                loginButton.isEnabled = true
                navigateToMain()
            } else {
                Log.w(TAG, "signInWithEmail:failure", result.exceptionOrNull())
                progressBar.visibility = android.view.View.GONE
                loginButton.isEnabled = true
                Toast.makeText(
                    baseContext,
                    "Authentication failed: ${result.exceptionOrNull()?.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}



