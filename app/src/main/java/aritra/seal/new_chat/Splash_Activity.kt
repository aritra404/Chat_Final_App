package aritra.seal.new_chat

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import java.util.concurrent.Executors

class Splash_Activity : AppCompatActivity() {
    private val TAG = "SplashActivity"
    private lateinit var auth: FirebaseAuth
    private lateinit var initializingText: TextView

    // Using a single thread executor for encryption initialization
    private val executor = Executors.newSingleThreadExecutor()

    // Minimum splash screen display time (milliseconds)
    private val SPLASH_DELAY = 1500L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Initialize Firebase Auth
        auth = Firebase.auth

        // Get UI reference
        initializingText = findViewById(R.id.initializingText)

        // Start initialization process
        initializeAppComponents()
    }

    private fun initializeAppComponents() {
        // Track when the initialization started
        val startTime = System.currentTimeMillis()

        // Run encryption initialization in background
        executor.execute {
            try {
                // Initialize encryption keys
                updateStatusOnUiThread("Initializing encryption...")
                val keyPair = EncryptionUtils.generateRSAKeyPair()
                Log.d(TAG, "Encryption keys initialized successfully")

                // Calculate how long initialization took
                val elapsedTime = System.currentTimeMillis() - startTime
                val remainingDelay = SPLASH_DELAY - elapsedTime

                // Wait at least the minimum splash delay time
                if (remainingDelay > 0) {
                    Thread.sleep(remainingDelay)
                }

                // Check if user is already signed in before proceeding
                runOnUiThread {
                    updateStatusOnUiThread("Checking login status...")
                    navigateToNextScreen()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize encryption keys: ${e.message}")

                // Even if encryption fails, proceed to login after delay
                runOnUiThread {
                    updateStatusOnUiThread("Continuing to app...")
                    Handler(Looper.getMainLooper()).postDelayed({
                        navigateToNextScreen()
                    }, 1000)
                }
            }
        }
    }

    private fun updateStatusOnUiThread(status: String) {
        runOnUiThread {
            initializingText.text = status
        }
    }

    private fun navigateToNextScreen() {
        // Check if user is already signed in
        val currentUser = auth.currentUser

        val intent = if (currentUser != null) {
            // User is signed in, go to main activity
            Intent(this, MainActivity::class.java)
        } else {
            // No user is signed in, go to login
            Intent(this, LoginActivity::class.java)
        }

        // Navigate to appropriate screen
        startActivity(intent)

        // Close splash activity
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Shutdown executor when activity is destroyed
        executor.shutdown()
    }
}