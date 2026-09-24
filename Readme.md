# Flytteprotokoll

Inn- og utflyttingsprotokoll for boligforvaltning: rom for rom med OK/FEIL, bilder, nøkler,
erstatningskrav, signatur på skjerm og ferdig PDF.

Alt lagres **bare på enheten** (IndexedDB), ingenting sendes til noen server.

## Funksjoner
* Protokoller lagres automatisk mens du jobber, og kan åpnes igjen senere.
* **Start utflytting** fra en innflytting: adresse, leietaker, nøkler og rom kopieres, og hvert punkt viser hva som ble registrert ved innflytting.
* Egne sjekklister per romtype (entré, kjøkken, bad, soverom, vaskerom, balkong …), brannsikring og utvask.
* Hvitevarer, egne punkter, flere bilder per punkt (tas med kamera eller hentes fra galleri, skaleres ned automatisk).
* PDF med logo, sammendrag, nøkkeltabell, avvik, signaturer og bildevedlegg. **Lagre** eller **Del** (e-post, Teams, OneDrive …).
* Innstillinger: navn, tittel, enhet, faste adresser, logo, valgfri PIN, egen erklæringstekst, sikkerhetskopi (eksport/import).
* **Resten OK**: sett alle punkter uten status til OK med ett trykk (per rom eller for hele protokollen).
* Oppstartsveiviser første gang: navn, enhet, faste adresser, logo og PIN, eller hent alt fra en sikkerhetskopi.
* **Signert og låst**: når PDF-en lages, låses protokollen. Feil rettes med en *korrigert kopi* som viser hvilken protokoll den erstatter.
* **Prisliste**: egne priser per skade og per manglende nøkkel fylles inn automatisk ved erstatningskrav.
* **Del PDF** med ferdig e-postemne og -tekst (redigeres under Innstillinger).
* **Frist for krav** (14 dager) vises på utflyttinger i listen og i PDF-en.
* **Per adresse**: listen kan grupperes per adresse og leilighet, og søket finner adresse, leil.nr, navn og dato.
* Avvik som allerede var registrert ved innflytting kan merkes **Kjent fra innflytting – ikke krav**.
* Virker uten nett.

## Nettversjon
Slå på GitHub Pages (Settings → Pages → *Deploy from a branch* → standardgrenen, `/ (root)`).
Åpne lenken på telefonen og velg *Installer app* / *Legg til på startskjermen*.

Når `index.html`, `app.css`, `vendor/` eller ikonene endres: øk `VERSION` i `sw.js` så installerte kopier oppdateres.
Brukes nye Tailwind-klasser i `index.html`, bygg CSS på nytt: `npx tailwindcss@3 -i src.css -o app.css --minify`.

## Android-app (APK)
Hver endring på standardgrenen bygger en ny APK med GitHub Actions og publiserer den som release.
Nyeste versjon: https://github.com/kosmet-crypto/flytteprotokoll/releases/latest/download/flytteprotokoll.apk

1. Åpne lenken på Android-telefonen og last ned `flytteprotokoll.apk`.
2. Åpne filen. Android spør om å tillate installasjon fra nettleseren eller filbehandleren; tillat én gang.
3. Installer. Nyere APK-er installeres over den gamle og beholder protokollene.

**Oppdateringer:** appen sjekker ved oppstart (høyst hver sjette time, eller Innstillinger → *Se etter oppdatering*).
Endringer i selve appen (`index.html`, CSS, ikoner) lastes ned i bakgrunnen og tas i bruk automatisk neste gang listen vises – uten ny APK.
Bare når Android-delen endres må en ny APK installeres; da laster appen den ned selv og åpner installasjonen (ett trykk).
Utviklere: øk tallet i `android/shell-version.txt` når Android-koden endres slik at nettsiden avhenger av det.

Appen og nettleseren lagrer hver for seg. Bruk **Eksporter / Importer** under Innstillinger for å flytte protokoller. **Bare innstillinger** lager en liten fil med navn, enhet, adresser, logo og tekster (uten PIN) som kan gis til en kollega.
Android-prosjektet ligger i `android/` (en liten WebView-innpakning). Bygg lokalt: `cd android && ./gradlew assembleRelease`.
Signeringsnøkkelen ligger i repoet så alle bygg kan oppdatere hverandre; bytt til en hemmelig nøkkel før eventuell publisering i Google Play.
