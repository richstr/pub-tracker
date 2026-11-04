package com.richstr.pubtracker.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.richstr.pubtracker.data.db.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Photos live in app-private storage; the database only stores file names. */
class PhotoStore(private val context: Context) {
    private val dir: File
        get() = File(context.filesDir, "photos").apply { mkdirs() }

    fun file(fileName: String): File = File(dir, fileName)

    fun newFile(): File = File(dir, "${newId()}.jpg")

    fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    suspend fun importFrom(uri: Uri): File = withContext(Dispatchers.IO) {
        val target = newFile()
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Couldn't read photo.")
        input.use { source -> target.outputStream().use { source.copyTo(it) } }
        target
    }
}
