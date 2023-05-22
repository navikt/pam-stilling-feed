# pam-stilling-feed
Ny feed for stillingsannonser på Arbeidsplassen.nav.no

## Opprettelse av nye konsumenter / tokens
Vi trenger følgende informasjon for å registrere en ny konsument:
* Identifikator (Firmanavn, eller annet identifiserende navn)
* Epost
* Telefonnummer
* Kontaktperson

Det finne script for å opprette nye konsumenter og token for en gitt konsument i `./scripts`. Disse går by default mot prod-miljøet. 

For å bruke disse scriptene må miljøvariablene `PAM_STILLING_FEED_PROD_KEY` og `PAM_STILLING_FEED_DEV_KEY` være satt.  

Variablene må peke på en fil som inneholder henholdsvis prod og dev nøkkel. Disse finner du i Google Secret Manager -> `pam-stilling-feed-admin-keys`.

```bash
export PAM_STILLING_FEED_PROD_KEY=<path_prod_key>
export PAM_STILLING_FEED_DEV_KEY=<path_dev_key>
```

### Ny konsument
Kjør `./scripts/opprett-konsument.sh`, og oppgi informasjonen som etterspørres.

```bash
$ ./scripts/opprett-konsument.sh

Registrerer ny konsument av pam-stilling-feed i <dev|prod>, fyll inn informasjon:
 identifikator: <identifikator>
 email: <email>
 telefon: <telefon>
 kontaktperson: <kontaktperson>

Dette vil lage en ny konsument i <dev|prod> med følgende JSON-fil:
 { "identifikator": "<identifikator>", "email": "<email>", "telefon": "<telefon>", "kontaktperson": "<kontaktperson>" } 

Er du sikker (Y/y for å fortsette)? y
```

Du får tilbake informasjon om konsumenten, noter IDen til neste steg.

### Nytt token
Kjør `./scripts/generer-token.sh`, og oppgi ID til konsumenten du vil lage token til når du blir spurt. Du kan også sette en utløpsdato for tokenet når du blir spurt om dette.
> NB! Når du genererer et nytt token for en konsument vil tidligere tokens for denne konsumenten invalideres.

```bash
$ ./scripts/generer-token.sh

Genererer nytt token for konsument i dev
 Konsument ID: <konsumentId>
 Utløpsdato (ISO formattert dato) - La være tom for ingen utløpsdato: <expires>

Dette vil generere nytt token for konsument <konsumentId> i <dev|prod> og invalidere tidligere utstedte tokens
Sender følgende JSON:
 { "konsumentId": "<konsumentId>", "expires": "<expires>" } 

Er du sikker (Y/y for å fortsette)?
```

Du får tilbake en authorization header, denne skal gis til konsumenten.
 
### Opprette konsumenter / tokens i dev
For å opprette konsumenter / tokens i dev brukes samme fremgangsmetode, men du må sende med flagget `--env=dev`.

```bash
$ ./scripts/opprett-konsument.sh --env=dev
$ ./scripts/generer-token.sh --env=dev
```
