package com.aven.ai.engine

import kotlinx.serialization.Serializable

/**
 * A user-configured online model. The [id] is a stable UUID used as the model's
 * internal identity and as the lookup key for its separately-stored API key.
 * Non-secret only: the credential never lives here.
 */
@Serializable
data class RemoteModelConfig(
    val id: String,
    val providerName: String,
    val baseUrl: String,
    val modelId: String
) {
    companion object {
        const val MAX_MODELS = 10
    }
}

/**
 * Human-readable display name derived deterministically from a raw model ID.
 * Presentation only: the underlying ID is never modified, and it remains the
 * sole value used for API requests, persistence, model lookup, provider
 * configuration and selection state. No information is invented and no
 * brand/provider mapping is involved — the output is fully rule-based.
 *
 * Keeps the model family, its versions and human-facing variants
 * (Mini, Small, Medium, Large, Fast, Sonnet, Opus, Haiku, Coder, Super, Plus).
 * Removes technical specs: parameter counts (`120b`, `7b`, `8x7b`), architecture
 * counts (`a12b`, `e4b`), quantization (`q4_k_m`, `q8_0`, `gguf`, `fp8`), technical
 * suffixes (`instruct`, `chat`, `it`, `lite`, and everything after `distill`),
 * the `repo/` prefix and the `:tag` routing suffix.
 *
 * Examples:
 * - `nvidia/nemotron-3-super-120b-a12b:free` → `Nemotron 3 Super`
 * - `deepseek-ai/DeepSeek-R1-Distill-Qwen-7B` → `DeepSeek R1`
 * - `google/gemma-3n-e4b-it` → `Gemma 3n`
 * - `anthropic/claude-sonnet-4-5` → `Claude Sonnet 4.5`
 */
fun formatModelDisplayName(modelId: String): String {
    val trimmed = modelId.trim()
    val core = trimmed.substringAfterLast('/').substringBefore(':')

    var tokens = core.split(Regex("[-_]+"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    // `...-Distill-<family>-<size>`: everything from `Distill` onward is
    // attribution rather than identity, so cut it along with the marker.
    val distill = tokens.indexOfFirst { it.equals("distill", ignoreCase = true) }
    if (distill >= 0) tokens = tokens.take(distill)

    // A k_m/k_s quantifier shatters into lone k/m/s tokens on split; drop them.
    val stripSingles = "k_m" in core.lowercase() || "k_s" in core.lowercase()

    tokens = tokens.filterNot(::isTechnicalToken)
    if (stripSingles) tokens = tokens.filterNot { it.length == 1 }

    // Fused family+version (`qwen3.5`, left bare by `qwen3.5-9b`) → `qwen` + `3.5`.
    // The prefix must be >= 2 lowercase letters so `v2`/`r1` stay whole.
    tokens = tokens.flatMap(::splitFusedFamilyVersion).filterNot(::isTechnicalToken)
    if (stripSingles) tokens = tokens.filterNot { it.length == 1 }

    // A digit-flanked separator is a version seam: `4-5` → `4.5`
    // (`claude-sonnet-4-5` → `Claude Sonnet 4.5`, never `Claude Sonnet 45`).
    tokens = mergeVersionParts(tokens)

    val result = tokens.joinToString(" ") { prettifyToken(it) }.trim()
    return result.ifBlank { trimmed }
}

private fun isTechnicalToken(token: String): Boolean {
    val t = token.lowercase()
    if (t in TECHNICAL_SUFFIXES || t in QUANT_TOKENS) return true
    if (t == "0") return true // `q8_0` shard
    if (t.length > 1 && t[0] == 'q' && t[1].isDigit()) return true // q4, q8, q4_k_m core
    if ("k_m" in t || "k_s" in t) return true
    if (t.matches(PARAM_COUNT)) return true // 120b, 7B, 8x7b, 3.5.9b
    if (t.matches(ARCH_COUNT)) return true // a12b, e4b
    return false
}

/** Non-human-facing suffixes; `distill` truncates the list instead. */
private val TECHNICAL_SUFFIXES = setOf("instruct", "chat", "it", "lite")

private val QUANT_TOKENS = setOf(
    "gguf", "safetensors", "awq", "gptq", "fp8", "int8", "int4"
)

/** Parameter counts and MoE architectures: `120b`, `7B`, `8x7b`, `1.8b`, `3.5.9b`. */
private val PARAM_COUNT = Regex("(?i)^\\d+(\\.\\d+)*([x×]\\d+)?[bkmgt]$")

/** Letter-led activation counts: `a12b`, `e4b`. Keeps `v2`, `r1`, `3n`. */
private val ARCH_COUNT = Regex("(?i)^[a-z]\\d+[bkmgt]$")

private val FUSED_FAMILY_VERSION = Regex("^([a-z]{2,})(\\d.*)$")

private val DIGITS_ONLY = Regex("^\\d+$")

private fun splitFusedFamilyVersion(token: String): List<String> {
    val m = FUSED_FAMILY_VERSION.matchEntire(token) ?: return listOf(token)
    return listOf(m.groupValues[1], m.groupValues[2])
}

/** Folds adjacent bare-digit tokens into one dotted version: `4`,`5` → `4.5`. */
private fun mergeVersionParts(tokens: List<String>): List<String> {
    val out = ArrayList<String>(tokens.size)
    for (token in tokens) {
        val prev = out.lastOrNull()
        if (prev != null && prev.matches(DIGITS_ONLY) && token.matches(DIGITS_ONLY)) {
            out[out.size - 1] = "$prev.$token"
        } else {
            out.add(token)
        }
    }
    return out
}

private fun prettifyToken(token: String): String {
    // Versions and numbers pass through untouched: `3.5`, `4.5`, `12`.
    if (!token.any { it.isLetter() }) return token
    // Digit-bearing tokens keep their casing, only the first letter is raised:
    // `v2` → `V2`, `3n` → `3n`, `V3.2` → `V3.2`.
    if (token.any { it.isDigit() }) return token.replaceFirstChar { it.uppercaseChar() }
    // Digit-free: short or generic acronyms uppercase (`gpt` → `GPT`).
    if (token.length <= 2 || token.lowercase() in ACRONYMS) return token.uppercase()
    // Otherwise capitalize only the first letter, never lowercase: DeepSeek, Qwen.
    return token.replaceFirstChar { it.uppercaseChar() }
}

/** Generic technology acronyms — not model identities, never model-specific. */
private val ACRONYMS = setOf("gpt", "llm")

/** Why a model configuration cannot be used, or null when it is valid. */
fun RemoteModelConfig.validationError(): String? = when {
    providerName.isBlank() -> "Enter a provider name."
    baseUrl.isBlank() -> "Enter the base gateway URL."
    !isValidGatewayUrl(baseUrl) -> "Enter a valid http(s) gateway URL."
    modelId.isBlank() -> "Enter the model ID."
    else -> null
}

private fun isValidGatewayUrl(raw: String): Boolean {
    val trimmed = raw.trim()
    if (!(trimmed.startsWith("http://") || trimmed.startsWith("https://"))) return false
    return runCatching { java.net.URI(trimmed).host }.getOrNull()?.isNotBlank() == true
}