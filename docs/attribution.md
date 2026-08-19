# Attribution

**This is a licence obligation, not a courtesy.** Every dataset in the dictionary is CC BY-SA, and shipping without attribution breaches the terms.

This file is the source of truth for the in-app licences screen. Keep it in step with `tools/dictbuild/sources.lock.json`, which records exactly which version of each dataset produced the shipped build.

## Where it has to appear

EDRDG's licence statement is specific about mobile applications:

> acknowledgement must be made **on a separate screen accessed from a menu, such as one labelled "About"** — it is not sufficient just to mention it on a start-up/launch page.

So the attribution screen is a hard requirement with a defined shape. `ux.md` places it inside Saved or a menu rather than the bottom nav (D-36), which satisfies this.

Two further constraints from the same statement:

- The documentation and licence files must be **included or linked**, not merely referenced.
- We must **not claim copyright** over the dataset material.

## Share-alike

All four datasets are CC BY-SA. The built `spotter.db` is a derivative of them, so **the database asset itself carries CC BY-SA** and must be distributed under the same or a compatible licence.

This is a statement of what the licences say, not legal advice — worth a proper read before release, since it governs how the shipped asset may be distributed.

## Text for the in-app screen

> **Dictionary data**
>
> Spotter uses the following freely licensed datasets. We claim no copyright over their content.
>
> **JMdict** — Japanese-English dictionary data.
> © Electronic Dictionary Research and Development Group, licensed under CC BY-SA 4.0.
> https://www.edrdg.org/wiki/index.php/JMdict-EDICT_Dictionary_Project
>
> **KANJIDIC2** — per-kanji readings, meanings and stroke counts.
> © Electronic Dictionary Research and Development Group, licensed under CC BY-SA 4.0.
> https://www.edrdg.org/wiki/index.php/KANJIDIC_Project
>
> **KanjiVG** — stroke order data.
> © 2009–2013 Ulrich Apel, licensed under CC BY-SA 3.0.
> http://kanjivg.tagaini.net
>
> **JmdictFurigana** — per-character reading alignment, derived from JMdict.
> Licensed under CC BY-SA.
> https://github.com/Doublevil/JmdictFurigana
>
> **Tatoeba** — example sentences, reaching us through JMdict's example-linked edition.
> Licensed under CC BY 2.0 FR.
> https://tatoeba.org
>
> Dataset versions in this build are listed below.
>
> **Fonts**
>
> **Noto Sans JP** — bundled so that kanji render with Japanese glyph forms
> rather than Chinese ones (D-34).
> © 2014–2021 Adobe, with Reserved Font Name 'Source'. Licensed under the SIL
> Open Font License, Version 1.1.
> https://fonts.google.com/noto/specimen/Noto+Sans+JP
>
> **Software**
>
> **Kuromoji** — the morphological analyser that decides where Japanese words
> begin and end, bundled with its IPADIC dictionary (D-07).
> © Atilika Inc. Licensed under the Apache License, Version 2.0.
> https://www.atilika.org/

KanjiVG's terms are the most prescriptive: attribution must state the use of KanjiVG **and link to the site**, both of which the wording above does.

**Bundled software is a third category, separate again from the datasets and the font.** Kuromoji is Apache 2.0, which requires the licence and any NOTICE to be distributed with the binary. Its jars each carry `META-INF/LICENSE.md`, and the Android packaging step is configured to keep one copy rather than drop both — dropping them would be the easy accident, since the merge error that forces the decision reads like a build annoyance rather than a licensing one.

**The font is a separate obligation from the datasets**, and easy to overlook because it is not part of the dictionary. The SIL Open Font License permits bundling and redistribution, but requires the copyright notice and licence to travel with the font — so the full OFL text ships in the app alongside this notice, not merely a link. Note the copyright names **Adobe**, not Google: Noto Sans JP derives from Source Han Sans.

Tatoeba is credited even though we ingest no Tatoeba file directly — the example sentences in `JMdict_e_examp` cite Tatoeba sentence ids, so the sentences are theirs (D-51).

## Versions in the current build

Read from `sources.lock.json`; the same values are stored in the database's `meta.source_versions`, so a shipped build can always report what produced it.

| Dataset | Version |
|---|---|
| JMdict (`JMdict_e_examp`) | generated 2026-08-06 |
| KANJIDIC2 | generated 2026-08-06 |
| KanjiVG | release `r20250816` |
| JmdictFurigana | release `2.3.1+2026-07-25` |

Three of these are published at fixed URLs with no version history (D-41), which is why the generation date from each file's own header is the identifier rather than a download date.

## When the datasets are refreshed

Refreshes happen at defined events (D-41). Update the version table above in the same change, since the attribution screen names the versions actually shipped.
