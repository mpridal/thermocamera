# Hti HT-102 – komunikační protokol

> Stav k 21. 11. 2026: **termo kanál je znám a ověřen na snímcích z kamery.**
> Termo data jsou ukrytá v běžném video snímku YUY2 – nejsou potřeba žádné vlastní USB požadavky ani inicializace.

## Shrnutí

| | |
|---|---|
| Kanál | standardní UVC stream, formát YUY2 **640 × 480** |
| Umístění dat | nejnižší bit (LSB) chroma bajtů (U/V = liché offsety) na začátku snímku |
| Rozměr | 32 × 32 bodů, po řádcích (row-major), bod (0, 0) vlevo nahoře |
| Velikost | 30 B snímku na 1 bod → 32 · 32 · 30 = **30 720 B** (prvních 24 řádků obrazu) |
| Jednotka | desetiny °C, se znaménkem |
| Frekvence | s každým video snímkem (termo senzor ~9 Hz, video až 30 fps → hodnoty se opakují) |
| Inicializace | žádná – data tečou i přes systémový ovladač Windows (`usbvideo`) |
| Emisivita | aplikace výrobce žádnou nenastavuje → kamera počítá s pevnou emisivitou (hodnota neznámá, ověřit referenčním měřením) |

## Kódování jednoho bodu

Bod `i` (0…1023) začíná na bajtu `p = i · 30` snímku.

- **znaménko** = `b[p + 1] & 1` (1 = záporná hodnota),
- **velikost** = 14 bitů z `b[p + 3] & 1`, `b[p + 5] & 1`, …, `b[p + 29] & 1`, nejvyšší bit první,
- **teplota °C** = `±velikost / 10`.

Rozsah 14 bitů (0–1638,3 °C) bohatě pokrývá měřicí rozsah kamery (−20 až 300 °C).
Změna nejnižšího bitu chroma je v obraze neviditelná, proto rozbor ve Windows (kap. 2.1 zadání) data nenašel.

Implementace: `thermal-core/.../Ht102Decoder.kt` (`decode` + `encode` pro testy a simulátor).

## Jak bylo zjištěno

1. **Aplikace výrobce** – stránka `xintest.com.cn/download.php` nefunguje; soubory výrobce jsou ale v Internet Archive
   (`web.archive.org`, originální adresy `xintest.com.cn/inc/lib/download/download.php?DId=…`). Staženo 21. 11. 2026:

   | Soubor | Balíček | Podpis | Pro kameru |
   |---|---|---|---|
   | `ThermalViewer_Hti_V1_1_1.apk` (2018, DId=28) | `com.meridianinno.senxorviewer` | Android Debug | **HT-102** – kód Nuvoton (výrobce čipu W55FA93), senzor Meridian Innovation SenXor, 32 × 32 |
   | `Thermal View-V-4.1.0.29-RELEASE.apk` (2023, DId=35) | `com.temp.action.thermal` | CN=Xiaozd, Shenzhen | Seek Thermal (USB vendor 0x289D) – jiná kamera |
   | `HT-ThermCameraViewer-V-6.3.20201238-RELEASE.apk` (2023, DId=37) | `com.hti.Xtherm` | CN=Xiaozd, Shenzhen | InfiRay/Xtherm (HT-301 apod.) – jiná kamera |

   Soubory jsou v `docs/vendor/` (v `.gitignore`, necommitují se).

2. **Rozbor `ThermalViewer_Hti_V1_1_1`** (jadx + disassembler z NDK):
   - Java: `AbstractUVCCameraHandler` má `THERMAL_WIDTH = THERMAL_HEIGHT = 32`; teplotu bodu čte jako
     `thermal_data[32 · y + x] / 10.0`. Data přicházejí z nativní knihovny v callbacku `onFrame(frame, int[] thermal_data)`.
   - Nativní `libuvc.so` (upravená knihovna libuvc): `decode_thermal_data(frame)` volá
     `image_generate(640, 480, 32, 32, 1, frame->data, thermal_buf)`, která dekóduje formát popsaný výše
     a vrací průměr středních 2 × 2 bodů („teplota ve středu“).
   - Žádné vendor/class USB požadavky na čtení teploty; video se nastavuje jako běžná UVC kamera 640 × 480 YUY2.
   - Zarovnání termo obrazu na běžný je v aplikaci jen ruční posun X/Y tažením prstu (`x_thermal_shift`, `y_thermal_shift`).

3. **Ověření** – dekódováním deseti snímků 640 × 480 zachycených 21. 11. 2026 ve Windows
   (`docs/captures/windows-2026-11-21/`, jeden snímek je testovací data v `thermal-core/src/test/resources/ht102/`):
   min 13,7–15,7 °C, max 30,7–31,7 °C, střed 25,7–26,8 °C; horní polovina scény 18–20 °C (místnost),
   dolní 28–31 °C (teplý předmět). Snímky se liší jen šumem. Test `Ht102DecoderTest`.

## Implementace v aplikaci (Fáze 1)

`Ht102Source` → `UvcStream` (VS_PROBE/COMMIT, 640 × 480 při 10 fps, alt. nastavení podle `dwMaxPayloadTransferSize`)
→ `IsoReader` (nativní izochronní přenosy přes usbfs) → `UvcFrameAssembler` → `Ht102Decoder` + `Yuy2`.
Opakované termo matice se přeskakují (senzor ~9 Hz, video 10 fps).

## Co zbývá ověřit na telefonu

- Na Androidu: stream 640 × 480 YUY2 přes UVC knihovnu, dekódování živě, skutečná frekvence termo dat.
- ~~Zda se termo data objevují i v rozlišení 320 × 240 / 160 × 120~~ – **ano**, ověřeno na snímcích z Windows
  (stejné hodnoty, test `decodesSmallerResolutions`).
- Orientace termo matice vůči obrazu modulu (otočení/zrcadlení) – ruka v pravé části záběru.
- Absolutní přesnost a emisivita: led ve vodě ≈ 0 °C, teplá voda s teploměrem, dlaň ≈ 34 °C.

## USB deskriptory

Ověřeno ve Windows 21. 11. 2026 (`tools/windows-diag/`, výsledky v `docs/captures/windows-2026-11-21/`):
VID `0x0416`, PID `0x9393`, „W55FA93 USB UVC Device“, UVC 1.0, jediný formát YUY2
(640 × 480, 320 × 240, 160 × 120), žádná Extension Unit, HID ani vendor rozhraní.
Úplný výpis: příloha A zadání (`docs/zadani.md`).

## Měření na Pixelu 10 Pro (2026-11-21)

| Rozlišení | Vyžádáno | Skutečně video | Termo | Ztracené snímky |
|---|---|---|---|---|
| 640 × 480 | 10 fps | 5,2 fps | 5,2 Hz (nová matice v každém snímku) | 0 |
| 640 × 480 | 30 fps | 5,0 fps | 5,2 Hz | 0 |
| 320 × 240 | … | … | … | … |
| 160 × 120 | 30 fps | 6,5 fps | 6,6 Hz | 0 |

Kamera dává ~5–6,5 snímku/s bez ohledu na požadavek a šířku pásma (160 × 120 = jen ~1 MB/s); každý snímek nese novou termo matici. Udávaných 9 Hz v praxi nedosahuje. Test kamery proto vyžaduje ≥ 5 Hz (rozhodnutí D22).
Rozlišení a frekvence jdou přepnout v Nastavení › Pro pokročilé.
