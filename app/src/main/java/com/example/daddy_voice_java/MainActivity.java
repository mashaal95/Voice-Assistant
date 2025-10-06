package com.example.daddy_voice_java;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

// Media3
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.common.TrackSelectionParameters;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int REQ_RECORD_AUDIO = 1001;

    private TextView status;
    private FrameLayout root;

    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;

    private ExoPlayer player;

    private WebView geoNewsWebView = null;
    private WebView dunyaNewsWebView = null;


    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // --- minimal UI ---
        root = new FrameLayout(this);
        status = new TextView(this);
        status.setTextSize(34f);
        status.setTypeface(null, Typeface.BOLD);
        status.setGravity(Gravity.CENTER);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        status.setPadding(pad, pad, pad, pad);
        status.setText("Press the down volume key and say “Geo News” or “Dunya News”. Press the up volume key to stop.");
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        params.gravity = Gravity.CENTER;
        root.addView(status, params);
        setContentView(root);

        // --- Speech setup ---
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { isListening = true; setStatus("Listening…"); }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() { isListening = false; }
            @Override public void onError(int error) { isListening = false; setStatus("Speech error (" + error + ")"); }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                String said = (list != null && !list.isEmpty()) ? list.get(0).trim() : "";
                setStatus("You said: " + said);

                String norm = said.toLowerCase(Locale.getDefault());

// STOP?
                if (norm.contains("stop") || norm.contains("band karo") || norm.contains("ruk jao")) {
                    stopAllPlayback();
                    isListening = false;
                    return;
                }

// PLAY GEO NEWS?
                if (norm.contains("geo news") || norm.contains("jio news")
                        || norm.contains("gio news") || norm.contains("geonews")) {
                    playGeoNewsAudio();
                }

// PLAY DUNYA NEWS?
                if (norm.contains("dunya news") || norm.contains("dunia news") || norm.contains("duniya news")) {
                    playDunyaNewsAudio();
                }

                isListening = false;

            }

            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });
    }

    private void stopAllPlayback() {
        // Stop ExoPlayer
        if (player != null) {
            try {
                player.stop();
                player.clearMediaItems();
            } catch (Throwable ignored) {}
            try {
                player.release();
            } catch (Throwable ignored) {}
            player = null;
        }

        // Kill any in-flight resolver WebViews
        if (geoNewsWebView != null) {
            try { root.removeView(geoNewsWebView); } catch (Throwable ignored) {}
            try { geoNewsWebView.destroy(); } catch (Throwable ignored) {}
            geoNewsWebView = null;
        }
        if (dunyaNewsWebView != null) {
            try { root.removeView(dunyaNewsWebView); } catch (Throwable ignored) {}
            try { dunyaNewsWebView.destroy(); } catch (Throwable ignored) {}
            dunyaNewsWebView = null;
        }

        setStatus("Stopped.");
    }


    // Start listening when user presses a volume key
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (!isListening) {
                checkPermissionAndStartListening();
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            stopAllPlayback();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void checkPermissionAndStartListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            startListening();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQ_RECORD_AUDIO);
        }
    }

    private void startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            setStatus("Speech recognition not available.");
            return;
        }
        if (isListening) return;

        Intent srIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        srIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        srIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        srIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        speechRecognizer.startListening(srIntent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startListening();
            } else {
                setStatus("Microphone permission denied.");
            }
        }
    }

    // ---------------- Geo News flow ----------------

    private void playGeoNewsAudio() {
        setStatus("Resolving Geo News Stream…");
        resolveGeoNewsStream(new UrlCallback() {
            @Override public void onResolved(String url) {
                setStatus("Playing Geo News…");
                Map<String, String> headers = new HashMap<>();
                headers.put("Origin",  "https://live.geo.tv");
                headers.put("Referer", "https://live.geo.tv/");
                playM3u8WithHeaders(url, headers);
            }
            @Override public void onError(Throwable t) {
                Log.e(TAG, "Geo News Stream resolution failed", t);
                setStatus("Couldn’t resolve Geo News stream.");
            }
        });
    }

    // ---------------- Dunya News flow ----------------

    private void playDunyaNewsAudio() {
        setStatus("Resolving Dunya News Stream…");
        resolveDunyaNewsStream(new UrlCallback() {
            @Override public void onResolved(String url) {
                setStatus("Playing Dunya News…");
                Map<String, String> headers = new HashMap<>();
                headers.put("Origin", "https://dunyanews.tv");
                headers.put("Referer", "https://dunyanews.tv/");
                playM3u8WithHeaders(url, headers);
            }
            @Override public void onError(Throwable t) {
                Log.e(TAG, "Dunya News Stream resolution failed", t);
                setStatus("Couldn’t resolve Dunya News stream.");
            }
        });
    }


    interface UrlCallback {
        void onResolved(String url);
        void onError(Throwable t);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void resolveDunyaNewsStream(final UrlCallback cb) {
        dunyaNewsWebView = new WebView(this);
        WebSettings s = dunyaNewsWebView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false); // <— allow autoplay
        s.setLoadsImagesAutomatically(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        dunyaNewsWebView.setVisibility(View.GONE);
        root.addView(dunyaNewsWebView, new FrameLayout.LayoutParams(1, 1));

        dunyaNewsWebView.setWebViewClient(new WebViewClient() {
            private boolean kicked = false;

            @Override
            public void onPageFinished(WebView view, String url) {
                // JW Player is used on this page; nudge it to start.
                if (!kicked) {
                    kicked = true;
                    String js =
                            "(function(){try{" +
                                    " if(window.jwplayer){ var p=window.jwplayer('uklive'); if(p){ p.play(true); } }" +
                                    "}catch(e){} return true;})();";
                    view.evaluateJavascript(js, null);
                }
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String u = (request != null && request.getUrl() != null) ? request.getUrl().toString() : "";
                // Don’t restrict to a single host — just grab any .m3u8
                if (u.endsWith(".m3u8")) {
                    runOnUiThread(() -> {
                        try { cb.onResolved(u); }
                        catch (Throwable t) { cb.onError(t); }
                        finally {
                            if (dunyaNewsWebView != null) {
                                try { root.removeView(dunyaNewsWebView); } catch (Throwable ignored) {}
                                dunyaNewsWebView.destroy();
                                dunyaNewsWebView = null;
                            }
                        }
                    });
                }
                return super.shouldInterceptRequest(view, request);
            }
        });

        try {
            dunyaNewsWebView.loadUrl("https://dunyanews.tv/live/");
        } catch (Throwable t) {
            cb.onError(t);
            if (dunyaNewsWebView != null) {
                try { root.removeView(dunyaNewsWebView); } catch (Throwable ignored) {}
                dunyaNewsWebView.destroy();
                dunyaNewsWebView = null;
            }
        }
    }


    @SuppressLint("SetJavaScriptEnabled")
    private void resolveGeoNewsStream(final UrlCallback cb) {

        geoNewsWebView = new WebView(this);
        geoNewsWebView.getSettings().setJavaScriptEnabled(true);
        geoNewsWebView.getSettings().setDomStorageEnabled(true);
        geoNewsWebView.setVisibility(View.GONE);
        root.addView(geoNewsWebView, new FrameLayout.LayoutParams(1, 1));

        // They currently serve HLS from 5centscdn; add more hosts here if they switch.
        final String[] m3u8Hosts = new String[] {
                "5centscdn.com",        // current CDN
                "hls",                  // loose fallback
                "cdn", "edge"           // extra safety
        };

        geoNewsWebView.setWebViewClient(new WebViewClient() {
            private boolean triedSubmit = false;

            @Override
            public void onPageFinished(WebView view, String url) {
                // After the first load of https://live.geo.tv/ submit the form to switch to “Stream 2”.
                // Two ways: call their function OR set hidden input and submit. We try both.
                if (!triedSubmit) {
                    triedSubmit = true;
                    String js =
                            "(function(){"
                                    + "try{"
                                    + "  if (typeof get_stream === 'function'){ get_stream('stream1'); return 'ok-fn'; }"
                                    + "}catch(e){}"
                                    + "try{"
                                    + "  var f=document.getElementById('stream_post');"
                                    + "  var i=document.getElementById('currentStream');"
                                    + "  if(f && i){ i.value='stream1'; f.submit(); return 'ok-form'; }"
                                    + "}catch(e){}"
                                    + "return 'no-op';"
                                    + "})();";
                    view.evaluateJavascript(js, null);
                }
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String u = (request != null && request.getUrl() != null) ? request.getUrl().toString() : "";
                if (u.endsWith(".m3u8")) {
                    for (String host : m3u8Hosts) {
                        if (u.contains(host)) {
                            runOnUiThread(() -> {
                                try { cb.onResolved(u); }
                                catch (Throwable t) { cb.onError(t); }
                                finally {
                                    if (geoNewsWebView != null) {
                                        root.removeView(geoNewsWebView);
                                        geoNewsWebView.destroy();
                                        geoNewsWebView = null;
                                    }
                                }
                            });
                            break;
                        }
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }
        });

        try {
            geoNewsWebView.loadUrl("https://live.geo.tv/");
        } catch (Throwable t) {
            cb.onError(t);
            if (geoNewsWebView != null) {
                root.removeView(geoNewsWebView);
                geoNewsWebView.destroy();
                geoNewsWebView = null;
            }
        }
    }


    @OptIn(markerClass = UnstableApi.class)
    private void playM3u8WithHeaders(String m3u8, Map<String, String> headers) {
        DefaultHttpDataSource.Factory dsFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent("GeoNewsAudio/1.0");
        if (headers != null) {
            dsFactory.setDefaultRequestProperties(headers);
        }

        HlsMediaSource hls = new HlsMediaSource.Factory(dsFactory)
                .createMediaSource(MediaItem.fromUri(m3u8));

        if (player != null) player.release();
        player = new ExoPlayer.Builder(this).build();

        // Prefer audio: disable VIDEO track type
        TrackSelectionParameters params = player.getTrackSelectionParameters()
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                .build();
        player.setTrackSelectionParameters(params);

        player.setMediaSource(hls);
        player.prepare();
        player.setPlayWhenReady(true);
    }


    private void setStatus(final String s) {
        runOnUiThread(() -> status.setText(s));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) speechRecognizer.destroy();
        if (player != null) { player.release(); player = null; }
        if (geoNewsWebView != null) {
            geoNewsWebView.destroy();
            geoNewsWebView = null;
        }
        if (dunyaNewsWebView != null) {
            dunyaNewsWebView.destroy();
            dunyaNewsWebView = null;
        }
    }
}
