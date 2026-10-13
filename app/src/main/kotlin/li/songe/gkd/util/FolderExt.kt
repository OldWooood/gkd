package li.songe.gkd.util

import android.text.format.DateUtils
import li.songe.gkd.app
import java.io.File

fun File.autoMk(): File {
    if (!exists()) {
        mkdirs()
    }
    return this
}

private val filesDir: File by lazy {
    val markFile = app.filesDir.resolve(".gkd")
    if (markFile.isFile) {
        app.filesDir
    } else {
        // fix #1333
        app.getExternalFilesDir(null) ?: app.filesDir.also {
            markFile.createNewFile()
        }
    }
}

val dbFolder: File
    get() = filesDir.resolve("db").autoMk()
val storeFolder: File
    get() = filesDir.resolve("store").autoMk()
val subsFolder: File
    get() = filesDir.resolve("subscription").autoMk()
val logFolder: File
    get() = filesDir.resolve("log").autoMk()

val privateStoreFolder: File
    get() = app.filesDir.resolve("private-store").autoMk()

private val cacheDir by lazy { app.externalCacheDir ?: app.cacheDir }
val sharedDir: File
    get() = cacheDir.resolve("shared").autoMk()
private val tempDir: File
    get() = cacheDir.resolve("temp").autoMk()

private fun removeExpired(dir: File) {
    dir.listFiles()?.forEach { f ->
        if (System.currentTimeMillis() - f.lastModified() > DateUtils.HOUR_IN_MILLIS) {
            if (f.isDirectory) {
                f.deleteRecursively()
            } else if (f.isFile) {
                f.delete()
            }
        }
    }
}

fun clearCache() {
    removeExpired(sharedDir)
    removeExpired(tempDir)
}
