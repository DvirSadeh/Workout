package workout.app.data

import android.content.Context
import workout.app.BuildConfig
import workout.app.auth.GoogleUser

data class SignedInAccount(val id: String, val email: String, val name: String) {
    val databaseName: String get() = "workout-${id.filter { it.isLetterOrDigit() }}.db"
}

class AccountStore(context: Context) {
    private val prefs = context.getSharedPreferences("accounts", Context.MODE_PRIVATE)

    fun current(): SignedInAccount? {
        val id = prefs.getString(ID, null) ?: return null
        val email = prefs.getString(EMAIL, null) ?: return null
        return SignedInAccount(id, email, prefs.getString(NAME, email).orEmpty())
    }

    fun save(user: GoogleUser) {
        prefs.edit()
            .putString(ID, user.id)
            .putString(EMAIL, user.email)
            .putString(NAME, user.name)
            .apply()
    }

    fun clear() {
        prefs.edit().remove(ID).remove(EMAIL).remove(NAME).apply()
    }

    fun webClientId(): String = prefs.getString(WEB, null) ?: BuildConfig.GOOGLE_WEB_CLIENT_ID

    fun setWebClientId(value: String) {
        prefs.edit().putString(WEB, value.trim()).apply()
    }

    fun legacyClaimed(): Boolean = prefs.getBoolean(LEGACY, false)

    fun claimLegacy() {
        prefs.edit().putBoolean(LEGACY, true).apply()
    }

    fun lastBackup(accountId: String): Long = prefs.getLong(backupKey(accountId), 0L)

    fun markBackup(accountId: String) {
        prefs.edit().putLong(backupKey(accountId), System.currentTimeMillis()).apply()
    }

    private fun backupKey(accountId: String) = "backup_$accountId"

    private companion object {
        const val ID = "account_id"
        const val EMAIL = "account_email"
        const val NAME = "account_name"
        const val WEB = "web_client_id"
        const val LEGACY = "legacy_claimed"
    }
}
