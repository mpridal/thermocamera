# Ruční test s HT-102

Checklist pro test na skutečném telefonu/tabletu (zadání kap. 10). Výsledky zapiš do tabulky dole.

## Testovací zařízení

- Telefon: Google Pixel 10 Pro
- Tablet: JENBIZZ 10", Android 16, slabé GPU (ověřit plynulost skla, případně „Omezit průhlednost“; zjistit, zda má blesk)

## Fáze 1 – kamera

Příprava: nainstalovat debug APK (artefakt `thermocamera-debug` z GitHub Actions nebo `app/build/outputs/apk/debug/app-debug.apk`),
v Nastavení › Pro pokročilé musí být **simulátor vypnutý**.

| # | Krok | Očekávání |
|---|---|---|
| 1 | Spustit aplikaci bez kamery | obrazovka „Připoj termokameru HT-102“, čip „Kamera odpojena“ (červený) |
| 2 | Zasunout HT-102 | systém nabídne otevřít ThermoCamera / zeptá se na přístup k USB → povolit (zaškrtnout „vždy“) |
| 3 | Po povolení | do 2 s živý termo obraz, čip „HT-102 · připojeno · ~9 Hz“ (zelený) |
| 4 | Ruka před kamerou | v obraze teplé místo ~30–34 °C, MAX odpovídá |
| 5 | Ruka v **pravé** části záběru | teplé místo vpravo? (pokud ne → zapsat, orientace se nastaví ve Fázi 2) |
| 6 | Režim Běžná / Vedle sebe | obraz z kamery modulu 640 × 480 |
| 7 | Klepnout na stavový čip → Test kamery | všech 6 kroků ✓, „Kamera funguje“, frekvence ≥ 7 Hz, 0 vadných bodů |
| 8 | Vytáhnout kameru během živého obrazu | aplikace nespadne, hned „Kamera odpojena“ |
| 9 | Znovu zasunout | obraz se obnoví sám (bez restartu aplikace) |
| 10 | Aplikaci dát do pozadí a vrátit | stream se v pozadí zastaví a po návratu obnoví |
| 11 | Led ve vodě (≈ 0 °C), teplá voda s teploměrem, dlaň | zapsat naměřené vs. skutečné hodnoty (přesnost ±3 °C, emisivita) |
| 12 | Nastavení › Pro pokročilé › USB diagnostika › Načíst deskriptory › Exportovat | soubor poslat zpět (uloží se do `docs/captures/`) |

Když něco nejde: v Testu kamery **Zkopírovat diagnostiku** a poslat text – nebo jen připojit telefon k počítači.
Každý dokončený test se ukládá sám a z počítače se přečte (debug build):

```
adb shell run-as io.github.mpridal.thermocamera cat files/diagnostics/latest-camera-test.txt
adb shell run-as io.github.mpridal.thermocamera ls files/diagnostics
```

## Fáze 2 – zobrazení a kamery

| # | Krok | Očekávání |
|---|---|---|
| 1 | Test kamery › Orientace: ruka vpravo | teplé místo vpravo; jinak „Otočit o 90°“ / „Zrcadlit“ dokud nesedí |
| 2 | Otočit telefon na šířku | termo i obraz modulu zůstanou správně otočené vůči scéně, rozložení s panelem vpravo |
| 3 | Klepnout do obrazu, táhnout bod, dlouze podržet | P1 přibude s hodnotou, posune se, zmizí |
| 4 | Nastavení › Kamera › Zarovnání překryvu, hrnek s horkou vodou | tažením/sevřením hrany sedí; po „Hotovo“ drží v režimu Překryv |
| 5 | První připojení HT-102 | nabídka „Tvůj telefon má lepší kameru“ → Použít → povolit kameru |
| 6 | Režim Překryv s kamerou telefonu na 0,5 / 1 / 3 m | obrazy se do ~1 s srovnají, odchylka ≤ 1 termo bod; odznak „foto … Mpx“ |
| 7 | Zakrýt kameru telefonu | po ~3 s tip „Modul možná míří jinam…“, překryv drží poslední polohu |
| 8 | Spoušť s kamerou telefonu | hláška „Fotka pořízena (… Mpx)“ |
| 9 | Svítilna ve tmě (s kamerou modulu i telefonu) | rozsvítí/zhasne; po odchodu z aplikace zhasne, po návratu se obnoví |
| 10 | Tablet (JENBIZZ) na šířku i na výšku | lišta vlevo, panel vpravo / pod obrazem; bez blesku tlačítko svítilny chybí; plynulost skla (případně „Omezit průhlednost“) |

## Fáze 3 – měření a kalibrace

| # | Krok | Očekávání |
|---|---|---|
| 1 | Čip „Rozsah“ → Vlastní, Min/Max | barvy odpovídají zadanému rozsahu, štítek ukazuje zámek a hodnoty |
| 2 | Automaticky + Zamknout rozsah, pohnout kamerou | barvy se nemění |
| 3 | Emisivita Cihla 0,93 | štítek ε 0,93, teplé předměty mírně teplejší |
| 4 | 1-bodová kalibrace: teploměr v teplé vodě, kříž na vodu, zadat teplotu, Změřit, Použít | střed pak ukazuje referenci s odchylkou ≤ 0,5 °C |
| 5 | 2-bodová: led ve vodě (0 °C) + teplá voda | obě reference sedí ≤ 0,5 °C |
| 6 | Uložit jako profil, změnit nastavení, profil vybrat z čipu „Profil“ | nastavení se vrátí |
| 7 | θi 21 °C, θe 0 °C, kříž na studený roh | pruh „Možný tepelný most“ s fRsi; ruční výpočet (θsi − θe)/(θi − θe) sedí |
| 8 | Zvýraznit místa pod limitem | studená místa azurově |
| 9 | θi − θe < 10 °C | upozornění v panelu |

## Fáze 4 – snímky a sdílení

| # | Krok | Očekávání |
|---|---|---|
| 1 | Spoušť (kamera modulu i telefonu) | „Snímek uložen“, kopie v Galerii telefonu › Pictures/ThermoCamera |
| 2 | Galerie → snímek | detail s obrázkem a hodnotami |
| 3 | V detailu jiná paleta / Vedle sebe / ε, „Ponechat toto zobrazení“ | obrázek se přepočítá, náhled v galerii se změní |
| 4 | Sdílet jako JPG, Vedle sebe, PDF, CSV do e-mailu / WhatsAppu | přílohy jdou otevřít; CSV v Excelu ve sloupcích, česky s desetinnou čárkou |
| 5 | Nový projekt, „Nové snímky jdou do…“, filtr | snímky se řadí do projektu |
| 6 | Dlouhý stisk, výběr více snímků, Sdílet › PDF | jeden PDF protokol, strana na snímek |
| 7 | Smazat | potvrzení, snímek zmizí (kopie v galerii telefonu zůstane) |

## Výsledky

| Datum | Zařízení (model, Android) | Kroky OK | Poznámky |
|---|---|---|---|
| 2026-11-21 | Pixel 10 Pro, Android 17 | Fáze 1: Test kamery 6/6 (320 × 240, 6,2 Hz, 0 vadných bodů) | nejdřív chybělo oprávnění Fotoaparát pro USB a handle na arm64 – opraveno; HT-102 reálně ~5–6,5 Hz (D22) |
| 2026-11-21 | Pixel 10 Pro, Android 17 | Fáze 2: orientace (nutné **zrcadlení**), telefon na šířku, překryv s kamerou telefonu, spoušť, svítilna – OK | odznak „foto … Mpx“ neověřen; přesnost proti teploměru a tablet zatím ne |
