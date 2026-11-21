# Vydání verze (zadání kap. 9)

## Jednorázové nastavení (dělá uživatel)

Podpisový klíč vznikl 2026-11-22 mimo repozitář: `C:\dev\thermocamera-signing\`

| Soubor | Obsah |
|---|---|
| `thermocamera-release.jks` | klíč (PKCS12, RSA 4096, alias `thermocamera`, platnost ~27 let) |
| `keystore.properties` | hesla (úložiště i klíč mají stejné) |
| `thermocamera-release.jks.base64` | klíč v base64 pro GitHub Secret |

SHA-256 certifikátu: `7B:0C:9D:38:35:49:A6:54:2F:80:28:9E:91:F1:33:41:37:4B:53:39:27:51:B4:53:97:D2:23:7A:E7:D2:4A:25`

> **Zálohuj klíč i hesla** (např. do správce hesel). Bez nich nepůjde vydat aktualizaci, kterou si nainstalovaná
> aplikace sama stáhne – updater ověřuje, že nová verze je podepsaná stejným klíčem.

GitHub › mpridal/thermocamera › Settings › Secrets and variables › Actions › New repository secret:

| Secret | Hodnota |
|---|---|
| `THERMOCAMERA_KEYSTORE_BASE64` | obsah `thermocamera-release.jks.base64` |
| `THERMOCAMERA_KEYSTORE_PASSWORD` | `storePassword` z `keystore.properties` |
| `THERMOCAMERA_KEY_ALIAS` | `thermocamera` |
| `THERMOCAMERA_KEY_PASSWORD` | `keyPassword` z `keystore.properties` |

Nebo z příkazové řádky (gh je přihlášené):

```
gh secret set THERMOCAMERA_KEYSTORE_BASE64 -R mpridal/thermocamera < C:\dev\thermocamera-signing\thermocamera-release.jks.base64
gh secret set THERMOCAMERA_KEYSTORE_PASSWORD -R mpridal/thermocamera
gh secret set THERMOCAMERA_KEY_ALIAS -R mpridal/thermocamera -b thermocamera
gh secret set THERMOCAMERA_KEY_PASSWORD -R mpridal/thermocamera
```

## Vydání

1. V `CHANGELOG.md` přejmenovat sekci `[Unreleased]` na `[1.0.0]` (text sekce se stane popisem vydání).
2. Commit a tag: `git tag v1.0.0 && git push origin v1.0.0`
3. GitHub Actions (`release.yml`) spustí testy, sestaví podepsané APK, ověří podpis a vytvoří Release
   s `thermocamera-1.0.0.apk` a `thermocamera-1.0.0.apk.sha256`.

`versionName` = tag bez `v`, `versionCode = major·10000 + minor·100 + patch`.

## První instalace

Release APK je podepsané jiným klíčem než debug buildy z Android Studia / CI – na telefonu je nejdřív nutné
debug verzi odinstalovat (smaže to data aplikace: snímky a nastavení!). Pak stáhnout APK z GitHub Releases
a nainstalovat. Další verze už aplikace stahuje a nabízí sama (banner „Stažena verze …“).

## Lokální podepsaný build (bez vydání)

```
$env:THERMOCAMERA_KEYSTORE="C:/dev/thermocamera-signing/thermocamera-release.jks"
$env:THERMOCAMERA_KEYSTORE_PASSWORD="…"; $env:THERMOCAMERA_KEY_ALIAS="thermocamera"; $env:THERMOCAMERA_KEY_PASSWORD="…"
./gradlew :app:assembleRelease -PversionName=1.0.0
```
