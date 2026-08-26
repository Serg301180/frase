package com.frase.app;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity {

    private int actual = -1;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }

        NotifReceiver.programar(this);
        mostrar();

        Button sig = findViewById(R.id.btnSiguiente);
        sig.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { mostrar(); }
        });
    }

    private void mostrar() {
        actual = Frases.siguiente(actual);
        String[] f = Frases.LISTA[actual];
        ((TextView) findViewById(R.id.ficha)).setText(
                String.format("ficha %02d/%d", actual + 1, Frases.LISTA.length));
        ((TextView) findViewById(R.id.es)).setText(f[0]);
        ((TextView) findViewById(R.id.roles)).setText(f[1]);
        ((TextView) findViewById(R.id.ru)).setText(f[2]);
        ((TextView) findViewById(R.id.say)).setText(f[3]);
        ((TextView) findViewById(R.id.nota)).setText(f[4]);
    }
}
