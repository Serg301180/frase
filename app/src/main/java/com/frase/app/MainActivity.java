package com.frase.app;

import android.Manifest;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

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
        s.setMediaPlaybackRequiresUserGesture(false);

        // микрофон внутри страницы — для записи своего голоса
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest req) {
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        try {
                            // разрешаем только микрофон, больше странице ничего не нужно
                            java.util.List<String> ok = new java.util.ArrayList<String>();
                            for (String r : req.getResources()) {
                                if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) ok.add(r);
                            }
                            if (ok.isEmpty()) req.deny();
                            else req.grant(ok.toArray(new String[0]));
                        } catch (Exception e) { req.deny(); }
                    }
                });
            }
        });

        // ссылки на ютюб и прочее наружу — не внутрь приложения
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return fuera(r.getUrl().toString());
            }
            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                return fuera(url);
            }
        });

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

        /** Размер всего текста в приложении, проценты. */
        @JavascriptInterface
        public void tamano(final int pct) {
            final int v = Math.max(70, Math.min(170, pct));
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    if (web != null) web.getSettings().setTextZoom(v);
                }
            });
        }

        /** Размер текста в виджете — следует за настройкой заголовков. */
        @JavascriptInterface
        public void escalaWidget(float k) {
            SharedPreferences p = getSharedPreferences("frase_prefs", MODE_PRIVATE);
            p.edit().putFloat("escala", k).apply();

            AppWidgetManager m = AppWidgetManager.getInstance(MainActivity.this);
            int[] ids = m.getAppWidgetIds(new ComponentName(MainActivity.this, FraseWidget.class));
            if (ids != null && ids.length > 0) {
                Intent i = new Intent(MainActivity.this, FraseWidget.class);
                i.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
                i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
                sendBroadcast(i);
            }
        }

        /** Сохраняет текстовый файл в папку «Загрузки». Возвращает true при успехе. */
        @JavascriptInterface
        public boolean guardar(String nombre, String contenido) {
            if (nombre == null || contenido == null) return false;
            try {
                byte[] datos = contenido.getBytes("UTF-8");

                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.Downloads.DISPLAY_NAME, nombre);
                    cv.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
                    Uri destino = getContentResolver().insert(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                    if (destino == null) return false;
                    java.io.OutputStream os = getContentResolver().openOutputStream(destino);
                    if (os == null) return false;
                    os.write(datos);
                    os.close();
                    return true;
                }

                java.io.File carpeta = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS);
                if (carpeta != null && !carpeta.exists()) carpeta.mkdirs();
                java.io.File f = new java.io.File(carpeta, nombre);
                java.io.FileOutputStream fo = new java.io.FileOutputStream(f);
                fo.write(datos);
                fo.close();
                return true;

            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public boolean disponible() {
            return listo;
        }

        /** Открыть ссылку в ютюбе или браузере. */
        @JavascriptInterface
        public void abrir(final String url) {
            runOnUiThread(new Runnable() {
                @Override public void run() { fuera(url); }
            });
        }

        /** true — микрофон уже разрешён; иначе спрашиваем и возвращаем false. */
        @JavascriptInterface
        public boolean pedirMicro() {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) return true;
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 2);
                }
            });
            return false;
        }
    }

    /** Внешняя ссылка уходит системе; file:// остаётся внутри. */
    private boolean fuera(String url) {
        if (url == null) return false;
        if (url.startsWith("file://") || url.startsWith("about:")) return false;
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            // некуда открыть — просто молчим, страница останется на месте
        }
        return true;
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
