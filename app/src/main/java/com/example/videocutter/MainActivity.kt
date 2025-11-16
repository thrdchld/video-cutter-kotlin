package com.example.videocutter

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private const val TAG = "VideoCutter"

// Note: Using local FFmpegShim. Replace with real FFmpegKit dependency for production:
// import com.arthenica.ffmpegkit.FFmpegKit
// import com.arthenica.ffmpegkit.ReturnCode

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private var running = false

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate started")
        
        try {
            // Set content view
            setContentView(R.layout.activity_main)
            Log.d(TAG, "setContentView completed")
            
            // Get WebView reference
            webView = findViewById(R.id.webview)
            if (webView == null) {
                Log.e(TAG, "WebView not found in layout!")
                throw IllegalStateException("WebView with id 'webview' not found")
            }
            Log.d(TAG, "WebView found")
            
            // Configure WebView settings
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                Log.d(TAG, "WebView settings applied")
            }
            
            // Set WebView clients
            webView.webChromeClient = WebChromeClient()
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d(TAG, "Page loaded: $url")
                }
                
                override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                    super.onReceivedError(view, request, error)
                    Log.e(TAG, "Error loading ${request?.url}: ${error?.description}")
                }
            }
            Log.d(TAG, "WebView clients set")
            
            // Add JavaScript interface
            webView.addJavascriptInterface(NativeBridge(), "AndroidBridge")
            Log.d(TAG, "JavaScript interface added")
            
            // Load the HTML file
            Log.d(TAG, "Loading file:///android_asset/www/index.html")
            webView.loadUrl("file:///android_asset/www/index.html")
            Log.d(TAG, "WebView.loadUrl called")
            
        } catch (e: Exception) {
            Log.e(TAG, "onCreate error: ${e.message}", e)
            e.printStackTrace()
            // Show error to user via toast
            android.widget.Toast.makeText(this, "Startup Error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    inner class NativeBridge {
        @JavascriptInterface
        fun startProcess(payloadJson: String) {
            Log.d(TAG, "startProcess called with: $payloadJson")
            
            if (running) {
                Log.w(TAG, "Process already running")
                runOnUiThread {
                    webView.evaluateJavascript("window.onError && window.onError({msg: 'Process already running'})", null)
                }
                return
            }
            
            running = true
            Thread {
                try {
                    Log.d(TAG, "Processing started in background thread")
                    val root = JSONObject(payloadJson)
                    val files = root.optJSONArray("files") ?: JSONArray()
                    Log.d(TAG, "Found ${files.length()} files")
                    
                    val outDir = File(getExternalFilesDir(null), "outputs")
                    outDir.mkdirs()
                    Log.d(TAG, "Output directory: ${outDir.absolutePath}")
                    
                    for (i in 0 until files.length()) {
                        val f = files.getJSONObject(i)
                        val inputPath = f.optString("path")
                        val ranges = f.optJSONArray("ranges") ?: JSONArray()
                        
                        Log.d(TAG, "File $i: inputPath=$inputPath, ranges=${ranges.length()}")
                        
                        for (r in 0 until ranges.length()) {
                            try {
                                val pair = ranges.getJSONArray(r)
                                val s = pair.getDouble(0)
                                val e = pair.getDouble(1)
                                val outFile = File(outDir, "cut-${System.currentTimeMillis()}-${i}-${r}.mp4")
                                
                                Log.d(TAG, "Processing range $r: $s to $e")
                                
                                val cmd = "-y -ss $s -to $e -i \"$inputPath\" -c copy \"${outFile.absolutePath}\""
                                Log.d(TAG, "FFmpeg command: $cmd")
                                
                                val session = FFmpegKit.execute(cmd)
                                val rc = session.returnCode
                                
                                Log.d(TAG, "FFmpeg returned code: $rc")
                                
                                if (ReturnCode.isSuccess(rc)) {
                                    Log.d(TAG, "Segment successful: ${outFile.absolutePath}")
                                    runOnUiThread {
                                        webView.evaluateJavascript(
                                            "window.onSegmentDone && window.onSegmentDone({path: '${outFile.absolutePath}'})",
                                            null
                                        )
                                    }
                                } else {
                                    val err = session.failStackTrace ?: "FFmpeg error"
                                    Log.e(TAG, "FFmpeg failed: $err")
                                    runOnUiThread {
                                        webView.evaluateJavascript(
                                            "window.onError && window.onError({msg: '${err.replace("'", "\\'").replace("\n", "\\n")}'})",
                                            null
                                        )
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error processing range $r", e)
                                runOnUiThread {
                                    webView.evaluateJavascript(
                                        "window.onError && window.onError({msg: '${e.message?.replace("'", "\\'")?.replace("\n", "\\n") ?: "Unknown error"}'})",
                                        null
                                    )
                                }
                            }
                        }
                    }
                    
                    running = false
                    Log.d(TAG, "Processing completed")
                    runOnUiThread {
                        webView.evaluateJavascript("window.onNativeStatus && window.onNativeStatus('finished')", null)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "startProcess error", e)
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
            Log.d(TAG, "stopProcess called")
            try {
                FFmpegKit.cancel()
                running = false
                runOnUiThread {
                    webView.evaluateJavascript("window.onNativeStatus && window.onNativeStatus('cancelled')", null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "stopProcess error", e)
            }
        }
        
        @JavascriptInterface
        fun logMessage(msg: String) {
            Log.d(TAG, "JS: $msg")
        }
    }
}
