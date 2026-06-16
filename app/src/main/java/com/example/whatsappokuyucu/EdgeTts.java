package com.example.whatsappokuyucu;

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

    private static final String TRUSTED_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4";
    private static final String GEC_VERSION = "1-130.0.2849.68";
    private static final String BASE =
            "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1";

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    /** Metni sese cevir. Hata/zaman asiminda null doner. */
    public byte[] synthesize(String text, String voice) {
        try {
            String url = BASE + "?TrustedClientToken=" + TRUSTED_TOKEN
                    + "&Sec-MS-GEC=" + secMsGec()
                    + "&Sec-MS-GEC-Version=" + GEC_VERSION;

            Request req = new Request.Builder()
                    .url(url)
                    .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
                    .header("User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                            + "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0")
                    .build();

            final ByteArrayOutputStream audio = new ByteArrayOutputStream();
            final CountDownLatch done = new CountDownLatch(1);
            final boolean[] ok = {false};
            final String reqId = UUID.randomUUID().toString().replace("-", "");

            WebSocket ws = client.newWebSocket(req, new WebSocketListener() {
                @Override public void onOpen(WebSocket w, Response r) {
                    w.send(configMsg());
                    w.send(ssmlMsg(reqId, text, voice));
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
                @Override public void onFailure(WebSocket w, Throwable e, Response r) { done.countDown(); }
                @Override public void onClosed(WebSocket w, int c, String reason) { done.countDown(); }
            });

            done.await(30, TimeUnit.SECONDS);
            ws.cancel();
            if (ok[0] && audio.size() > 0) return audio.toByteArray();
            return null;
        } catch (Exception e) {
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

    private String ssmlMsg(String reqId, String text, String voice) {
        String ssml = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='tr-TR'>"
                + "<voice name='" + voice + "'>" + esc(text) + "</voice></speak>";
        return "X-RequestId:" + reqId + "\r\n"
                + "Content-Type:application/ssml+xml\r\n"
                + "X-Timestamp:" + ts() + "\r\n"
                + "Path:ssml\r\n\r\n" + ssml;
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String ts() {
        return new java.text.SimpleDateFormat(
                "EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'",
                java.util.Locale.US).format(new java.util.Date());
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
