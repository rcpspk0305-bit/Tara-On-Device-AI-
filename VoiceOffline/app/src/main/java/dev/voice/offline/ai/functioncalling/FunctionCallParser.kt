package dev.voice.offline.ai.functioncalling

data class RawFunctionCall(
    val name: String,
    val arguments: Map<String, String>
)

/**
 * Deterministic model output parser supporting the FunctionGemma
 * function-call response format:
 * `<start_function_call>call:name{param1:<escape>value1<escape>}<end_function_call>`
 */
object FunctionCallParser {

    private val CALL_REGEX = Regex(
        """<start_function_call>\s*call:([a-zA-Z_][a-zA-Z0-9_]*)\s*\{(.*)\}\s*<end_function_call>""",
        RegexOption.DOT_MATCHES_ALL
    )

    fun parse(modelOutput: String): RawFunctionCall? {
        val trimmed = modelOutput.trim()
        val match = CALL_REGEX.matchEntire(trimmed) ?: return null

        val functionName = match.groupValues[1].trim()
        val argsBlock = match.groupValues[2].trim()

        if (argsBlock.isEmpty()) {
            return RawFunctionCall(functionName, emptyMap())
        }

        val arguments = mutableMapOf<String, String>()
        val argRegex = Regex(
            """\s*([a-zA-Z_][a-zA-Z0-9_]*)\s*:\s*(?:<escape>(.*?)<escape>|([^,{}<>]+))\s*""",
            RegexOption.DOT_MATCHES_ALL
        )

        var position = 0
        while (position < argsBlock.length) {
            val m = argRegex.find(argsBlock, position) ?: return null
            if (m.range.first != position) return null
            val key = m.groupValues[1].trim()
            if (key in arguments) return null
            val value = m.groups[2]?.value ?: m.groupValues[3].trim()
            arguments[key] = value
            position = m.range.last + 1
            if (position < argsBlock.length) {
                if (argsBlock[position] != ',') return null
                position++
                if (position == argsBlock.length) return null
            }
        }

        return RawFunctionCall(functionName, arguments)
    }
}
