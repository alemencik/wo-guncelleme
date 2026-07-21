package com.example.whatsappokuyucu;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * Microsoft Edge "read aloud" (online neural TTS) istemcisi.
 * Ucretsiz, anahtarsiz. tr-TR-AhmetNeural dogal erkek sesi.
 * synthesize() arka planda cagrilir; MP3 byte[] dondurur (yoksa null).
 */
public class EdgeTts {

    private static final String TAG = "EdgeTts";
    private static final String TRUSTED_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4";
    // Guncel Chromium surumu (edge-tts referansiyla ayni). Eskidikce buyutulebilir.
    private static final String GEC_VERSION = "1-143.0.3650.75";
    private static final String CHROME_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0";
    private static final String BASE =
            "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1";

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    private final Context ctx;

    public EdgeTts(Context c) { ctx = c != null ? c.getApplicationContext() : null; }

    /** Metni normal hizda sese cevir. */
    public byte[] synthesize(String text, String voice) {
        return synthesize(text, voice, 0);
    }

    /**
     * Metni sese cevir. Hata/zaman asiminda null doner.
     * @param hizYuzde okuma hizi farki (0 = normal, 50 = 1.5x, 100 = 2x)
     */
    public byte[] synthesize(String text, String voice, int hizYuzde) {
        try {
            String url = BASE + "?TrustedClientToken=" + TRUSTED_TOKEN
                    + "&Sec-MS-GEC=" + secMsGec()
                    + "&Sec-MS-GEC-Version=" + GEC_VERSION;

            Request req = new Request.Builder()
                    .url(url)
                    .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
                    .header("User-Agent", CHROME_UA)
                    .build();

            final ByteArrayOutputStream audio = new ByteArrayOutputStream();
            final CountDownLatch done = new CountDownLatch(1);
            final boolean[] ok = {false};
            final String reqId = UUID.randomUUID().toString().replace("-", "");

            WebSocket ws = client.newWebSocket(req, new WebSocketListener() {
                @Override public void onOpen(WebSocket w, Response r) {
                    Log.d(TAG, "WS acildi (HTTP " + r.code() + ") ses=" + voice);
                    w.send(configMsg());
                    w.send(ssmlMsg(reqId, text, voice, hizYuzde));
                }
                @Override public void onMessage(WebSocket w, String t) {
                    if (t.contains("Path:turn.end")) { ok[0] = true; w.close(1000, null); done.countDown(); }
                }
                @Override public void onMessage(WebSocket w, ByteString bytes) {
                    byte[] b = bytes.toByteArray();
                    if (b.length < 2) return;
                    int headerLen = ((b[0] & 0xff) << 8) | (b[1] & 0xff);
                    int start = 2 + headerLen;
                    if (start < b.length) audio.write(b, start, b.length - start);
                }
                @Override public void onFailure(WebSocket w, Throwable e, Response r) {
                    String msg = "WS HATA" + (r != null ? " (HTTP " + r.code() + ")" : "") + ": " + e;
                    Log.w(TAG, msg, e);
                    Gunluk.yaz(ctx, "    EdgeTts " + msg);
                    done.countDown();
                }
                @Override public void onClosed(WebSocket w, int c, String reason) { done.countDown(); }
            });

            done.await(30, TimeUnit.SECONDS);
            if (!ok[0]) ws.cancel(); // basarida soket zaten turn.end'de kapandi; tekrar cancel sahte "Socket closed" hatasi uretiyordu
            if (ok[0] && audio.size() > 0) {
                Log.d(TAG, "Edge BASARILI: " + audio.size() + " bayt mp3");
                return audio.toByteArray();
            }
            Log.w(TAG, "Edge BASARISIZ (ok=" + ok[0] + ", bayt=" + audio.size() + ")");
            Gunluk.yaz(ctx, "    EdgeTts BASARISIZ (ok=" + ok[0] + ", bayt=" + audio.size() + ")");
            return null;
        } catch (Exception e) {
            Log.w(TAG, "Edge istisna: " + e, e);
            Gunluk.yaz(ctx, "    EdgeTts istisna: " + e);
            return null;
        }
    }

    private String configMsg() {
        return "X-Timestamp:" + ts() + "\r\n"
                + "Content-Type:application/json; charset=utf-8\r\n"
                + "Path:speech.config\r\n\r\n"
                + "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{"
                + "\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"},"
                + "\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}";
    }

    private String ssmlMsg(String reqId, String text, String voice, int hizYuzde) {
        String govde = esc(text);
        if (hizYuzde != 0) // prosody rate: "+50%" = 1.5x, "+100%" = 2x
            govde = "<prosody rate='" + (hizYuzde > 0 ? "+" : "") + hizYuzde + "%'>" + govde + "</prosody>";
        String ssml = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='tr-TR'>"
                + "<voice name='" + voice + "'>" + govde + "</voice></speak>";
        return "X-RequestId:" + reqId + "\r\n"
                + "Content-Type:application/ssml+xml\r\n"
                + "X-Timestamp:" + ts() + "\r\n"
                + "Path:ssml\r\n\r\n" + ssml;
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String ts() {
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat(
                "EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'",
                java.util.Locale.US);
        f.setTimeZone(java.util.TimeZone.getTimeZone("UTC")); // etiketle ('GMT+0000') tutarli
        return f.format(new java.util.Date());
    }

    /** Sec-MS-GEC token: SHA256( ticks(5dk yuvarlanmis) + trustedToken ). */
    private static String secMsGec() throws Exception {
        long ticks = (System.currentTimeMillis() / 1000L + 11644473600L) * 10000000L;
        ticks -= ticks % 3000000000L; // 300 sn = 3e9 * 100ns
        String str = ticks + TRUSTED_TOKEN;
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] h = md.digest(str.getBytes("UTF-8"));
        StringBuilder sb = new StringBuilder();
        for (byte x : h) sb.append(String.format("%02X", x));
        return sb.toString();
    }
}
