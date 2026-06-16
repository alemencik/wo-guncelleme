package com.example.whatsappokuyucu;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Kablosuz oto-guncelleme. version.json'a bakar; versionCode yereldekinden buyukse
 * APK'yi indirip kurar (FileProvider ile). Arka planda cagrilmali.
 */
public class Guncelleyici {

    private static final String VERSION_URL =
            "https://raw.githubusercontent.com/alemencik/wo-guncelleme/main/version.json";

    private final Context ctx;
    private final OkHttpClient http = new OkHttpClient();

    public Guncelleyici(Context c) { ctx = c.getApplicationContext(); }

    /** Yeni surum var mi diye bak; varsa indirip kurma akisini baslat. */
    public void kontrolEt() {
        try {
            Request req = new Request.Builder().url(VERSION_URL).build();
            String json;
            try (Response r = http.newCall(req).execute()) {
                if (!r.isSuccessful() || r.body() == null) return;
                json = r.body().string();
            }
            int uzakKod = jsonInt(json, "versionCode");
            String url = jsonStr(json, "url");
            if (uzakKod <= 0 || url == null) return;

            int yerelKod = yerelVersionCode();
            if (uzakKod <= yerelKod) return; // guncel

            File apk = indir(url);
            if (apk != null) kur(apk);
        } catch (Exception ignore) { }
    }

    private int yerelVersionCode() {
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            return (int) (Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode);
        } catch (Exception e) { return Integer.MAX_VALUE; }
    }

    private File indir(String url) {
        try {
            Request req = new Request.Builder().url(url).build();
            try (Response r = http.newCall(req).execute()) {
                if (!r.isSuccessful() || r.body() == null) return null;
                File f = new File(ctx.getCacheDir(), "guncelleme.apk");
                try (InputStream in = r.body().byteStream(); FileOutputStream out = new FileOutputStream(f)) {
                    byte[] buf = new byte[8192]; int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
                return f;
            }
        } catch (Exception e) { return null; }
    }

    private void kur(File apk) {
        try {
            Uri uri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".apkprovider", apk);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            ctx.startActivity(i);
        } catch (Exception ignore) { }
    }

    private static int jsonInt(String j, String key) {
        try {
            int i = j.indexOf("\"" + key + "\"");
            if (i < 0) return -1;
            i = j.indexOf(":", i) + 1;
            StringBuilder s = new StringBuilder();
            while (i < j.length() && (Character.isDigit(j.charAt(i)) || j.charAt(i) == ' ')) {
                if (Character.isDigit(j.charAt(i))) s.append(j.charAt(i));
                i++;
            }
            return s.length() > 0 ? Integer.parseInt(s.toString()) : -1;
        } catch (Exception e) { return -1; }
    }

    private static String jsonStr(String j, String key) {
        try {
            int i = j.indexOf("\"" + key + "\"");
            if (i < 0) return null;
            i = j.indexOf("\"", j.indexOf(":", i) + 1) + 1;
            int e = j.indexOf("\"", i);
            return j.substring(i, e);
        } catch (Exception e) { return null; }
    }
}
