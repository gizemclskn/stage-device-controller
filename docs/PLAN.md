# stage-device-controller: Proje Planı

Bu doküman projenin hedefini, kapsamını, teknik kararlarını ve görev listesini içerir.
Tamamlanan görevler `[x]` ile işaretlenir. ⭐ ile işaretli görevler opsiyoneldir.

## 1. Hedef ve başarı ölçütleri

**Hedef:** Java ile yazılmış bir Android uygulamasının, ağdaki bir sahne cihazını (Python simülatörü) TCP üzerinden JSON komutlarıyla kontrol ettiğini gösteren, çalışır ve anlatılabilir bir proje.

Proje bittiğinde:

- [ ] Sunucu ve uygulama README'deki adımlarla 10 dakikada ayağa kalkıyor
- [ ] Uygulama sunucu kapanınca, geçersiz yanıt gelince, bağlantı kopunca çökmüyor
- [ ] Test Modu en az 10 senaryoyu çalıştırıp PASS/FAIL raporunu dosyaya kaydediyor
- [ ] Python testleri tek komutla geçiyor
- [ ] Git geçmişi temiz: aşama başına 2-4 commit, feature branch'ler, tag'ler
- [ ] README (Türkçe + İngilizce), ekran görüntüleri ve demo GIF'i hazır
- [ ] Projedeki her sınıf 1-2 cümleyle anlatılabiliyor

## 2. Kapsam

| Kapsam içi | Kapsam dışı (bilerek) |
|---|---|
| Java, Android Studio, minSdk 24 | Kotlin, Compose, Retrofit, Hilt/Dagger, Room, Coroutine |
| ExecutorService, `org.json`, RecyclerView | Karmaşık mimari kalıpları |
| Python standart kütüphane sunucusu | Gerçek donanım, kimlik doğrulama, TLS |
| Otomatik yeniden bağlanma, timeout | Veritabanı, bulut, çoklu cihaz yönetimi |

## 3. Teknik kararlar

- Kod yorumları **Türkçe**; sınıf/değişken isimleri ve commit mesajları **İngilizce**; README **Türkçe + İngilizce**
- Lisans: MIT
- Kanal numaraları **1-8**, değerler **0-100 tam sayı**, mute yalnızca ses kanallarında
- Varsayılan port: **5000**
- Zaman aşımları: bağlanma 3 sn, okuma 3 sn
- Yeniden bağlanma: en fazla 5 deneme, bekleme 1-2-4-8-16 sn
- Kaydırıcı: parmak kalkınca gönder, sürüklerken en fazla her 100 ms'de bir
- Satır sınırı: 1 KB; aşılırsa `INVALID_JSON`

## 4. Haberleşme protokolü (newline-delimited JSON)

Her mesaj tek satırlık JSON'dur ve `\n` ile biter.

| Komut | İstek | Başarılı yanıt |
|---|---|---|
| ping | `{"cmd":"ping"}` | `{"status":"ok","cmd":"pong"}` |
| set_level | `{"cmd":"set_level","type":"light","channel":3,"value":75}` | `{"status":"ok","cmd":"set_level","type":"light","channel":3,"value":75}` |
| set_mute | `{"cmd":"set_mute","channel":2,"mute":true}` | `{"status":"ok","cmd":"set_mute","channel":2,"mute":true}` |
| get_state | `{"cmd":"get_state"}` | Tüm kanalların durumu |

Hata yanıtı: `{"status":"error","code":"..."}` — kodlar: `INVALID_JSON`, `INVALID_CHANNEL`, `INVALID_VALUE`, `UNKNOWN_CMD`.

## 5. Görev listesi

### Aşama 0: Ortam ve repo · `main`
- [x] 0.1 Git kurulumu, kimlik ayarı
- [x] 0.2 Klasör yapısı, `git init`
- [ ] 0.3 `.gitignore`, iki dilli README, MIT `LICENSE`, `docs/PLAN.md`
- [ ] 0.4 GitHub'da boş repo, remote, ilk push
- [ ] 0.5 Android Studio, sanallaştırma, RAM kontrolü

### Aşama 1: Python sunucusu · `feature/tcp-server`
- [ ] 1.1 Sunucu iskeleti: dinleme, istemci başına thread
- [ ] 1.2 Satır tamponlama (yarım ve birleşik satırlar)
- [ ] 1.3 Durum modeli (8 ışık, 8 ses + mute), `Lock`
- [ ] 1.4 Komutlar: ping, set_level, set_mute, get_state
- [ ] 1.5 Doğrulama, 4 hata kodu, çökmeme garantisi
- [ ] 1.6 Zaman damgalı konsol logu
- [ ] 1.7 `--host`, `--port` argümanları
- [ ] 1.8 Ctrl+C ile düzgün kapanma, boşta kalan istemciler için timeout
- [ ] 1.9 Maksimum satır uzunluğu (1 KB)
- [ ] 1.10 `test_client.py` elle test aracı
- [ ] 1.11 Hata enjeksiyon modu: `--latency-ms`, `--drop-rate`

### Aşama 2: Android iskeleti · `feature/android-skeleton`
- [ ] 2.1 Empty Views Activity, Java, minSdk 24
- [ ] 2.2 Emülatör (AVD) kurulumu
- [ ] 2.3 `INTERNET` izni
- [ ] 2.4 Bağlantı arayüzü: IP, port, Bağlan/Kes, durum göstergesi
- [ ] 2.5 IP/port doğrulama
- [ ] 2.6 Son IP/port'u SharedPreferences ile hatırlama
- [ ] 2.7 `10.0.2.2` (emülatör) ve yerel IP (gerçek telefon) notları
- [ ] 2.8 Windows güvenlik duvarı notu

### Aşama 3: SocketClient ve ilk uçtan uca sürüm · `feature/socket-client`
- [ ] 3.1 `ConnectionState` enum'u
- [ ] 3.2 `SocketClient` + `ExecutorService`
- [ ] 3.3 Gönder/oku, timeout, kaynak temizliği
- [ ] 3.4 `CommandBuilder` (ping), `ResponseParser`
- [ ] 3.5 UI thread'ine geçiş
- [ ] 3.6 Ping butonu ve yanıt süresi (ms)
- [ ] 3.7 Ekran döndürmede bağlantının korunması
- [ ] 3.8 `CommandBuilder` ve `ResponseParser` için JUnit testleri
- [ ] 3.9 Tutarlı logcat etiketleri
- 🏷️ Tag: `v0.1.0`

### Aşama 4: Işık ve ses kontrolleri · `feature/controls`
- [ ] 4.1 Sekme yapısı
- [ ] 4.2 Işık sekmesi: 8 SeekBar
- [ ] 4.3 Ses sekmesi: 8 SeekBar + mute
- [ ] 4.4 Kontrollü gönderme (bırakınca + 100 ms sınırı)
- [ ] 4.5 set_level, set_mute, get_state komutları
- [ ] 4.6 Bağlanınca `get_state` ile başlangıç durumu
- [ ] 4.7 Bağlı değilken kontrollerin devre dışı kalması
- [ ] 4.8 Hata yanıtı için kullanıcı mesajı
- [ ] 4.9 Görsel cila: renk paleti, etiketler, yatay/dikey düzen
- [ ] 4.10 Uygulama ikonu ve adı

### Aşama 5: Log ekranı · `feature/log-screen`
- [ ] 5.1 `LogEntry` modeli
- [ ] 5.2 En fazla 500 kayıt
- [ ] 5.3 RecyclerView, gönderilen/alınan farklı renk
- [ ] 5.4 Her mesajın loga düşmesi
- [ ] 5.5 Temizle butonu, otomatik kaydırma
- [ ] 5.6 Logu paylaşma/kopyalama

### Aşama 6: Dayanıklılık · `feature/resilience`
- [ ] 6.1 Kopma algılama
- [ ] 6.2 Otomatik yeniden bağlanma
- [ ] 6.3 Kullanıcı "Kes" derse durdurma
- [ ] 6.4 Yeniden bağlanınca `get_state`
- [ ] 6.5 Bozuk yanıt yönetimi
- [ ] 6.6 Executor'ın kapatılması, thread sızıntısı kontrolü
- [ ] 6.7 Elle hata senaryoları (sunucuyu kapat/aç, yanlış IP/port)
- [ ] 6.8 Hata enjeksiyonuyla timeout davranışını doğrulama
- [ ] 6.9 Bulunan hataları GitHub Issues'a şablonla yazma

### Aşama 7: Test Modu ve testler · `feature/test-mode`
- [ ] 7.1 `TestResult` modeli
- [ ] 7.2 `TestRunner` (arka thread)
- [ ] 7.3 Senaryolar T1-T11 (aşağıdaki tablo)
- [ ] 7.4 Test ekranı: yeşil/kırmızı sonuçlar, özet satırı
- [ ] 7.5 Raporu uygulama klasörüne kaydetme (`getExternalFilesDir`)
- [ ] 7.6 Raporu paylaş butonu
- [ ] 7.7 Raporda cihaz bilgisi (tarih, sunucu adresi, sürüm, toplam PASS/FAIL)
- [ ] 7.8 Python `unittest` testleri (`server/test_server.py`)
- [ ] 7.9 Hata enjeksiyonlu sunucuda Test Modu'nun FAIL ürettiğini gösterme
- [ ] 7.10 GitHub Actions ile Python testleri + README rozeti

| ID | Senaryo | PASS koşulu |
|---|---|---|
| T1 | Bağlantı | Süre içinde bağlandı |
| T2 | Ping süresi | 5 ping, ortalama < 500 ms |
| T3 | Işık kanalları 1-8 | Her yanıt gönderilenle eşleşiyor |
| T4 | Ses kanalları 1-8 | Aynı |
| T5 | Mute aç/kapat | Yanıtlar doğru |
| T6 | get_state tutarlılığı | Durum son yazılanlarla uyumlu |
| T7 | Geçersiz kanal | `INVALID_CHANNEL` döner |
| T8 | Geçersiz değer | `INVALID_VALUE` döner |
| T9 | Bilinmeyen komut | `UNKNOWN_CMD` döner |
| T10 | Bozuk JSON | `INVALID_JSON` döner |
| T11 | Zaman aşımı | Kapalı porta bağlanma süre içinde hata veriyor, arayüz donmuyor |

### Aşama 8: Dokümantasyon ve yayın · `docs/readme`
- [ ] 8.1 README (Türkçe + İngilizce): özet, mimari şeması, protokol tablosu, kurulum/çalıştırma, öğrendiklerim, sınırlamalar
- [ ] 8.2 `docs/protocol.md`
- [ ] 8.3 `docs/commissioning-checklist.md`: cihaz devreye alma kontrol listesi
- [ ] 8.4 README'de Sorun Giderme bölümü
- [ ] 8.5 README'de sektör notu (DMX / Art-Net / OSC ile ilişkisi)
- [ ] 8.6 README'de bilinen sınırlamalar ve gelecek çalışmalar
- [ ] 8.7 Ekran görüntüleri
- [ ] 8.8 Demo GIF'i / video (30-60 sn)
- [ ] 8.9 `docs/sample-test-report.json`
- [ ] 8.10 GitHub Issue şablonu (`.github/ISSUE_TEMPLATE/bug_report.md`)
- [ ] 8.11 Repo açıklaması, topic'ler, profilde sabitleme
- [ ] 8.12 Temiz klasöre klonlayıp README'yi baştan sona deneme
- [ ] 8.13 Debug APK'yı GitHub Release olarak yayınlama
- 🏷️ Tag: `v1.0.0`

### Aşama 9: Başvuru paketi (repo dışı)
- [ ] 9.1 CV maddeleri
- [ ] 9.2 Ön yazı paragrafı
- [ ] 9.3 10 mülakat sorusu ve cevapları
- [ ] 9.4 Projeyi 2 dakikada anlatma provası
- [ ] 9.5 "En zor hata neydi?" hikâyesi
- [ ] 9.6 LinkedIn ve GitHub profil düzenleme

### Aşama 10 ⭐ (opsiyonel, v1.1.0)
- [ ] 10.1 ⭐ Preset sistemi: durumu isimle kaydet/yükle, JSON dışa/içe aktar
- [ ] 10.2 ⭐ Çok istemcili durum bildirimi (sunucudan olay mesajı)
- [ ] 10.3 ⭐ GitHub Actions'a Android `assembleDebug` ve JUnit adımı
- 🏷️ Tag: `v1.1.0`

## 6. Git akışı

- `main` her zaman çalışır durumda
- Her aşama için kısa bir feature branch açılır, aşama bitince `main`'e merge edilir
- Commit mesajları İngilizce, kısa, emir kipinde; aşama başına 2-4 küçük commit
- Kilometre taşlarında tag: `v0.1.0`, `v1.0.0`, ⭐ `v1.1.0`

| Aşama | Branch |
|---|---|
| 0 | `main` |
| 1 | `feature/tcp-server` |
| 2 | `feature/android-skeleton` |
| 3 | `feature/socket-client` |
| 4 | `feature/controls` |
| 5 | `feature/log-screen` |
| 6 | `feature/resilience` |
| 7 | `feature/test-mode` |
| 8 | `docs/readme` |

## 7. Zaman çizelgesi (haftada 8-10 saat)

| Hafta | Aşamalar |
|---|---|
| 1 | 0, 1, 2 |
| 2 | 3, 4 |
| 3 | 5, 6 |
| 4 | 7 |
| 5 | 8, 9 |
| 6+ | 10 (opsiyonel) |

## 8. Riskler

| Risk | Önlem |
|---|---|
| Emülatör sunucuya ulaşamıyor | `10.0.2.2` kullanımı, sunucuyu `0.0.0.0`'da dinletme, güvenlik duvarı izni |
| `NetworkOnMainThreadException` | Tüm ağ işi Executor'da |
| Kaydırıcı mesaj seline yol açıyor | Bırakınca gönderme, 100 ms sınırı |
| Yarım/birleşik satır (TCP akışı) | İki tarafta satır tamponlama |
| Çoklu thread'de durum bozulması | Sunucuda `Lock` |
| Kapsamın şişmesi | Kısıt listesine sadık kalmak, yeni fikirleri gelecek çalışmalara yazmak |
