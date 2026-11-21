# Changelog

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
