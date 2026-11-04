# Pub Tracker

A local-first Android app for exploring pubs and bars by town, keeping a visit journal, and tracking challenge progress.

## Current features

- Create multiple challenges with a fixed centre, adjustable radius, and pub/bar selection.
- Discover places from OpenStreetMap (OSM), with a preview before creating or resizing a challenge.
- View challenge places on a map or in a list; filter visited, remaining, closed, excluded, pubs and bars.
- Search a challenge by pub name or street.
- Add pubs manually, edit details, and exclude a pub from an individual challenge.
- Record repeatable check-ins with a date, free-text drinks, rating, notes and photos.
- Keep pub and visit data on the device; no app account or app-operated server is required.

## Not implemented yet

Automatic weekly OSM refreshes and notifications, offline map downloads, drink/note search, manual backup export and restore, badges, accounts and social features are planned but not included in this test build.

Android system backup is enabled, but photos are excluded. This is not a substitute for a complete export-and-restore backup.

## Build and test

Requirements: Android Studio, Android SDK Platform 37, and the JDK bundled with Android Studio.

From the project root:

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Install it on a connected device with Android Studio's Run button or `adb install -r`.

## Test APK

Test APKs are published on the [GitHub Releases page](https://github.com/richstr/pub-tracker/releases). Download the APK asset on an Android device and install it. Android may ask you to allow installs from the app used to open the downloaded file. These debug builds are for testing, not Play Store releases.

## Data and privacy

Challenges, pub records, check-ins, notes and ratings are stored in the app's private on-device SQLite database. Photos are kept as app-private files. The app does not upload journal data to an app server.

Creating a challenge or previewing a radius change sends the selected centre and radius to OSM's Overpass service to look up places. Map viewing also requests tiles from OpenFreeMap. See the [OpenStreetMap copyright and licence](https://www.openstreetmap.org/copyright).

## Technology and data sources

- Kotlin and Jetpack Compose
- Room over Android SQLite
- MapLibre Native with OpenFreeMap map tiles
- OpenStreetMap pub/bar data via Overpass API and town search via Nominatim

OSM pub and bar coverage and details vary by area. OSM data is community-maintained and licensed under the ODbL; the app displays attribution in context.
