# Rozhodnutí

Drobná rozhodnutí přijatá během vývoje (zadání kap. 0, bod 4). Nejnovější nahoře.

## 2026-11-21 – Fáze 0

| # | Rozhodnutí | Důvod |
|---|---|---|
| D1 | Repozitář `mpridal/thermocamera`, veřejný; application ID `io.github.mpridal.thermocamera`; projekt v `C:\dev\thermocamera` (mimo OneDrive) | odpovědi uživatele na otázky z kap. 13 |
| D2 | Překlady es/it/fr/de jsou strojové, nikdo je nereviduje | odpověď uživatele; stav v `docs/translations.md` |
| D3 | Release keystore se vygeneruje nový, mimo repozitář, do GitHub Secrets | odpověď uživatele; řeší se ve Fázi 5 |
| D4 | AGP 9.5.2 + Gradle 9.9.2 + Kotlin 2.4.21 (vestavěná podpora Kotlinu v AGP 9), `compileSdk`/`targetSdk` 37; Compose BOM 2026.09.00 | AndroidX knihovny vyžadují API 37. Novější Compose BOM (2026.11) vyžaduje platformu 37.1, která na tomto počítači není – přejít, až se doinstaluje. |
| D5 | Haze 2.1.0 | řada 2.x je od podzimu 2026 stabilní (zadání: použij poslední stabilní) |
| D6 | Písma Inter a JetBrains Mono zatím nejsou přibalená – používá se systémové bezpatkové a neproporcionální písmo | stažení souborů písem čeká na souhlas uživatele; doplní se do `res/font` (licence OFL) |
| D7 | Updater z Deskovek se nepřebírá jako kód – Deskovky jsou ve Flutteru (Dart). Logika (GitHub releases API, SHA-256, kontrola podpisu) se převezme jako vzor do Kotlinu ve Fázi 5 | jiný jazyk |
| D8 | Simulátor je výchozí zapnutý jen na emulátoru (`Settings.isEmulator`), jinak vypnutý | od Fáze 1 čte `Ht102Source` skutečná data; zadání kap. 3.1 |
| D9 | Texty všech 6 jazyků se generují skriptem `tools/i18n/gen_strings.py` (jediný zdroj pravdy pro překlady) | žádný jazyk nemůže zapomenout klíč; lint `MissingTranslation` je navíc chyba buildu |
| D10 | Ve Fázi 0 není svítilna ani body P1–P5 – tlačítka spouště, galerie, videa a „+ Bod“ ukazují „Bude v některé z dalších verzí“ | patří do Fází 2 a 4 |
| D11 | Termo data HT-102 se čtou z běžného snímku YUY2 640 × 480 (LSB chroma, viz `docs/ht102-protocol.md`). `Ht102Source` tedy potřebuje jen UVC knihovnu, která dodá surové YUY2 snímky; dekódování je v `:thermal-core` (`Ht102Decoder`). Volba knihovny ve Fázi 1. | rozbor aplikace výrobce `ThermalViewer_Hti_V1_1_1` (Nuvoton/SenXor) + ověření na snímcích z kamery |
| D12 | APK výrobce získány z Internet Archive (původní adresy `xintest.com.cn`), uloženy v `docs/vendor/` mimo git; jadx v `tools/bin/` mimo git | web výrobce nefunguje, uživatel aplikaci v telefonu nemá; stažení odsouhlaseno 21. 11. 2026 |
| D13 | UVC stream bez cizích knihoven: izochronní přenosy vlastní malou nativní vrstvou nad Linux usbfs (`app/src/main/cpp/ht102_usb.c`), UVC probe/commit a výběr alt. nastavení přes Java `UsbDeviceConnection`, skládání snímků a převod YUY2 v čistém Kotlinu (`UvcFrameAssembler`, `Yuy2`, testované) | Java USB API neumí izochronní přenosy; Camera2 „external camera“ je jen na některých telefonech a převádí YUY2 (zničila by skryté bity); UVCCamera/AndroidUSBCamera jsou velké, neudržované a také převádějí formát |
| D14 | Stream 640 × 480 YUY2 při 10 fps; opakované termo matice (senzor ~9 Hz) se přeskakují, frekvence se počítá z nových matic | 30 fps by přesáhlo šířku pásma alt. nastavení (2 × 900 B / mikrorámec ≈ 14 MB/s); termo data stejně chodí ~9 Hz |
