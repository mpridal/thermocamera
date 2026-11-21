# ThermoCamera

Android aplikace pro telefony a tablety, která s externí termokamerou **Hti HT-102** hledá tepelné mosty
v budovách a místnostech. Plně offline; jedinou síťovou funkcí bude kontrola aktualizací z GitHub Releases.

Zadání: [docs/zadani.md](docs/zadani.md) · rozhodnutí: [docs/decisions.md](docs/decisions.md) ·
protokol kamery: [docs/ht102-protocol.md](docs/ht102-protocol.md)

## Stav

**Fáze 1 – kamera.** Aplikace čte živě termo data i obraz z HT-102 (protokol v `docs/ht102-protocol.md`),
má stavový čip, obrazovku „Kamera odpojena“ a Test kamery. Na emulátoru běží simulátor.
**Ještě neověřeno na skutečném telefonu** – postup v `docs/manual-test.md`.

## Sestavení

Potřeba: JDK 21, Android SDK (API 37).

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
