package com.opuscube.munshi.brain

data class ModelFile(val name: String, val url: String, val sha256: String, val bytes: Long)

// Gemma 4 for LiteRT-LM from litert-community (Apache-2.0, not gated), pinned so the file can't change
// under us. Both files were uploaded 2026-05-04, before LiteRT-LM 0.16.1.
object Models {
    val E4B = ModelFile(
        "gemma-4-E4B-it.litertlm",
        "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/e6a3892a8c22cf87ff99583996149cf3cb32452b/gemma-4-E4B-it.litertlm",
        "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0", 3_659_530_240,
    )
    val E2B = ModelFile(
        "gemma-4-E2B-it.litertlm",
        "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/1caa527c1e88b55f046fcd8d2b4265606680663d/gemma-4-E2B-it.litertlm",
        "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c", 2_588_147_712,
    )

    /** Phones sold as 8 GB report ~7.2–7.6 GB, 6 GB phones ~5.6 GB, 4 GB phones ~3.7 GB. */
    fun forRam(totalBytes: Long): ModelFile? = when {
        totalBytes >= 6_000_000_000 -> E4B
        totalBytes >= 3_500_000_000 -> E2B
        else -> null
    }
}
