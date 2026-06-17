package com.example.whatsappokuyucu;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

import java.util.HashMap;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Bildirimleri dinler:
 *   - WhatsApp (com.whatsapp)   → Ahmet (erkek) sesiyle, SADECE mesaj metni (gonderen adi okunmaz), 20+ kelime SESSIZCE atlanir
 *   - ntfy     (io.heckel.ntfy) → Emel (kadin) sesiyle, tam okunur (kelime siniri yok)
 * Selam/nezaket YOK. Mesai saatleri + resmi tatil disinda okumaz. Ana on/off anahtari.
 * Cift okuma onleme: grup ozeti atlanir + ayni mesaj kisa surede tekrar gelirse okunmaz.
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
    // CIFT OKUMA ONLEME: en son okunan bildirim imzalari (anahtar+metin) -> zaman
    private final HashMap<String, Long> sonOkunanlar = new HashMap<>();
    private static final long TEKRAR_PENCERE = 15_000L; // ayni mesaj 15 sn icinde tekrar okunmaz

    @Override public void onListenerConnected() {
        // Tekrar bağlanmalarda yeniden yaratma (yoksa her rebind'de TTS motoru + thread sizar)
        if (konusmaci == null) konusmaci = new Konusmaci(this);
        if (ayar == null) ayar = new Ayar(this);
        if (takvim == null) takvim = new Takvim(this);
        yeniTatilTazele();
        Gunluk.yaz(this, "== LISTENER CONNECTED ==");
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (konusmaci == null) onListenerConnected();
        String paket = sbn.getPackageName();
        boolean whatsapp = WHATSAPP.equals(paket);
        boolean ntfy = NTFY.equals(paket);
        if (!whatsapp && !ntfy) return;

        Notification n = sbn.getNotification();
        if (n == null) return;
        // Grup ozeti bildirimi son mesajin metnini tekrar tasir → cift okumayi onlemek icin atla
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) { Gunluk.yaz(this, "  ATLA: grup ozeti"); return; }
        Bundle ex = n.extras;
        if (ex == null) return;

        CharSequence titleCs = ex.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence textCs = ex.getCharSequence(Notification.EXTRA_TEXT);
        String title = titleCs != null ? titleCs.toString().trim() : "";
        String text = textCs != null ? textCs.toString().trim() : "";
        Gunluk.yaz(this, "POST paket=" + paket + " title=" + title + " | text=" + text);
        if (TextUtils.isEmpty(text)) return;

        // UZAKTAN KOMUT: yetkili numaradan "on"/"off" → okumayi ac/kapat (mesaj okunmaz, mesai/okuma kontrolundan ONCE)
        if (whatsapp && ayar.komutYetkili(title)) {
            String k = text.toLowerCase(Locale.US).trim();
            if (k.equals("off") || k.equals("kapat") || k.equals("sus")) {
                ayar.okumaAcik(false); Gunluk.yaz(this, "  KOMUT: okuma KAPATILDI (" + title + ")"); return;
            }
            if (k.equals("on") || k.equals("ac") || k.equals("aç") || k.equals("ac.")) {
                ayar.okumaAcik(true); Gunluk.yaz(this, "  KOMUT: okuma ACILDI (" + title + ")"); return;
            }
        }

        // Ana on/off
        if (!ayar.okumaAcik()) { Gunluk.yaz(this, "  ATLA: okuma kapali"); return; }
        // Mesai saatleri disinda okuma yok
        if (!ayar.simdiOkunsun()) { Gunluk.yaz(this, "  ATLA: mesai disi"); return; }
        // Resmi tatilde okuma yok
        yeniTatilTazele();
        if (ayar.tatildeKapali() && takvim.bugunTatil()) { Gunluk.yaz(this, "  ATLA: tatil"); return; }

        // CIFT OKUMA ONLEME: ayni bildirim (anahtar+metin) kisa surede tekrar gelirse atla
        String imza = sbn.getKey() + "|" + title + "|" + text;
        long simdi = System.currentTimeMillis();
        sonOkunanlar.values().removeIf(t -> simdi - t > 60_000L); // eski kayitlari temizle
        Long oncekiZaman = sonOkunanlar.get(imza);
        if (oncekiZaman != null && simdi - oncekiZaman < TEKRAR_PENCERE) { Gunluk.yaz(this, "  ATLA: tekrar (dedup)"); return; }
        sonOkunanlar.put(imza, simdi);

        if (whatsapp) {
            // WhatsApp'in kendi sistem/durum bildirimleri (baslik "WhatsApp") okunmaz
            if ("WhatsApp".equalsIgnoreCase(title)) { Gunluk.yaz(this, "  ATLA: whatsapp sistem bildirimi"); return; }
            // ozet bildirimi atla
            if (OZET.matcher(text).matches()) { Gunluk.yaz(this, "  ATLA: ozet eslesti"); return; }
            // emoji/URL temizle, sonra oku
            String temiz = temizle(text);
            if (TextUtils.isEmpty(temiz)) { Gunluk.yaz(this, "  ATLA: temizleyince bos (emoji/link)"); return; }
            // 20+ kelime → SESSIZCE atla (uyari yok)
            if (kelimeSay(temiz) > MAKS_KELIME) { Gunluk.yaz(this, "  ATLA: 20+ kelime"); return; }
            // SADECE mesaj metni, Ahmet sesiyle (gonderen adi okunmaz)
            Gunluk.yaz(this, "  -> SESLENDIR (Ahmet): " + temiz);
            konusmaci.seslendir(temiz, Konusmaci.SES_ERKEK);
        } else { // ntfy → Ahmet (dogal erkek), SADECE mesaj metni (baslik/uygulama adi okunmaz)
            String temiz = temizle(text);
            if (TextUtils.isEmpty(temiz)) return;
            Gunluk.yaz(this, "  -> SESLENDIR (Ahmet): " + temiz);
            konusmaci.seslendir(temiz, Konusmaci.SES_ERKEK);
        }
    }

    private static int kelimeSay(String s) {
        if (s == null || s.trim().isEmpty()) return 0;
        return s.trim().split("\\s+").length;
    }

    // URL'leri ve emoji/sembolleri at, bosluklari sadeles. (TTS daha dogal okur)
    private static String temizle(String s) {
        if (s == null) return "";
        String t = s.replaceAll("https?://\\S+", " ").replaceAll("(?i)www\\.\\S+", " ");
        // emoji + cesitli sembol araliklari
        t = t.replaceAll("[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{2190}-\\x{21FF}\\x{2B00}-\\x{2BFF}"
                + "\\x{2300}-\\x{23FF}\\x{FE00}-\\x{FE0F}\\x{200D}\\x{20E3}\\x{1F1E6}-\\x{1F1FF}]", "");
        return t.replaceAll("\\s+", " ").trim();
    }

    private void yeniTatilTazele() {
        long now = System.currentTimeMillis();
        if (now - sonTatilTazel > 6 * 3600_000L) { // 6 saatte bir
            sonTatilTazel = now;
            new Thread(() -> takvim.tazele()).start();
        }
    }
}
