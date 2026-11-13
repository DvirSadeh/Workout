package workout.app.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

class DownloadsBackup(private val context: Context) {
    private val prefs = context.getSharedPreferences("local_backup", Context.MODE_PRIVATE)

    fun enabled(): Boolean = prefs.getBoolean(ENABLED, false)

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(ENABLED, value).apply()
    }

    fun write(json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        if (Build.VERSION.SDK_INT >= 29) {
            val existing = find()
            if (existing != null && writeTo(existing, bytes)) return
            if (!writeTo(create(), bytes)) error("Could not save the file in Downloads.")
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!dir.exists()) dir.mkdirs()
            File(dir, FILE_NAME).writeBytes(bytes)
        }
    }

    private fun find(): Uri? {
        if (Build.VERSION.SDK_INT < 29) return null
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME}=?",
            arrayOf(FILE_NAME),
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            return ContentUris.withAppendedId(collection, cursor.getLong(0))
        }
        return null
    }

    private fun create(): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, FILE_NAME)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        return context.contentResolver.insert(
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            values,
        ) ?: error("Could not create the file in Downloads.")
    }

    private fun writeTo(uri: Uri, bytes: ByteArray): Boolean = runCatching {
        context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
            stream.write(bytes)
            true
        } ?: false
    }.getOrDefault(false)

    private companion object {
        const val ENABLED = "enabled"
        const val FILE_NAME = "workout-backup.json"
    }
}
