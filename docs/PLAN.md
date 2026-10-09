# stage-device-controller: Proje Planı (v3)

Son güncelleme: 9 Ekim 2026
Durum işaretleri: **[x]** bitti, **[ ]** yapılacak, **⭐** opsiyonel (zaman kalırsa)

---

## 1. Proje tanımı

**Ne yapıyor?** Python ile yazılmış bir sahne cihazı simülatörü (8 ışık kanalı, 8 ses kanalı) ve onu ağ üzerinden kontrol eden bir Java Android uygulaması. İkisi TCP üzerinden satır tabanlı JSON (newline-delimited JSON) ile konuşur. Uygulama ışık ve ses seviyelerini ayarlar, mute yapar, tüm trafiği log ekranında gösterir, bağlantı koparsa kendini toparlar ve kendi içinde bir **Test Modu** ile cihazı otomatik sınayıp PASS/FAIL raporu üretir. Sunucuda ayrıca bir **hata enjeksiyon modu** vardır: yapay gecikme ve yanıt düşürme ile uygulamanın ve testlerin gerçekten hata yakaladığı gösterilir.

**Neden bu proje?** Ağ üzerinden kontrol edilen sahne/AV cihazlarıyla çalışan, test, hata raporlama, cihaz kurulumu ve destek becerileri arayan bir pozisyona başvuru için, bu becerilerin hepsini tek bir çalışır ve anlatılabilir projede kanıtlamak.

**Hedef:** Java ile yazılmış bir Android uygulamasının, ağdaki bir sahne cihazını (simülatör) TCP üzerinden JSON komutlarıyla kontrol ettiğini gösteren, çalışır ve anlatılabilir bir portfolyo projesi.

### Başarı ölçütleri

Proje bittiğinde şunların hepsi doğru olmalı:

- [ ] Sunucu ve uygulama, README'deki adımlarla 10 dakikada ayağa kalkıyor
- [ ] Uygulama sunucu kapanınca, geçersiz yanıt gelince, bağlantı kopunca çökmüyor
- [ ] Test Modu en az 10 senaryoyu çalıştırıp PASS/FAIL raporunu dosyaya kaydediyor ve paylaşılabiliyor
- [ ] Hata enjeksiyonlu sunucuda Test Modu FAIL, sağlam sunucuda PASS veriyor
- [ ] Python testleri tek komutla geçiyor, GitHub Actions'ta yeşil
- [ ] Proje boyunca bulunan gerçek hatalar şablonlu GitHub Issue olarak kayıtlı
- [ ] GitHub geçmişi temiz: anlamlı küçük commit'ler (aşama başına yaklaşık 3-6), feature branch'ler, `v0.1.0` ve `v1.0.0` tag'leri
- [ ] README, ekran görüntüleri, demo GIF'i ve debug APK (GitHub Release) hazır
- [ ] Projedeki her sınıfı mülakatta 1-2 cümleyle anlatabiliyorsun

---

## 2. Kapsam

| Kapsam içi | Kapsam dışı (bilerek) |
|---|---|
| Java, Android Studio, minSdk 24 | Kotlin, Compose, Retrofit, Hilt/Dagger, Room, Coroutine |
| `ExecutorService`, `org.json`, RecyclerView, SharedPreferences | Karmaşık mimari kalıpları (MVVM katmanları vb.) |
| Python 3.12, yalnızca standart kütüphane (`socket`, `json`, `threading`) | Gerçek donanım, kimlik doğrulama, TLS |
| Otomatik yeniden bağlanma, timeout, hata enjeksiyonu | Veritabanı, bulut, çoklu cihaz yönetimi |
| JUnit (Android), `unittest` (Python), GitHub Actions | Android UI testleri (Espresso) |

---

## 3. Teknik kararlar (sabit)

**Genel**
- Geliştirme ortamı: Windows, Python 3.12 (sunucu yalnızca standart kütüphane kullanır)
- Kod yorumları **Türkçe**; sınıf, metot ve değişken adları, commit mesajları **İngilizce**; README **İngilizce + Türkçe**
- Lisans dosyası yok (bilinçli karar)

**Protokol ve davranış**
- Kanal numaraları 1-8 (arayüzde "Kanal 1..8")
- Değer: tam sayı, 0-100. Ondalık, metin, `true/false` ve aralık dışı değerler `INVALID_VALUE`
- Mute yalnızca ses kanallarına uygulanır (`set_mute` komutunda `type` alanı yoktur)
- Varsayılan port: 5000, sunucu varsayılan olarak `0.0.0.0` üzerinde dinler
- Zaman aşımları: bağlanma 3 sn, okuma 3 sn
- **Okuma zaman aşımında istek başarısız sayılır ve bağlantı LOST durumuna düşer.** Geç gelen bir yanıtın sonraki komutun yanıtıyla karışmasını önler
- Sunucu, 300 sn boyunca hiçbir şey göndermeyen istemciyi kapatır (`--idle-timeout` ile ayarlanır). Uygulama bunu bir sonraki komutta fark edip yeniden bağlanır
- Yeniden bağlanma: en fazla 5 deneme, bekleme 1-2-4-8-16 sn
- Kaydırıcı: parmak kalkınca gönder, sürüklerken en fazla her 100 ms'de bir
- Mesaj sınırı: satır başına en fazla 1 KB. Fazlası `INVALID_JSON` ile reddedilir, satırın kalanı atılır, bağlantı kapanmaz
- Başlangıç durumu: tüm ışıklar 0, tüm ses kanalları 0 ve mute kapalı

### Protokol (newline-delimited JSON)

| Komut | İstek | Başarılı yanıt |
|---|---|---|
| ping | `{"cmd":"ping"}` | `{"status":"ok","cmd":"pong"}` |
| set_level | `{"cmd":"set_level","type":"light","channel":3,"value":75}` | aynı alanlar + `"status":"ok"` |
| set_mute | `{"cmd":"set_mute","channel":2,"mute":true}` | `{"status":"ok","cmd":"set_mute","channel":2,"mute":true}` |
| get_state | `{"cmd":"get_state"}` | `{"status":"ok","cmd":"get_state","light":[8 sayı],"audio":[{"level":..,"mute":..} x8]}` |

`set_level` için `type` değeri `light` veya `audio` olur. `ping` yanıtındaki `"cmd":"pong"` bilinçli bir tasarım tercihidir.

**Hata yanıtı:** `{"status":"error","code":"INVALID_VALUE","message":"..."}`. `message` insan okuması içindir, uygulama yalnızca `code` alanına bakar.

**Hata kodları ve hangi durumda döndükleri**

| Durum | Kod |
|---|---|
| Bozuk JSON, JSON ama nesne değil (`5`, `[1]`), boş satır, 1 KB'ı aşan satır | `INVALID_JSON` |
| `cmd` alanı tanınmayan bir komut (veya hiç yok) | `UNKNOWN_CMD` |
| `channel` yok, tam sayı değil veya 1-8 dışı | `INVALID_CHANNEL` |
| `type` yok veya `light`/`audio` dışı | `INVALID_VALUE` |
| `value` yok, ondalık, metin, `true/false` veya 0-100 dışı | `INVALID_VALUE` |
| `mute` bool değil | `INVALID_VALUE` |

`get_state` dizilerinde indeks 0 = Kanal 1.

---

## 4. Görev listesi

Süreler tahminidir. Toplam: çekirdek (Aşama 0-9) yaklaşık **48-63 saat**, Aşama 10 ile birlikte yaklaşık 54-71 saat.

### Aşama 0: Ortam ve repo (~1-2 sa) · `main`
- [x] 0.1 Git kurulumu ve kimlik ayarı
- [x] 0.2 Klasör yapısı, `git init`
- [x] 0.3 `.gitignore` ve çift dilli ilk README
- [x] 0.4 GitHub'da repo, remote, ilk push
- [ ] 0.5 Android Studio kurulumu, sanallaştırma (emülatör için) ve RAM kontrolü
- [ ] 0.6 Bu planı `docs/PLAN.md` olarak güncelleme (commit: `Update project plan`)

**Bitti ölçütü:** GitHub'da repo, klasörler ve güncel plan görünüyor; Android Studio emülatörü açabilecek durumda.

### Aşama 1: Python sunucusu (~5-6 sa) · `feature/tcp-server`
- [ ] 1.1 Sunucu iskeleti: socket dinleme, her istemci için thread
- [ ] 1.2 Satır tamponlama: yarım satır, birleşik satır, 1 KB sınırı (aşılırsa `INVALID_JSON`)
- [ ] 1.3 Durum modeli: 8 ışık, 8 ses (level + mute), `threading.Lock` ile koruma
- [ ] 1.4 Komut işleyicileri: ping, set_level, set_mute, get_state
- [ ] 1.5 Doğrulama, 4 hata kodu, çökmeme garantisi (hiçbir girdi sunucuyu düşürmemeli)
- [ ] 1.6 Zaman damgalı konsol logu (istemci adresi, gelen/giden mesaj)
- [ ] 1.7 Komut satırı argümanları: `--host`, `--port`
- [ ] 1.8 Ctrl+C ile düzgün kapanma, boşta kalan istemci için timeout (`--idle-timeout`)
- [ ] 1.9 `test_client.py`: elle komut gönderme aracı
- [ ] 1.10 **Hata enjeksiyon modu:** `--latency-ms N` (her yanıta yapay gecikme), `--drop-rate X` (komutların bir kısmına yanıt vermeme, 0-1 arası). README'de anlatılacak

**Commit planı:**
`Add TCP server skeleton with line buffering` ·
`Add device state and command handlers` ·
`Add validation errors and timestamped logging` ·
`Add CLI options, graceful shutdown and idle timeout` ·
`Add manual test client` ·
`Add fault injection options`

**Bitti ölçütü:** 4 komut ve 4 hata kodu test istemcisiyle doğru yanıtlanıyor, 2 istemci aynı anda bağlanabiliyor, bozuk girdiyle sunucu çökmüyor, Ctrl+C temiz kapatıyor, hata enjeksiyonu gözlemlenebiliyor.

### Aşama 2: Android iskeleti (~4-5 sa) · `feature/android-skeleton`
- [ ] 2.1 Empty Views Activity projesi (Java, minSdk 24), `android-app/` klasöründe
- [ ] 2.2 Emülatör (AVD) oluşturma ve ilk çalıştırma
- [ ] 2.3 `INTERNET` izni (ham TCP için `usesCleartextTraffic` gerekmez)
- [ ] 2.4 Bağlantı arayüzü: IP, port, Bağlan/Kes butonu, durum göstergesi
- [ ] 2.5 IP/port doğrulama: boş, geçersiz format, port 1-65535, düzgün uyarı mesajı
- [ ] 2.6 Son IP/port'u SharedPreferences ile hatırlama
- [ ] 2.7 Emülatör için `10.0.2.2`, gerçek telefon için yerel IP notları
- [ ] 2.8 Windows güvenlik duvarında Python için inbound kural notu (gerçek telefon için)

**Commit planı:**
`Create Android project skeleton` ·
`Add connection screen layout` ·
`Add input validation and remembered address` ·
`Add network permission`

**Bitti ölçütü:** Uygulama emülatörde açılıyor, arayüz doğru görünüyor, geçersiz girdi uyarı veriyor, son adres hatırlanıyor.

### Aşama 3: SocketClient, ilk uçtan uca sürüm (~7-9 sa) · `feature/socket-client`
- [ ] 3.1 `ConnectionState` enum'u (DISCONNECTED, CONNECTED, LOST, RECONNECTING)
- [ ] 3.2 `SocketClient`: tek thread'li `ExecutorService`, connect/disconnect
- [ ] 3.3 Satır gönder, satır oku, bağlanma ve okuma timeout'u (okuma timeout'u bağlantıyı LOST yapar), `try/finally` ile kaynak temizliği
- [ ] 3.4 `CommandBuilder` (ping), `ResponseParser`
- [ ] 3.5 Callback ile UI thread'ine geçiş (`runOnUiThread` veya `Handler`)
- [ ] 3.6 Ping butonu, yanıt süresini (ms) gösterme
- [ ] 3.7 **SocketClient ömrü:** tek örnek `Application` sınıfında yaşar, ekran döndürmede bağlantı korunur. Activity callback'i `onStart`'ta kaydeder, `onStop`'ta kaldırır (bellek sızıntısı olmasın)
- [ ] 3.8 `CommandBuilder` ve `ResponseParser` için JUnit testleri
- [ ] 3.9 `Log.d/e` ile tutarlı logcat etiketleri, logcat okuma alıştırması

**Commit planı:**
`Add ConnectionState enum` ·
`Add SocketClient with executor` ·
`Add CommandBuilder and ResponseParser with unit tests` ·
`Wire ping to UI` ·
`Keep SocketClient alive across rotation`

**Bitti ölçütü:** Emülatörden sunucuya bağlanıp ping atılıyor, ekran döndürünce bağlantı kopmuyor, JUnit testleri geçiyor. **Tag: `v0.1.0`**

### Aşama 4: Işık ve ses kontrolleri (~7-9 sa) · `feature/controls`
- [ ] 4.1 Sekme yapısı (TabLayout + ViewPager2 veya basit Fragment geçişi)
- [ ] 4.2 Işık sekmesi: 8 SeekBar, her biri için etiket ve değer
- [ ] 4.3 Ses sekmesi: 8 SeekBar + mute butonu
- [ ] 4.4 Kontrollü gönderme: bırakınca, sürüklerken en fazla 100 ms'de bir
- [ ] 4.5 `CommandBuilder`'a set_level, set_mute, get_state ekleme
- [ ] 4.6 Bağlanınca `get_state` ile arayüzü başlangıç durumuna getirme
- [ ] 4.7 Bağlı değilken kontrollerin devre dışı kalması
- [ ] 4.8 Sunucu hata yanıtında kullanıcıya kısa mesaj (Toast/Snackbar)
- [ ] 4.9 Görsel cila: tutarlı renk paleti, kanal etiketleri, dikey/yatay düzen kontrolü
- [ ] 4.10 Uygulama ikonu ve adı

**Commit planı:**
`Add tab layout for light and audio` ·
`Add light sliders with throttled sending` ·
`Add audio sliders and mute buttons` ·
`Load initial state with get_state` ·
`Polish layout, icon and app name`

**Bitti ölçütü:** Kaydırıcı sunucu konsolunda görünüyor, uygulamayı kapatıp açınca değerler sunucudan geri yükleniyor, yatay ve dikey düzen bozulmuyor.

### Aşama 5: Log ekranı (~3-4 sa) · `feature/log-screen`
- [ ] 5.1 `LogEntry` modeli (zaman damgası, yön: SENT/RECEIVED, metin)
- [ ] 5.2 Bellek içi log listesi (en fazla 500 kayıt, eskiler silinir)
- [ ] 5.3 RecyclerView + adapter, gönderilen/alınan için farklı renk
- [ ] 5.4 SocketClient'tan her mesajın loga düşmesi
- [ ] 5.5 Temizle butonu, otomatik en alta kaydırma
- [ ] 5.6 Log'u metin olarak paylaşma/kopyalama (destek senaryosu için)

**Commit planı:**
`Add LogEntry model and bounded log store` ·
`Add log screen with RecyclerView` ·
`Add clear, auto-scroll and share`

**Bitti ölçütü:** Her mesaj zaman damgasıyla listede, sunucu logu ile eşleşiyor, log paylaşılabiliyor.

### Aşama 6: Dayanıklılık (~5-7 sa) · `feature/resilience`
- [ ] 6.1 Bağlantı kopmasının algılanması (okuma/yazma hatası, `null` satır, okuma timeout'u)
- [ ] 6.2 Otomatik yeniden bağlanma: 5 deneme, 1-2-4-8-16 sn, RECONNECTING durumu
- [ ] 6.3 Kullanıcı "Kes" derse yeniden bağlanmayı durdurma
- [ ] 6.4 Yeniden bağlanınca `get_state` ile durumu tazeleme
- [ ] 6.5 Geçersiz JSON veya beklenmeyen yanıt: çökmeden loglama
- [ ] 6.6 **Executor yaşam döngüsü:** Executor'lar `Application` düzeyindeki tek örnekle yaşar. "Kes" ile veya süreç sonlanırken kapatılır, `Activity.onDestroy`'da kapatılmaz (ekran dönünce bağlantı giderdi). Thread sızıntısı kontrolü
- [ ] 6.7 Elle hata senaryoları: sunucuyu kapat/aç, yanlış IP, yanlış port, boşta kalma timeout'u
- [ ] 6.8 **Hata enjeksiyonuyla doğrulama:** `--latency-ms` ve `--drop-rate` ile timeout davranışını gözlemleme
- [ ] 6.9 **Gerçek hata raporu yazma:** bulduğun her hata için GitHub Issue (aşağıdaki şablonla). Hata uydurma, yalnızca gerçekten bulduklarını yaz

**Commit planı:**
`Detect connection loss` ·
`Add automatic reconnect with backoff` ·
`Handle malformed responses` ·
`Manage executor lifecycle`

**Bitti ölçütü:** Sunucu kapatılıp açılınca uygulama kendi toparlıyor, "Kes" yeniden bağlanmayı durduruyor, hiçbir senaryoda çökme yok.

### Aşama 7: Test Modu ve testler (~7-9 sa) · `feature/test-mode`
- [ ] 7.1 `TestResult` modeli (ad, PASS/FAIL, süre, açıklama)
- [ ] 7.2 `TestRunner`: senaryoları arka thread'de sırayla çalıştırır. Kendi bağlantısını açar, timeout alan senaryo FAIL olur ve bir sonraki senaryo için yeniden bağlanır. Bozuk JSON senaryosu için ham satır gönderebilir
- [ ] 7.3 Senaryolar T1-T11 (aşağıdaki tablo)
- [ ] 7.4 Test ekranı: canlı sonuç listesi, yeşil/kırmızı gösterim, özet satırı
- [ ] 7.5 Raporu uygulamanın kendi klasörüne kaydetme (`getExternalFilesDir`, izin gerektirmez; JSON, zaman damgalı dosya adı)
- [ ] 7.6 Raporu **Paylaş** butonu
- [ ] 7.7 Raporda cihaz bilgisi: tarih, sunucu adresi, uygulama sürümü, toplam PASS/FAIL
- [ ] 7.8 Python `unittest` testleri (`server/test_server.py`), tek komut: `python -m unittest`
- [ ] 7.9 **Hata enjeksiyonlu gösterim:** aynı test sağlam sunucuda PASS, bozuk sunucuda FAIL verir (örn. `--latency-ms 600` ile T2 FAIL, `--drop-rate 0.5` ile T3-T6'da timeout FAIL). README'de anlatılacak
- [ ] 7.10 **GitHub Actions:** her push'ta Python testlerini (Python 3.12) çalıştıran workflow + README'de rozet

**Uygulama senaryoları**

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

**Sunucu testleri (`unittest`):** ping, geçerli set_level, sınır değerleri (0, 100), aralık dışı (-1, 101), ondalık ve metin değer, `true/false` değer, kanal 0 ve 9, mute, mute için bool olmayan değer, get_state, bilinmeyen komut, bozuk JSON, JSON ama nesne değil, boş satır, eksik alan, 1 KB'ı aşan satır, `LineBuffer` (yarım satır, birleşik satır), iki istemcinin ortak durumu.

**Commit planı:**
`Add test result model and runner` ·
`Add test scenarios` ·
`Add test screen and report export` ·
`Add server unit tests` ·
`Add GitHub Actions workflow`

**Bitti ölçütü:** Test Modu çalışıyor, rapor oluşuyor ve paylaşılıyor, `python -m unittest` geçiyor, Actions yeşil, hata enjeksiyonunda FAIL gözlemleniyor.

### Aşama 8: Dokümantasyon ve yayın (~6-8 sa) · `docs/readme`
- [ ] 8.1 README nihai sürüm: özet (TR+EN), mimari şeması, protokol tablosu, kurulum/çalıştırma, öğrendiklerim
- [ ] 8.2 `docs/protocol.md` (bölüm 3'teki protokol ve hata tablosu temel alınır)
- [ ] 8.3 `docs/commissioning-checklist.md`: cihaz devreye alma kontrol listesi (bağlantı, ping, kanal taraması, hata yanıtları, rapor). Test Modu bu listenin otomatik karşılığıdır
- [ ] 8.4 README'de **Sorun Giderme** bölümü (güvenlik duvarı, `10.0.2.2`, yanlış IP, port kullanımda)
- [ ] 8.5 README'de **Sektör notu:** DMX/Art-Net/OSC gibi standartlarla ilişkisi ve bu protokolün neden sade tutulduğu (3-4 cümle)
- [ ] 8.6 README'de **Bilinen sınırlamalar ve gelecek çalışmalar** (dürüstçe; kimlik doğrulama ve TLS yok, tek cihaz)
- [ ] 8.7 Ekran görüntüleri (bağlantı, ışık, ses, log, test sonucu, sunucu konsolu)
- [ ] 8.8 Demo GIF'i veya video (30-60 sn)
- [ ] 8.9 Örnek test raporu `docs/sample-test-report.json` (gerçek çalıştırmadan)
- [ ] 8.10 GitHub Issue şablonu `.github/ISSUE_TEMPLATE/bug_report.md`
- [ ] 8.11 Repo açıklaması, topic'ler (`android`, `java`, `tcp-socket`, `json`, `python`, `iot`, `testing`), profilde repoyu sabitleme (pin)
- [ ] 8.12 Temiz bir klasöre klonlayıp README'yi baştan sona deneme
- [ ] 8.13 Debug APK'yı GitHub Release olarak yayınlama

**Bitti ölçütü:** Başka biri README'ye bakarak çalıştırabiliyor. **Tag: `v1.0.0`**

### Aşama 9: Başvuru paketi (~3-4 sa) · repo dışı
- [ ] 9.1 CV için 2-3 madde
- [ ] 9.2 Ön yazıya eklenecek paragraf
- [ ] 9.3 10 mülakat sorusu ve örnek cevaplar
- [ ] 9.4 "Projeyi 2 dakikada anlat" provası
- [ ] 9.5 "En zor hata neydi, nasıl buldun?" hikâyesi (Issue'lardan)
- [ ] 9.6 LinkedIn'e proje ekleme ve GitHub profil düzenleme

**Önemli:** Aşama 9 bitince başvuruyu yap, Aşama 10'u bekleme. İlan kapanmadan başvurmak, ekstra özellikten daha değerlidir.

### Aşama 10 ⭐ (opsiyonel, ~6-8 sa) · `feature/presets` vb.
- [ ] 10.1 ⭐ **Preset sistemi:** mevcut ışık/ses durumunu isimle kaydet, geri yükle, JSON olarak dışa/içe aktar
- [ ] 10.2 ⭐ Çok istemcili durum bildirimi (bir istemci değiştirince diğeri güncellenir). Protokole olay mesajı eklemeyi gerektirir, bu yüzden bilinçli olarak sona bırakıldı
- [ ] 10.3 ⭐ GitHub Actions'a Android `assembleDebug` ve JUnit adımı

**Tag: `v1.1.0`**

---

## 5. Git akışı

| Aşama | Branch | Tag |
|---|---|---|
| 0 | `main` | |
| 1 | `feature/tcp-server` | |
| 2 | `feature/android-skeleton` | |
| 3 | `feature/socket-client` | **v0.1.0** |
| 4 | `feature/controls` | |
| 5 | `feature/log-screen` | |
| 6 | `feature/resilience` | |
| 7 | `feature/test-mode` | |
| 8 | `docs/readme` | **v1.0.0** |
| 10 | `feature/presets` vb. | **v1.1.0** |

**Kurallar**
- `main` her zaman çalışır durumda
- Commit mesajları İngilizce ve emir kipinde (`Add ...`, `Fix ...`)
- Her aşama sonunda `main`'e merge (`git merge --no-ff`, aşama geçmişte görünür kalır), push, gerekiyorsa tag
- Tag'ler `main` üzerinde atılır

---

## 6. Zaman çizelgesi (haftada 8-10 saat)

| Hafta | Aşamalar |
|---|---|
| 1 | 0, 1 |
| 2 | 2, 3'ün ilk yarısı |
| 3 | 3'ün ikinci yarısı (`v0.1.0`), 4'ün ilk yarısı |
| 4 | 4'ün ikinci yarısı, 5 |
| 5 | 6 |
| 6 | 7 |
| 7 | 8, 9 (gerekirse 8. haftaya taşar) → başvuru |
| 8+ | 10 (opsiyonel) |

Aşama 3 ve 7 en zorlu noktalar, gerekirse orada fazladan gün ayır. README bölümlerini (8.1-8.6) son haftaya bırakmadan, ilgili özellik bittikçe taslak olarak yazmak işi hafifletir.

---

## 7. Başlıca riskler

| Risk | Önlem |
|---|---|
| Emülatör sunucuya ulaşamıyor | `10.0.2.2` kullanımı, Windows güvenlik duvarında Python'a izin, sunucuyu `0.0.0.0`'da dinletme |
| `NetworkOnMainThreadException` | Tüm ağ işi Executor'da |
| Kaydırıcı mesaj seline yol açıyor | Bırakınca gönderme, sürüklerken 100 ms sınırı |
| Yarım satır/birleşik satır (TCP akışı) | Sunucuda ve istemcide satır tamponlama |
| Çoklu thread'de durum bozulması | Sunucuda `Lock` |
| Ekran döndürmede bağlantı kaybı | `SocketClient`'ı `Application` düzeyinde tutmak, Activity'de callback kaydı/kaldırması |
| Geç gelen yanıtın sonraki komutla karışması | Okuma timeout'unda bağlantıyı LOST yapıp yeniden bağlanmak |
| Boşta kalma timeout'u kullanıcıyı yanıltıyor | 300 sn varsayılan, ilk komutta yeniden bağlanma, README'de açıklama |
| Kapsamın şişmesi | Kapsam tablosuna sadık kalmak, yeni fikirleri "gelecek çalışmalar"a yazmak |
| Kodu anlamadan ilerlemek | Her aşamada mülakat notu, takıldığın yeri sormak |

---

## 8. Hata raporu şablonu (Aşama 6'dan itibaren)

```
Title: Short, specific summary
Environment: Emulator Pixel 6 / API 34, server 127.0.0.1:5000
Steps to reproduce: 1... 2... 3...
Expected result:
Actual result:
Logs: (paste relevant logcat / server log lines)
Severity: low / medium / high
```

---

## 9. Çalışma düzenimiz

Her aşamada bu sırayla ilerliyoruz: özet → tam kod (dosya yollarıyla) → çalıştırma/test → Git adımı → mülakat notu. "Tamam, çalıştı" ya da hata çıktısı gelmeden sonraki aşamaya geçilmez.

**Şu anki konum:** Aşama 0'da yalnızca 0.5 (Android Studio kurulumu) ve 0.6 (planın güncellenmesi) açık. Aşama 1'de 1.1-1.2 kodu yazıldı, çalıştırma çıktısı bekleniyor.