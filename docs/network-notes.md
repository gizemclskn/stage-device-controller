\# Network notes / Ağ bağlantı notları



How the Android app reaches the Python server. These notes feed the README troubleshooting section.

Android uygulamasının Python sunucusuna nasıl ulaştığı. Bu notlar README'deki Sorun Giderme bölümünü besleyecek.



\## 1. Emulator / Emülatör



| | |

|---|---|

| Server address in the app / Uygulamadaki sunucu adresi | `10.0.2.2` |

| Port | `5000` (default / varsayılan) |



Inside the emulator, `127.0.0.1` points to the emulator itself, not to your computer. `10.0.2.2` is the special address the emulator uses for your computer's loopback interface.



Emülatörün içinde `127.0.0.1` bilgisayarını değil, emülatörün kendisini gösterir. `10.0.2.2`, emülatörün bilgisayarına ulaşmak için kullandığı özel adrestir.



\## 2. Real phone / Gerçek telefon



1\. Put the phone and the computer on the same Wi-Fi network. / Telefonu ve bilgisayarı aynı Wi-Fi ağına bağla.

2\. On the computer run `ipconfig` and find the `IPv4 Address` of the active Wi-Fi or Ethernet adapter (for example `192.168.1.20`). / Bilgisayarda `ipconfig` çalıştır, kullandığın Wi-Fi ya da Ethernet bağdaştırıcısındaki `IPv4 Address` değerini bul (örn. `192.168.1.20`).

3\. Enter that address in the app. / Bu adresi uygulamaya yaz.

4\. Start the server with the default host (`0.0.0.0`) so that it listens on all network interfaces. / Sunucuyu varsayılan adresle (`0.0.0.0`) başlat, böylece tüm ağ arayüzlerini dinler.



Guest Wi-Fi networks, active VPNs and the "client isolation" option of some routers block traffic between devices.

Misafir Wi-Fi ağları, açık VPN'ler ve bazı yönlendiricilerdeki "istemci yalıtımı" seçeneği cihazlar arası trafiği engeller.



\## 3. Windows Firewall / Windows Güvenlik Duvarı



The first time Python listens on a port, Windows asks whether to allow it. Choose \*\*Private networks\*\*.

Python bir portu ilk kez dinlediğinde Windows izin ister. \*\*Özel ağlar\*\*'ı seç.



If you missed the prompt, open PowerShell \*\*as Administrator\*\* and add a rule for the port:

Uyarıyı kaçırdıysan PowerShell'i \*\*yönetici olarak\*\* aç ve port için bir kural ekle:



```

New-NetFirewallRule -DisplayName "stage-device-controller (TCP 5000)" -Direction Inbound -Protocol TCP -LocalPort 5000 -Action Allow -Profile Private

```



Remove the rule when you no longer need it: / İhtiyacın kalmayınca kuralı sil:



```

Remove-NetFirewallRule -DisplayName "stage-device-controller (TCP 5000)"

```



The simulator has no authentication. Use it only on networks you trust.

Simülatörde kimlik doğrulama yoktur. Yalnızca güvendiğin ağlarda kullan.



\## 4. Quick checks on the computer / Bilgisayarda hızlı kontroller



Start the server in one window: / Sunucuyu bir pencerede başlat:



```

python server\\server.py

```



In another window: / Başka bir pencerede:



```

Get-NetTCPConnection -LocalPort 5000 -State Listen

Test-NetConnection 127.0.0.1 -Port 5000

python server\\test\_client.py

```



The first command shows that the server is listening. `TcpTestSucceeded : True` in the second means the port accepts connections on the computer. From another computer you can run `Test-NetConnection <ip> -Port 5000` to check the network path.



İlk komut sunucunun dinlediğini gösterir. İkincide `TcpTestSucceeded : True` yazması, portun bilgisayarda bağlantı kabul ettiğini gösterir. Ağ yolunu denemek için başka bir bilgisayardan `Test-NetConnection <ip> -Port 5000` çalıştırabilirsin.



\## 5. Symptoms / Belirtiler



| Symptom / Belirti | Likely cause / Olası neden | Fix / Çözüm |

|---|---|---|

| Connection refused right away / Hemen "bağlantı reddedildi" | Server not running or wrong port / Sunucu kapalı ya da port yanlış | Start the server, check the port / Sunucuyu başlat, portu kontrol et |

| Hangs, then times out / Bekliyor, sonra zaman aşımı | Wrong IP, different network or firewall / IP yanlış, farklı ağ ya da güvenlik duvarı | `ipconfig`, same Wi-Fi, firewall rule / `ipconfig`, aynı Wi-Fi, güvenlik duvarı kuralı |

| Works with `test\_client.py`, not from the emulator / `test\_client.py` çalışıyor, emülatörden çalışmıyor | App uses `127.0.0.1` / Uygulama `127.0.0.1` kullanıyor | Use `10.0.2.2` / `10.0.2.2` kullan |

| Works on the emulator, not on the phone / Emülatörde çalışıyor, telefonda çalışmıyor | Firewall or different network / Güvenlik duvarı ya da farklı ağ | Sections 2 and 3 / 2. ve 3. bölümler |

| "Address already in use" when starting the server / Sunucu başlarken "adres kullanımda" | Another server instance holds the port / Başka bir sunucu portu tutuyor | Close it or use `--port` / Kapat ya da `--port` kullan |

