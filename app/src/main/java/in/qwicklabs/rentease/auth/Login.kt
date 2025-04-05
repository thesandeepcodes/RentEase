package `in`.qwicklabs.rentease.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.GoogleAuthProvider
import `in`.qwicklabs.rentease.MainActivity
import `in`.qwicklabs.rentease.R
import `in`.qwicklabs.rentease.constants.Constants
import `in`.qwicklabs.rentease.databinding.ActivityLoginBinding
import `in`.qwicklabs.rentease.utils.Loader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class Login : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth

    private lateinit var loader: Loader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loader = Loader(this)
        loader.dialogTitle.text = "Logging in"

        auth = FirebaseAuth.getInstance()

        binding.continueButton.setOnClickListener {
            CoroutineScope(Dispatchers.Main).launch {
                handleSigning()
            }
        }
    }

    private suspend fun handleSigning() {
        try {
            val googleIdOption =
                GetGoogleIdOption.Builder().setServerClientId(getString(R.string.web_client_id))
                    .setFilterByAuthorizedAccounts(false).setAutoSelectEnabled(true).build()

            val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()

            val credentialManager = CredentialManager.create(this)
            val result = credentialManager.getCredential(this, request)
            val credential = result.credential

            if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleTokenId = GoogleIdTokenCredential.createFrom(credential.data).idToken

                loader.dialog.show()

                firebaseAuthWithGoogle(googleTokenId)
            } else {
                showAlert("Woah-woah!", "Credential is not of type Google ID!", "Okay")
            }
        } catch (e: Exception) {
            if (e !is GetCredentialCancellationException) {
                e.message?.replaceFirstChar { it.uppercase() }
                    ?.let { showAlert("Error", it, "Retry") }
            }
        }
    }

    private fun firebaseAuthWithGoogle(googleTokenId: String) {
        val credential = GoogleAuthProvider.getCredential(googleTokenId, null)
        auth.signInWithCredential(credential).addOnCompleteListener(this) { task ->
            loader.dialog.hide()

            if (task.isSuccessful) {
                Constants().revalidateUser()

                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else {
                val errorMessage = when (val exception = task.exception) {
                    is FirebaseAuthInvalidUserException -> {
                        if (exception.errorCode == "ERROR_USER_DISABLED") {
                            "Your account has been disabled. Please contact support."
                        } else {
                            "User account not found. Please try again."
                        }
                    }

                    is FirebaseAuthException -> {
                        "Authentication failed: ${exception.message}"
                    }

                    else -> {
                        exception?.message?.replaceFirstChar { it.uppercase() }
                            ?: "Unexpected Error Occurred!"
                    }
                }

                showAlert("Oops!", errorMessage, "Okay")
            }
        }
    }

    private fun showAlert(title: String, msg: String, positive: String) {
        loader.dialog.hide()
        MaterialAlertDialogBuilder(this).setTitle(title).setMessage(msg)
            .setPositiveButton(positive) { dialog, _ ->
                dialog.dismiss()
            }.show()
    }
}