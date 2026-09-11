package li.songe.gkd.data

import kotlinx.serialization.Serializable
import li.songe.gkd.util.LogUtils
import li.songe.gkd.util.crashFolder
import li.songe.gkd.util.crashTempFolder
import li.songe.gkd.util.format
import li.songe.gkd.util.json

private const val MAX_CRASH_RECORD_COUNT = 20

@Serializable
data class CrashData(
    val id: Long,
    val mtime: Long,
    val device: String,
    val androidVersionCode: Int,
    val androidVersionName: String,
    val versionCode: Int,
    val versionName: String,
    val name: String,
    val message: String?,
    val thread: String,
    val stackTrace: String,
) {
    val filename get() = "gkd_crash-" + mtime.format("yyyyMMdd_HHmmss") + ".json"
    fun save() {
        val text = json.encodeToString(this)
        crashFolder.resolve(filename).writeText(text)
        crashTempFolder.resolve(filename).writeText(text)
        trimCrashDataFiles()
    }

    fun delete(): Boolean = listOf(
        crashFolder.resolve(filename),
        crashTempFolder.resolve(filename),
    ).map { file ->
        !file.exists() || file.delete()
    }.all { it }
}

fun deleteCrashDataList(): Boolean = listOf(
    crashFolder,
    crashTempFolder,
).flatMap { folder ->
    (folder.listFiles() ?: emptyArray()).filter { it.isFile }
}.map { file ->
    !file.exists() || file.delete()
}.all { it }

fun trimCrashDataFiles() {
    listOf(crashFolder, crashTempFolder).forEach { folder ->
        (folder.listFiles() ?: emptyArray())
            .filter { it.isFile }
            .sortedByDescending { it.name }
            .drop(MAX_CRASH_RECORD_COUNT)
            .forEach { file ->
                if (!file.delete()) {
                    LogUtils.d("删除过期崩溃日志失败: ${file.name}")
                }
            }
    }
}

fun loadCrashDataList(): List<CrashData> =
    (crashFolder.listFiles() ?: emptyArray())
        .filter { it.isFile }
        .mapNotNull { file ->
            try {
                json.decodeFromString<CrashData>(file.readText())
            } catch (e: Exception) {
                LogUtils.d("解析崩溃日志失败: ${file.name}", e)
                null
            }
        }
        .sortedByDescending { it.mtime }
