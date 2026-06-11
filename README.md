# ⚽ FIFA World Cup 2026 Prediction App

Bu proje, **FIFA Dünya Kupası 2026** için kullanıcıların grup aşaması sıralamaları, maç skorları ve eleme braketleri (knockout bracket) üzerinde tahminler yapıp puan toplayarak liderlik tablosunda yarışabileceği modern bir tahmin uygulamasıdır.

Uygulama, hem **Android (Native)** hem de **Web (Kotlin WasmJs / Compose Multiplatform)** platformlarını destekleyen çoklu platform mimarisine sahiptir.

---

## 🚀 Öne Çıkan Özellikler

*   **Fikstür & Canlı Arama:** Turnuva aşamalarına (Grup Maçları, 1./2./3. Tur, Eleme Aşamaları) göre filtrelenebilir ve takımlara göre aranabilir gelişmiş fikstür ekranı.
*   **Canlı Puan Durumu:** Oynanan maçların sonuçlarına göre gerçek zamanlı olarak hesaplanan grup liderlik tabloları.
*   **Skor Tahminleri:** Maçların skorlarını tahmin etme ve puan kazanma (+20 puan tam skor, +5 puan doğru sonuç).
*   **Sürükle-Bırak Grup Tahminleri (Drag & Drop):** Takımları sürükleyip bırakarak grupları 1. ile 4. sıra arasında tahmin etme mekanizması.
*   **Eleme Aşaması Braketi (Knockout Bracket):** Son 32 turundan finale kadar dinamik olarak değişen tahmin ağacı.
*   **Liderlik Tablosu (Leaderboard):** Tüm kullanıcıların tahmin puanlarını listeleyen canlı sıralama tablosu.
*   **Akıllı Takım Bildirimleri (FCM):**
    *   Kullanıcıların diledikleri takımları seçerek bildirim tercihlerini özelleştirebilmesi.
    *   Seçilen takımların maçları başlamadan **tam 1 saat önce** tarayıcıya/cihaza otomatik push bildirimi gönderilmesi.
    *   Tercihler ilk kaydedildiğinde en yakın maça kalan süreyi hesaplayan anlık yerel karşılama bildirimi.

---

## 🛠️ Kullanılan Teknolojiler (Tech Stack)

### Web Uygulaması
*   **Core:** Kotlin WasmJs (WebAssembly)
*   **UI Framework:** Jetpack Compose Multiplatform (WasmJs)
*   **Network:** Ktor HTTP Client (REST API) & Kotlinx Serialization
*   **Görsel Yükleme:** Coil 3 (WasmJs uyumlu)
*   **Veritabanı:** Firebase Realtime Database
*   **Bildirim Sistemi:** Firebase Cloud Messaging (FCM) & JavaScript Service Worker interop

### Android Uygulaması
*   **Core:** Kotlin & Native Android SDK
*   **UI Framework:** Jetpack Compose (Material 3)
*   **Architecture:** ViewModel & LiveData
*   **Network:** Retrofit & OkHttp (TheSportsDB API)
