package li.songe.gkd.util

import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import li.songe.gkd.app
import java.io.IOException
import java.io.OutputStream

val webpLossyCompressFormat = Bitmap.CompressFormat.WEBP_LOSSY

object ImageUtils {
    fun save2Album(
        src: Bitmap,
        quality: Int = 85,
        format: Bitmap.CompressFormat = webpLossyCompressFormat,
        recycle: Boolean = true,
    ): Boolean {
        val safeDirName = app.packageName
        val suffix = when {
            Bitmap.CompressFormat.JPEG == format -> "JPG"
            format.name.startsWith("WEBP") -> "WEBP"
            else -> format.name
        }
        val fileName = System.currentTimeMillis().toString() + "_" + quality + "." + suffix
        val contentValues = ContentValues()
        contentValues.put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        contentValues.put(MediaStore.Images.Media.MIME_TYPE, "image/*")
        val contentUri: Uri
        if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        } else {
            contentUri = MediaStore.Images.Media.INTERNAL_CONTENT_URI
        }
        contentValues.put(
            MediaStore.Images.Media.RELATIVE_PATH,
            Environment.DIRECTORY_DCIM + "/" + safeDirName
        )
        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 1)
        val uri: Uri = app.contentResolver.insert(contentUri, contentValues) ?: return false
        var os: OutputStream? = null
        try {
            os = app.contentResolver.openOutputStream(uri)
            src.compress(format, quality, os!!)
            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            app.contentResolver.update(uri, contentValues, null, null)
            return true
        } catch (e: Exception) {
            app.contentResolver.delete(uri, null, null)
            e.printStackTrace()
            return false
        } finally {
            try {
                os?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
}
