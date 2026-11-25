# Kept

Screenshots and saves, read into searchable cards on your phone.

Kept reads each screenshot or shared link into a card (title, summary, type, tags, items, place/date/link) with
**Gemini Nano on the device** through **Firebase AI Logic**, so private screenshots never leave the phone. Phones
without Nano can opt in to cloud reading. Ask questions about your saves with Gemini in the cloud.

Built for the #BuildHybridAI hackathon (Nov 2026). Work in progress: setup steps, the hybrid map and the
architecture diagram land here before submission.

## Build
- Android Studio (AGP 9.2), JDK 21.
- Add your own `app/google-services.json` from a Firebase project with AI Logic enabled. Without it the app still
  builds and says that AI is not set up.

## Licence
Apache-2.0, see [LICENSE](LICENSE).
