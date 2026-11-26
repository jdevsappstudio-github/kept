# Kept privacy policy

Last updated: 26 November 2026

Kept saves screenshots, links and shared posts on your phone and makes them searchable. The same policy is at
https://jdevsappstudio.com/kept/privacy-policy.html. This page says what
Kept keeps, what leaves your phone, and when.

## What stays on your phone

- Your saves: the copied images, thumbnails, links, shared text, and the cards Kept makes from them (titles,
  tags, items, notes).
- Your collections, shelves, recent searches, the Ask chat, and the "What was sent" log.
- Your settings.

Kept has no account and no Kept server. Nothing above is uploaded or backed up by Kept.

## What leaves your phone, and when

Kept uses Google's Gemini models through **Firebase AI Logic**. Data goes to Google only in these cases:

| When | What is sent |
|---|---|
| A save is read in the cloud. This happens only with **Cloud help** on. On a phone without on-device AI (Gemini Nano), Cloud help covers reading saves, and Kept asks you first. | The save's image (scaled down) and any text or link that came with it |
| You ask a question in **Ask** (Cloud help on) | Your question and the text of the saves that best match it. Never images. |
| You tap **Fill in with cloud** on a link | The link, so Gemini can read that page |

On a phone with Gemini Nano, saves are read on the phone and nothing is sent.

Every request is listed in **Settings > Privacy and AI > What was sent**, with the exact text, for 30 days.

Kept uses the paid tier of the Gemini Developer API. Under its terms, Google processes what is sent only to answer
the request and does not use it to improve its products. See the [Gemini API terms](https://ai.google.dev/gemini-api/terms).

### Other Google services

- **Firebase App Check** with Play Integrity confirms that requests come from the real Kept app.
- **Firebase Remote Config** fetches settings such as which model to use and daily limits. It sends your country,
  language, time zone and Android version, as every Remote Config app does.

Both use a Firebase installation ID. Neither receives your saves.

## What Kept does not do

- No ads, no analytics, no crash reporting, no tracking, no selling of data.
- No access to your photos beyond the images you pick or share with Kept.

## Notifications

Kept posts one optional notification about saves waiting to be read. Turn it off in **Settings > Reading updates**.

## Deleting your data

**Settings > Privacy and AI > Delete everything** removes every save, image, collection, the Ask chat and the
sent log from your phone. Uninstalling Kept removes everything as well.

## Children

Kept is meant for adults (18 and over) and is not directed at children.

## Changes

Changes to this policy are made in this file. Its history is public in this repository.

## Contact

jdevsappstudio@gmail.com
