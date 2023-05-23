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
  URL="https://pam-stilling-feed.intern.nav.no/internal/api/newApiToken"
elif [ $ENV == "dev" ]; then
  TOKEN_PATH=$PAM_STILLING_FEED_DEV_KEY
  URL="https://pam-stilling-feed.intern.dev.nav.no/internal/api/newApiToken"
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

echo "Genererer nytt token for konsument i $ENV"
read -p " Konsument ID: " id
read -p " Utløpsdato (ISO formattert dato) - La være tom for ingen utløpsdato: " exp

if [ -z "$exp" ]; then
  exp=null
else
  exp='"'$exp'"'
fi

json='{ "konsumentId": "'$id'", "expires": '$exp' }'

echo -e "\nDette vil generere nytt token for konsument: $id i $ENV og invalidere tidligere utstedte tokens"
echo -e "Sender følgende JSON:\n $json \n"

read -p "Er du sikker (Y/y for å fortsette)? " -n 1 -r

echo
if [[ $REPLY =~ ^[Yy]$ ]]
then
  curl -POST -H "Authorization: Bearer $(cat "$TOKEN_PATH")" -H "Content-Type: application/json" -d "$json" "$URL"
else
  echo "Avbryter, lager ikke nytt token for konsument: $id"
fi
