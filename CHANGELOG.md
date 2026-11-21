# Changelog

## [Unreleased] – Fáze 4

- Spoušť ukládá snímek měření: render.jpg (obraz se značkami, stupnicí a pruhem hodnot), thermal.f32 + thermal.json
  (surová matice, °C před korekcemi), visible.jpg, u kamery telefonu photo.jpg v plném rozlišení + alignment.json
  (matice termo → foto); metadata v Room; kopie do Pictures/ThermoCamera (volitelně).
- Galerie: mřížka, filtr podle projektu, projekty („Nové snímky jdou do…“), vícenásobný výběr (dlouhý stisk),
  sdílení výběru jako JPG nebo jeden PDF protokol, mazání s potvrzením.
- Detail: přerenderování (režim, paleta, automatický/uložený rozsah, prolnutí, emisivita), „Ponechat toto zobrazení“,
  název, poznámka, projekt, sdílení jako JPG / vedle sebe / PDF / CSV (oddělovač `;`, desetinná čárka podle jazyka).

## [Unreleased] – Fáze 3

- Panel „Rozsah a kalibrace“ (z čipu Rozsah, na tabletu z lišty): předvolby rozsahu, vlastní min/max po 0,1 °C,
  zámek automatického rozsahu, emisivita (posuvník + Malba/Cihla/Sklo/Dřevo), odražená teplota.
- Kalibrace podle reference: 1-bodová (offset) a 2-bodová (offset + gain), měření středu přes 2 s.
- Pojmenované kalibrační profily (Room) a rychlé přepínání profilu z hlavní obrazovky.
- Tepelné mosty: θi, θe, limit fRsi, pruh „Možný tepelný most · fRsi … < …“, fRsi u bodů (tablet),
  izoterma (azurové zvýraznění míst pod limitem), upozornění na malý rozdíl θi − θe.
- Databáze Room (profily, projekty, snímky) připravená i pro Fázi 4.

## [Unreleased] – Fáze 2

- Orientace modulu (0/90/180/270°, zrcadlení) v Nastavení a v Testu kamery; kompenzace otočení displeje.
- Body P1–P5 (klepnutí, tažení, dlouhý stisk), tlačítko „+ Bod“, Zmrazit / Živě.
- Zarovnání překryvu kamery modulu: průvodce s tažením, sevřením a otočením prstů.
- Kamera telefonu (F11): výběr nejlepší zadní kamery, nabídka „Tvůj telefon má lepší kameru“, 3 režimy
  v Nastavení, živý náhled, focení v plném rozlišení, automatické zarovnání na obraz modulu (vlastní registrace
  obrazu ~2× za s), tip při opakovaném selhání, odznak „foto 12 Mpx“. Oprávnění CAMERA až při volbě.
- Svítilna (F7) – skrytá bez blesku, vypíná se při odchodu z aplikace.
- Rozložení podle kap. 7.5: telefon na šířku (postranní panel 140 dp), tablet na šířku (lišta, svislá pilulka,
  panel 336 dp) i na výšku (panel pod obrazem).
- `:thermal-core`: Orientation, OverlayAlignment/CropSquare, ImageRegistration, FovMath – s testy.

## [Unreleased] – Fáze 1

- `Ht102Source`: živý stream z HT-102 – UVC 640 × 480 YUY2 při 10 fps, termo matice dekódovaná z každého snímku,
  obraz kamery modulu jako běžný obraz. Oprávnění k USB, připojení/odpojení, watchdog (žádná data > 2 s → chyba),
  automatické obnovení po opětovném připojení.
- Vlastní malá nativní vrstva pro izochronní USB přenosy (usbfs), bez cizích knihoven; skládání snímků UVC
  a převod YUY2 → ARGB v `:thermal-core` s testy (včetně skutečného snímku z kamery).
- Test kamery (F6): 6 kroků, surová data 32 × 32, šum mezi snímky, Zopakovat / Hotovo / Zkopírovat diagnostiku;
  otevírá se ze stavového čipu a z obrazovky „Kamera odpojena“.
- Kamera streamuje jen když je aplikace vidět. Simulátor je výchozí jen na emulátoru.
- `docs/manual-test.md`: checklist pro test na telefonu.

## [Unreleased] – Fáze 0

- Projekt: `:app` (Kotlin, Jetpack Compose, Hilt) + `:thermal-core` (čistý Kotlin).
- `:thermal-core`: palety Iron/Duha/Šedá/Arktida, bikubická interpolace, korekce emisivity a offset/gain,
  1- a 2-bodová kalibrace, MIN/MAX/STŘED a body, automatický rozsah (percentily + EMA), fRsi a izoterma,
  renderer snímku, syntetická scéna – vše s unit testy.
- `ThermalCameraSource` + `SimulatedSource` (místnost s oknem, studené nadpraží, radiátor, 9 Hz + běžný obraz).
- Skleněné UI (Haze) se světlým/tmavým motivem a volbou „Omezit průhlednost“; hlavní obrazovka podle kap. 7.5
  (stavový čip, štítky rozsahu a ε, živý obraz se značkami a stupnicí, dlaždice, 4 režimy, prolnutí, rozsah, paleta).
- Nastavení: motiv, průhlednost, jazyk (rozbalovací seznam, 6 jazyků), simulátor, USB diagnostika.
- USB diagnostika: zařízení, rozhraní, endpointy, surové deskriptory (po povolení), kopírování a export.
- Rozpoznání připojení HT-102 (VID 0x0416 / PID 0x9393) a obrazovka „Kamera odpojena“.
- CI (GitHub Actions): build, unit testy, lint.
- **Protokol HT-102 rozluštěn:** termo matice 32 × 32 je v LSB chroma bajtů běžného snímku YUY2 640 × 480
  (desetiny °C). `Ht102Decoder` v `:thermal-core`, ověřeno testem na skutečném snímku z kamery.
