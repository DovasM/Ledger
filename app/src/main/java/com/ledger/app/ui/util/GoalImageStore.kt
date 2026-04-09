package com.ledger.app.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/**
 * Stores goal images in internal storage, keyed by goal name.
 * Uses SharedPreferences to map goal name → file path.
 */
object GoalImageStore {

    private const val PREFS = "goal_images"
    private fun key(goalName: String) = "img_${goalName.trim()}"

    /** Copy image from [uri] to internal storage and record the path under [goalName]. */
    fun save(context: Context, goalName: String, uri: Uri) {
        val sanitized = goalName.trim().replace("[^a-zA-Z0-9_\\-]".toRegex(), "_")
        val destFile = File(context.filesDir, "goal_images/$sanitized.jpg")
        destFile.parentFile?.mkdirs()
        context.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(key(goalName), destFile.absolutePath).apply()
    }

    /** Returns the saved file path for a goal, or null if no image was set. */
    fun getPath(context: Context, goalName: String): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key(goalName), null)

    /** Loads the stored bitmap synchronously, or null if none saved. */
    fun loadBitmap(context: Context, goalName: String): Bitmap? =
        getPath(context, goalName)?.let { BitmapFactory.decodeFile(it) }

    /** Removes the stored image for a goal. */
    fun delete(context: Context, goalName: String) {
        getPath(context, goalName)?.let { File(it).delete() }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(key(goalName)).apply()
    }
}
