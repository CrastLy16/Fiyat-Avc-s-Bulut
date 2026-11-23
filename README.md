# Fiyat Avcısı Bulut (GitHub Actions + Playwright + Android APK)

7 büyük alışveriş sitesini (**Amazon TR, Hepsiburada, N11, Trendyol, Vatan, Teknosa, MediaMarkt**) telefon pilini harcamadan **GitHub Actions bulutu (gerçek Chromium / Playwright)** üzerinde 30 dakikada bir tarayan, barkod/model kodu ile aynı ürün doğrulaması yapan ve belirlenen indirim oranı aşıldığında telefonuna karşılaştırmalı bildirim gönderen bulut destekli Android projesidir.

## 3 Adımda Kurulum

1. **GitHub'da Public bir depo oluştur** (ör. `FiyatAvcisiBulut`) ve bu klasörün içindeki tüm dosyaları (`.github` klasörü dahil) yükle.
2. **APK'yı indir:** Üstteki **Actions** sekmesinde **FiyatAvcisiTR Bulut APK Derle** tamamlanınca **Artifacts** bölümünden `FiyatAvcisiTR-Bulut-debug-apk` dosyasını indirip telefonuna kur.
3. **Bulutu bağla:** Uygulamayı aç, **Bulut & Ayarlar** sekmesine GitHub kullanıcı adını ve depo adını yazıp **Buluta Bağlan** butonuna bas. Takip etmek istediğin ürün ve kategori linklerini GitHub'daki `cloud_monitor/watchlist.json` dosyasına eklediğin anda bulut tarayıcısı otomatik çalışıp telefonuna güncel fiyatları ve bildirimleri iletir.
