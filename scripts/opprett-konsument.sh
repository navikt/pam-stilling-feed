#!/bin/bash
set -e

for i in "$@"
do
    case $i in
        --env=*|-e=*)	ENV=${i#*=} ;;
        *)
    esac
done

if [ -z "$ENV" ]; then
    ENV=prod
fi

if [ $ENV == "prod" ]; then
  TOKEN_PATH=$PAM_STILLING_FEED_PROD_KEY
  URL="https://pam-stilling-feed.intern.nav.no/internal/api/newConsumer"
elif [ $ENV == "dev" ]; then
  TOKEN_PATH=$PAM_STILLING_FEED_DEV_KEY
  URL="https://pam-stilling-feed.intern.dev.nav.no/internal/api/newConsumer"
else
  echo "Miljø $ENV er ikke støttet"
  exit 1
fi

if [[ $ENV == "prod" && -z $PAM_STILLING_FEED_PROD_KEY ]]
  then
    echo "PAM_STILLING_FEED_PROD_KEY er ikke definert!"
    exit 1
fi

if [[ $ENV == "dev" && -z $PAM_STILLING_FEED_DEV_KEY ]]
  then
    echo "PAM_STILLING_FEED_DEV_KEY er ikke definert!"
    exit 1
fi

echo "Registrerer ny konsument av pam-stilling-feed i $env, fyll inn informasjon:"
read -p " identifikator: " identifikator
read -p " email: " email
read -p " telefon: " telefon
read -p " kontaktperson: " kontaktperson

json='{ "identifikator": "'$identifikator'", "email": "'$email'", "telefon": "'$telefon'", "kontaktperson": "'$kontaktperson'" }'
echo -e "\nDette vil lage en ny konsument i $env med følgende JSON:\n $json \n"
read -p "Er du sikker (Y/y for å fortsette)? " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]
then
  curl -POST -H "Authorization: Bearer `cat $TOKEN_PATH`" -H "Content-Type: application/json" -d "$json" "$URL"
else
  echo "Avbryter, lager ikke ny konsument"
fi
