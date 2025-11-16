package com.example.videocutter

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
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
        try {
            setContentView(R.layout.activity_main)
            webView = findViewById(R.id.webview)
            
            // Configure WebView settings
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
            }
            
            // Set WebView clients
            webView.webChromeClient = WebChromeClient()
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    android.util.Log.d("WebView", "Page loaded: $url")
                }
                
                override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                    super.onReceivedError(view, request, error)
                    android.util.Log.e("WebView", "Error loading ${request?.url}: ${error?.description}")
                }
            }
            
            // Add JavaScript interface
            webView.addJavascriptInterface(NativeBridge(), "AndroidBridge")
            
            // Load the HTML file
            webView.loadUrl("file:///android_asset/www/index.html")
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "onCreate error", e)
            throw e
        }
    }

    inner class NativeBridge {
        @JavascriptInterface
        fun startProcess(payloadJson: String) {
            if (running) {
                runOnUiThread {
                    webView.evaluateJavascript("window.onError && window.onError({msg: 'Process already running'})", null)
                }
                return
            }
            running = true
            Thread {
                try {
                    val root = JSONObject(payloadJson)
                    val files = root.optJSONArray("files") ?: JSONArray()
                    val outDir = File(getExternalFilesDir(null), "outputs")
                    outDir.mkdirs()
                    
                    for (i in 0 until files.length()) {
                        val f = files.getJSONObject(i)
                        val inputPath = f.optString("path")
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
                                    webView.evaluateJavascript(
                                        "window.onSegmentDone && window.onSegmentDone({path: '${outFile.absolutePath}'})",
                                        null
                                    )
                                }
                            } else {
                                val err = session.failStackTrace ?: "FFmpeg error"
                                runOnUiThread {
                                    webView.evaluateJavascript(
                                        "window.onError && window.onError({msg: '${err.replace("'", "\\'").replace("\n", "\\n")}'})",
                                        null
                                    )
                                }
                            }
                        }
                    }
                    
                    running = false
                    runOnUiThread {
                        webView.evaluateJavascript("window.onNativeStatus && window.onNativeStatus('finished')", null)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("NativeBridge", "startProcess error", e)
                    running = false
                    runOnUiThread {
                        webView.evaluateJavascript(
                            "window.onError && window.onError({msg: '${e.message?.replace("'", "\\'")?.replace("\n", "\\n") ?: "Unknown error"}'})",
                            null
                        )
                    }
                }
            }.start()
        }

        @JavascriptInterface
        fun stopProcess() {
            try {
                FFmpegKit.cancel()
                running = false
                runOnUiThread {
                    webView.evaluateJavascript("window.onNativeStatus && window.onNativeStatus('cancelled')", null)
                }
            } catch (e: Exception) {
                android.util.Log.e("NativeBridge", "stopProcess error", e)
            }
        }
    }
}
