# ThermoCamera

Android aplikace pro telefony a tablety, která s externí termokamerou **Hti HT-102** hledá tepelné mosty
v budovách a místnostech. Plně offline; jedinou síťovou funkcí bude kontrola aktualizací z GitHub Releases.

Zadání: [docs/zadani.md](docs/zadani.md) · rozhodnutí: [docs/decisions.md](docs/decisions.md) ·
protokol kamery: [docs/ht102-protocol.md](docs/ht102-protocol.md)

## Stav

Hotové Fáze 0–6 podle zadání (`docs/zadani.md`): živý obraz z HT-102 (protokol v `docs/ht102-protocol.md`),
4 režimy, body, orientace, zarovnání, kamera telefonu s automatickým zarovnáním, svítilna, rozsahy, emisivita,
kalibrace, profily, tepelné mosty (fRsi, izoterma), snímky, galerie, sdílení (JPG/PDF/CSV), aktualizace z GitHubu,
6 jazyků, °C/°F, telefon i tablet. Na emulátoru běží simulátor.

Ověřeno na Pixelu 10 Pro: Fáze 1–2 (viz `docs/manual-test.md`). Fáze 3–6 zatím jen na emulátoru.
Vydání verzí: `docs/release.md` (čeká na nahrání podpisového klíče do GitHub Secrets).

## Sestavení

Potřeba: JDK 21, Android SDK (API 37), NDK 28.2 a CMake 3.22.1 (nativní USB vrstva).

```
./gradlew :thermal-core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Struktura

- `app/` – UI (Jetpack Compose), Android integrace (USB, nastavení).
- `thermal-core/` – čistý Kotlin: palety, interpolace, korekce, měření, fRsi, simulovaná scéna. Plně pokryté unit testy.
- `tools/windows-diag/` – diagnostický skript HT-102 pro Windows (PowerShell + C#, jen čte).
- `tools/i18n/` – generátor `strings.xml` pro všech 6 jazyků.
- `docs/` – zadání, rozhodnutí, protokol, překlady, zachycená data (`captures/`).
  `docs/vendor/` je v `.gitignore` – APK výrobce a jeho rozbor se necommitují.

## Instalace a vydání verze

Doplní se ve Fázi 5 (GitHub Actions release + updater).
