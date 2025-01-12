package com.mrannonymus.discordwithnormalnotificationsunifiedpush

import android.os.Build
import android.util.Log
import android.webkit.*
import androidx.annotation.RequiresApi

class DiscordWebViewClient : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false
        Log.d("DiscordWebView", "Loading URL: $url")
        
        // Handle Discord URLs internally, let system handle external URLs
        return if (url.startsWith("https://discord.com") || url.startsWith("https://cdn.discordapp.com")) {
            view?.loadUrl(url)
            true
        } else {
            false
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        Log.d("DiscordWebView", "Page finished loading: $url")
        
        // Inject viewport meta tag and CSS fixes
        view?.evaluateJavascript("""
            // Add viewport meta tag if not present
            if (!document.querySelector('meta[name="viewport"]')) {
                const viewport = document.createElement('meta');
                viewport.name = 'viewport';
                viewport.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no';
                document.head.appendChild(viewport);
            }
            
            // Apply desktop mode styles
            document.body.style.cssText += `
                width: 100vw !important;
                height: 100vh !important;
                overflow-x: hidden !important;
                overflow-y: auto !important;
                position: fixed !important;
                top: 0 !important;
                left: 0 !important;
            `;
        """.trimIndent(), null)
        
        // Enable PWA features
        view?.evaluateJavascript("""
            if ('serviceWorker' in navigator) {
                navigator.serviceWorker.register('/service-worker.js')
                    .then(function(registration) {
                        console.log('Service Worker registered');
                    })
                    .catch(function(err) {
                        console.log('Service Worker registration failed: ', err);
                    });
            }
        """.trimIndent(), null)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        val errorUrl = request?.url?.toString() ?: "unknown"
        Log.e("DiscordWebView", "Error loading $errorUrl: ${error?.description} (${error?.errorCode})")
        
        // Only reload on specific errors
        if (error?.errorCode == ERROR_FAILED_SSL_HANDSHAKE || 
            error?.errorCode == ERROR_TIMEOUT) {
            view?.reload()
        } else {
            super.onReceivedError(view, request, error)
        }
    }
} 