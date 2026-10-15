# Protokol

TCP üzerinden satır tabanlı JSON (newline-delimited JSON). Satır başına bir JSON nesnesi (`\n` ile biter), UTF-8. Varsayılan port: 5000.

## Kurallar

- Kanallar 1-8 arası numaralanır.
- `value`, 0 ile 100 arasında bir tam sayıdır. Ondalık sayılar, metinler, `true/false` ve aralık dışı sayılar `INVALID_VALUE` ile reddedilir.
- Mute yalnızca ses kanallarına uygulanır, bu yüzden `set_mute` komutunda `type` alanı yoktur.
- Satır uzunluğu en fazla 1 KB olabilir. Daha uzun bir satır `INVALID_JSON` ile reddedilir, satırın kalanı atılır ve bağlantı açık kalır.
- Başlangıç durumu: tüm ışıklar 0, tüm ses seviyeleri 0, mute kapalı.
- `get_state` yanıtındaki dizilerde indeks 0 Kanal 1'dir.

## Komutlar

| Komut | İstek | Başarılı yanıt |
|---|---|---|
| ping | `{"cmd":"ping"}` | `{"status":"ok","cmd":"pong"}` |
| set_level | `{"cmd":"set_level","type":"light","channel":3,"value":75}` | aynı alanlar + `"status":"ok"` |
| set_mute | `{"cmd":"set_mute","channel":2,"mute":true}` | `{"status":"ok","cmd":"set_mute","channel":2,"mute":true}` |
| get_state | `{"cmd":"get_state"}` | `{"status":"ok","cmd":"get_state","light":[8 sayı],"audio":[{"level":..,"mute":..} x8]}` |

`set_level` için `type` değeri `light` veya `audio` olur.
Ping yanıtındaki `"cmd":"pong"`, bilinçli bir tasarım tercihidir.

## Hatalar

Hata yanıtı biçimi:

```json
{"status":"error","code":"INVALID_VALUE","message":"..."}
```

`message` insan okuması içindir. İstemciler yalnızca `code` alanına bakmalıdır.

| Durum | Kod |
|---|---|
| Bozuk JSON, JSON olup nesne olmayan değer (`5`, `[1]`), boş satır, 1 KB'ı aşan satır | `INVALID_JSON` |
| Tanınmayan ya da eksik `cmd` | `UNKNOWN_CMD` |
| `channel` eksik, tam sayı değil veya 1-8 dışı | `INVALID_CHANNEL` |
| `type` eksik veya `light`/`audio` dışı | `INVALID_VALUE` |
| `value` eksik, ondalık, metin, `true/false` veya 0-100 dışı | `INVALID_VALUE` |
| `mute` bool değil | `INVALID_VALUE` |

## Zaman aşımları

- İstemci bağlanma zaman aşımı: 3 sn. İstemci okuma zaman aşımı: 3 sn.
- Okuma zaman aşımında istemci isteği başarısız sayar ve bağlantıyı kopmuş olarak işaretler. Böylece geç gelen bir yanıt, sonraki komutun yanıtı sanılamaz.
- Sunucu, 300 sn boyunca hiçbir şey göndermeyen istemciyi kapatır (`--idle-timeout`).

## Hata enjeksiyonu (sunucu seçenekleri)

- `--latency-ms N`: her yanıtı N milisaniye geciktirir.
- `--drop-rate X`: komutların X oranında (0-1) bir kısmına yanıt vermez.