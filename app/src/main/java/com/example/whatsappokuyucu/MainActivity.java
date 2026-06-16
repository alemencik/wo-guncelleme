package com.example.whatsappokuyucu;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

/** Basit ayar ekrani. mute/unmute YOK — tek on/off anahtari. */
public class MainActivity extends AppCompatActivity {

    private Ayar ayar;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        ayar = new Ayar(this);

        LinearLayout kok = new LinearLayout(this);
        kok.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        kok.setPadding(pad, pad, pad, pad);

        TextView baslik = new TextView(this);
        baslik.setText("WhatsApp Okuyucu");
        baslik.setTextSize(22);
        baslik.setPadding(0, 0, 0, pad);
        kok.addView(baslik);

        // ANA ON/OFF
        Switch acik = new Switch(this);
        acik.setText("Okuma acik");
        acik.setTextSize(18);
        acik.setChecked(ayar.okumaAcik());
        acik.setOnCheckedChangeListener((v, c) -> {
            ayar.okumaAcik(c);
            Toast.makeText(this, c ? "Okuma acik" : "Okuma kapali", Toast.LENGTH_SHORT).show();
        });
        kok.addView(acik);

        // MESAI
        Switch mesai = new Switch(this);
        mesai.setText("Sadece mesai saatlerinde oku");
        mesai.setChecked(ayar.mesaiAktif());
        mesai.setOnCheckedChangeListener((v, c) -> ayar.mesaiAktif(c));
        kok.addView(mesai);

        LinearLayout saatSatir = new LinearLayout(this);
        saatSatir.setOrientation(LinearLayout.HORIZONTAL);
        saatSatir.setGravity(Gravity.CENTER_VERTICAL);
        TextView et1 = new TextView(this); et1.setText("Baslangic: ");
        EditText bas = saatKutu(ayar.mesaiBas());
        TextView et2 = new TextView(this); et2.setText("  Bitis: ");
        EditText bit = saatKutu(ayar.mesaiBit());
        saatSatir.addView(et1); saatSatir.addView(bas); saatSatir.addView(et2); saatSatir.addView(bit);
        kok.addView(saatSatir);

        // TATIL
        Switch tatil = new Switch(this);
        tatil.setText("Resmi tatillerde okuma (kapali = sus)");
        tatil.setChecked(ayar.tatildeKapali());
        tatil.setOnCheckedChangeListener((v, c) -> ayar.tatildeKapali(c));
        kok.addView(tatil);

        // UZAKTAN KOMUT NUMARASI (bu numaradan "off"/"on" gelince okuma kapanir/acilir)
        TextView komutEt = new TextView(this);
        komutEt.setText("\nUzaktan komut numarasi (bu numaradan 'off'=kapat, 'on'=ac):");
        kok.addView(komutEt);
        EditText komut = new EditText(this);
        komut.setInputType(InputType.TYPE_CLASS_PHONE);
        komut.setText(ayar.komutNumara());
        kok.addView(komut);

        // SESLER bilgi
        TextView ses = new TextView(this);
        ses.setText("\nSesler: WhatsApp = Ahmet (erkek), ntfy bildirimleri = Emel (kadin).\n"
                + "20 kelimeden uzun WhatsApp mesajlari sessizce atlanir.\n");
        kok.addView(ses);

        // BILDIRIM ERISIMI
        Button izin = new Button(this);
        izin.setText("Bildirim erisimi ver (gerekli)");
        izin.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        kok.addView(izin);

        // KURULUM IZNI (oto-guncelleme icin "bilinmeyen kaynaktan kurulum")
        Button kurulumIzni = new Button(this);
        kurulumIzni.setText("Kurulum izni ver (guncelleme icin)");
        kurulumIzni.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES));
            }
        });
        kok.addView(kurulumIzni);

        // GUNCELLEME
        Button gun = new Button(this);
        gun.setText("Guncellemeyi kontrol et");
        gun.setOnClickListener(v -> {
            Toast.makeText(this, "Kontrol ediliyor...", Toast.LENGTH_SHORT).show();
            new Thread(() -> new Guncelleyici(this).kontrolEt()).start();
        });
        kok.addView(gun);

        // KAYDET (mesai saatleri)
        Button kaydet = new Button(this);
        kaydet.setText("Kaydet (saatler + komut no)");
        kaydet.setOnClickListener(v -> {
            Integer b1 = parseDk(bas.getText().toString());
            Integer b2 = parseDk(bit.getText().toString());
            if (b1 != null) ayar.mesaiBas(b1);
            if (b2 != null) ayar.mesaiBit(b2);
            ayar.komutNumara(komut.getText().toString());
            Toast.makeText(this, "Kaydedildi", Toast.LENGTH_SHORT).show();
        });
        kok.addView(kaydet);

        setContentView(kok);

        // acilista guncelleme + tatil tazele
        new Thread(() -> { new Guncelleyici(this).kontrolEt(); new Takvim(this).tazele(); }).start();
    }

    private EditText saatKutu(int dk) {
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME);
        e.setText(String.format(Locale.US, "%02d:%02d", dk / 60, dk % 60));
        e.setWidth((int) (90 * getResources().getDisplayMetrics().density));
        return e;
    }

    private static Integer parseDk(String s) {
        try {
            String[] p = s.trim().split(":");
            int h = Integer.parseInt(p[0]); int m = p.length > 1 ? Integer.parseInt(p[1]) : 0;
            if (h < 0 || h > 23 || m < 0 || m > 59) return null;
            return h * 60 + m;
        } catch (Exception e) { return null; }
    }
}
