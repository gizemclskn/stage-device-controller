# Devreye Alma Kontrol Listesi

Bu liste, Stage Device Controller'ı (Android uygulaması + Python sunucusu) sıfırdan kuran birinin sistemin çalıştığını adım adım doğrulaması içindir. Her madde işaretlenebilir ve bir "beklenen sonuç" içerir. Bir madde başarısız olursa sonraki adıma geçmeden önce README'deki Sorun Giderme bölümüne bak.

Kontrol tarihi: ____________  Kontrolü yapan: ____________  Sürüm: ____________

## 1. Ortam

- [ ] Python kurulu ve `python --version` çalışıyor.
  - Beklenen: sürüm numarası yazılır.
- [ ] Repo klonlandı ve doğru klasördesin.
  - Beklenen: `server\server.py` dosyası mevcut.
- [ ] Android Studio emülatörü açılıyor ve uygulama kuruluyor.
  - Beklenen: uygulama açılır, Bağlantı, Işık ve Ses sekmeleri görünür.

## 2. Sunucu

- [ ] Sunucu başlatıldı: `python server\server.py`
  - Beklenen: hata vermeden çalışır, konsol açık kalır.
- [ ] Sunucu doğru portta dinliyor (varsayılan: 5000).
  - Beklenen: konsolda dinleme bilgisi görünür, başka bir program aynı portu kullanmıyor.
- [ ] Güvenlik duvarı uyarısı çıktıysa erişime izin verildi.
  - Beklenen: uyarı yok ya da izin verildi.

## 3. Ağ bağlantısı

- [ ] Uygulamanın Bağlantı sekmesinde adres ve port girildi.
  - Emülatör için: `10.0.2.2` / `5000`
- [ ] **Bağlan**'a basıldı.
  - Beklenen: uygulamada "Bağlı" yazar, sunucu konsolunda bağlantı kaydı görünür.
- [ ] Bağlantı yanlış adresle denendi.
  - Beklenen: uygulama bağlı görünmez, anlaşılır bir hata verir (hata yönetimi kontrolü).

## 4. Işık komutları

- [ ] Işık sekmesindeki bir kaydırıcı oynatıldı.
  - Beklenen: sunucu konsolunda komut satırları akar, değerler kaydırıcıyla uyumlu.
- [ ] Kaydırıcı uç değerlere (en düşük ve en yüksek) getirildi.
  - Beklenen: sunucu uç değerleri doğru alır.

## 5. Ses komutları

- [ ] Ses sekmesindeki bir kaydırıcı oynatıldı.
  - Beklenen: sunucu konsolunda ilgili komutlar görünür.
- [ ] **Mute** düğmesine basıldı.
  - Beklenen: sunucu mute komutunu alır, uygulamadaki durum değişir.

## 6. Kayıt (log) ve Test Modu

- [ ] Log kaydı kontrol edildi.
  - Beklenen: yapılan işlemler kayıtta sırayla görünür.
- [ ] Test Modu çalıştırıldı.
  - Beklenen: test PASS ile biter. PASS ve FAIL örnek ekran görüntüleri için `docs/images/` klasörüne bak.

## 7. Bağlantı kopması

- [ ] Sunucu çalışırken kapatıldı (Ctrl + C).
  - Beklenen: uygulama bağlantının koptuğunu gösterir, çökmez.
- [ ] Sunucu yeniden başlatıldı ve uygulama tekrar bağlandı.
  - Beklenen: yeniden bağlantı sorunsuz olur, komutlar yine akar.

## Sonuç

- [ ] Tüm maddeler geçti. Sistem devreye alınmaya hazır.
- [ ] Geçmeyen maddeler var. Aşağıya yaz:

| Madde | Gözlem | Çözüm |
|---|---|---|
|  |  |  |
