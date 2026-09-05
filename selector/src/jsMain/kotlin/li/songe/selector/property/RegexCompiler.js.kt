package li.songe.selector.property

// fork: JS 产物仅用于编译通过（app 只消费 JVM 产物），
// 不引入上游的 npm regex_wasm，直接用 Kotlin/JS 自带 Regex 实现。
internal actual fun String.compilePlatformRegex(): RegexCompileResult {
    val regex = try {
        Regex(this)
    } catch (error: IllegalArgumentException) {
        return RegexCompileResult.Failure(
            listOfNotNull(error::class.simpleName, error.message)
                .distinct()
                .joinToString(": "),
        )
    }
    return RegexCompileResult.Success { input -> regex.matches(input) }
}
