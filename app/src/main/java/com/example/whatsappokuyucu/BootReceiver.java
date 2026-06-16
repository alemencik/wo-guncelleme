package com.example.whatsappokuyucu;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Acilista oto-guncellemeyi kontrol eder. Bildirim dinleyici sistemce zaten baslar. */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            final Context app = context.getApplicationContext();
            new Thread(() -> new Guncelleyici(app).kontrolEt()).start();
        }
    }
}
