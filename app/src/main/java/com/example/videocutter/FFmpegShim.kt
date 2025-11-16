package com.example.videocutter

// Shim to allow building without the external FFmpegKit AAR.
// Replace with real FFmpegKit dependency for production behavior.

object FFmpegKit {
    data class Session(val returnCode: Int, val failStackTrace: String?)

    object ReturnCode {
        fun isSuccess(rc: Int?): Boolean = rc != null && rc == 0
    }

    private var cancelled = false

    fun execute(cmd: String): Session {
        // Shim: pretends command succeeded.
        cancelled = false
        return Session(0, null)
    }

    fun cancel() {
        cancelled = true
    }
}

object ReturnCode {
    fun isSuccess(rc: Int?): Boolean = rc != null && rc == 0
}
