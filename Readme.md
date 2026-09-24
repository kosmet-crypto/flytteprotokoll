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

Appen ser etter ny versjon høyst to ganger i døgnet og tilbyr nedlasting (eller: Innstillinger → *Se etter oppdatering*).

Appen og nettleseren lagrer hver for seg. Bruk **Eksporter / Importer** under Innstillinger for å flytte protokoller. **Bare innstillinger** lager en liten fil med navn, enhet, adresser, logo og tekster (uten PIN) som kan gis til en kollega.
Android-prosjektet ligger i `android/` (en liten WebView-innpakning). Bygg lokalt: `cd android && ./gradlew assembleRelease`.
Signeringsnøkkelen ligger i repoet så alle bygg kan oppdatere hverandre; bytt til en hemmelig nøkkel før eventuell publisering i Google Play.
