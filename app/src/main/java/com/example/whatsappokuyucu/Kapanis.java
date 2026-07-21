package com.example.whatsappokuyucu;

import java.util.Random;

/**
 * Uzun mesajlarda ilk 15 kelime okunduktan sonra soylenen kapanis cumlesi.
 * Rastgele secilir; ust uste ayni cumle gelmez.
 */
public class Kapanis {

    private static final String[] CUMLELER = {
            "Kitap mı yazıyorsun kardeşim, kalanını kendin oku uğraşamayacam.",
            "Bu kadar yazacağına arasaydın mına koym.",
            "Nefesin yetmedi okumaya, Allah kocana sabır versin.",
            "Veleddalin amiin. Ööffff.",
            "Kız parmakların acımıştır bunları yazarken. Getir öpeyim de geçsin.",
            "Destan yazmış maşallah.",
            "Hiç mi üşenmedin bunu yazarken.",
            "Allah senin canını almasın, işediğini sıçtığını da yazsaydın.",
    };

    private static final Random RASTGELE = new Random();
    private static int sonIndeks = -1;

    private Kapanis() { }

    /** Ust uste tekrar etmeyen rastgele kapanis. */
    public static synchronized String rastgele() {
        if (CUMLELER.length == 1) return CUMLELER[0];
        int i;
        do { i = RASTGELE.nextInt(CUMLELER.length); } while (i == sonIndeks);
        sonIndeks = i;
        return CUMLELER[i];
    }
}
