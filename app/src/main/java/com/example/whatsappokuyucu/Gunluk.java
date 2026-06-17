package com.example.whatsappokuyucu;

import android.content.Context;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Basit dosya gunlugu (MIUI logcat'i bastirdigi icin teshis amacli).
 * /sdcard/Android/data/com.example.whatsappokuyucu/files/debug.log
 * adb ile okunabilir: adb shell cat /sdcard/Android/data/com.example.whatsappokuyucu/files/debug.log
 */
public class Gunluk {
    /** Teshis gunlugu varsayilan KAPALI. Sorun olursa true yapip yeniden derle. */
    public static boolean AKTIF = false;

    public static synchronized void yaz(Context c, String s) {
        try {
            if (!AKTIF || c == null) return;
            File d = c.getExternalFilesDir(null);
            if (d == null) return;
            File f = new File(d, "debug.log");
            // dosya cok buyurse sifirla (~64KB)
            if (f.length() > 65536) f.delete();
            String t = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(new Date());
            try (FileWriter w = new FileWriter(f, true)) { w.write(t + "  " + s + "\n"); }
        } catch (Exception ignore) { }
    }
}
