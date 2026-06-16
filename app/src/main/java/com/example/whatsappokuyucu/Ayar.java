package com.example.whatsappokuyucu;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Calendar;

/** Basit ayar deposu (SharedPreferences). Mesai saatleri vb. */
public class Ayar {
    private static final String DOSYA = "wo_ayar";
    private final SharedPreferences p;

    public Ayar(Context c) { p = c.getApplicationContext().getSharedPreferences(DOSYA, Context.MODE_PRIVATE); }

    // Ana on/off anahtari (mute/unmute YOK — sadece acik/kapali)
    public boolean okumaAcik() { return p.getBoolean("okuma_acik", true); }
    public void okumaAcik(boolean v) { p.edit().putBoolean("okuma_acik", v).apply(); }

    public boolean mesaiAktif() { return p.getBoolean("mesai_aktif", true); } // varsayilan: acik (09-18)
    public void mesaiAktif(boolean v) { p.edit().putBoolean("mesai_aktif", v).apply(); }

    public boolean tatildeKapali() { return p.getBoolean("tatil_kapali", true); } // resmi tatilde okuma yok
    public void tatildeKapali(boolean v) { p.edit().putBoolean("tatil_kapali", v).apply(); }

    public int mesaiBas() { return p.getInt("mesai_bas", 9 * 60); }   // dakika cinsinden (varsayilan 09:00)
    public void mesaiBas(int dk) { p.edit().putInt("mesai_bas", dk).apply(); }

    public int mesaiBit() { return p.getInt("mesai_bit", 18 * 60); }  // varsayilan 18:00
    public void mesaiBit(int dk) { p.edit().putInt("mesai_bit", dk).apply(); }

    /** Su an okuma yapilmali mi? Mesai kapaliysa her zaman true; aciksa saat araliginda mi? */
    public boolean simdiOkunsun() {
        if (!mesaiAktif()) return true;
        Calendar c = Calendar.getInstance();
        int dk = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        int bas = mesaiBas(), bit = mesaiBit();
        if (bas <= bit) return dk >= bas && dk < bit;
        return dk >= bas || dk < bit; // gece asan aralik
    }
}
