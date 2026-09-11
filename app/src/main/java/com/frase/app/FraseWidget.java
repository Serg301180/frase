package com.frase.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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

    private void pintar(Context ctx, AppWidgetManager mgr, int id) {
        Tarjeta t = elegir(ctx);

        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget);
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
