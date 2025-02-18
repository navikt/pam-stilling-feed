---
layout: single
title: "NAV Job Vacancy Feed"
toc: true
---

## Introduction

This API provides a feed of job vacancies in Norway. NAV’s Job Database contains a majority of all publicly 
advertised job vacancies in Norway.

This API provides a feed with all the job vacancies we have the opportunity to share. 
The job vacancies are either directly registered at NAV or obtained through third parties/ATS systems. 
(vacancies from Finn.no are not included in the API)

Each change to an ad will generate a new entry in the feed, and the latest entry will contain the current state of the ad.
When querying for the details of a vacancy you will receive the newest data.

> Note: If an ad is actively stopped (i.e. not simply inactive because of expiry), it will be updated by masking or
> removing certain fields including title, employer, business and contact information.

Consumers are responsible for keeping their data up to date by subscribing to the feed, with extra consideration for not
exposing contact information for inactive vacancies.

## API specification

Open API specification is available in both [SwaggerUI](https://pam-stilling-feed.ekstern.dev.nav.no/swagger)
and [Redoc](https://pam-stilling-feed.ekstern.dev.nav.no/redoc).

## Who can use this service?

Anyone can use this API free of charge, but by doing so you agree to
the [terms of use](https://arbeidsplassen.nav.no/vilkar-api). However, API requires
authentication using a signed JWT token.

You can find a public token that can be used for experiments here: https://pam-stilling-feed.nav.no/api/publicToken
This token will rotate at irregular intervals.

In order to receive a private token, you need to be registered as a consumer.
Please provide us with a written confirmation that you agree to the terms of use as described above, as well
the following information by email
to [nav.team.arbeidsplassen@nav.no](mailto:nav.team.arbeidsplassen@nav.no):

* Identifier (Company name etc.)
* Contact email
* Contact phone number
* Contact person

### Authorization request example

The API is not publicly open, all requests need to be authenticated using the HTTP bearer authorization header.

Example:

```
GET https://pam-stilling-feed.nav.no/api/v1/feed
Accept: application/json
Authorization: Bearer <your secret key>
```

## Frequently asked questions (FAQ)

1. Why do I see ads listed as inactive in the feed?
   - The feed contains all ads and their state registered at NAV since ca. 2019, ads that are fulfilled or expired are
     marked as inactive.
2. I want to see all active ads in the feed, how do I do that?
   - The API does not only show active ads, it is a continuous feed of all ads. If you want to filter on active ads,
     you will need to implement this on your side, once an ad is marked as inactive you will need to remove it from your service as stated in the [terms of use](https://arbeidsplassen.nav.no/vilkar-api).
3. Can I filter on ads containing a specific employer/company/municipality/keyword?
   - If you want to filter on specific criteria, you will need to implement this on your side. If you want to fetch ads after a given date, you can use the `If-Modified-Since` header.

## Using the feed

Please see the [OpenApi specification](#api-specification) for details about the endpoints and response data structure.

Suggested usage:

1. Start by fetching a page you want to start with. You can fetch the first, the last, or use the
   header `If-Modified-Since` to specify a start time.
2. The response will include the field `next_url` which can be used to fetch the next page.
3. A page contains a number of items consisting of top-level details about a vacancy, and an `url` field that can be
   queried to get the details of the given vacancy.
4. When the end of the feed has been reached, the page will contain `next_url` and `next_id` = `null`. The consumer
   should poll the current site until these point to a new page. As described in the note below, it is recommended to
   use the `ETag` and `Last-Modified` request headers.

> Note: A page response will contain the headers `ETag` and `Last-Modified`.
>
> These can be supplied to a request for a given page with the `ETag` and `Last-Modified` request-headers, and will be
> used to check if the contents of the page has changed since the last time.
>
> It is recommended to use these when re-requesting for a given page, as they will return 304 if contents have not
> changed since last time, and thus minimize data-transfer. This will also potentially make it easier for the consumer
> application to know how to react to the response.

### Response objects

Feed page

```json
{
  "version": "string - Version of the feed",
  "title": "string - Feed title",
  "home_page_url": "string",
  "feed_url": "string - relative url of the returned feed-page",
  "description": "string",
  "next_url": "string - relative url to the next page in the feed",
  "id": "string - ID of the current page",
  "next_id": "string - ID of next page",
  "items": [
    FeedItem
  ]
}
```

Feed item

```json
{
  "id": "string - Id of the entry",
  "url": "string - URL for fetching the details of the given vacancy",
  "title": "string - Job vacancy title",
  "content_text": "string",
  "date_modified": "2023-05-25T07:47:08.935Z - ISO formatted time for last change to the ad",
  "_feed_entry": {
    "uuid": "string - UUID for the ad",
    "status": "string - Status of the given ad, can be ACTIVE or INACTIVE",
    "title": "string - Title of the ad",
    "businessName": "string - Name of the business",
    "municipal": "string - Municipal of the work place",
    "sistEndret": "2023-05-25T07:47:08.935Z - ISO formatted time for last change to the ad"
  }
}
```

Feed entry

```json
{
  "uuid": "string",
  "json": {
    "uuid": "string",
    "published": "2023-05-25T08:06:08.355Z",
    "expires": "2023-05-25T08:06:08.355Z",
    "updated": "2023-05-25T08:06:08.355Z",
    "workLocations": [
      {
        "country": "string",
        "address": "string",
        "city": "string",
        "postalCode": "string",
        "county": "string",
        "municipal": "string"
      }
    ],
     "contactList": [
        {
           "name": "string",
           "email": "string",
           "phone": "string",
           "role": "string",
           "title": "string"
        }
     ],
    "title": "string",
    "description": "string",
    "sourceurl": "string",
    "source": "string",
    "applicationUrl": "string",
    "applicationDue": "string",
    "occupationCategories": [
      {
        "level1": "string",
        "level2": "string"
      }
    ],
    "categoryList": [
      {
        "categoryType": "string",
        "code": "string",
        "name": "string",
        "score": 0
      }
    ],
    "jobtitle": "string",
    "link": "string",
    "employer": {
      "name": "string",
      "orgnr": "string",
      "description": "string",
      "homepage": "string"
    },
    "engagementtype": "string",
    "extent": "string",
    "starttime": "string",
    "positioncount": "string",
    "sector": "string"
  },
  "sistEndret": "2023-05-25T08:06:08.355Z",
  "status": "string"
}
```

## Migrating from the old public-feed
If you are migrating from the old public-feed, you will find that you must implement more filtering on your side.
On the other hand: this feed will make it easier for you to comply with our terms.

This is an example in pseudo-code of how to download all active ads in the Bergen municipality.
Note that an ad can never be active for more than 6 months, so it's no point in downloading older ads. Assuming that
today is 2024-12-01, it's no point in downloading ads before 2024-06-01.
Note also that the time format uses RFC-1193 format.

```
lastModified = Sat, 1 Jun 2024 00:00:00 +0200
etag = null
feedPage = "/api/v1/feed"

while (true) {
   response = GET https://pam-stilling-feed.nav.no/$feedPage
   Accept: application/json
   Authorization: Bearer <your secret key>
   If-Modified-Since: $lastModified
   If-None-Match: $etag (not mandatory: only provide this if etag is not null, i.e. don't provide etag in first call)   
   
   if (response.status == 200) {
     lastModifed = response.header.last-modified
     etag = response.header.etag
     feedPage = response.next_url
     
     foreach (adHeader in response.bodyAsJson.items) {
       if (adHeader._feed_entry.status != "ACTIVE")
           # Delete inactive ads even if their municipality is wrong
           # Municipality, and other fields that you filter on, might change
           DELETE_AD(adHeader._feed_entry.id)
       } else {
           if (adHeader._feed_entry.municipal == "BERGEN") {
               adResponse = GET https://pam-stilling-feed.nav.no/${adHeader.url}
               Accept: application/json
               Authorization: Bearer <your secret key>
   
               if (adResponse.status == 200)
                   SAVE_AD(adResponse.bodyAsJson.ad_content)                
       }        
   } else {
     # We have either downloaded all ads, or an error occured
     # In any case, sleep for 2 minutes before trying to fetch ad updates     
     sleep(120 seconds)
   }
}     
```

## Contact & Questions

If you have questions or suggestions feel free to report it as
an [issue](https://github.com/navikt/pam-stilling-feed/issues) on GitHub, or contact the team
at [nav.team.arbeidsplassen@nav.no](mailto:nav.team.arbeidsplassen@nav.no).
