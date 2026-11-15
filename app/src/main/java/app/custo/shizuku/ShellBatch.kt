package app.custo.shizuku

/**
 * Runs many read-only commands in one `sh -c` call: each command's output is preceded by a marker
 * line, so one round trip through Shizuku replaces many. Pure, so it unit-tests on the JVM.
 */
object ShellBatch {
    const val MARK = "@@custo-batch@@"

    /** One script for [commands] (id -> command). Ids must be single-line and quote-free. */
    fun script(commands: List<Pair<String, String>>): String =
        commands.joinToString("; ") { (id, cmd) -> "echo '$MARK$id'; $cmd 2>&1" }

    /** Splits [output] back into id -> that command's output (trailing newlines trimmed). */
    fun split(output: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        var id: String? = null
        val buf = StringBuilder()
        fun flush() {
            id?.let { out[it] = buf.toString().trimEnd('\n', '\r') }
            buf.setLength(0)
        }
        for (line in output.lineSequence()) {
            if (line.startsWith(MARK)) {
                flush()
                id = line.removePrefix(MARK).trim()
            } else if (id != null) {
                buf.append(line).append('\n')
            }
        }
        flush()
        return out
    }
}
