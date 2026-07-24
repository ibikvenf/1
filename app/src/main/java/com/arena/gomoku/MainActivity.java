package com.arena.gomoku;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.KeyEvent;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private WebView mWebView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Setup simple layout with full screen WebView
        mWebView = new WebView(this);
        setContentView(mWebView);

        // Configure robust WebView Settings for rich local HTML5 gameplay
        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true); // Supports localStorage / sessionStorage
        webSettings.setDatabaseEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        
        // Critical for allowing local asset files to load external JS/CSS dependencies
        webSettings.setAllowFileAccessFromFileURLs(true);
        webSettings.setAllowUniversalAccessFromFileURLs(true);
        
        // Dynamic dynamic Web Audio API auto-play policy override
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        
        // Hardware acceleration support
        mWebView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null);
        
        // Keep screen on during playing
        mWebView.setKeepScreenOn(true);

        // Standard custom client redirects
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }
        });

        // Set WebChromeClient to handle JavaScript dialog alerts nicely inside Native dialogs
        mWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView view, String url, String message, final JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("五子棋对局")
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, new AlertDialog.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                result.confirm();
                            }
                        })
                        .setCancelable(false)
                        .create()
                        .show();
                return true;
            }
        });

        // Load the local offline-ready webgame assets
        mWebView.loadUrl("file:///android_asset/www/index.html");
    }

    // Handle standard hardware back key redirects (e.g. support undo/navigation)
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BACK)) {
            if (mWebView.canGoBack()) {
                mWebView.goBack();
                return true;
            } else {
                showExitConfirmation();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private void showExitConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("退出游戏")
                .setMessage("确定要退出五子棋大决战吗？")
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
