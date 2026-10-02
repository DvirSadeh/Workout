package workout.app.auth

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import org.json.JSONObject
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

data class GoogleUser(val id: String, val email: String, val name: String)

sealed interface DriveAccess {
    data class Token(val token: String) : DriveAccess
    data class Consent(val pending: PendingIntent) : DriveAccess
    data class Failed(val message: String) : DriveAccess
}

class SignInCancelled : Exception()

class GoogleAuth(private val context: Context) {
    suspend fun signIn(activity: Activity, webClientId: String): GoogleUser {
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()
        try {
            val result = CredentialManager.create(context).getCredential(activity, request)
            val credential = result.credential
            if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                throw IllegalStateException("Google did not return an account.")
            }
            val google = GoogleIdTokenCredential.createFrom(credential.data)
            val subject = subjectOf(google.idToken).ifBlank { google.id }
            return GoogleUser(
                id = subject,
                email = google.id,
                name = google.displayName?.takeIf { it.isNotBlank() } ?: google.id,
            )
        } catch (cancelled: GetCredentialCancellationException) {
            throw SignInCancelled()
        } catch (missing: NoCredentialException) {
            throw missing
        }
    }

    suspend fun driveAccess(activity: Activity): DriveAccess = driveAccess(activity as Context)

    suspend fun silentDriveToken(): String? = when (val access = driveAccess(context)) {
        is DriveAccess.Token -> access.token
        else -> null
    }

    fun tokenFromIntent(data: Intent?): String {
        val result = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
        return result.accessToken ?: throw IllegalStateException("Google did not return Drive access.")
    }

    private suspend fun driveAccess(host: Context): DriveAccess {
        return try {
            val request = AuthorizationRequest.builder().setRequestedScopes(DRIVE_SCOPES).build()
            val result = Identity.getAuthorizationClient(host).authorize(request).awaitTask()
            when {
                result.hasResolution() -> {
                    val pending = result.pendingIntent
                        ?: return DriveAccess.Failed("Google did not open the account prompt.")
                    DriveAccess.Consent(pending)
                }
                result.accessToken.isNullOrBlank() -> DriveAccess.Failed("Google did not return Drive access.")
                else -> DriveAccess.Token(result.accessToken!!)
            }
        } catch (error: Exception) {
            DriveAccess.Failed(error.message ?: "Google Drive access failed.")
        }
    }

    private fun subjectOf(idToken: String): String {
        val payload = idToken.split('.').getOrNull(1) ?: return ""
        val bytes = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        return JSONObject(String(bytes, Charsets.UTF_8)).optString("sub")
    }

    private companion object {
        val DRIVE_SCOPES = listOf(
            Scope("https://www.googleapis.com/auth/drive.appdata"),
            Scope("openid"),
            Scope("https://www.googleapis.com/auth/userinfo.email"),
        )
    }
}

fun signingSha1(context: Context): String {
    val signatures = if (Build.VERSION.SDK_INT >= 28) {
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SIGNING_CERTIFICATES,
        )
        info.signingInfo?.apkContentsSigners ?: emptyArray()
    } else {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SIGNATURES,
        )
        @Suppress("DEPRECATION")
        info.signatures ?: emptyArray()
    }
    val digest = MessageDigest.getInstance("SHA-1").digest(signatures.firstOrNull()?.toByteArray() ?: return "")
    return digest.joinToString(":") { "%02X".format(it) }
}

private suspend fun <T> Task<T>.awaitTask(): T = suspendCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
}

fun developerError(error: Throwable?): Boolean {
    val api = error as? ApiException ?: error?.cause as? ApiException
    return api?.statusCode == 10
}
