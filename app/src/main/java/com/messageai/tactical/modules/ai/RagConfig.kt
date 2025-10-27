package com.messageai.tactical.modules.ai

/**
 * RAG configuration defaults and budgets. Values are feature-tuned but may be
 * overridden per task call site. Token counts are approximate and model-agnostic.
 */
object RagConfig {
    const val CHUNK_TOKENS = 600
    const val CHUNK_OVERLAP_TOKENS = 80
    const val TOP_K_INITIAL = 8
    const val TOP_K_RERANKED = 4

    // Budgets (input = retrieved context + prompt)
    const val BUDGET_FAST_INPUT_TOK = 1200   // autofill, geo, intent
    const val BUDGET_FAST_OUTPUT_TOK = 300

    const val BUDGET_SUMMARY_INPUT_TOK = 2000   // threat reduce, SITREP reduce
    const val BUDGET_SUMMARY_OUTPUT_TOK = 600

    // Feature-specific
    const val AUTOFILL_CONTEXT_TOK = 1000
    const val THREAT_REDUCE_INPUT_TOK = 1800
    const val SITREP_REDUCE_INPUT_TOK = 2500
    const val INTENT_WINDOW_MSGS_MIN = 20
    const val INTENT_WINDOW_MINUTES = 5
}


