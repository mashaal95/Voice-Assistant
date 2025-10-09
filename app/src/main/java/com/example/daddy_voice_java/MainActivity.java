package com.example.daddy_voice_java;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.hls.HlsMediaSource;

import org.apache.commons.text.similarity.LevenshteinDistance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.json.JSONException;

/**
 * Combined functionality:
 * - Original news streaming (Geo News & Dunya News) preserved exactly (structure kept same logic)
 * - Added Quran playback intent parsing & streaming (audio-only) without altering existing news code behavior
 * - Volume Down: start listening; Volume Up: stop playback
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "CombinedMain";
    private static final int REQ_RECORD_AUDIO = 2001;

    // --- UI ---
    private TextView status;
    private FrameLayout root;

    // --- Speech ---
    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;

    // --- Player ---
    private ExoPlayer player;

    // --- News Streams (original keywords preserved) ---
    private List<Stream> streams; // Geo & Dunya
    private Stream currentStream;

    // --- Quran Data ---
    private static final Map<String, Integer> SURAH_NAME_TO_NUM = new HashMap<>();
    private static final Map<Integer, Integer> SURAH_NUM_TO_VERSE_COUNT = new HashMap<>();

    // In your MainActivity class, you'll need the list of Surah names.
// You can define this once as a static final array.
    private static final String[] SURAH_NAMES_FOR_BIASING = {
            "Fatiha", "Baqarah", "Imran", "Nisa", "Ma'idah", "An'am", "A'raf", "Anfal", "Tawbah",
            "Yunus", "Hud", "Yusuf", "Ra'd", "Ibrahim", "Hijr", "Nahl", "Isra", "Kahf", "Maryam",
            "Taha", "Anbiya", "Hajj", "Mu'minun", "Nur", "Furqan", "Shu'ara", "Naml", "Qasas",
            "Ankabut", "Rum", "Luqman", "Sajdah", "Ahzab", "Saba", "Fatir", "Ya-Sin", "Saffat",
            "Sad", "Zumar", "Ghafir", "Fussilat", "Shura", "Zukhruf", "Dukhan", "Jathiyah",
            "Ahqaf", "Muhammad", "Fath", "Hujurat", "Qaf", "Dhariyat", "Tur", "Najm", "Qamar",
            "Rahman", "Waqi'ah", "Hadid", "Mujadila", "Hashr", "Mumtahanah", "Saff", "Jumu'ah",
            "Munafiqun", "Taghabun", "Talaq", "Tahrim", "Mulk", "Qalam", "Haqqah", "Ma'arij",

            "Nuh", "Jinn", "Muzzammil", "Muddaththir", "Qiyamah", "Insan", "Mursalat", "Naba",
            "Nazi'at", "Abasa", "Takwir", "Infitar", "Mutaffifin", "Inshiqaq", "Buruj",
            "Tariq", "A'la", "Ghashiyah", "Fajr", "Balad", "Shams", "Layl", "Duha", "Sharh",
            "Tin", "Alaq", "Qadr", "Bayyinah", "Zalzalah", "Adiyat", "Qari'ah", "Takathur",
            "Asr", "Humazah", "Fil", "Quraysh", "Ma'un", "Kawthar", "Kafirun", "Nasr", "Masad",
            "Ikhlas", "Falaq", "Nas"
            // You can also add common transliteration variations if you wish
    };


    static {
        Object[][] surahEntries = {
                {"fatiha",1,7}, {"al fatiha",1,7}, {"baqarah",2,286}, {"al baqarah",2,286}, {"cow",2,286},
                {"aal imran",3,200}, {"ali imran",3,200}, {"nisa",4,176}, {"an nisa",4,176}, {"maidah",5,120},
                {"al maida",5,120}, {"anam",6,165}, {"al anam",6,165}, {"araf",7,206}, {"a'raf",7,206},
                {"anfal",8,75}, {"tawbah",9,129}, {"yunus",10,109}, {"hud",11,123}, {"yusuf",12,111},
                {"rad",13,43}, {"ibrahim",14,52}, {"hijr",15,99}, {"nahl",16,128}, {"isra",17,111},
                {"bani israel",17,111}, {"kahf",18,110}, {"cave",18,110}, {"maryam",19,98}, {"taha",20,135},
                {"anbiya",21,112}, {"hajj",22,78}, {"muminun",23,118}, {"nur",24,64}, {"furqan",25,77},
                {"shuara",26,227}, {"naml",27,93}, {"qasas",28,88}, {"ankabut",29,69}, {"rum",30,60},
                {"luqman",31,34}, {"sajdah",32,30}, {"ahzab",33,73}, {"saba",34,54}, {"fatir",35,45},
                {"yaseen",36,83}, {"yasin",36,83}, {"saffat",37,182}, {"sad",38,88}, {"zumar",39,75},
                {"ghafir",40,85}, {"mumin",40,85}, {"fussilat",41,54}, {"shura",42,53}, {"zukhruf",43,89},
                {"dukhan",44,59}, {"jathiyah",45,37}, {"ahqaf",46,35}, {"muhammad",47,38}, {"fath",48,29},
                {"hujurat",49,18}, {"qaf",50,45}, {"dhariyat",51,60}, {"tur",52,49}, {"najm",53,62},
                {"qamar",54,55}, {"rahman",55,78}, {"ar rahman",55,78}, {"waqiah",56,96}, {"al waqiah",56,96},
                {"hadid",57,29}, {"mujadila",58,22}, {"hashr",59,24}, {"mumtahanah",60,13}, {"saff",61,14},
                {"jumuah",62,11}, {"juma",62,11}, {"munafiqun",63,11}, {"taghabun",64,18}, {"talaq",65,12},
                {"tahrim",66,12}, {"mulk",67,30}, {"al mulk",67,30}, {"qalam",68,52}, {"haqqah",69,52},
                {"maarij",70,44}, {"nuh",71,28}, {"jinn",72,28}, {"muzammil",73,20}, {"muddaththir",74,56},
                {"qiyamah",75,40}, {"insan",76,31}, {"dahr",76,31}, {"mursalat",77,50}, {"naba",78,40},
                {"naziat",79,46}, {"abasa",80,42}, {"takwir",81,29}, {"infitar",82,19}, {"mutaffifin",83,36},
                {"inshiqaq",84,25}, {"buruj",85,22}, {"tariq",86,17}, {"ala",87,19}, {"ghashiyah",88,26},
                {"fajr",89,30}, {"balad",90,20}, {"shams",91,15}, {"layl",92,21}, {"duha",93,11},
                {"sharh",94,8}, {"inshirah",94,8}, {"tin",95,8}, {"alaq",96,19}, {"qadr",97,5},
                {"bayyinah",98,8}, {"zilzal",99,8}, {"adiyat",100,11}, {"qariah",101,11}, {"takathur",102,8},
                {"asr",103,3}, {"humazah",104,9}, {"fil",105,5}, {"quraysh",106,4}, {"maun",107,7},
                {"kawthar",108,3}, {"kafirun",109,6}, {"nasr",110,3}, {"masad",111,5}, {"ikhlas",112,4},
                {"falak",113,5}, {"nas",114,6}
        };
        for (Object[] e : surahEntries) {
            String name = (String) e[0];
            Integer number = (Integer) e[1];
            Integer verses = (Integer) e[2];
            SURAH_NAME_TO_NUM.put(name.toLowerCase(Locale.ROOT), number);
            SURAH_NUM_TO_VERSE_COUNT.put(number, verses);
        }
    }

    // --- Action representation for Quran ---
    private static class QuranRequest {
        int surah;
        int verse;
        boolean continueToEnd;
        QuranRequest(int s, int v, boolean c){ surah=s; verse=v; continueToEnd=c; }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());

    // --- Gemini LLM Configuration (added) ---
    private static final String GEMINI_API_KEY = ""; // <-- put your key here
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-latest:generateContent?key=" + GEMINI_API_KEY;
    private static final OkHttpClient llmClient = new OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    // --- Executor for network (added) ---
    private final ExecutorService llmExecutor = Executors.newSingleThreadExecutor();

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
        status.setText("Press Vol-Down and say 'Geo News', 'Dunya News', or a Surah name (e.g., 'Play Surah Yaseen verse 5'). Vol-Up to stop.");
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        params.gravity = Gravity.CENTER;
        root.addView(status, params);
        setContentView(root);

        // --- Stream setup (unchanged logic for Geo / Dunya) ---
        streams = new ArrayList<>();
        streams.add(new GeoNewsStream());
        streams.add(new DunyaNewsStream());

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
                handleUtterance(said);
            }
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });
    }

    private void handleUtterance(String said){
        setStatus("You said: " + said);
        String norm = said.toLowerCase(Locale.getDefault());

        // Global stop
        if (norm.contains("stop") || norm.contains("band karo") || norm.contains("ruk jao")) {
            stopAllPlayback();
            isListening = false;
            return;
        }

        // First try news (original behavior) --- do not modify existing matching semantics
        for (Stream stream : streams) {
            for (String keyword : stream.getKeywords()) {
                if (norm.contains(keyword)) {
                    stopAllPlayback();
                    currentStream = stream;
                    stream.play(MainActivity.this);
                    isListening = false;
                    return;
                }
            }
        }

        // Quran detection
        QuranRequest qr = parseQuranRequest(norm);
        if (qr != null) {
            stopAllPlayback();
            playQuran(qr);
            return;
        }

        // If nothing matched
        // Fallback to Gemini only for Quran (does not alter news logic)
        setStatus("Trying AI parser..." + "\n" + said);
        fallbackToGemini(said);
    }

    // --- Quran Parsing (lightweight, local) ---
    // --- Quran Parsing (lightweight, local) ---
    private QuranRequest parseQuranRequest(String norm){
        // Pattern examples:
        // "play surah yaseen verse 5"
        // "surah mulk"
        // "play yaseen 5" (implied "verse")
        // "start surah kahf from verse 10 continue" (continue to end)
        // NEW: "surah 105 verse 2" or "surah 105 2"

        boolean continueToEnd = norm.contains("continue") || norm.contains("keep playing") || norm.contains("pura");

        // --- NEW: Handle "Surah [number] verse [number]" format ---
        // This pattern is very specific and efficient to parse locally.
        Pattern surahNumPattern = Pattern.compile("surah\\s+(\\d{1,3})(?:\\s+verse|\\s+ayat|\\s+vs|\\s+was|\\s+bus)?\\s*(\\d{1,3})?");
        Matcher surahNumMatcher = surahNumPattern.matcher(norm);

        if (surahNumMatcher.find()) {
            try {
                int surahNumber = Integer.parseInt(Objects.requireNonNull(surahNumMatcher.group(1)));
                int verseNumber = 1; // Default to verse 1

                // Check if a verse number was also captured
                if (surahNumMatcher.group(2) != null) {
                    verseNumber = Integer.parseInt(Objects.requireNonNull(surahNumMatcher.group(2)));
                }

                // Validate that the surah number is within the valid range (1-114)
                if (surahNumber >= 1 && surahNumber <= 114) {
                    return new QuranRequest(surahNumber, verseNumber, continueToEnd);
                }
            } catch (NumberFormatException e) {
                // This might happen if regex matches something odd, though unlikely with this pattern.
                // We'll just let it fall through to the name-based parsing.
            }
        }

        // --- EXISTING: Handle Surah by name (no changes needed here) ---
        // Identify surah by name
        String bestMatchName = null;
        int minDistance = 4; // Allow some errors (e.g., "yaseen" vs "yasin")

        for (String surahName : SURAH_NAME_TO_NUM.keySet()) {
            if (norm.contains(surahName)) {
                // Simple containment check first for speed
                int distance = LevenshteinDistance.getDefaultInstance().apply(surahName, norm);
                if (distance < minDistance) {
                    minDistance = distance;
                    bestMatchName = surahName;
                }
            }
        }

        if (bestMatchName != null) {
            Integer surahNum = SURAH_NAME_TO_NUM.get(bestMatchName);
            if (surahNum == null) return null;

            int verse = 1; // Default
            Pattern versePattern = Pattern.compile("(?:verse|ayat|vs|was|bus|number|#|)\\s*(\\d+)");
            Matcher m = versePattern.matcher(norm);
            if (m.find()) {
                try {
                    verse = Integer.parseInt(Objects.requireNonNull(m.group(1)));
                } catch (Exception e) {
                    // Fallback
                }
            }
            return new QuranRequest(surahNum, verse, continueToEnd);
        }

        // If no pattern matched, return null to fallback to Gemini
        return null;
    }


    private String bestSurahMatch(String input){
        LevenshteinDistance dist = new LevenshteinDistance();
        String best = null; double bestScore = 0.0;
        for (String name : SURAH_NAME_TO_NUM.keySet()){
            int d = dist.apply(input, name);
            double sim = 1.0 - (double)d / Math.max(input.length(), name.length());
            if (input.contains(name)) sim = Math.max(sim, 0.9);
            if (sim > bestScore){ bestScore = sim; best = name; }
        }
        if (bestScore < 0.55) return null; // threshold
        return best;
    }

    // --- Quran Playback ---
    private void playQuran(QuranRequest qr){
        if (qr.surah < 1 || qr.surah > 114){ setStatus("Invalid Surah"); return; }
        Integer total = SURAH_NUM_TO_VERSE_COUNT.get(qr.surah);
        if (total == null){ setStatus("Unknown Surah"); return; }
        if (qr.verse < 1) qr.verse = 1; if (qr.verse > total) qr.verse = total;

        String reciter = "ar.alafasy"; // configurable
        String quality = "128"; // 64 or 128
        String base = String.format(Locale.US, "https://cdn.islamic.network/quran/audio-surah/%s/%s/%d.mp3", quality, reciter, qr.surah);

        long avgMsPerVerse = 4500; // heuristic
        long startMs = (qr.verse > 1) ? (qr.verse - 1) * avgMsPerVerse : 0;

        MediaItem.Builder builder = new MediaItem.Builder().setUri(base)
                .setClippingConfiguration(new MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startMs)
                        .build());
        List<MediaItem> list = new ArrayList<>();
        list.add(builder.build());
        if (qr.continueToEnd){
            for (int s = qr.surah + 1; s <= 114; s++){
                String next = String.format(Locale.US, "https://cdn.islamic.network/quran/audio-surah/%s/%s/%d.mp3", quality, reciter, s);
                list.add(MediaItem.fromUri(next));
            }
        }
        setStatus(String.format(Locale.US, "Playing Surah %d from verse %d...", qr.surah, qr.verse));
        playMediaItems(list);
    }

    private void playMediaItems(List<MediaItem> items){
        if (player != null){
            try { player.stop(); player.release(); } catch (Throwable ignored) {}
        }
        player = new ExoPlayer.Builder(this).build();
        TrackSelectionParameters params = player.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true).build();
        player.setTrackSelectionParameters(params);
        player.setMediaItems(items);
        player.prepare();
        player.setPlayWhenReady(true);
    }

    private void stopAllPlayback(){
        if (currentStream != null){
            currentStream.stop();
            currentStream = null;
        }
        if (player != null){
            try { player.stop(); player.clearMediaItems(); } catch (Throwable ignored) {}
            try { player.release(); } catch (Throwable ignored) {}
            player = null;
        }
        setStatus("Stopped.");
    }

    private void setStatus(final String s){ runOnUiThread(() -> status.setText(s)); }

    // --- Permissions ---
    private void checkPermissionAndStartListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            startListening();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQ_RECORD_AUDIO);
        }
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

    private void startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            setStatus("Speech recognition not available.");
            return;
        }
        if (isListening) return;

        Intent srIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        srIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
        srIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN"); // en-US or others work too
        srIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);

        // --- KEY ADDITION: BIASING STRINGS ---
        // Create an ArrayList from your array of Surah names
        ArrayList<String> biasingStrings = new ArrayList<>(Arrays.asList(SURAH_NAMES_FOR_BIASING));
        // Also add other keywords to improve recognition of the full command
        biasingStrings.add("surah");
        biasingStrings.add("verse");
        srIntent.putExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, biasingStrings);
        // --- END OF ADDITION ---

        speechRecognizer.startListening(srIntent);
    }

    // --- Key Events ---
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
            isListening = false;
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAllPlayback();
        if (speechRecognizer != null) speechRecognizer.destroy();
        llmExecutor.shutdown();
    }

    // --- Stream interface (unaltered) ---
    interface Stream {
        String getName();
        String[] getKeywords();
        void play(MainActivity activity);
        void stop();
    }

    // --- Geo News Stream (logic preserved) ---
    class GeoNewsStream implements Stream {
        private WebView webView;
        @Override public String getName() { return "Geo News"; }
        @Override public String[] getKeywords() { return new String[]{"geo news", "jio news", "gio news", "geonews"}; }
        @Override public void play(MainActivity activity) {
            activity.setStatus("Resolving Geo News Stream…");
            resolveGeoNewsStream(activity, new UrlCallback() {
                @Override public void onResolved(String url) {
                    activity.setStatus("Playing Geo News…");
                    Map<String, String> headers = new HashMap<>();
                    headers.put("Origin", "https://live.geo.tv");
                    headers.put("Referer", "https://live.geo.tv/");
                    activity.playM3u8WithHeaders(url, headers);
                }
                @Override public void onError(Throwable t) { Log.e(TAG, "Geo News Stream resolution failed", t); activity.setStatus("Couldn’t resolve Geo News stream."); }
            });
        }
        @Override public void stop() {
            if (webView != null) {
                try { root.removeView(webView); } catch (Throwable ignored) {}
                try { webView.destroy(); } catch (Throwable ignored) {}
                webView = null;
            }
        }
        @SuppressLint("SetJavaScriptEnabled")
        private void resolveGeoNewsStream(final MainActivity activity, final UrlCallback cb) {
            webView = new WebView(activity);
            webView.getSettings().setJavaScriptEnabled(true);
            webView.getSettings().setDomStorageEnabled(true);
            webView.setVisibility(View.GONE);
            root.addView(webView, new FrameLayout.LayoutParams(1, 1));
            final String[] m3u8Hosts = new String[]{"5centscdn.com", "hls", "cdn", "edge"};
            webView.setWebViewClient(new WebViewClient() {
                private boolean triedSubmit = false;
                @Override public void onPageFinished(WebView view, String url) {
                    if (!triedSubmit) {
                        triedSubmit = true;
                        String js = "(function(){try{if (typeof get_stream === 'function'){ get_stream('stream1'); return 'ok-fn'; }}catch(e){}try{var f=document.getElementById('stream_post');var i=document.getElementById('currentStream');if(f && i){ i.value='stream1'; f.submit(); return 'ok-form'; }}catch(e){}return 'no-op';})();";
                        view.evaluateJavascript(js, null);
                    }
                }
                @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                    String u = (request != null && request.getUrl() != null) ? request.getUrl().toString() : "";
                    if (u.endsWith(".m3u8")) {
                        for (String host : m3u8Hosts) { if (u.contains(host)) { runOnUiThread(() -> { try { cb.onResolved(u); } catch (Throwable t) { cb.onError(t); } finally { stop(); } }); break; } }
                    }
                    return super.shouldInterceptRequest(view, request);
                }
            });
            try { webView.loadUrl("https://live.geo.tv/"); } catch (Throwable t) { cb.onError(t); stop(); }
        }
    }

    // --- Dunya News Stream (logic preserved) ---
    class DunyaNewsStream implements Stream {
        private WebView webView;
        @Override public String getName() { return "Dunya News"; }
        @Override public String[] getKeywords() { return new String[]{"dunya news", "dunia news", "duniya news"}; }
        @Override public void play(MainActivity activity) {
            activity.setStatus("Resolving Dunya News Stream…");
            resolveDunyaNewsStream(activity, new UrlCallback() {
                @Override public void onResolved(String url) {
                    activity.setStatus("Playing Dunya News…");
                    Map<String, String> headers = new HashMap<>();
                    headers.put("Origin", "https://dunyanews.tv");
                    headers.put("Referer", "https://dunyanews.tv/");
                    activity.playM3u8WithHeaders(url, headers);
                }
                @Override public void onError(Throwable t) { Log.e(TAG, "Dunya News Stream resolution failed", t); activity.setStatus("Couldn’t resolve Dunya News stream."); }
            });
        }
        @Override public void stop() {
            if (webView != null) {
                try { root.removeView(webView); } catch (Throwable ignored) {}
                try { webView.destroy(); } catch (Throwable ignored) {}
                webView = null;
            }
        }
        @SuppressLint("SetJavaScriptEnabled")
        private void resolveDunyaNewsStream(final MainActivity activity, final UrlCallback cb) {
            webView = new WebView(activity);
            WebSettings s = webView.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setMediaPlaybackRequiresUserGesture(false);
            s.setLoadsImagesAutomatically(true);
            s.setJavaScriptCanOpenWindowsAutomatically(true);
            s.setAllowContentAccess(true);
            s.setAllowFileAccess(true);
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            webView.setVisibility(View.GONE);
            root.addView(webView, new FrameLayout.LayoutParams(1, 1));
            webView.setWebViewClient(new WebViewClient() {
                private boolean kicked = false;
                @Override public void onPageFinished(WebView view, String url) {
                    if (!kicked) {
                        kicked = true;
                        String js = "(function(){try{ if(window.jwplayer){ var p=window.jwplayer('uklive'); if(p){ p.play(true); } }}catch(e){} return true;})();";
                        view.evaluateJavascript(js, null);
                    }
                }
                @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                    String u = (request != null && request.getUrl() != null) ? request.getUrl().toString() : "";
                    if (u.endsWith(".m3u8")) { runOnUiThread(() -> { try { cb.onResolved(u); } catch (Throwable t) { cb.onError(t); } finally { stop(); } }); }
                    return super.shouldInterceptRequest(view, request);
                }
            });
            try { webView.loadUrl("https://dunyanews.tv/live/"); } catch (Throwable t) { cb.onError(t); stop(); }
        }
    }

    interface UrlCallback { void onResolved(String url); void onError(Throwable t); }

    @OptIn(markerClass = UnstableApi.class)
    private void playM3u8WithHeaders(String m3u8, Map<String, String> headers) {
        DefaultHttpDataSource.Factory dsFactory = new DefaultHttpDataSource.Factory().setUserAgent("GeoNewsAudio/1.0");
        if (headers != null) { dsFactory.setDefaultRequestProperties(headers); }
        HlsMediaSource hls = new HlsMediaSource.Factory(dsFactory).createMediaSource(MediaItem.fromUri(m3u8));
        if (player != null) player.release();
        player = new ExoPlayer.Builder(this).build();
        TrackSelectionParameters params = player.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true).build();
        player.setTrackSelectionParameters(params);
        player.setMediaSource(hls);
        player.prepare();
        player.setPlayWhenReady(true);
    }

    // --- Gemini Fallback Methods (added) ---
    private void fallbackToGemini(String utterance) {
        llmExecutor.execute(() -> {
            setStatus("Using AI to get the right Surah");
            QuranRequest qr = callGeminiForQuran(utterance);
            if (qr != null) {
                runOnUiThread(() -> {
                    stopAllPlayback();
                    playQuran(qr);
                    isListening = false;
                });
            } else {
                runOnUiThread(() -> {
                    setStatus("Sorry, I didn't understand that. This is what I understood your words as - "+utterance);
                    isListening = false;
                });
            }
        });
    }

    private QuranRequest callGeminiForQuran(String utterance) {
        String payload = getString(utterance);
        Request req = new Request.Builder().url(GEMINI_API_URL).post(RequestBody.create(payload, JSON)).build();
        try (Response resp = llmClient.newCall(req).execute()) {
            if (!resp.isSuccessful()) return null;
            String body = resp.body().string();
            try {
                JSONObject root = new JSONObject(body);
                JSONObject cand = root.getJSONArray("candidates").getJSONObject(0)
                        .getJSONObject("content").getJSONArray("parts").getJSONObject(0);
                String text = cand.getString("text").trim();
                // Remove markdown fences if any
                text = text.replace("```json", "").replace("```", "").trim();
                JSONObject parsed = new JSONObject(text);
                String type = parsed.optString("type", "unknown");
                if ("stop".equals(type)) {
                    runOnUiThread(this::stopAllPlayback);
                    return null;
                }
                if ("play_quran".equals(type)) {
                    int surah = parsed.optInt("surah_number", -1);
                    if (surah < 1 || surah > 114) return null;
                    int verse = parsed.optInt("start_verse", 1);
                    boolean cont = parsed.optBoolean("continue", false);
                    return new QuranRequest(surah, verse, cont);
                }
            } catch (JSONException je) {
                Log.e(TAG, "Gemini parse error", je);
                return null;
            }
        } catch (IOException ioe) {
            Log.e(TAG, "Gemini request failed", ioe);
        }
        return null;
    }

    @NonNull
    private static String getString(String utterance) {
        String prompt = "You are a strict parser for Quran playback voice commands. You must return ONLY a compact JSON object. " +            "Your schema is: {type: 'play_quran'|'stop'|'unknown', surah_number?: number, start_verse?: number, continue?: boolean}. " +
                "Your primary job is to find the correct Surah number and verse number. " +
                "The user might mispronounce words. For example, 'Nahal', 'Nihal', or 'nahal' should be interpreted as Surah An-Nahl (16). " +
                "Words like 'was', 'verse', 'vs', or 'bus' often precede the verse number. " +
                "If the phrase after the Surah name is nonsensical or clearly not a verse number (e.g., 'bus stand'), try to find a number in it. If no number is present, you can ignore the nonsensical part. " +
                "If no verse is mentioned at all, default to 1. " +
                "If the command is to stop, set type to 'stop'.\n" +

                "Examples:\n" +
                "User: 'Play Surah Al-Fatiha'\n" +
                "{\"type\":\"play_quran\",\"surah_number\":1,\"start_verse\":1,\"continue\":false}\n" +
                "User: 'surahkah west 25'\n" +
                "{\"type\":\"play_quran\",\"surah_number\":18,\"start_verse\":25,\"continue\":false}\n" +
                "User: 'Surah Nahal was 10'\n" +
                "{\"type\":\"play_quran\",\"surah_number\":16,\"start_verse\":10,\"continue\":false}\n" +
                "User: 'recite al baqarah from verse 100'\n" +
                "{\"type\":\"play_quran\",\"surah_number\":2,\"start_verse\":100,\"continue\":false}\n" +

                // --- NEW, LOGICAL EXAMPLE ---
                // This teaches the LLM to find the number '10' and ignore the garbage word 'bus'
                "User: 'Surah nahal bus 10'\n" +
                "{\"type\":\"play_quran\",\"surah_number\":16,\"start_verse\":10,\"continue\":false}\n" +

                // This teaches the LLM what to do if there's no number at all (default to verse 1)
                "User: 'Surah nahal bus stand'\n" +
                "{\"type\":\"play_quran\",\"surah_number\":16,\"start_verse\":1,\"continue\":false}\n" +

                "User: 'stop playback'\n" +
                "{\"type\":\"stop\"}\n" +

                "Now, parse the following user command:\n" +
                "User: '" + utterance.replace("\"", "'") + "'";

        return "{\"contents\":[{\"parts\":[{\"text\":\"" + prompt + "\"}]}]}";
    }



}
