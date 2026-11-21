# ThermoCamera – zadání pro Claude Code

> Android aplikace pro telefony a tablety, která s externí termokamerou **Hti HT-102** hledá tepelné mosty v budovách a místnostech.
> Verze zadání: 1.4 · 21. 11. 2026 – připraveno k předání do vývoje (stav a první kroky, analýza aplikace výrobce v kap. 3.2)
> Verze 1.3: výsledky měření USB komunikace HT-102 (kap. 2.1, příloha A)
> Verze 1.1–1.2: skleněný vzhled se světlým/tmavým režimem, kamera telefonu pro fotografie, 6 jazyků s volbou z rozbalovacího seznamu
> Vizuální návrh (pro autora zadání): https://claude.ai/artifact/RGg1tSx7SAZ4ysShp8vbEE – všechno podstatné z něj je přepsáno níže, zadání je soběstačné.

---

## Stav k 21. 11. 2026 a kde navázat

**Hotovo (mimo repozitář, před zahájením vývoje):**

- Schválený vizuální návrh a rozložení ovládacích prvků – přepsané do kap. 7 (závazné).
- Proměření kamery HT-102 na uživatelově notebooku (Windows 11): USB deskriptory, formáty, zachycené snímky – výsledky v kap. 2.1 a příloze A.
- **Klíčové zjištění:** kamera je standardní UVC webkamera s jediným formátem YUY2 (640 × 480 / 320 × 240 / 160 × 120). Video stream nese **jen běžný obraz** z druhé čočky modulu. **Termo data (32 × 32) standardní UVC cestou nechodí** a v deskriptorech není žádný další kanál – oficiální aplikace je čte vlastními USB požadavky, které je potřeba zjistit.

> **Kamera je teď od notebooku odpojená** – zabírá USB-C port a notebook se s ní nenabíjí. Vše, co jde bez kamery (projekt, UI, simulátor, rozbor aplikace výrobce), dělej bez ní. **Když budeš kameru potřebovat, vyzvi uživatele, ať ji připojí** (napiš k čemu a na jak dlouho), a po skončení mu řekni, že ji může zase odpojit.

**Kde navázat – první kroky Fáze 0:**

1. Polož uživateli otázky z kap. 13 (repozitář, application ID, testovací zařízení).
2. Založ projekt, téma (kap. 7), `ThermalCameraSource` + `SimulatedSource` – UI se vyvíjí nezávisle na kameře.
3. **Stáhni a analyzuj oficiální aplikaci Thermal Viewer** (kap. 3.2) – uživatel to výslovně svěřil tobě. Cíl: zjistit, jak aplikace čte termo data z kamery a jak je převádí na °C.
4. Ověř zjištění na Androidu (debug build s USB diagnostikou) a zdokumentuj v `docs/ht102-protocol.md`.

---

## 0. Jak s tímto zadáním pracovat

1. Přečti si celé zadání dřív, než začneš psát kód. Pracuj **po fázích** (kapitola 11). Po každé fázi: build, testy, lint, commit, krátké shrnutí pro uživatele.
2. **Největší riziko je komunikace s kamerou** – výrobce nemá veřejné SDK ani dokumentaci protokolu. Proto Fáze 0 začíná průzkumem a celá aplikace stojí na rozhraní `ThermalCameraSource` se **simulátorem**, aby šlo vyvíjet a testovat i bez kamery.
3. Když potřebuješ fyzickou kameru, máš dvě cesty:
   - **Kamera zapojená do PC** (USB-C): u uživatele je to firemní notebook s Windows 11, kamera je tam vidět jako „W55FA93 USB UVC Device“. **Běžně je odpojená** (blokuje nabíjení) – před každým měřením uživatele vyzvi, ať ji připojí, a po měření mu dej vědět, že ji může odpojit. Úkoly s kamerou slučuj, ať se nepřipojuje zbytečně často. Hotový diagnostický skript (PowerShell + C#, jen čte) je převzatý v `tools/windows-diag/` – spouští se dvojklikem na `HT102-diagnostika.cmd`, výsledky jdou do podsložky `vysledky`. Neinstaluj ovladače ani nástroje vyžadující práva správce bez souhlasu uživatele.
   - **Kamera zapojená do telefonu**: připrav debug APK s diagnostickou obrazovkou, vyzvi uživatele, ať kameru připojí k telefonu a diagnostiku spustí, a ať ti pošle exportovaný soubor (ulož ho do `docs/captures/`).
4. Nejasnosti, které mění architekturu, se ptej. Drobnosti rozhodni sám a zapiš je do `docs/decisions.md`.
5. Kód a identifikátory anglicky. **Žádný text v UI natvrdo v kódu** – vše od prvního commitu v `strings.xml` (aplikace je šestijazyčná, viz F10). Komentáře a dokumentace česky nebo anglicky – hlavně konzistentně.

---

## 1. Cíl a rozsah

| Požadavek uživatele | Kde v zadání |
|---|---|
| Android tablet i mobilní telefon, obě rozlišení | kap. 7 (layouty), F10 |
| Aplikace uložena v GitHubu, při každém spuštění kontrola novější verze a její stažení | F8, kap. 9 |
| Jinak plně offline | F9 |
| Externí termokamera Hti HT-102 | kap. 2, kap. 3, Fáze 0–1 |
| Skenování budov a místností, hledání tepelných mostů | F3 |
| Kalibrace pro různá rozpětí teplot | F2 |
| Ukládání snímků a sdílení | F4, F5 |
| Kombinace běžné kamery a termokamery – překryv i vedle sebe | F1 |
| Přisvícení bleskem zařízení ve tmě | F7 |
| Indikace zapojené/odpojené kamery a jednoduchý test funkčnosti | F6 |
| Moderní rozhraní s průhlednými (skleněnými) prvky, volba světlý / tmavý režim | kap. 7, F10 |
| Má-li telefon lepší kameru než HT-102, nabídnout její použití (kvalita fotografií) | F11 |
| Vícejazyčná aplikace: ENG, ESP, CZE, ITA, FRA, DEU | F10, kap. 14 |
| Rozložení ovládacích prvků podle schváleného návrhu | kap. 7.5 (závazné) |

**Mimo rozsah verze 1:** cloud, účty, synchronizace, analytika, reklamy, iOS, jiné kamery než HT-102 (ale architektura je na další kamery připravená).

---

## 2. Hardware: Hti HT-102

Výrobce: Dongguan Xintai Instrument Co., Ltd. (xintest.com.cn, hyfechcn.com.cn). Oficiální aplikace výrobce: **Thermal Viewer** (Android).

| Parametr | Hodnota |
|---|---|
| Termo rozlišení | **32 × 32 px** (1 024 bodů) |
| Běžná (viditelná) kamera v modulu | **640 × 480 px** – druhá čočka na modulu |
| Snímková frekvence | 9 Hz |
| Měřicí rozsah | −20 až 300 °C |
| Přesnost | ±3 °C nebo ±5 % z měřené hodnoty |
| Rozlišení teploty | 0,1 °C |
| Spektrální pásmo | 8–14 µm |
| Zorné pole (FOV) | 43° × 43° (±1°), pevné ohnisko |
| Pracovní teplota kamery | **0 až 35 °C** (skladovací −20 až 60 °C) |
| Připojení / napájení | USB-C, napájeno z telefonu (v telefonu musí být povoleno napájení externích zařízení / OTG) |
| Rozměry, hmotnost | 59,4 × 30,3 × 12 mm, cca 20 g |

Co z toho plyne pro aplikaci:

- **32 × 32 px je hodně málo.** Na vzdálenost 2 m pokrývá jeden bod cca 5 × 5 cm, na 1 m cca 2,5 × 2,5 cm. Aplikace musí obraz kvalitně interpolovat (bikubicky) a fúze s běžným obrazem je pro orientaci zásadní. V nápovědě doporuč skenovat detaily (rohy, nadpraží, napojení oken) ze vzdálenosti 1–2 m.
- **Termo obraz je čtvercový** (43° × 43°), běžný 4 : 3. Pro překryv se běžný obraz ořezává na čtverec odpovídající termo FOV (viz F1, zarovnání).
- **Přesnost ±3 °C** → absolutní teploty jsou orientační, pro tepelné mosty jsou důležitější rozdíly. Proto korekce posunu (offset) podle reference (F2).
- **Pracovní teplota 0–35 °C** → venkovní skenování fasády v mrazu je mimo specifikaci kamery. U předvolby „Fasáda zvenku“ zobraz upozornění.
- USB-C je oboustranné a modul může mířit dopředu i dozadu podle toho, jak je zasunutý → aplikace musí umět obraz otočit a zrcadlit (F1).

### 2.1 Co víme o komunikaci (změřeno 21. 11. 2026 na notebooku s Windows 11)

Kameru jsme proměřili diagnostickým skriptem (čtení USB deskriptorů přes hub, výpis formátů a zachycení snímků přes Windows MediaCapture). Surový výpis je v příloze A.

**Potvrzeno:**

- USB 2.0 High-Speed, **VID `0x0416`, PID `0x9393`**, `bcdDevice 1.00`, sériové číslo `000055FA93`, produkt „W55FA93 USB UVC Device“ (SoC Nuvoton W55FA93). Kompozitní zařízení (IAD), ve Windows ovladač `usbccgp` + `usbvideo`.
- **Jediná funkce: UVC 1.0 kamera.** Rozhraní 0 = VideoControl (interrupt endpoint `0x83`, 16 B, interval 4 ms), rozhraní 1 = VideoStreaming (izochronní endpoint `0x81`, alt. nastavení 1 a 2 shodná: 2 × 900 B za mikrorámec).
- Topologie: Camera Terminal (ID 1, žádné ovladatelné prvky) → Processing Unit (ID 5: jas, kontrast, odstín, sytost, ostrost, gama, kompenzace protisvětla, odrušení sítě) → Output Terminal (ID 3). **Žádná Extension Unit, žádné HID ani vendor-specific rozhraní, žádný bulk endpoint.**
- **Jediný formát: nekomprimované YUY2** – 640 × 480, 320 × 240, 160 × 120 při 30/20/15/10/5/1 snímcích/s; `dwMaxVideoFrameBufferSize` přesně odpovídá velikosti snímku (614 400 B u 640 × 480). Still image method 2.
- **Video stream nese jen běžný (viditelný) obraz** kamery v modulu – rozmazaný, šumivý, typická levná webkamera. Ve snímcích nejsou žádné řádky ani bloky s daty navíc, chroma je normální, 10 po sobě jdoucích snímků se liší jen šumem (žádné prokládání termo snímků). Platí pro všechna tři rozlišení.
- Hodnoty standardních ovládacích prvků (jas, kontrast, odstín, kompenzace protisvětla) se s teplotou scény nemění – data jimi netečou.

**Z toho plyne:** termo data (32 × 32 bodů, 9 Hz) **nejdou standardní UVC cestou, kterou vidí systémový ovladač**. Oficiální aplikace je musí číst přímo přes USB (libusb / `UsbDeviceConnection`). Kandidáti, seřazení podle pravděpodobnosti:

1. **Vendor-specific control requesty na endpointu 0** (`bmRequestType` `0xC0`/`0xC1`, vlastní `bRequest`) – v deskriptorech nejsou vidět, systémový ovladač je neposílá. Nejčastější řešení u upravených firmwarů webkamerových SoC.
2. **UVC class requesty na nedeklarovanou entitu** (např. GET_CUR na unit ID, která v deskriptorech chybí) – „skrytá“ Extension Unit.
3. **Data v hlavičkách UVC paketů** (prodloužený payload header, `bHeaderLength` > 12) – systémový ovladač i běžné libuvc je zahazují.
4. **Interrupt endpoint `0x83`** (status VideoControl) – kapacita jen ~4 kB/s, na 32 × 32 × 2 B při 9 Hz nestačí; málo pravděpodobné.

Neznámé zůstává i to, **v jaké podobě data chodí**: hotové teploty (např. int16 v desetinách °C), nebo surové hodnoty termočlánkového pole, které potřebují kalibrační konstanty z kamery; a zda kamera počítá s pevnou emisivitou.

**Doporučený postup k odhalení kanálu:** analyzovat oficiální aplikaci Thermal Viewer (APK) – v její Java/Kotlin části nebo nativní knihovně budou vidět volání `controlTransfer` / libusb s konkrétními kódy požadavků a délkami dat. Jde o zajištění interoperability s vlastním zařízením; z cizí aplikace se nic nekopíruje, zdokumentuje se jen protokol.

---

## 3. Fáze 0 – průzkum kamery (spike)

Cíl: získat z HT-102 v Androidu **matici 32 × 32 teplot v °C cca 9× za sekundu** a **viditelný snímek**, nebo jasně zdokumentovat, proč to nejde.

Úkoly:

1. **USB diagnostická obrazovka** (je i součástí finální aplikace, skrytá v Nastavení › Pro pokročilé):
   - seznam zařízení z `UsbManager` – VID/PID, výrobce, produkt, sériové číslo,
   - všechna rozhraní a endpointy (class/subclass/protocol, směr, typ, max packet size),
   - surový dump deskriptorů (`UsbDeviceConnection.getRawDescriptors()`) v hex – musí odpovídat příloze A,
   - **Export** do textového souboru + sdílení (aby ho uživatel mohl poslat zpět).
2. **Viditelný obraz:** UVC stream YUY2 640 × 480 přes knihovnu na bázi libuvc pro Android (aktivně udržovaný fork `UVCCamera` / `AndroidUSBCamera`; vyber podle aktuálního stavu a licence, rozhodnutí zapiš do `docs/decisions.md`). Tohle je ověřené, že funguje (kap. 2.1).
3. **Termo kanál – nejdřív analýza oficiální aplikace:** stáhni a analyzuj APK Thermal Viewer podle kap. 3.2. Výsledek zapiš do `docs/ht102-protocol.md` vlastními slovy.
4. **Pokud APK nepůjde získat nebo z něj kanál nepůjde vyčíst:** v debug buildu „USB sonda“ – pouze **čtecí** (device-to-host) požadavky: GET_CUR/GET_LEN/GET_INFO na unit ID 1–15 a selektory 1–31 (UVC class, rozhraní 0), a pak vendor requesty `bmRequestType 0xC0/0xC1`, `bRequest` 0x00–0xFF, `wLength` 64 → logovat ty, které vrátí data, a zda se data mění s teplotou scény (ruka před kamerou / pryč). **Nikdy neposílej host-to-device (zápisové) požadavky s neznámým významem** – mohly by přepnout firmware do servisního režimu. Sondu pouštět jen na pokyn uživatele.
5. **Kontrola hlaviček UVC paketů:** v nativní části zaloguj `bHeaderLength` a obsah hlaviček izochronních paketů (prvních pár set) – pokud je hlavička delší než 12 B, data mohou být tam.
6. **Výsledek zdokumentuj** v `docs/ht102-protocol.md`: deskriptory, kanál termo dat, struktura dat, převod na °C, ověřovací měření (sklenice vody s teploměrem, led ve vodě ≈ 0 °C, dlaň ≈ 34 °C).
7. Implementuj `Ht102Source : ThermalCameraSource` – viditelný obraz přes UVC, termo data přes zjištěný kanál, obojí přes jedno `UsbDeviceConnection` (pozor na souběh: termo požadavky na endpoint 0 se nesmí tlouct s UVC řízením).

**Výstupní kritérium:** v debug aplikaci se zobrazuje živá matice 32 × 32 s hodnotami, které odpovídají realitě v rámci ±3 °C, a viditelný obraz. Pokud se to nepodaří ani po krocích 3–5, zastav se a předlož uživateli zjištění a varianty (kontaktovat výrobce kvůli SDK, nebo jiná kamera s popsaným protokolem).

### 3.1 Simulátor (dělá se hned ve Fázi 0)

`SimulatedSource : ThermalCameraSource` – generuje syntetickou scénu (roh místnosti s oknem: studený roh u stropu, studené nadpraží, teplý radiátor; jemný šum ±0,1 °C) 9× za sekundu + odpovídající viditelný obraz. Umí také **přehrát nahraný záznam** (sekvence surových snímků z reálné kamery, formát viz F4). Simulátor jde zapnout v Nastavení › Pro pokročilé a automaticky se používá v UI testech a na emulátoru.

### 3.2 Analýza oficiální aplikace Thermal Viewer

Uživatel výslovně souhlasí se stažením a analýzou aplikace výrobce **pouze za účelem interoperability s vlastní kamerou**. Z aplikace se nic nekopíruje do repozitáře – zjištěný protokol se popíše vlastními slovy a implementuje znovu.

**Získání APK:**

- Podle návodu k HT-102 se aplikace „Thermal Viewer“ stahuje ze stránek výrobce **xintest.com.cn**; na zadní straně modulu je QR kód (podle návodu vede ke stažení aplikace – ověř). Další adresy výrobce na štítku: hyfechcn.com.cn, xintest.en.alibaba.com.
- Pokud ze stránek výrobce stáhnout nejde, požádej uživatele, ať APK vytáhne ze svého telefonu (např. aplikací typu APK Extractor), nebo ať naskenuje QR kód a pošle odkaz.
- Ověř, že jde o aplikaci výrobce (název balíčku, podpis, velikost); APK z neznámých zrcadel nepoužívej.
- APK ulož do `docs/vendor/` a **přidej do `.gitignore`** – necommitovat APK ani dekompilovaný kód.

**Nástroje** (přednostně přenosné verze do složky projektu, bez práv správce; před instalací čehokoli do systému se zeptej – jde o firemní notebook):

- `jadx` (dekompilace Java/Kotlin, potřebuje Javu – JDK je u Android vývoje stejně potřeba), případně `apktool` / `androguard`.
- Nativní knihovny (`lib/arm64-v8a/*.so`): `strings`, výpis exportovaných symbolů (`nm -D` / `readelf`), případně Ghidra.

**Co hledat:**

- Volání `UsbManager` / `UsbDeviceConnection`: `controlTransfer(...)`, `bulkTransfer(...)`, `requestWait`, `UsbRequest` – a jejich argumenty (`requestType`, `request`, `value`, `index`, délka bufferu).
- Konstanty VID/PID: `0x0416` / `0x9393` (dekadicky 1046 / 37779), řetězce „W55FA93“, „UVC“.
- Knihovny `libusb`, `libuvc`, `UVCCamera` a jejich úpravy: `libusb_control_transfer`, `uvc_get_ctrl`/`uvc_set_ctrl`, `uvc_query_*`, zpracování payload hlaviček (`bHeaderLength`, extra bajty za 12 B hlavičky).
- JNI metody (`native …` v Javě, `Java_…` symboly v `.so`) s názvy typu *temp*, *thermal*, *ir*, *frame*, *calib*, *emiss*, *ht102*.
- Pole o 1 024 prvcích (32 × 32), převodní vzorce na °C, kalibrační tabulky nebo konstanty čtené z kamery, emisivita.
- Periodu čtení (cca 9 Hz) a zda aplikace před čtením něco do kamery zapisuje (inicializace / přepnutí režimu).

**Výstup:** `docs/ht102-protocol.md` – kanál (např. „vendor IN request 0xC1, bRequest 0x??, wLength 2048“), struktura odpovědi, převod na °C, inicializační sekvence, ověření na reálné kameře. Pokud aplikace posílá zápisové (host-to-device) požadavky, implementuj jen ty, které dělá i oficiální aplikace, ve stejném pořadí.

**Ověření na Windows (volitelné):** na notebooku je kamera pod systémovým ovladačem `usbvideo`, takže vlastní USB požadavky z Windows poslat nejdou. Výměna ovladače (např. Zadig → WinUSB) by kameru dočasně vyřadila jako webkameru a mění systém – **jen se souhlasem uživatele**; jinak ověřuj na Androidu.

---

## 4. Technologie a architektura

- **Kotlin**, **Jetpack Compose** (Material 3, vlastní tmavé téma), Coroutines + Flow.
- `minSdk 26`, `targetSdk` a `compileSdk` = nejnovější stabilní API. Gradle Kotlin DSL + version catalog.
- Architektura: single-activity, UDF/MVVM (ViewModel + `StateFlow`), DI přes Hilt.
- Moduly (úmyslně málo, je to osobní projekt):
  - `:app` – UI, navigace, Android integrace (USB, kamera telefonu, svítilna, úložiště, updater),
  - `:thermal-core` – **čistý Kotlin/JVM bez Android závislostí**: korekce, měření, palety, interpolace, fRsi, izoterma. Plně pokryté unit testy.
- Persistence: Room (metadata snímků, projekty, kalibrační profily), DataStore (nastavení), soubory v app-specific storage.
- Žádné Google Play Services, Firebase ani jiné síťové knihovny kromě HTTP klienta pro updater.
- Písma Inter a JetBrains Mono **přibalená v APK** (licence OFL), ne stahovaná.

### 4.1 Klíčová rozhraní

```kotlin
interface ThermalCameraSource {
    val state: StateFlow<CameraState>          // Disconnected, Connecting, NeedsPermission, Streaming(fps), Error(reason)
    val frames: Flow<ThermalFrame>             // ~9 Hz
    val visibleFrames: Flow<VisibleFrame>?     // null, pokud zdroj nemá vlastní běžnou kameru
    suspend fun start()
    suspend fun stop()
    suspend fun selfTest(): SelfTestReport     // pro F6
    fun diagnostics(): DiagnosticsDump         // pro export
}

data class ThermalFrame(
    val width: Int = 32,
    val height: Int = 32,
    val celsius: FloatArray,                   // po převodu z kamery, před uživatelskými korekcemi
    val timestampNanos: Long,
)
```

Implementace: `Ht102Source`, `SimulatedSource`. Další kamery v budoucnu = další implementace.

### 4.2 Zpracování snímku (`:thermal-core`)

Pipeline pro každý snímek:

1. **Korekce** (F2): emisivita + odražená teplota, pak lineární korekce `T = gain · T + offset` z kalibračního profilu.
2. **Měření** na opravené matici 32 × 32: MIN a MAX (hodnota + pozice), STŘED (průměr 2 × 2 uprostřed), uživatelské body (bilineární interpolace v subpixelové pozici), fRsi (F3).
3. **Volitelné časové vyhlazení** (EMA, výchozí α = 0,5; vypnutelné) – jen pro zobrazení, ne pro uložená data.
4. **Mapování na barvy:** rozsah (ruční min/max, nebo auto = 2. a 98. percentil vyhlazený EMA, aby obraz neblikal) → LUT 256 barev zvolené palety.
5. **Interpolace** na zobrazovací velikost (bikubicky, výchozí 320 × 320; volitelně „pixely“ = nejbližší soused).
6. **Izoterma** (F3): body pod limitní teplotou přebarvit zvýrazňovací barvou.
7. Výstup: `Bitmap` pro Compose (`ImageBitmap`). Při 9 Hz a 32 × 32 to stačí počítat na CPU (`Dispatchers.Default`); recykluj buffery, žádné alokace na snímek v hot path.

Palety (LUT, 256 hodnot, definované jako barevné body s lineární interpolací):

| Paleta | Barevné body (0 → 1) |
|---|---|
| Iron (výchozí) | `#080418` 0 · `#220C5C` 0,14 · `#70148C` 0,30 · `#BE226E` 0,46 · `#E84A28` 0,60 · `#F88C14` 0,74 · `#FDCE3C` 0,88 · `#FFFADC` 1 |
| Duha | `#1B2A8F` · `#1C9BD7` · `#3CC46A` · `#F2E53A` · `#E8352B` (rovnoměrně) |
| Šedá | `#000000` → `#FFFFFF` |
| Arktida | `#0B1E4F` · `#2F6FD6` · `#A7D8F5` · `#F7F2D8` · `#F5A24B` (rovnoměrně) |

---

## 5. Funkční požadavky

### F1 – Živý obraz a kombinace kamer

- **Režimy zobrazení** (přepínač se 4 segmenty): **Termo** · **Překryv** · **Vedle sebe** · **Běžná**.
  - *Překryv:* termo obraz přes běžný, posuvník **Prolnutí 0–100 %** (výchozí 55 %, pamatuje se).
  - *Vedle sebe:* na telefonu na výšku pod sebou, na šířku a na tabletu vedle sebe. Oba obrazy stejně velké, zaměřovací kříž v obou na stejném místě.
  - *Běžná:* jen viditelný obraz (užitečné pro orientaci a se svítilnou).
- **Zdroj běžného obrazu:** kamera v modulu HT-102 (pevná poloha vůči termo senzoru), nebo – je-li lepší – **zadní kamera telefonu** (F11). Pokud by obraz z kamery modulu nešel získat, je kamera telefonu jediný zdroj.
- **Zarovnání překryvu:** u kamery modulu pevné (posun X/Y, měřítko, otočení – jednorázově, průvodce „namiř na teplý předmět s ostrou hranou, např. hrnek, a posuň obraz, dokud hrany nesedí“); u kamery telefonu automatické podle F11 bod 4.
- **Orientace modulu:** otočení 0/90/180/270° a zrcadlení; nastavuje se v Testu kamery (F6) a v Nastavení, pamatuje se.
- **Překryvy v obraze:** zaměřovací kříž uprostřed s teplotou, značka **MIN** (modrý trojúhelník špičkou dolů), **MAX** (červený trojúhelník špičkou nahoru), uživatelské body **P1–P5** (bílý kroužek + hodnota). Klepnutí do obrazu = nový bod, tažení = posun, dlouhý stisk = odstranění. Svislá **teplotní stupnice** u pravého okraje s hodnotami horní/dolní meze. Odznaky vlevo nahoře: aktivní rozsah (se zámkem, je-li zamčen) a emisivita.
- **Hodnoty pod obrazem:** dlaždice MIN / STŘED / MAX, pruh tepelného mostu (F3).
- Obrazovka během živého náhledu nezhasíná (`FLAG_KEEP_SCREEN_ON`).
- Volitelně: **Zmrazit obraz** (pauza živého náhledu, dál lze přidávat body a uložit).

### F2 – Teplotní rozsah a kalibrace

- **Předvolby rozsahu** (určují mapování barev, ne měření):

  | Předvolba | Rozsah | Poznámka |
  |---|---|---|
  | Automaticky | podle scény | percentily 2–98 %, vyhlazené |
  | Interiér | 12–24 °C | výchozí pro hledání tepelných mostů zevnitř |
  | Fasáda zvenku | −15–15 °C | upozornění: kamera je specifikovaná pro 0–35 °C |
  | Topení, rozvody | 20–80 °C | |
  | Celý rozsah | −20–300 °C | |
  | Vlastní | uložené profily | |

- Ruční **Min/Max** (krok 0,1 °C) a přepínač **Zamknout rozsah**.
- **Emisivita** 0,10–1,00 (posuvník + předvolby materiálů: Malba 0,95 · Cihla 0,93 · Sklo 0,92 · Dřevo 0,90; hodnoty označ jako orientační). **Odražená teplota** (výchozí 20 °C).
- Korekce emisivity (v Kelvinech; T⁴ je pro pásmo 8–14 µm aproximace, pro třídu přesnosti ±3 °C dostačující):
  `T_obj = ((T_mer⁴ − (1 − ε) · T_odr⁴) / ε)^(1/4)`.
  Ve Fázi 0 ověř, s jakou emisivitou počítá kamera sama; pokud ne s 1,0, nejdřív hodnotu přepočítej na ε = 1.
- **Kalibrace podle reference:**
  - *Jednobodová (offset):* „Namiř kříž na předmět se známou teplotou a zadej skutečnou hodnotu“ → průměr středu přes 2 s (≈ 18 snímků) → `offset = skutečná − naměřená`.
  - *Dvoubodová (offset + gain):* dvě reference (např. led ve vodě 0 °C a teplá voda změřená teploměrem) → lineární korekce.
- Vše se ukládá jako **pojmenovaný profil** (rozsah, ε, odražená teplota, offset, gain). Rychlé přepínání profilů z hlavní obrazovky.

### F3 – Tepelné mosty

- **Teplotní faktor:** `fRsi = (θsi − θe) / (θi − θe)`, kde θsi je povrchová teplota v bodě (MIN nebo vybraný bod), θi teplota vnitřního vzduchu, θe venkovního vzduchu. θi a θe zadává uživatel (pamatují se; volitelně u projektu).
- **Limit** výchozí 0,75, nastavitelný. Pod limitem: pruh „Možný tepelný most · fRsi 0,64 < 0,75“ v barvě varování. Aplikace je orientační pomůcka, ne posudek – uveď to v nápovědě.
- **Izoterma / zvýraznění:** přepínač „Zvýraznit místa pod limitem“ – body s `θsi < θe + limit · (θi − θe)` se v obraze přebarví (výrazná azurová, odlišná od všech palet).
- Když je `θi − θe < 10 °C`, zobraz upozornění, že rozdíl teplot je pro spolehlivé hledání tepelných mostů malý.

### F4 – Ukládání snímků

Spoušť uloží **snímek měření**:

- `render.jpg` – přesně to, co uživatel vidí (režim, prolnutí, paleta, značky, stupnice) + pruh s hodnotami (MIN/MAX/STŘED/body, fRsi, ε, datum),
- `thermal.f32` – surová matice 32 × 32 (`float32` little-endian, °C **před** uživatelskými korekcemi) + `thermal.json` s rozměry a verzí formátu,
- `visible.jpg` – běžný snímek,
- metadata v Room: čas, projekt, název/místnost, poznámka, režim, prolnutí, paleta, rozsah, ε, odražená teplota, offset/gain, θi/θe, body, orientace, zdroj kamery, verze aplikace.

Protože se ukládají surová data, jde snímek **později přerenderovat** s jinou paletou, rozsahem, prolnutím nebo emisivitou.

- Volitelně kopie `render.jpg` do galerie telefonu (`MediaStore`, `Pictures/ThermoCamera`), výchozí zapnuto.
- **Video** (volitelné, Fáze 6): záznam vyrenderovaného náhledu do MP4 (`MediaCodec` přes Surface) + paralelně záznam surových snímků (pro přehrání v simulátoru).
- **Projekty:** snímky lze řadit do projektů (např. „Chata – leden“). Výchozí projekt „Bez projektu“.

### F5 – Galerie a sdílení

- Mřížka snímků podle data, filtr podle projektu, vícenásobný výběr.
- Detail: obraz se značkami, dlaždice MIN/MAX/fRsi/ε, úprava názvu a poznámky, přerenderování (paleta/rozsah/prolnutí), smazání (s potvrzením).
- **Sdílet jako** (Android share sheet přes `FileProvider`):
  1. Obrázek s hodnotami (JPG),
  2. Termo a běžný vedle sebe (JPG),
  3. Protokol PDF (`android.graphics.pdf.PdfDocument`) – jeden nebo více snímků, hodnoty, poznámky, θi/θe, datum, projekt,
  4. Naměřená data 32 × 32 (CSV, oddělovač `;`, desetinná čárka v češtině / tečka v angličtině kvůli Excelu, hlavička s metadaty).
- Galerie a sdílení fungují i bez připojené kamery.

### F6 – Stav kamery a test

- **Stavový čip** vlevo nahoře na hlavní obrazovce, vždy viditelný:

  | Stav | Barva tečky | Text |
  |---|---|---|
  | Připojeno | zelená `#3DD68C` | `HT-102 · připojeno · 9 Hz` (skutečná frekvence) |
  | Připojuji / čeká na oprávnění | žlutá `#FFC14D` | `Připojuji…` / `Povol přístup k USB` |
  | Odpojeno | červená `#FF6B6B` | `Kamera odpojena` |
  | Chyba | červená | `Chyba: žádná data` (watchdog: žádný snímek > 2 s) |

- Detekce připojení/odpojení přes `UsbManager.ACTION_USB_DEVICE_ATTACHED/DETACHED`; `device_filter.xml` s VID/PID z Fáze 0, aby se aplikace mohla nabídnout jako výchozí pro HT-102 a systém se neptal na oprávnění pokaždé. Při odpojení se živý obraz okamžitě nahradí stavem „Odpojeno“, nic nepadá.
- **Obrazovka „Kamera odpojena“:** ilustrace modulu, text „Připoj termokameru HT-102“, tlačítko **Spustit test kamery**, karta „Když se kamera neobjeví“ (zasunout na doraz, zapnout OTG/napájení externích zařízení, odpojit a připojit). Spoušť a video neaktivní, galerie dostupná.
- **Test kamery** (klepnutím na stavový čip nebo z obrazovky odpojení), postupně s průběhem:
  1. USB zařízení nalezeno (typ rozhraní),
  2. Oprávnění k USB uděleno,
  3. Termo data přicházejí (změřená frekvence, OK při ≥ 7 Hz),
  4. Běžná kamera modulu (rozlišení),
  5. Senzor 32 × 32 – počet vadných bodů (bod mimo −40…350 °C nebo zaseknutý stejnou hodnotou > 5 s; OK = 0),
  6. Kontrolní měření – hodnota středu v rozumném rozsahu.

  Výsledek „Kamera funguje“ / „Kamera nefunguje: …“ s konkrétní radou. Pod tím náhled **surových dat 32 × 32** (nejbližší soused, aby byly vidět jednotlivé body) a šum mezi snímky. Tlačítka **Zopakovat**, **Hotovo**, **Zkopírovat diagnostiku**. Součástí testu je krok „Orientace“: „Dej ruku do pravé části záběru – je teplé místo v obraze vpravo?“ → nastaví otočení/zrcadlení.

### F7 – Svítilna (přisvícení bleskem)

- Tlačítko se symbolem blesku v horní liště (na šířku a na tabletu v pravém panelu). Zapnuto = žluté pozadí `#FFC14D`.
- Implementace: `CameraManager.setTorchMode()` na zadní kameře s bleskem; pokud je kamera telefonu aktivní přes CameraX (záložní zdroj běžného obrazu), použij `cameraControl.enableTorch()`.
- Na zařízení bez blesku (časté u tabletů) se tlačítko nezobrazí (`FLASH_INFO_AVAILABLE`).
- Svítilna se vypne při odchodu z aplikace a obnoví po návratu, pokud byla zapnutá.
- V nápovědě: světlo pomáhá jen běžnému obrazu, termo obraz neovlivní.

### F8 – Aktualizace z GitHubu

Viz kapitola 9.

### F9 – Offline

- Aplikace funguje plně bez sítě. Jediná síťová komunikace je updater (F8). Žádná analytika, žádné stahování písem ani obsahu.
- Při spuštění bez sítě se kontrola aktualizace tiše přeskočí, uživatel nic nevidí.

### F10 – Nastavení, vzhled a jazyky

**Vzhled:** Světlý / Tmavý / Podle systému (výchozí podle systému), přepnutí se projeví okamžitě bez restartu; volba „Omezit průhlednost“ (kap. 7.1).

**Jazyky:** angličtina (`en`), španělština (`es`), čeština (`cs`), italština (`it`), francouzština (`fr`), němčina (`de`).

- Výchozí jazyk = jazyk telefonu, pokud je mezi podporovanými, jinak angličtina.
- **Volba jazyka je rozbalovací seznam (combo box)** v Nastavení › Jazyk aplikace – v Compose `ExposedDropdownMenuBox` ve skleněném stylu (pole 52 dp, rozbalená nabídka jako skleněný panel s vyšším krytím). Pole ukazuje zkratku a název aktuální volby (např. `CZE · Čeština`). Položky v pořadí: **Podle telefonu · <jazyk telefonu>** (`AUTO`), pak jazyky jejich vlastními názvy se zkratkou: `CZE` Čeština, `ENG` English, `ESP` Español, `ITA` Italiano, `FRA` Français, `DEU` Deutsch. Vybraná položka má fajfku. Po výběru se seznam zavře a texty se přepnou hned, bez restartu.
- Volba jazyka pro aplikaci přes `AppCompatDelegate.setApplicationLocales()` + `res/xml/locales_config.xml`, aby se jazyk aplikace dal nastavit i v systémovém nastavení Androidu 13+.
- **Všechny texty** v `res/values*/strings.xml` včetně plurálů (`plurals`), textů v exportech (PDF protokol, popisky v JPG, hlavička CSV), nápovědy, chybových hlášek a `contentDescription`. Lint `MissingTranslation` = chyba buildu.
- Formátování podle jazyka: čísla a datum přes `NumberFormat`/`DateTimeFormatter` s aktuálním `Locale` (desetinná čárka v cs/es/it/fr/de, tečka v en), mezera mezi číslem a jednotkou nezalomitelná (`19,6 °C`). Zkratka megapixelů: „Mpx“ (cs, fr), „MP“ (en, es, it, de).
- Odborné pojmy překládej podle glosáře (kap. 14). Překlady připrav ty; označ je v `docs/translations.md` jako „k revizi rodilým mluvčím“.
- Rozložení musí unést o třetinu delší texty (typicky němčina): popisky se zalomí na dva řádky, nikdy neořežou; v segmentovém přepínači se smí písmo zmenšit na 12 sp. Krátké popisky režimů:

  | cs | en | es | it | fr | de |
  |---|---|---|---|---|---|
  | Termo | Thermal | Térmica | Termica | Thermique | Thermo |
  | Překryv | Overlay | Superpuesta | Sovrapposta | Superposée | Überlagert |
  | Vedle sebe | Side by side | Lado a lado | Affiancate | Côte à côte | Geteilt |
  | Běžná | Photo | Foto | Foto | Photo | Foto |

**Ostatní nastavení:** jednotky °C/°F, výchozí paleta, interpolace (hladká / pixely), vyhlazení, orientace modulu, kamera pro fotografie a náhled (F11), zarovnání překryvu, limit fRsi, ukládat do galerie telefonu, aktualizace (kontrolovat při startu, stahovat jen přes Wi-Fi – výchozí vypnuto, tj. stahovat vždy), O aplikaci (verze, licence písem a knihoven), Pro pokročilé (USB diagnostika, simulátor).

### F11 – Lepší kamera telefonu pro fotografie

Kamera v modulu HT-102 má jen 640 × 480 (0,3 Mpx). Většina telefonů má výrazně lepší zadní kameru – aplikace ji má **nabídnout a umět použít** pro fotografie, překryv i zobrazení vedle sebe. Termo měření se tím nemění.

1. **Zjištění kamer:** přes Camera2 (`CameraManager`) najdi zadní kamery, jejich rozlišení (`SCALER_STREAM_CONFIGURATION_MAP`, výstup JPEG) a zorné pole (z `SENSOR_INFO_PHYSICAL_SIZE` a `LENS_INFO_AVAILABLE_FOCAL_LENGTHS`). Vyber kameru, jejíž zorné pole pokryje termo pole 43° × 43° a dá v něm nejvíc pixelů (u logických multi-kamer stačí CameraX se správným `zoomRatio`). Efektivní rozlišení ve výřezu ≈ šířka × tan(21,5°) / tan(HFOV/2).
2. **Nabídka:** pokud je efektivní rozlišení kamery telefonu ve výřezu vyšší než u kamery modulu (prakticky vždy), zobraz při prvním úspěšném připojení HT-102 bottom sheet **„Tvůj telefon má lepší kameru“**: porovnání výřezu z kamery modulu a z kamery telefonu (stejné místo scény), rozlišení obou, tip „Zasuň modul čočkou na stejnou stranu jako zadní kamera telefonu“, tlačítka **Použít kameru telefonu** / **Ponechat kameru modulu**, poznámka „Volbu můžeš kdykoli změnit v Nastavení“. Volbu si pamatuj; znovu nabídni jen po změně zařízení.
3. **Režimy použití** (Nastavení › Fotografie a překryv):
   - *Kamera telefonu* (doporučeno): živý náhled i fotografie z kamery telefonu (CameraX `Preview` + `ImageCapture`, `CAPTURE_MODE_MAXIMIZE_QUALITY`).
   - *Kamera telefonu + náhled z modulu*: přepínač „Živý náhled z kamery modulu“ – rychlejší náhled s pevným zarovnáním, fotka při stisku spouště z telefonu.
   - *Kamera modulu*: jako dosud.
   - Odznak v obraze ukazuje aktivní zdroj („foto 12 Mpx“).
4. **Zarovnání (paralaxa):** kamera telefonu je od termo senzoru vzdálená typicky 10–15 cm, takže posun obrazů závisí na vzdálenosti od stěny – pevná kalibrace nestačí. Postup:
   - Kamera v modulu je pevně spojená s termo senzorem → **registruj obraz telefonu na obraz kamery modulu** (detekce rysů + RANSAC, model podobnosti/afinita, při rovinné scéně homografie) při každém snímku a v živém náhledu cca 2× za sekundu na zmenšeném šedém obraze.
   - Knihovna: OpenCV pro Android (ORB + `findHomography`/`estimateAffinePartial2D`), nebo vlastní lehčí implementace, pokud by OpenCV neúměrně zvětšilo APK – rozhodni a zapiš do `docs/decisions.md`. APK z GitHubu sestavuj jen pro `arm64-v8a`.
   - Když registrace selže (málo rysů, tma), použij poslední platnou transformaci; když selhává opakovaně, ukaž tip „Modul možná míří jinam než kamera telefonu“.
   - Pokud by se ve Fázi 0 ukázalo, že obraz kamery modulu nejde získat: ruční zarovnání (tažení, sevření prstů) + posuvník „Vzdálenost 0,5–5 m“ pro korekci paralaxy.
   - Přepínač „Automaticky zarovnat s termo obrazem“ (výchozí zapnuto).
5. **Ukládání:** k snímku navíc `photo.jpg` v plném rozlišení telefonu a `alignment.json` (matice 3 × 3 termo → foto). Render, „vedle sebe“ i PDF protokol používají fotku z telefonu, výřez odpovídá termo poli.
6. **Svítilna a blesk:** při aktivní kameře telefonu svítilna přes `cameraControl.enableTorch()`; při focení ve tmě nabídni blesk (`FLASH_MODE_AUTO`), aby fotka nebyla tmavá.
7. **Oprávnění `CAMERA`** se žádá až po volbě „Použít kameru telefonu“; při odmítnutí se vrať ke kameře modulu a vysvětli proč.

---

## 6. Nefunkční požadavky

- Latence živého obrazu (snímek z kamery → displej) < 150 ms, plynulých 9 Hz bez zahazování snímků na středně výkonném telefonu.
- Spuštění aplikace do živého obrazu (kamera zapojená) < 2 s; kontrola aktualizace nesmí start zdržet.
- Žádný pád při odpojení kamery během streamu, ukládání nebo testu.
- Přístupnost: dotykové cíle min. 48 dp, kontrast textu min. 4,5 : 1, `contentDescription` u všech ikon, ovladatelné přes TalkBack.
- Podpora otočení obrazovky bez ztráty stavu (body, režim, zmrazený snímek).

---

## 7. UI a vizuální styl

Moderní rozhraní se **skleněnými (průhlednými) prvky** a volbou **světlý / tmavý / podle systému** (výchozí: podle systému). Rozložení ovládacích prvků je schválené a je stejné v obou motivech (kap. 7.5).

### 7.1 Princip skleněného vzhledu

- **Pozadí obrazovky** = živý termo obraz silně rozmazaný a přes něj závoj (tmavý motiv: černá 40 %, světlý: světle šedá 35 %). Stačí vykreslit 32 × 32 snímek zmenšený (např. na 8 × 8) a roztažený s bilineárním filtrem – je přirozeně „rozmazaný“ a nic to nestojí. Bez kamery (odpojeno, nastavení, test) je pozadím jemný barevný přechod (fialová vlevo nahoře, oranžová vpravo dole).
- **Panely, dlaždice, čipy, lišty** = poloprůhledná výplň + rozmazání toho, co je pod nimi (backdrop blur) + 1 px světlý okraj + měkký stín.
- **Popisky v obraze** (hodnoty, značky, stupnice, odznaky) = menší rozmazání a vyšší krytí, aby byly čitelné na jakékoli barvě termo obrazu.
- **Živý obraz sám nikdy není průhledný ani rozmazaný** – je to měřicí plocha.
- Akční barvy (oranžová spoušť, vybraný segment, primární tlačítka) jsou plné, ne skleněné; jemná záře (`box-shadow` v barvě akce) je zvýrazňuje.
- **Implementace v Compose:** vestavěný `Modifier.blur` rozmazává vlastní obsah, ne pozadí pod prvkem. Pro backdrop blur použij knihovnu **Haze** (`dev.chrisbanes.haze`, glassmorphism pro Compose). Použij poslední stabilní verzi (v polovině 2026 byla řada 2.0 v alfa verzi – pokud stále je, zůstaň na 1.x). Ověř chování na nejstarším podporovaném Androidu; kde blur není k dispozici nebo je pomalý, použij **náhradní výplň s vyšším krytím** (tmavý 85 %, světlý 90 %) – vzhled se zachová, jen bez rozmazání.
- Volba „Omezit průhlednost“ v Nastavení › Vzhled (a automaticky při zapnutém systémovém snížení průhlednosti/animací) přepne na náhradní výplň.

### 7.2 Barvy – motivy

| Token | Tmavý | Světlý | Použití |
|---|---|---|---|
| `root` | `#0A0C10` | `#F2F3F6` | základ pod rozmazaným pozadím |
| `veil` | `rgba(8,10,14,.40)` | `rgba(245,246,250,.35)` | závoj přes rozmazaný obraz |
| `glass` | `rgba(32,36,46,.46)` | `rgba(255,255,255,.50)` | panely, dlaždice, lišty (blur 22 dp, sytost 150 %) |
| `glassHi` | `rgba(255,255,255,.10)` | `rgba(255,255,255,.62)` | čipy a tlačítka uvnitř panelů |
| `sheet` | `rgba(22,25,33,.66)` | `rgba(250,251,253,.70)` | bottom sheety (blur 30 dp) |
| `glassBorder` | `rgba(255,255,255,.14)` | `rgba(255,255,255,.78)` | okraj skla 1 dp |
| `shadow` | `0 8 32 rgba(0,0,0,.32)` | `0 8 30 rgba(60,50,80,.14)` | stín panelů |
| `text` | `#F5F7FA` | `#14161C` | hlavní text |
| `textMuted` | `#B9C0CC` | `#454C5A` | popisky |
| `link` | `#FFB07A` | `#A9480C` | textové odkazy (oranžový text na světlém pozadí by nesplnil kontrast) |
| `segOff` | `#D3D8E1` | `#2E3440` | nevybraný segment |
| `imgLabel` | `rgba(10,12,16,.46)` + bílý text | `rgba(255,255,255,.62)` + `#14161C` | popisky v obraze (blur 12 dp) |
| `coldText` | `#8CC8FF` | `#15539E` | text MIN |
| `hotText` | `#FFB07A` | `#9A300C` | text MAX |
| `okChip` | výplň `rgba(61,214,140,.16)`, okraj `.45` | výplň `.26`, okraj `rgba(24,140,84,.42)` | stavový čip „připojeno“ |
| `errChip` | výplň `rgba(255,107,107,.16)`, okraj `.45` | výplň `.24`, okraj `rgba(190,40,40,.42)` | „odpojeno“, chyba |
| `warn` | výplň `rgba(255,193,77,.16)`, okraj `.40`, nadpis `#FFD27A`, text `#EFE3CB` | výplň `rgba(255,183,52,.28)`, okraj `rgba(190,120,0,.42)`, nadpis `#5E3A00`, text `#4F3D1C` | tepelný most |

**Společné pro oba motivy:** akce `#FF8A3D` (text na ní `#1A0E05`), značka MIN `#5AB0FF`, značka MAX `#FF5B4A`, připojeno `#3DD68C`, chyba `#FF6B6B`, varování / svítilna `#FFC14D`. Barvy termo palet, značek a stupnice v obraze se motivem **nemění**.

Kontrast textu min. 4,5 : 1 musí platit v obou motivech i nad nejsvětlejší a nejtmavší částí rozmazaného pozadí – ověř screenshot testy (kap. 10).

### 7.3 Typografie

- **Inter**: nadpis 20/700, text 14/400, popisky sekcí 12/600 verzálkami s prostrkáním 0,05 em.
- **JetBrains Mono** 500/600 pro **všechna čísla** (teploty, ε, fRsi, procenta), aby hodnoty při změně „neskákaly“.
- Obě písma mají plnou podporu znaků všech šesti jazyků (č, ř, ñ, à, ç, ß, ü…). Respektuj systémové zvětšení písma; rozložení musí unést 130 %.

### 7.4 Komponenty

Stavový čip (výška 36 dp, zaoblení 18, skleněný s barevným nádechem stavu a svítící tečkou), segmentový přepínač (4 segmenty ve skleněném podkladu, výška 40, vybraný = plná `accent` se září), dlaždice hodnot (výška 60, zaoblení 16), spodní lišta jako skleněná „pilulka“ (výška 104, zaoblení 30, okraje 16 dp) se spouští 80 dp (prstenec 4 dp v barvě textu, uvnitř `accent` se září), kulatá ikonová tlačítka 44 dp ve skle, bottom sheet se zaoblením 28 nahoře a táhlem, přepínače (switch) 52 × 32 dp, značky MIN/MAX/bod/kříž dle F1.

### 7.5 Rozložení obrazovek (schválené – neměnit)

Rozhodování podle `WindowSizeClass`: **výška compact** (< 480 dp) = telefon na šířku; **šířka compact** (< 600 dp) = telefon na výšku; jinak (šířka i výška aspoň medium) = tablet. Tablet na šířku má postranní panel vpravo, tablet na výšku má panel pod obrazem.

**Telefon na výšku – hlavní obrazovka** (shora dolů):

```
┌──────────────────────────────────────┐
│ (● HT-102 připojeno · 9 Hz)   (⚡) (⚙) │  horní lišta 56 dp, prvky ve skle
├──────────────────────────────────────┤
│ [zámek Interiér 12–24 °C][ε 0,95][foto 12 Mpx]
│                                   ┌──┐ │
│        ▼13,4°                     │24│ │  živý obraz – čtverec
│  ○P1 15,3°                        │▓▓│ │  přes celou šířku (neprůhledný)
│               ┼ 19,6 °C           │▓▓│ │
│      ▲43,1°                       │12│ │
│                                   └──┘ │
├──────────────────────────────────────┤
│ [▼ MIN 13,4] [+ STŘED 19,6] [▲ MAX 43,1]│  skleněné dlaždice
│ [⚠ Možný tepelný most  fRsi 0,64<0,75 ]│  pruh (jen při riziku)
│ [ Termo | Překryv | Vedle sebe | Běžná]│  režim
│ Prolnutí ────────●──────── 55 %       │  jen v režimu Překryv
│ (Rozsah Interiér ▾) (◐ Iron ▾) (+ Bod) │  rychlé volby
│ ╭────────────────────────────────────╮ │
│ │ [galerie]      ( ◉ )      [video]  │ │  skleněná spodní lišta
│ ╰────────────────────────────────────╯ │
└──────────────────────────────────────┘
```

**Telefon na šířku:** vlevo obraz (v režimu Vedle sebe termo a fotka vedle sebe), nahoře úzký pruh se stavovým čipem a hodnotami MIN/STŘED/MAX a fRsi; vpravo skleněný svislý panel 140 dp: svítilna + nastavení, výběr režimu, spoušť, galerie.

**Tablet (na šířku):**

```
┌────┬──────────────────────────────────────────────┬──────────────────┐
│logo│ (● HT-102 · 9 Hz)   [Termo|Překryv|Vedle|Běž] (⚡) │ MĚŘENÍ           │
│    │ ┌──────────────────────────────┐   ╭──────╮   │ MIN  STŘED  MAX  │
│Kam.│ │                              │   │galer.│   │ ○ P1 nadpraží  ✕ │
│Gal.│ │      živý obraz 660 × 660    │   │      │   │ + Přidat bod     │
│Kal.│ │      se značkami a stupnicí  │   │ ( ◉ )│   │ ⚠ TEPELNÝ MOST   │
│Test│ │                              │   │      │   │ Uvnitř/Venku/Lim.│
│    │ └──────────────────────────────┘   │video │   │ ROZSAH (čipy)    │
│Nast│ (Prolnutí ─────────●───────── 55 %) ╰──────╯   │ PALETA (4 vzorky)│
└────┴──────────────────────────────────────────────┴──────────────────┘
 skleněný rail      hlavní plocha                     skleněný panel 336 dp
```

Popisky v navigaci se smí zalomit na dva řádky (s dělením slov), nikdy se neořezávají – viz němčina („Einstellungen“).

**Další obrazovky:** Kamera odpojena (F6), Test kamery (F6), Rozsah a kalibrace jako bottom sheet (telefon) / postranní panel nebo dialog (tablet) (F2), Galerie a Detail snímku se sekcí „Sdílet jako“ (F5), **Nabídka lepší kamery** jako bottom sheet (F11), **Nastavení** (F10), USB diagnostika (Fáze 0).

**Banner aktualizace** (F8): skleněná karta pod horní lištou – ikona stažení, „Stažena verze 1.3.0 · Nainstaluje se po potvrzení systémem“, tlačítko **Nainstalovat** (`accent`).

---

## 8. Oprávnění a manifest

- `android.hardware.usb.host` (`required="false"`, aby šla aplikace nainstalovat i bez USB host – pak jen galerie), USB intent-filter + `device_filter.xml`.
- `CAMERA` – jen pokud uživatel zvolí kameru telefonu (F11); žádat až v tu chvíli. Svítilna přes `setTorchMode` oprávnění nepotřebuje.
- `res/xml/locales_config.xml` a `android:localeConfig` v manifestu (F10).
- `INTERNET`, `ACCESS_NETWORK_STATE` – jen updater.
- `REQUEST_INSTALL_PACKAGES` – instalace aktualizace.
- Ukládání do galerie přes `MediaStore` bez oprávnění k úložišti (API 29+); pro API 26–28 `WRITE_EXTERNAL_STORAGE` s `maxSdkVersion="28"`.
- `FileProvider` pro sdílení.

---

## 9. Aktualizace z GitHubu (detail)

> Pokud už updater existuje v projektu Deskovky (stejný princip: Android, offline, aktualizace z GitHubu), převezmi ho a uprav – nepiš ho znovu.

**Repozitář:** `https://github.com/<GITHUB_OWNER>/<REPO>` (doplní uživatel). Releases musí být **veřejně čitelné** (veřejný repozitář, nebo samostatný veřejný repozitář jen pro releases). Do APK se nikdy nevkládá GitHub token.

**Release (GitHub Actions, `.github/workflows/release.yml`):**

1. Spouští se na tag `v*` (např. `v1.3.0`).
2. Sestaví podepsané release APK. Keystore je v GitHub Secrets (base64) + hesla; **keystore nikdy necommitovat**.
3. `versionName` = tag bez `v`; `versionCode = major·10000 + minor·100 + patch`.
4. Vytvoří GitHub Release s assetem `thermocamera-<verze>.apk` a souborem `thermocamera-<verze>.apk.sha256`. Release notes z tagu / `CHANGELOG.md`.

**Kontrola v aplikaci (při každém spuštění):**

1. Na pozadí, neblokuje UI. Bez validované sítě → konec, nic se nezobrazí.
2. `GET https://api.github.com/repos/<owner>/<repo>/releases/latest` (timeout 5 s, hlavička `Accept: application/vnd.github+json`; neautentizovaný limit je dostatečný).
3. Porovnání verze z `tag_name` (semver) s `BuildConfig.VERSION_NAME`. Draft a pre-release se ignorují.
4. Je-li novější: stažení APK do `cacheDir/updates/` (pokračování po přerušení, nestahovat znovu, pokud už je staženo), **ověření SHA-256** a **ověření, že podpisový certifikát APK je stejný jako u nainstalované aplikace** (`getPackageArchiveInfo` + `GET_SIGNING_CERTIFICATES`).
5. Po stažení banner „Stažena verze X · Nainstalovat“. Instalace přes `PackageInstaller` session. Na Android 12+ nastav `setRequireUserAction(USER_ACTION_NOT_REQUIRED)` – kde to systém dovolí, proběhne bez dotazu, jinak systém zobrazí potvrzení. Při prvním použití systém požádá o povolení „Instalovat neznámé aplikace“ pro ThermoCamera – aplikace k tomu ukáže krátké vysvětlení a otevře příslušné nastavení.
6. Chyby (síť, hash, podpis) se zalogují do lokálního logu (viditelného v Pro pokročilé), uživatele neruší. Neplatný soubor se smaže.
7. Nastavení: „Kontrolovat aktualizace při startu“ (výchozí zapnuto), „Stahovat jen přes Wi-Fi“ (výchozí vypnuto).

**První instalace:** uživatel stáhne APK z GitHub Releases ručně; další verze už aplikace řeší sama.

---

## 10. Testování

- **Unit testy `:thermal-core`:** palety (krajní body), bikubická interpolace (zachování hodnot v uzlech), korekce emisivity (ε = 1 nemění hodnotu), offset/gain, auto-rozsah (stabilita při šumu), fRsi a izoterma, převod surových dat HT-102 (podle Fáze 0).
- **Golden-image testy** rendereru (porovnání s referenčními PNG s tolerancí).
- **UI testy (Compose)** se `SimulatedSource`: přepínání režimů, přidání bodu, uložení snímku, sdílení (intent), stavy odpojení/připojení, test kamery, nabídka lepší kamery, přepnutí motivu a jazyka za běhu.
- **Screenshot testy** (např. Roborazzi nebo Paparazzi) hlavních obrazovek v matici **2 motivy × 6 jazyků × telefon/tablet** – hlídají ořezané texty, kontrast a rozbité rozložení. Navíc pseudolokalizace `en-XA` (prodloužené texty).
- **Registrace obrazu (F11):** unit testy na syntetických párech snímků se známým posunem/měřítkem (chyba < 2 px na zmenšeném obraze).
- **Updater:** testy proti mock serveru (novější / stejná / starší verze, špatný hash, jiný podpis, bez sítě).
- **Ruční test s HT-102** podle checklistu v `docs/manual-test.md` (telefon + tablet, obě orientace USB-C, tma + svítilna, odpojení během streamu, režim letadlo, kamera telefonu a zarovnání na 0,5 / 1 / 3 m, oba motivy, všech 6 jazyků).
- CI (GitHub Actions) na každý push/PR: build, unit testy, lint.

---

## 11. Fáze a akceptační kritéria

| Fáze | Obsah | Hotovo, když |
|---|---|---|
| **0 – Základ a průzkum** | projekt, Compose téma pro světlý i tmavý motiv a skleněné komponenty (kap. 7), všechny texty v `strings.xml` od začátku, CI, analýza aplikace výrobce (kap. 3.2), `ThermalCameraSource`, `SimulatedSource`, USB diagnostika, průzkum HT-102, `docs/ht102-protocol.md` | aplikace ukazuje živý obraz ze simulátoru ve skleněném UI v obou motivech; diagnostika exportuje dump; protokol HT-102 zdokumentovaný (nebo zdokumentovaný blocker a varianty) |
| **1 – Kamera** | `Ht102Source`, stavový čip, připojení/odpojení, watchdog, obrazovka Kamera odpojena, Test kamery | po zapojení se do 2 s objeví živý termo obraz ~9 Hz; odpojení nikdy neshodí aplikaci; test projde na funkční kameře a selže srozumitelně bez ní |
| **2 – Zobrazení a kamery** | 4 režimy, prolnutí, zarovnání, orientace/zrcadlení, značky, stupnice, body, svítilna, **kamera telefonu: zjištění, nabídka, náhled, focení, automatické zarovnání** (F11) | všechny režimy na telefonu (obě orientace) i tabletu; svítilna funguje / je skrytá bez blesku; s kamerou telefonu sedí překryv na 0,5–3 m s odchylkou ≤ 1 termo bod |
| **3 – Měření a kalibrace** | rozsahy, palety, emisivita, odražená teplota, 1- a 2-bodová kalibrace, profily, fRsi, izoterma | po jednobodové kalibraci měří kamera referenci s odchylkou ≤ 0,5 °C v blízkosti kalibračního bodu; fRsi odpovídá ručnímu výpočtu |
| **4 – Snímky** | ukládání (render, surová data, běžný snímek, metadata), projekty, galerie, detail, přerenderování, sdílení JPG / vedle sebe / PDF / CSV | uložený snímek jde otevřít, přerenderovat jinou paletou a sdílet do e-mailu/WhatsAppu ve všech 4 formátech |
| **5 – Aktualizace** | GitHub Actions release, updater v aplikaci | po vydání tagu `v1.0.1` si aplikace s `v1.0.0` při startu sama stáhne a nabídne instalaci; bez sítě start nijak nezdrží |
| **6 – Jazyky a doladění** | překlady do en, es, it, fr, de podle glosáře, volba jazyka, formátování čísel, tablet layout, přístupnost, „Omezit průhlednost“, výkon, volitelně video a zmrazení | všech 6 jazyků bez chybějících překladů a bez ořezaných textů (screenshot testy); splněny nefunkční požadavky (kap. 6); ruční checklist prošel na telefonu i tabletu |

**Definition of Done pro každou fázi:** `./gradlew assembleRelease` projde, testy zelené, lint bez chyb, funguje v režimu letadlo, aktualizovaný `README.md` a `CHANGELOG.md`, commit s popisem.

---

## 12. Struktura repozitáře (návrh)

```
thermocamera/
├─ app/
│  ├─ src/main/res/values{,-cs,-es,-it,-fr,-de}/strings.xml   # values = angličtina (výchozí)
│  └─ src/main/java/<package>/
│     ├─ camera/        # Ht102Source, SimulatedSource, PhoneCameraSource, CameraCapabilities, UsbMonitor
│     ├─ registration/  # zarovnání fotky telefonu na termo obraz (F11)
│     ├─ ui/            # theme (light/dark, glass), components, live, gallery, detail, calibration, test, settings, diagnostics
│     ├─ data/          # Room, DataStore, repositories, storage
│     ├─ export/        # JPG/PDF/CSV renderery, FileProvider
│     └─ update/        # GitHub updater
├─ thermal-core/        # čistý Kotlin: korekce, měření, palety, interpolace, fRsi
├─ docs/
│  ├─ ht102-protocol.md
│  ├─ decisions.md
│  ├─ manual-test.md
│  ├─ translations.md  # glosář, stav revize překladů
│  ├─ captures/         # dumpy z diagnostiky, nahrané surové sekvence
│  └─ vendor/           # APK výrobce a jeho rozbor – v .gitignore, NECOMMITOVAT
├─ tools/
│  └─ windows-diag/     # diagnostický skript HT-102 pro Windows (PowerShell + C#)
├─ .github/workflows/   # ci.yml, release.yml
├─ CHANGELOG.md
└─ README.md            # instalace, první spuštění, jak vydat verzi
```

---

## 13. Rizika a otevřené otázky

| Riziko | Dopad | Opatření |
|---|---|---|
| Termo kanál HT-102 nejde rozluštit (UVC stream nese jen běžný obraz – ověřeno) | aplikace bez dat | Fáze 0 jako první; varianty: dotaz na SDK u výrobce (kontakty na modulu: xintest.com.cn), jiná kamera s popsaným protokolem – architektura `ThermalCameraSource` to umožní |
| Běžný obraz z modulu nejde získat | překryv nefunguje | záložní kamera telefonu přes CameraX + ruční zarovnání |
| Rozlišení 32 × 32 | malé detaily nejsou vidět | interpolace, fúze, doporučení snímat z 1–2 m |
| Přesnost ±3 °C | chybné absolutní hodnoty | kalibrace podle reference, důraz na rozdíly, fRsi jako orientační |
| Pracovní teplota kamery 0–35 °C | venkovní měření v zimě nespolehlivé | upozornění u předvolby Fasáda |
| Tablet bez blesku | svítilna chybí | tlačítko skrýt, nápověda |
| Paralaxa mezi kamerou telefonu a modulem | překryv nesedí | automatická registrace (F11), ruční korekce, tip k orientaci modulu |
| Modul míří jinam než kamera telefonu | kamera telefonu nejde použít | detekce selhávající registrace, tip v UI, návrat ke kameře modulu |
| Backdrop blur na starších / slabších telefonech | pomalé UI | náhradní výplň bez rozmazání, volba „Omezit průhlednost“ |
| Kvalita strojových překladů | nepřesné odborné pojmy | glosář (kap. 14), revize rodilým mluvčím, `docs/translations.md` |
| Instalace aktualizací vyžaduje potvrzení | „tichá“ aktualizace není vždy možná | `USER_ACTION_NOT_REQUIRED`, kde to jde; jinak banner + systémové potvrzení |

**Otázky na uživatele (zeptej se na začátku Fáze 0):**

1. Název GitHub účtu a repozitáře; má být repozitář veřejný?
2. Application ID (např. `cz.<něco>.thermocamera`).
3. Na jakém telefonu a tabletu se bude testovat (model, verze Androidu, má tablet blesk?).
4. (Zodpovězeno: kamera je zapojená do uživatelova notebooku s Windows 11 – viz kap. 0, bod 3.)
5. Kdo zreviduje překlady (es, it, fr, de)? Pokud nikdo, nech je označené jako strojové v `docs/translations.md`.

---

## 14. Glosář odborných pojmů (pro překlady)

| cs | en | es | it | fr | de |
|---|---|---|---|---|---|
| termokamera | thermal camera | cámara térmica | termocamera | caméra thermique | Wärmebildkamera |
| termo obraz | thermal image | imagen térmica | immagine termica | image thermique | Wärmebild |
| tepelný most | thermal bridge | puente térmico | ponte termico | pont thermique | Wärmebrücke |
| teplotní faktor fRsi | temperature factor fRsi | factor de temperatura fRsi | fattore di temperatura fRsi | facteur de température fRsi | Temperaturfaktor fRsi |
| emisivita | emissivity | emisividad | emissività | émissivité | Emissionsgrad |
| odražená teplota | reflected temperature | temperatura reflejada | temperatura riflessa | température réfléchie | reflektierte Temperatur |
| teplotní rozsah | temperature range | rango de temperatura | intervallo di temperatura | plage de température | Temperaturbereich |
| paleta | palette | paleta | tavolozza | palette | Palette |
| izoterma | isotherm | isoterma | isoterma | isotherme | Isotherme |
| kalibrace | calibration | calibración | calibrazione | étalonnage | Kalibrierung |
| prolnutí | blend | fusión | fusione | fondu | Überblendung |
| svítilna | flashlight | linterna | torcia | lampe torche | Taschenlampe |
| nadpraží | lintel | dintel | architrave | linteau | Fenstersturz |
| kondenzace | condensation | condensación | condensa | condensation | Tauwasser |
| plíseň | mold | moho | muffa | moisissure | Schimmel |
| protokol (PDF) | report | informe | rapporto | rapport | Bericht |

Barvy, symboly MIN ▼ / MAX ▲ a zkratky (fRsi, ε) jsou ve všech jazycích stejné.

---

## Příloha A – USB deskriptory HT-102 (změřeno 21. 11. 2026)

```
DEVICE: 12 01 00 02 EF 02 01 40 16 04 93 93 00 01 01 02 03 01
        USB 2.00, class EF/02/01 (IAD), EP0 64 B, VID 0416, PID 9393, bcdDevice 1.00, 1 konfigurace
STRING 3 (serial): 000055FA93

CONFIG (344 B):
  @   0: 09 02 58 01 02 01 00 C0 00                      konfigurace, 2 rozhraní, self/bus powered
  @   9: 08 0B 00 02 0E 03 00 02                         IAD: rozhraní 0–1, Video
  @  17: 09 04 00 00 01 0E 01 00 02                      IF0 VideoControl, 1 endpoint
  @  26: 0D 24 01 00 01 32 00 80 8D 5B 00 01 01          VC header, UVC 1.00, clock 6 MHz, streaming IF 1
  @  39: 09 24 03 03 01 01 00 05 00                      Output Terminal ID 3 (USB streaming), zdroj 5
  @  48: 11 24 02 01 01 02 00 00 00 00 00 00 00 00 02 00 00   Camera Terminal ID 1, bez ovládání
  @  65: 0B 24 05 05 01 00 00 02 3F 05 00                Processing Unit ID 5, bmControls 0x053F
  @  76: 07 05 83 03 10 00 06                            EP 0x83 interrupt IN, 16 B, interval 6
  @  83: 05 25 03 10 00                                  CS endpoint, wMaxTransferSize 16
  @  88: 09 04 01 00 00 0E 02 00 00                      IF1 VideoStreaming alt 0
  @  97: 0E 24 01 01 D7 00 81 00 03 02 01 00 01 00       VS input header, 1 formát, EP 0x81, still method 2
  @ 111: 1B 24 04 01 03 59 55 59 32 00 00 10 00 80 00 00 AA 00 38 9B 71 10 01 00 00 00 00
                                                         Uncompressed YUY2, 3 rámce, 16 bpp
  @ 138: 32 24 05 01 00 80 02 E0 01 ...                  640 × 480, 30/20/15/10/5/1 fps
  @ 188: 32 24 05 02 00 40 01 F0 00 ...                  320 × 240
  @ 238: 32 24 05 03 00 A0 00 78 00 ...                  160 × 120
  @ 288: 12 24 03 00 03 80 02 E0 01 40 01 F0 00 A0 00 78 00 00   Still image frames
  @ 306: 06 24 0D 01 01 04                               Color matching
  @ 312: 09 04 01 01 01 0E 02 00 00                      IF1 alt 1
  @ 321: 07 05 81 05 84 0B 01                            EP 0x81 iso IN, 0x0B84 = 2 × 900 B
  @ 328: 09 04 01 02 01 0E 02 00 00                      IF1 alt 2
  @ 337: 07 05 81 05 84 0B 01                            EP 0x81 iso IN, 2 × 900 B
```

Diagnostický skript (PowerShell + C#) je v `tools/windows-diag/`.
