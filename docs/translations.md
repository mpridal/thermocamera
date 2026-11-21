# Překlady

Aplikace je šestijazyčná: `en` (výchozí, `values/`), `cs`, `es`, `it`, `fr`, `de`.

## Jak přidat nebo změnit text

1. Uprav slovník `S` v `tools/i18n/gen_strings.py` – každý klíč má hodnoty ve všech šesti jazycích
   v pořadí en, cs, es, it, fr, de.
2. Spusť `python tools/i18n/gen_strings.py app/src/main/res` – přegeneruje všechny `strings.xml`.
3. Ručně `strings.xml` neupravuj, příští generování by změny přepsalo.

Odborné pojmy podle glosáře v zadání (kap. 14).

## Stav revize

| Jazyk | Stav |
|---|---|
| cs | výchozí jazyk autora |
| en | strojový / autor |
| es | **strojový překlad, nerevidováno** (rozhodnutí uživatele 2026-11-21: nikdo nereviduje) |
| it | **strojový překlad, nerevidováno** |
| fr | **strojový překlad, nerevidováno** |
| de | **strojový překlad, nerevidováno** |
