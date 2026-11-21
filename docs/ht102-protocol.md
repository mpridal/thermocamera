# Hti HT-102 – komunikační protokol

> Stav k 21. 11. 2026: **termo kanál není znám.** Tento dokument shrnuje, co je ověřené, a eviduje průzkum.

## Ověřeno (Windows 11, 21. 11. 2026)

Měřeno skriptem `tools/windows-diag/` – surová data v `docs/captures/windows-2026-11-21/`.

- USB 2.0 High-Speed, VID `0x0416`, PID `0x9393`, bcdDevice 1.00, sériové číslo `000055FA93`,
  produkt „W55FA93 USB UVC Device“ (SoC Nuvoton W55FA93). Kompozitní zařízení (IAD).
- Jediná funkce: **UVC 1.0**. IF0 = VideoControl (interrupt EP `0x83`, 16 B), IF1 = VideoStreaming
  (izochronní EP `0x81`, alt 1 a 2: 2 × 900 B / mikrorámec).
- Topologie: Camera Terminal (ID 1) → Processing Unit (ID 5) → Output Terminal (ID 3).
  Žádná Extension Unit, HID, vendor rozhraní ani bulk endpoint.
- Jediný formát YUY2: 640 × 480, 320 × 240, 160 × 120 (30/20/15/10/5/1 fps).
- **Stream nese jen běžný (viditelný) obraz**, žádná termo data v obrazových datech.

Úplné deskriptory: zadání, příloha A (kopie zadání v `docs/zadani.md`).

## Průzkum termo kanálu

### Oficiální aplikace Thermal Viewer (kap. 3.2)

| Datum | Zjištění |
|---|---|
| 2026-11-21 | Návod i QR kód vedou na `http://www.xintest.com.cn/download.php`. Server teď nepřijímá spojení (porty 80/443 odmítnuty), `hyfechcn.com.cn` neexistuje v DNS. APK se tedy ze stránek výrobce stáhnout nedá. |
| 2026-11-21 | Na Google Play není „Thermal Viewer“ od Hti/Xintai. Výrobce tam má novější aplikace s prefixem `com.hti.xtherm.*` (např. `com.hti.xtherm.hti103` „HT-103 Smart Thermal“). Podpora HT-102 v nich není potvrzená. |
| 2026-11-21 | Veřejně není nic o rozluštění HT-102 / `0416:9393`. Projekty pro HT-301 (`stawel/ht301_hacklib` aj.) se týkají jiného modulu (InfiRay, raw režim přes UVC Zoom, řádky metadat navíc) a na HT-102 se pravděpodobně nevztahují. |

**Další krok:** získat APK z telefonu uživatele (pokud tam aplikaci má), nebo z archivu stránek výrobce
(Wayback Machine – `download.php`), případně vyzkoušet `com.hti.xtherm.hti103`. Pak analýza (jadx) podle kap. 3.2.

### Kandidáti (kap. 2.1), seřazeno podle pravděpodobnosti

1. Vendor control requesty na EP0 (`bmRequestType` `0xC0`/`0xC1`).
2. UVC class requesty na nedeklarovanou entitu („skrytá“ Extension Unit).
3. Data v prodloužené hlavičce UVC paketů (`bHeaderLength` > 12).
4. Interrupt EP `0x83` – kapacita nestačí, málo pravděpodobné.

## Převod na °C

Neznámý.

## Ověřovací měření

Zatím neprovedeno (led ve vodě ≈ 0 °C, teplá voda s teploměrem, dlaň ≈ 34 °C).
