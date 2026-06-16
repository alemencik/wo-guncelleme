package com.example.whatsappokuyucu;

import android.content.Context;
import android.media.AudioAttributes;
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

    private final Context ctx;
    private final EdgeTts edge;
    // her oge: [0]=metin, [1]=ses adi
    private final LinkedBlockingQueue<String[]> kuyruk = new LinkedBlockingQueue<>();
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

    /** Belirli sesle seslendir (SES_ERKEK / SES_KADIN). */
    public void seslendir(String metin, String ses) {
        if (metin != null && !metin.trim().isEmpty())
            kuyruk.offer(new String[]{ metin.trim(), ses != null ? ses : SES_ERKEK });
    }

    private void dongu() {
        while (true) {
            try {
                String[] oge = kuyruk.take();
                Gunluk.yaz(ctx, "  KUYRUK al, Edge cagriliyor: " + oge[1]);
                byte[] mp3 = edge.synthesize(oge[0], oge[1]);
                if (mp3 != null) { Gunluk.yaz(ctx, "  Edge OK (" + mp3.length + " bayt), cal"); cal(mp3); }
                else { Gunluk.yaz(ctx, "  Edge NULL -> Google yedek (kadin ses)"); yedekSeslendir(oge[0]); }
            } catch (InterruptedException e) {
                return;
            } catch (Exception ignore) { }
        }
    }

    private void cal(byte[] mp3) {
        try {
            File f = File.createTempFile("wo", ".mp3", ctx.getCacheDir());
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
