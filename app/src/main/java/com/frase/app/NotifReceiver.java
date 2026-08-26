package com.frase.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class NotifReceiver extends BroadcastReceiver {

    private static final String CANAL = "frase_canal";
    private static final long INTERVALO = 3 * 60 * 60 * 1000L; // раз в 3 часа

    @Override
    public void onReceive(Context ctx, Intent intent) {
        crearCanal(ctx);

        String[] f = Frases.LISTA[Frases.aleatorio()];

        Intent abrir = new Intent(ctx, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, abrir,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification n = new Notification.Builder(ctx, CANAL)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(f[0])
                .setContentText(f[2])
                .setStyle(new Notification.BigTextStyle()
                        .bigText(f[3] + "\n\n" + f[2] + "\n\n" + f[1]))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();

        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm != null) nm.notify((int) (System.currentTimeMillis() % 100000), n);
    }

    public static void crearCanal(Context ctx) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel c = new NotificationChannel(
                CANAL, "Фраза дня", NotificationManager.IMPORTANCE_DEFAULT);
        c.setDescription("Испанская фраза с разбором");
        nm.createNotificationChannel(c);
    }

    public static void programar(Context ctx) {
        crearCanal(ctx);
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        if (am == null) return;

        Intent i = new Intent(ctx, NotifReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 100, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        am.setInexactRepeating(AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 60 * 1000L, INTERVALO, pi);
    }
}
