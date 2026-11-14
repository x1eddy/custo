package app.custo.shizuku

data class ShellResult(val code: Int, val output: String) {
    val ok: Boolean get() = code == 0
    val text: String get() = output.trim()

    /** One readable line out of whatever a failing command printed (often a Java stack trace). */
    val error: String get() {
        val lines = output.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val ex = lines.firstOrNull { it.contains("Exception:") }?.substringAfter("Exception:")?.trim()
        return ex ?: lines.firstOrNull() ?: "Command failed (code $code)"
    }

    companion object {
        fun parse(raw: String): ShellResult {
            val nl = raw.indexOf('\n')
            if (nl < 0) return ShellResult(raw.toIntOrNull() ?: -1, "")
            return ShellResult(raw.substring(0, nl).toIntOrNull() ?: -1, raw.substring(nl + 1))
        }
    }
}

/** Something that can run shell commands with elevated (ADB-level) privileges. */
interface Shell {
    suspend fun run(command: String): ShellResult

    /** Register + enable a fabricated overlay owned by the shell package. Throws on failure. */
    suspend fun fabricate(target: String, name: String, resource: String, type: Int, value: Int)

    /** Remove a fabricated overlay created by [fabricate]. Throws on failure. */
    suspend fun unfabricate(name: String)
}

class ShellUnavailableException : IllegalStateException("Shizuku is not ready")
