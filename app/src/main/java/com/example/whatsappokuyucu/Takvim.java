package com.example.whatsappokuyucu;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Turkiye resmi tatil gunleri. date.nager.at (ucretsiz, anahtarsiz) API'sinden
 * yilin tatillerini ceker, cihaza onbellekler. Gunde bir tazelenir.
 * Internet yoksa onbellekteki listeyi kullanir.
 */
public class Takvim {
    private static final String DOSYA = "wo_tatil";
    private final SharedPreferences p;
    private final OkHttpClient http = new OkHttpClient();

    public Takvim(Context c) { p = c.getApplicationContext().getSharedPreferences(DOSYA, Context.MODE_PRIVATE); }

    /** Bugun resmi tatil mi? (onbellekteki yyyy-MM-dd listesine bakar) */
    public boolean bugunTatil() {
        String bugun = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().getTime());
        return p.getString("gunler", "").contains("[" + bugun + "]");
    }

    /** Tatil listesini API'den tazele (arka planda cagir). Gunde bir yeter. */
    public void tazele() {
        try {
            int yil = Calendar.getInstance().get(Calendar.YEAR);
            String bugun = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().getTime());
            if (bugun.equals(p.getString("son_tazel", "")) && !p.getString("gunler", "").isEmpty()) return;

            StringBuilder gunler = new StringBuilder();
            for (int y = yil; y <= yil + 1; y++) {
                Request req = new Request.Builder()
                        .url("https://date.nager.at/api/v3/PublicHolidays/" + y + "/TR").build();
                try (Response r = http.newCall(req).execute()) {
                    if (!r.isSuccessful() || r.body() == null) continue;
                    String json = r.body().string();
                    int i = 0;
                    while ((i = json.indexOf("\"date\":\"", i)) >= 0) {
                        i += 8;
                        String d = json.substring(i, Math.min(i + 10, json.length()));
                        if (d.matches("\\d{4}-\\d{2}-\\d{2}")) gunler.append("[").append(d).append("]");
                    }
                }
            }
            if (gunler.length() > 0) {
                p.edit().putString("gunler", gunler.toString()).putString("son_tazel", bugun).apply();
            }
        } catch (Exception ignore) { }
    }
}
