---
layout: single
title: "NAV Job Vacancy Feed"
toc: true
---

## Introduction

This API provides a feed of job vacancies in Norway available on [Arbeidsplassen.no](https://arbeidsplassen.nav.no) on
behalf of the Norwegian Labour and Welfare Administration.

The feed contains all job vacancies that have been posted at [Arbeidsplassen.no](https://arbeidsplassen.nav.no), and
details about currently active job vacancies.

Each change to an ad will generate a new entry in the feed, but when querying for the details of a vacancy you will only
receive the newest data.

> Note: If an ad is actively stopped (i.e. not simply inactive because of expiry), it will be updated by masking or
> removing certain fields including title, employer, business and contact information.

Consumers are responsible for keeping their data up to date by subscribing to the feed, with extra consideration for not
exposing contact information for inactive vacancies.

## API specification

Open API specification is available in both [SwaggerUI](https://pam-stilling-feed.intern.dev.nav.no/swagger)
and [Redoc](https://pam-stilling-feed.intern.dev.nav.no/redoc).

## Who can use this service?

Anyone can use this API free of charge, but by doing so you agree to
the [terms of use](https://arbeidsplassen.nav.no/vilkar-api). However, API is not publicly open and requires
authentication using a signed JWT token.

There are two types of tokens, public and private. The public token may be revoked at any time and replaced by another
one. If you require a more stable token please see information about registering as a consumer below.

The current public token is

```
{% if site.public_api_token %}
{{site.public_api_token}}
{% else %}
placeholder
{% endif %}
```

### Registering for a private token

In order to receive a private token, you need to be registered as a consumer.
Please provide us with the following information by email
to [nav.team.arbeidsplassen@nav.no](mailto:nav.team.arbeidsplassen@nav.no):

* Identifier (Company name etc.)
* Contact email
* Contact phone number
* Contact person

### Authorization request example

The API is not publicly open, all requests need to be authenticated using the HTTP bearer authorization header.

Example:

```
GET https://arbeidsplassen.nav.no/stillinger-feed/api/v1/feed
Accept: application/json
Authorization: Bearer <your secret key>
```

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
        "description": "string",
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

## Contact & Questions

If you have questions or suggestions feel free to report it as
an [issue](https://github.com/navikt/pam-stilling-feed/issues) on GitHub, or contact the team
at [nav.team.arbeidsplassen@nav.no](mailto:nav.team.arbeidsplassen@nav.no).