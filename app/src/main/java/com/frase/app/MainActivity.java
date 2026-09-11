package com.frase.app;

import android.Manifest;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;

import java.util.Locale;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {

    private WebView web;
    private TextToSpeech tts;
    private boolean listo = false;

    /** Скорость речи. 1.0 — обычная. Меняется из приложения. */
    private float velocidad = 0.70f;

    /** Молчание перед фразой, мс — аудиоканал успевает открыться,
     *  первые буквы не срезаются. */
    private static final long CALENTAR = 220;

    /** Молчание после фразы, мс — канал не закрывается раньше времени,
     *  последние буквы не срезаются. */
    private static final long ENFRIAR = 350;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        tts = new TextToSpeech(this, this);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setTextZoom(100);
        web.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
        web.addJavascriptInterface(new Puente(), "Android");
        web.loadUrl("file:///android_asset/app.html");
        setContentView(web);

        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }

        NotifReceiver.programar(this);
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) return;

        int r = tts.setLanguage(new Locale("es", "ES"));
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(new Locale("es"));
        }

        tts.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build());

        tts.setSpeechRate(velocidad);

        // сообщаем странице, когда речь действительно закончилась
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) { }

            @Override public void onDone(String id) {
                if ("despues".equals(id)) avisarFin();
            }

            @Override public void onError(String id) { avisarFin(); }
        });

        listo = true;

        // прогрев движка при запуске — первая фраза за сеанс тоже не обрежется
        tts.playSilentUtterance(1, TextToSpeech.QUEUE_FLUSH, "arranque");
    }

    public class Puente {
        @JavascriptInterface
        public void hablar(String texto) {
            if (!listo || tts == null || texto == null) return;
            String t = texto.trim();
            if (t.isEmpty()) return;

            // QUEUE_FLUSH на первой паузе сам сбрасывает очередь — stop() не нужен,
            // он обрывал предыдущую фразу на полуслове
            tts.playSilentUtterance(CALENTAR, TextToSpeech.QUEUE_FLUSH, "antes");
            tts.speak(t, TextToSpeech.QUEUE_ADD, null, "frase");
            tts.playSilentUtterance(ENFRIAR, TextToSpeech.QUEUE_ADD, "despues");
        }

        @JavascriptInterface
        public void velocidad(float v) {
            if (v < 0.4f) v = 0.4f;
            if (v > 1.2f) v = 1.2f;
            MainActivity.this.velocidad = v;
            if (listo && tts != null) tts.setSpeechRate(v);
        }

        /** Приложение передаёт сюда свой материал — виджет берёт его оттуда же. */
        @JavascriptInterface
        public void sincronizar(String json, int nivel) {
            if (json == null || json.length() < 5) return;

            SharedPreferences p = getSharedPreferences("frase_prefs", MODE_PRIVATE);
            p.edit().putString("datos", json).putInt("nivel", nivel).apply();

            AppWidgetManager m = AppWidgetManager.getInstance(MainActivity.this);
            int[] ids = m.getAppWidgetIds(new ComponentName(MainActivity.this, FraseWidget.class));
            if (ids != null && ids.length > 0) {
                Intent i = new Intent(MainActivity.this, FraseWidget.class);
                i.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
                i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
                sendBroadcast(i);
            }
        }

        @JavascriptInterface
        public boolean disponible() {
            return listo;
        }
    }

    private void avisarFin() {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                if (web != null) {
                    web.evaluateJavascript("window.__vozFin && window.__vozFin();", null);
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
