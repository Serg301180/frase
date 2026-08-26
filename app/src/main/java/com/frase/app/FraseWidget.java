package com.frase.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

public class FraseWidget extends AppWidgetProvider {

    public static final String ACCION_SIGUIENTE = "com.frase.app.SIGUIENTE";
    private static final String PREFS = "frase_prefs";
    private static final String CLAVE = "indice";

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int i = Frases.siguiente(p.getInt(CLAVE, -1));
        p.edit().putInt(CLAVE, i).apply();
        for (int id : ids) pintar(ctx, mgr, id, i);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        if (ACCION_SIGUIENTE.equals(intent.getAction())) {
            SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            int i = Frases.siguiente(p.getInt(CLAVE, -1));
            p.edit().putInt(CLAVE, i).apply();

            AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
            int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, FraseWidget.class));
            for (int id : ids) pintar(ctx, mgr, id, i);
        }
    }

    private void pintar(Context ctx, AppWidgetManager mgr, int id, int i) {
        String[] f = Frases.LISTA[i];
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget);

        v.setTextViewText(R.id.ficha, String.format("ficha %02d/%d", i + 1, Frases.LISTA.length));
        v.setTextViewText(R.id.es, f[0]);
        v.setTextViewText(R.id.roles, f[1]);
        v.setTextViewText(R.id.ru, f[2]);
        v.setTextViewText(R.id.say, f[3]);

        Intent t = new Intent(ctx, FraseWidget.class);
        t.setAction(ACCION_SIGUIENTE);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, t,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.raiz, pi);

        mgr.updateAppWidget(id, v);
    }
}
