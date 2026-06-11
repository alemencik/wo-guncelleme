# WhatsApp Okuyucu - guncelleme kanali

Bu depo, telefondaki "WhatsApp Okuyucu" uygulamasinin kablosuz guncelleme
kaynagidir. Uygulama acilista `version.json`'a bakar; `versionCode` yereldekinden
buyukse `url`'deki APK'yi indirip kurar.

Yeni surum yayinlamak: yeni APK'yi buraya koy, `version.json` icindeki
`versionCode`/`versionName`/`not` alanlarini guncelle, commit + push.
