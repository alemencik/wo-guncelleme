package com.example.whatsappokuyucu;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

import java.util.regex.Pattern;

/**
 * Bildirimleri dinler:
 *   - WhatsApp (com.whatsapp)   → Ahmet (erkek) sesiyle, 20+ kelime SESSIZCE atlanir
 *   - ntfy     (io.heckel.ntfy) → Emel (kadin) sesiyle, tam okunur (kelime siniri yok)
 * Selam/nezaket YOK. Mesai saatleri + resmi tatil disinda okumaz. Ana on/off anahtari.
 */
public class BildirimDinleyici extends NotificationListenerService {

    private static final String WHATSAPP = "com.whatsapp";
    private static final String NTFY = "io.heckel.ntfy";
    private static final int MAKS_KELIME = 20; // WhatsApp: 20+ kelime sessizce atla

    // WhatsApp ozet bildirimleri ("3 yeni mesaj", "2 sohbet" vb.) — okunmaz
    private static final Pattern OZET = Pattern.compile(
            "(?i).*\\d+\\s*(yeni\\s*)?(mesaj|sohbet|message|chat).*");

    private Konusmaci konusmaci;
    private Ayar ayar;
    private Takvim takvim;
    private long sonTatilTazel = 0;

    @Override public void onListenerConnected() {
        konusmaci = new Konusmaci(this);
        ayar = new Ayar(this);
        takvim = new Takvim(this);
        yeniTatilTazele();
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (konusmaci == null) onListenerConnected();
        String paket = sbn.getPackageName();
        boolean whatsapp = WHATSAPP.equals(paket);
        boolean ntfy = NTFY.equals(paket);
        if (!whatsapp && !ntfy) return;

        // Ana on/off
        if (!ayar.okumaAcik()) return;
        // Mesai saatleri disinda okuma yok
        if (!ayar.simdiOkunsun()) return;
        // Resmi tatilde okuma yok
        yeniTatilTazele();
        if (ayar.tatildeKapali() && takvim.bugunTatil()) return;

        Notification n = sbn.getNotification();
        if (n == null) return;
        Bundle ex = n.extras;
        if (ex == null) return;

        CharSequence titleCs = ex.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence textCs = ex.getCharSequence(Notification.EXTRA_TEXT);
        String title = titleCs != null ? titleCs.toString().trim() : "";
        String text = textCs != null ? textCs.toString().trim() : "";
        if (TextUtils.isEmpty(text)) return;

        if (whatsapp) {
            // ozet bildirimi atla
            if (OZET.matcher(text).matches()) return;
            // 20+ kelime → SESSIZCE atla (uyari yok)
            if (kelimeSay(text) > MAKS_KELIME) return;
            // gonderen + mesaj, Ahmet sesiyle
            String soylenecek = TextUtils.isEmpty(title) ? text : title + ". " + text;
            konusmaci.seslendir(soylenecek, Konusmaci.SES_ERKEK);
        } else { // ntfy → Emel, tam oku
            String soylenecek = TextUtils.isEmpty(title) ? text : title + ". " + text;
            konusmaci.seslendir(soylenecek, Konusmaci.SES_KADIN);
        }
    }

    private static int kelimeSay(String s) {
        if (s == null || s.trim().isEmpty()) return 0;
        return s.trim().split("\\s+").length;
    }

    private void yeniTatilTazele() {
        long now = System.currentTimeMillis();
        if (now - sonTatilTazel > 6 * 3600_000L) { // 6 saatte bir
            sonTatilTazel = now;
            new Thread(() -> takvim.tazele()).start();
        }
    }
}
