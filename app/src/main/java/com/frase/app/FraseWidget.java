package com.frase.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Random;

public class FraseWidget extends AppWidgetProvider {

    public static final String ACCION_SIGUIENTE = "com.frase.app.SIGUIENTE";
    private static final String PREFS = "frase_prefs";

    private static final Random RND = new Random();

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) pintar(ctx, mgr, id);
    }

    /** Пользователь растянул или сжал виджет — пересчитываем кегль. */
    @Override
    public void onAppWidgetOptionsChanged(Context ctx, AppWidgetManager mgr,
                                          int id, Bundle nuevasOpciones) {
        pintar(ctx, mgr, id);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        if (ACCION_SIGUIENTE.equals(intent.getAction())) {
            AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
            int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, FraseWidget.class));
            for (int id : ids) pintar(ctx, mgr, id);
        }
    }

    /** Одна карточка: испанский, разбор, перевод, произношение. */
    private static class Tarjeta {
        String es = "", rol = "", ru = "", say = "";
        int total = 0, num = 0;
    }

    /**
     * Берём материал, который приложение положило в общее хранилище.
     * Если его ещё нет (приложение ни разу не открывали) — работаем
     * на встроенном наборе из Frases.java.
     */
    private Tarjeta elegir(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String json = p.getString("datos", null);
        int nivel = p.getInt("nivel", 1);

        Tarjeta t = new Tarjeta();

        if (json != null && json.length() > 5) {
            try {
                JSONArray todo = new JSONArray(json);

                int libres = 0;
                for (int i = 0; i < todo.length(); i++) {
                    if (todo.getJSONObject(i).optInt("n", 1) <= nivel) libres++;
                }

                if (libres > 0) {
                    int objetivo = RND.nextInt(libres);
                    int visto = 0;
                    for (int i = 0; i < todo.length(); i++) {
                        JSONObject o = todo.getJSONObject(i);
                        if (o.optInt("n", 1) > nivel) continue;
                        if (visto == objetivo) {
                            t.es  = o.optString("e", "");
                            t.rol = o.optString("p", "");
                            t.ru  = o.optString("r", "");
                            t.say = o.optString("s", "");
                            t.total = libres;
                            t.num = visto + 1;
                            return t;
                        }
                        visto++;
                    }
                }
            } catch (Exception e) {
                // битые данные — тихо уходим на встроенный набор
            }
        }

        int i = RND.nextInt(Frases.LISTA.length);
        String[] f = Frases.LISTA[i];
        t.es = f[0]; t.rol = f[1]; t.ru = f[2]; t.say = f[3];
        t.total = Frases.LISTA.length;
        t.num = i + 1;
        return t;
    }

    /**
     * Подбираем кегль так, чтобы текст заполнил отведённое место:
     * спускаемся от максимума, пока строки не влезут и по ширине, и по высоте.
     */
    private static float ajustar(String texto, float anchoDp, float altoDp,
                                 float kAncho, float sMax, float sMin, int lineasMax) {
        if (texto == null || texto.length() == 0) return sMin;
        if (sMax < sMin) sMax = sMin;

        for (float s = sMax; s > sMin; s -= 1f) {
            int porLinea = (int) (anchoDp / (kAncho * s));
            if (porLinea < 1) porLinea = 1;
            int lineas = (int) Math.ceil((double) texto.length() / porLinea);
            if (lineas <= lineasMax && lineas * s * 1.25f <= altoDp) return s;
        }
        return sMin;
    }

    private void pintar(Context ctx, AppWidgetManager mgr, int id) {
        Tarjeta t = elegir(ctx);

        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget);

        // масштаб из настроек приложения
        float k = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                     .getFloat("escala", 1f);
        if (k < 0.8f) k = 0.8f;
        if (k > 1.6f) k = 1.6f;

        // фактический размер виджета на экране
        float anchoDp = 250f, altoDp = 150f;
        try {
            Bundle o = mgr.getAppWidgetOptions(id);
            boolean vertical = ctx.getResources().getConfiguration()
                    .orientation == Configuration.ORIENTATION_PORTRAIT;
            int w = o.getInt(vertical ? AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH
                                      : AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH);
            int h = o.getInt(vertical ? AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
                                      : AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
            if (w > 40) anchoDp = w;
            if (h > 40) altoDp = h;
        } catch (Exception e) {
            // остаёмся на значениях по умолчанию
        }

        float aw = Math.max(anchoDp - 32f, 80f);
        float ah = Math.max(altoDp - 40f, 60f);

        float sFicha = Math.max(9f,  Math.min(15f, ah * 0.075f));
        float sRol   = Math.max(9f,  Math.min(16f, ah * 0.080f));
        float sEs = ajustar(t.es, aw, ah * 0.46f, 0.52f, 56f, 15f, 4);
        float sRu = ajustar(t.ru, aw, ah * 0.26f,  0.56f,
                            Math.max(12f, sEs * 0.62f), 12f, 3);
        float sSay = Math.max(10f, Math.min(16f, sRu * 0.68f));

        v.setTextViewTextSize(R.id.ficha, TypedValue.COMPLEX_UNIT_SP, sFicha * k);
        v.setTextViewTextSize(R.id.es,    TypedValue.COMPLEX_UNIT_SP, sEs * k);
        v.setTextViewTextSize(R.id.roles, TypedValue.COMPLEX_UNIT_SP, sRol * k);
        v.setTextViewTextSize(R.id.ru,    TypedValue.COMPLEX_UNIT_SP, sRu * k);
        v.setTextViewTextSize(R.id.say,   TypedValue.COMPLEX_UNIT_SP, sSay * k);

        // пустые строки прячем, чтобы не оставалась дыра
        v.setViewVisibility(R.id.say,   t.say.length() > 0 ? View.VISIBLE : View.GONE);
        v.setViewVisibility(R.id.roles, t.rol.length() > 0 ? View.VISIBLE : View.GONE);
        // в совсем низком виджете номер карточки только мешает
        v.setViewVisibility(R.id.ficha, altoDp >= 110f ? View.VISIBLE : View.GONE);

        v.setTextViewText(R.id.ficha, String.format("ficha %02d/%d", t.num, t.total));
        v.setTextViewText(R.id.es, t.es);
        v.setTextViewText(R.id.roles, t.rol);
        v.setTextViewText(R.id.ru, t.ru);
        v.setTextViewText(R.id.say, t.say);

        Intent siguiente = new Intent(ctx, FraseWidget.class);
        siguiente.setAction(ACCION_SIGUIENTE);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, siguiente,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.raiz, pi);

        mgr.updateAppWidget(id, v);
    }
}
