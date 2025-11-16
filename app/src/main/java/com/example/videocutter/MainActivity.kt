package com.example.videocutter

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// Note: Using local FFmpegShim. Replace with real FFmpegKit dependency for production:
// import com.arthenica.ffmpegkit.FFmpegKit
// import com.arthenica.ffmpegkit.ReturnCode

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private var running = false

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        webView = findViewById(R.id.webview)
        webView.settings.javaScriptEnabled = true
        webView.webChromeClient = WebChromeClient()
        webView.addJavascriptInterface(NativeBridge(), "AndroidBridge")
        webView.loadUrl("file:///android_asset/www/index.html")
    }

    inner class NativeBridge {
        @JavascriptInterface
        fun startProcess(payloadJson: String) {
            if (running) return
            running = true
            Thread {
                val root = JSONObject(payloadJson)
                val files = root.optJSONArray("files") ?: JSONArray()
                val outDir = File(getExternalFilesDir(null), "outputs")
                outDir.mkdirs()
                for (i in 0 until files.length()) {
                    val f = files.getJSONObject(i)
                    val inputPath = f.optString("path") // expect native provided path
                    val ranges = f.optJSONArray("ranges") ?: JSONArray()
                    for (r in 0 until ranges.length()) {
                        val pair = ranges.getJSONArray(r)
                        val s = pair.getDouble(0)
                        val e = pair.getDouble(1)
                        val outFile = File(outDir, "cut-${System.currentTimeMillis()}-${i}-${r}.mp4")
                        val cmd = "-y -ss $s -to $e -i \"$inputPath\" -c copy \"${outFile.absolutePath}\""
                        val session = FFmpegKit.execute(cmd)
                        val rc = session.returnCode
                        if (ReturnCode.isSuccess(rc)) {
                            runOnUiThread {
                                webView.evaluateJavascript("window.onSegmentDone && window.onSegmentDone(${JSONObject().put("path", outFile.absolutePath)})", null)
                            }
                        } else {
                            val err = session.failStackTrace ?: "FFmpeg error"
                            runOnUiThread {
                                webView.evaluateJavascript("window.onError && window.onError(${JSONObject().put("msg", err)})", null)
                            }
                        }
                    }
                }
                running = false
                runOnUiThread { webView.evaluateJavascript("window.onNativeStatus && window.onNativeStatus('finished')", null) }
            }.start()
        }

        @JavascriptInterface
        fun stopProcess() {
            FFmpegKit.cancel()
            running = false
            runOnUiThread { webView.evaluateJavascript("window.onNativeStatus && window.onNativeStatus('cancelled')", null) }
        }
    }
}
