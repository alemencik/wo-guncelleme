package com.example.whatsappokuyucu;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.speech.tts.TextToSpeech;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Locale;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Mesajlari SIRAYLA seslendirir. Once Edge TTS (Ahmet, dogal Turkce).
 * Edge basarisizsa (internet yoksa) telefon TTS'ine duser ki mesaj yine okunsun.
 */
public class Konusmaci {

    public static final String SES_ERKEK = "tr-TR-AhmetNeural"; // WhatsApp
    public static final String SES_KADIN = "tr-TR-EmelNeural";  // ntfy

    /** Kuyruk ogesi: ne okunacak, hangi sesle, hangi hizda, sonrasinda ne kadar susulacak. */
    private static final class Oge {
        final String metin, ses; final int hiz; final long bekleMs;
        Oge(String metin, String ses, int hiz, long bekleMs) {
            this.metin = metin; this.ses = ses; this.hiz = hiz; this.bekleMs = bekleMs;
        }
    }

    private final Context ctx;
    private final EdgeTts edge;
    private final LinkedBlockingQueue<Oge> kuyruk = new LinkedBlockingQueue<>();
    private TextToSpeech yedekTts;
    private volatile boolean yedekHazir = false;
    private Thread isci;

    public Konusmaci(Context c) {
        ctx = c.getApplicationContext();
        edge = new EdgeTts(ctx);
        yedekTts = new TextToSpeech(ctx, status -> {
            if (status == TextToSpeech.SUCCESS) {
                yedekTts.setLanguage(new Locale("tr", "TR"));
                yedekHazir = true;
            }
        });
        isci = new Thread(this::dongu, "konusmaci");
        isci.setDaemon(true);
        isci.start();
    }

    /** Belirli sesle, normal hizda seslendir (SES_ERKEK / SES_KADIN). */
    public void seslendir(String metin, String ses) {
        seslendir(metin, ses, 0, 0);
    }

    /**
     * @param hiz     okuma hizi farki (0 = normal, 50 = 1.5x)
     * @param bekleMs okuduktan sonra bu kadar sus (sonraki oge gecikir)
     */
    public void seslendir(String metin, String ses, int hiz, long bekleMs) {
        if (metin != null && !metin.trim().isEmpty())
            kuyruk.offer(new Oge(metin.trim(), ses != null ? ses : SES_ERKEK, hiz, bekleMs));
    }

    private void dongu() {
        while (true) {
            try {
                // Mesajlar SIRAYLA okunur. Birikmis (geriden gelen) mesajlar zaten 1.5x hizda
                // gelir (hiz BildirimDinleyici'de mesaj yasina gore ayarlanir), kuyruk hizli bosalir.
                seslendirOge(kuyruk.take());
            } catch (InterruptedException e) {
                return;
            } catch (Exception ignore) { }
        }
    }

    /** Tek bir kuyruk ogesini seslendirir (gorusme bitince, Edge -> Google yedek). */
    private void seslendirOge(Oge oge) throws InterruptedException {
        gorusmeBiteneKadarBekle();
        Gunluk.yaz(ctx, "  KUYRUK al, Edge cagriliyor: " + oge.ses
                + (oge.hiz != 0 ? " (hiz +" + oge.hiz + "%)" : ""));
        byte[] mp3 = edge.synthesize(oge.metin, oge.ses, oge.hiz);
        if (mp3 == null) { // anlik hata olabilir: kisa bekle, bir kez daha dene (Google'a dusmeden once)
            Gunluk.yaz(ctx, "  Edge NULL -> 1.5sn sonra TEKRAR dene");
            Thread.sleep(1500);
            mp3 = edge.synthesize(oge.metin, oge.ses, oge.hiz);
        }
        if (mp3 != null) { Gunluk.yaz(ctx, "  Edge OK (" + mp3.length + " bayt), cal"); cal(mp3); }
        else { Gunluk.yaz(ctx, "  Edge yine NULL -> Google yedek"); yedekSeslendir(oge.metin); }
        if (oge.bekleMs > 0) Thread.sleep(oge.bekleMs);
    }

    /**
     * Telefon gorusmesi surerken okuma yapma; kuyrukta bekletir, gorusme bitince devam eder.
     * AudioManager modu kullanilir — izin gerektirmez (READ_PHONE_STATE'e gerek yok).
     * WhatsApp/Telegram sesli aramalari da MODE_IN_COMMUNICATION olarak gorunur.
     */
    private void gorusmeBiteneKadarBekle() throws InterruptedException {
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        boolean bildirildi = false;
        while (true) {
            int mod = am.getMode();
            if (mod != AudioManager.MODE_IN_CALL && mod != AudioManager.MODE_IN_COMMUNICATION) break;
            if (!bildirildi) { Gunluk.yaz(ctx, "  BEKLE: gorusme suruyor, kuyrukta tutuluyor"); bildirildi = true; }
            Thread.sleep(2000);
        }
        if (bildirildi) {
            Gunluk.yaz(ctx, "  DEVAM: gorusme bitti");
            Thread.sleep(1500); // gorusme biter bitmez agzina okuma yapmasin
        }
    }

    private void cal(byte[] mp3) {
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        AudioManager.OnAudioFocusChangeListener afl = f -> { };
        if (am != null) am.requestAudioFocus(afl, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK); // calan muzigi kis, sonra geri ver
        try {
            File f = File.createTempFile("woku", ".mp3", ctx.getCacheDir()); // onek >=3 karakter olmali
            try (FileOutputStream o = new FileOutputStream(f)) { o.write(mp3); }
            final CountDownLatch latch = new CountDownLatch(1);
            MediaPlayer mp = new MediaPlayer();
            // USAGE_MEDIA: muzik akisina gider, MIUI'de duyulur (USAGE_ASSISTANT bastiriliyordu)
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            mp.setDataSource(f.getAbsolutePath());
            mp.setOnCompletionListener(p -> { Gunluk.yaz(ctx, "      cal: tamamlandi"); latch.countDown(); });
            mp.setOnErrorListener((p, w, e) -> { Gunluk.yaz(ctx, "      cal: HATA what=" + w + " extra=" + e); latch.countDown(); return true; });
            mp.prepare();
            mp.start();
            Gunluk.yaz(ctx, "      cal: start (sure=" + mp.getDuration() + "ms)");
            latch.await(60, TimeUnit.SECONDS);
            mp.release();
            f.delete();
        } catch (Exception e) { Gunluk.yaz(ctx, "      cal: istisna " + e); }
        finally { if (am != null) am.abandonAudioFocus(afl); }
    }

    private void yedekSeslendir(String metin) {
        if (!yedekHazir) return;
        final CountDownLatch latch = new CountDownLatch(1);
        String id = "wo" + System.currentTimeMillis();
        yedekTts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
            public void onStart(String s) { }
            public void onDone(String s) { latch.countDown(); }
            public void onError(String s) { latch.countDown(); }
        });
        yedekTts.speak(metin, TextToSpeech.QUEUE_FLUSH, null, id);
        try { latch.await(60, TimeUnit.SECONDS); } catch (InterruptedException ignore) { }
    }
}
