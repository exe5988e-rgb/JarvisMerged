package com.syncwave.app

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.*
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var errorView: FrameLayout
    private lateinit var errorText: TextView

    private val serverUrl = BuildConfig.SERVER_URL

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView      = findViewById(R.id.webView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        progressBar  = findViewById(R.id.progressBar)
        errorView    = findViewById(R.id.errorView)
        errorText    = findViewById(R.id.errorText)

        setupWebView()
        setupSwipeRefresh()
        loadApp()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled                = true
            domStorageEnabled                = true
            allowFileAccess                  = true
            allowContentAccess               = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode                 = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode                        = WebSettings.LOAD_DEFAULT
            setSupportZoom(false)
            displayZoomControls              = false
            builtInZoomControls              = false
            loadWithOverviewMode             = true
            useWideViewPort                  = true
            databaseEnabled                  = true
        }

        webView.addJavascriptInterface(AndroidBridge(this), "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE
                errorView.visibility   = View.GONE
                webView.visibility     = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility    = View.GONE
                swipeRefresh.isRefreshing = false
                injectTheme()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    progressBar.visibility    = View.GONE
                    webView.visibility        = View.GONE
                    errorView.visibility      = View.VISIBLE
                    swipeRefresh.isRefreshing = false
                    errorText.text =
                        "Cannot connect to SyncWave server.\n\n" +
                        "Make sure Termux server is running.\n\n" +
                        "${error?.description}"
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url.toString()
                if (!url.startsWith(serverUrl) &&
                    (url.startsWith("http") || url.startsWith("https"))) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    return true
                }
                return false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                if (newProgress == 100) progressBar.visibility = View.GONE
            }
            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.grant(request.resources)
            }
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setColorSchemeColors(getColor(R.color.cyan))
        swipeRefresh.setOnRefreshListener { loadApp() }
    }

    private fun loadApp() {
        errorView.visibility   = View.GONE
        webView.visibility     = View.VISIBLE
        progressBar.visibility = View.VISIBLE
        webView.loadUrl(serverUrl)
    }

    private fun injectTheme() {
        val js = """
            (function(){
                var m = document.querySelector('meta[name=viewport]');
                if(!m){ m=document.createElement('meta'); m.name='viewport'; document.head.appendChild(m); }
                m.content='width=device-width,initial-scale=1.0,maximum-scale=1.0,user-scalable=no';
                document.body.style.userSelect='none';
                document.body.style.webkitUserSelect='none';
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onResume()  { super.onResume();  webView.onResume()  }
    override fun onPause()   { super.onPause();   webView.onPause()   }
    override fun onDestroy() { super.onDestroy(); webView.destroy()   }
}
