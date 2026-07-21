package com.example.whatsappokuyucu;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TTS'e gitmeden once metni duzeltir.
 *
 * Iki ters yonlu is yapar:
 *   A) Sesli tepkiler: Edge TTS "aa"yi "Anadolu Ajansi" gibi genisletiyor.
 *      Harf uzatarak tepki gibi duyulmasini saglariz (aa -> aaa).
 *   B) Sohbet kisaltmalari: harf harf okununca anlasilmaz (slm -> selam).
 */
public class Sozluk {

    /** Sira onemli: once uzun anahtar, sonra kisa (vb / vbb karismasin). */
    private static final Map<String, String> KARSILIK = new LinkedHashMap<>();
    static {
        // --- A) sesli tepkiler: genislemeyi engelle, tepki gibi duyulsun ---
        KARSILIK.put("aa", "aaa");
        KARSILIK.put("ee", "eee");
        KARSILIK.put("oo", "ooo");
        KARSILIK.put("ıı", "ııı");
        KARSILIK.put("hmmm", "hımm");
        KARSILIK.put("hmm", "hımm");
        KARSILIK.put("pfff", "pöf");
        KARSILIK.put("pff", "pöf");
        KARSILIK.put("öff", "öf");

        // --- B) sohbet kisaltmalari: acilsin ---
        KARSILIK.put("slm", "selam");
        KARSILIK.put("tmm", "tamam");
        KARSILIK.put("nbr", "ne haber");
        KARSILIK.put("kib", "kendine iyi bak");
        KARSILIK.put("eyw", "eyvallah");
        KARSILIK.put("msj", "mesaj");
        KARSILIK.put("bkz", "bakınız");
        KARSILIK.put("örn", "örneğin");
        KARSILIK.put("vb", "ve benzeri");
        KARSILIK.put("vs", "vesaire");
        KARSILIK.put("tl", "lira");
    }

    /** Kelime siniri: harf/rakam olmayan her sey sinir sayilir (Turkce harfler korunur). */
    private static final Map<String, Pattern> DESEN = new LinkedHashMap<>();
    static {
        for (String k : KARSILIK.keySet()) {
            DESEN.put(k, Pattern.compile(
                    "(?<![\\p{L}\\p{N}])" + Pattern.quote(k) + "(?![\\p{L}\\p{N}])",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
        }
    }

    private Sozluk() { }

    /** Metindeki kisaltma/tepkileri okunabilir karsiliklariyla degistirir. */
    public static String duzelt(String s) {
        if (s == null || s.isEmpty()) return "";
        String t = s;
        for (Map.Entry<String, String> e : KARSILIK.entrySet()) {
            Pattern p = DESEN.get(e.getKey());
            if (p == null) continue;
            Matcher m = p.matcher(t);
            if (m.find()) t = m.replaceAll(Matcher.quoteReplacement(e.getValue()));
        }
        return t;
    }

    /** Kucuk harfe cevirirken Turkce kurallarini kullan (I/ı tuzagi). */
    static String kucuk(String s) {
        return s == null ? "" : s.toLowerCase(new Locale("tr", "TR"));
    }
}
