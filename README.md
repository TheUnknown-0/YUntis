# UntisX

**by LSX Studios**

An open-source timetable viewer for the Untis school system - built natively with Kotlin and Jetpack Compose.

---

## Features

- **Timetable** - clear weekly view with all lessons, times, rooms and teachers
- **School events** - displayed directly in the timetable and tappable for full details
- **Events overview** - upcoming school events, announcements and holidays with countdown in one place
- **Messages** - read messages sent by your school
- **Exams** - exams and tests are marked on the timetable, with a list of upcoming ones
- **Lesson details** - lesson information, substitutions, topic and class register notes
- **Cancelled lessons** - showing up grayed out
- **Subject colors** - every subject color can be customized
- **Custom subject names** - rename subjects with short and long names
- **Teachers & rooms** - the whole school at a glance
- **Statistics** - lessons total, held, cancelled and changed, cancellation rate and lessons by subject per school year
- **Multi-account** - add several accounts and switch between them
- **Themes & appearance** - dynamic colors, theme presets, custom accent color and animations
- **English & German** - choose the app language independently of the system
- **Offline** - your timetable is cached locally and opens instantly
- **Notifications** - alerts for cancellations and room/teacher changes, automatically silent during lessons, plus an overview of the upcoming school day
- **In-app updates** - new versions install straight from the app
- **Lightweight** - ~3 MB, no ads, no tracking

UntisX is in early development - expect bugs and incomplete features. If you find one, please [report it as an issue](https://codeberg.org/Losterixx/UntisX/issues).

---

## Screenshots

| | |
|---|---|
| ![Timetable](screenshots/timetable.png) | ![Drawer](screenshots/drawer.png) |
| ![Subject colors](screenshots/subject_colors.png) | ![Restricted days](screenshots/restricted.png) |

---

## Privacy

Your username and password are sent exclusively to your school's Untis server and nowhere else. On your device they are stored encrypted with the Android Keystore. There is no ads, analytics or tracking.

The only external contact besides your Untis server is this project's releases for updates.

If you enable notifications, UntisX periodically checks your timetable on your school's Untis server in the background (you can choose the interval). The data is compared locally on your device to detect changes - nothing is sent anywhere else.

---

## Unofficial client

UntisX is not affiliated with or endorsed by Untis. It talks to your school's Untis server through partially undocumented interfaces (the same ones other community clients use). These can change or be restricted at any time - if that happens, parts of the app may stop working until they are adapted. The app only reads data and never changes anything on the server.

---

## Requirements

**App**

- Android 8.0 (API 26) or newer, on a phone or emulator
- A student account for your school's Untis server (teacher accounts are not supported)

**Building**

- Android Studio (latest stable) - easiest; it brings its own JDK and installs the Android SDK for you
- or from the command line: JDK 21 (recommended; Android Studio bundles a compatible one), Android SDK Platform 37, Build-Tools and platform-tools

---

## Building

The easiest way is to open the project in Android Studio and press Run - it syncs Gradle and downloads the required SDK components automatically.

From the command line you need JDK 21 and the Android SDK (Platform 37, Build-Tools and platform-tools; set `ANDROID_HOME` or add `sdk.dir` to `local.properties`):

```bash
git clone https://codeberg.org/Losterixx/UntisX
cd UntisX
./gradlew assembleDebug
```

The APK ends up in `app/build/outputs/apk/debug/app-debug.apk` and can be installed on a connected device with `adb install -r <apk>`.

Release builds are signed with the project keystore: put `untisx.keystore` in the project root and pass the password to Gradle:

```bash
./gradlew assembleRelease -PkeystorePassword=yourpassword
```

Without the keystore, the release build asks for confirmation and falls back to the debug key.

---

## Note on AI

This project contains AI generated code. All code has been carefully reviewed and manually tested before release.

---

## Contributing

Issues and feature requests are welcome on [Codeberg](https://codeberg.org/Losterixx/UntisX/issues). Pull requests are welcome too - but please be patient: this is a hobby project and I'm still new to maintaining one, so it may take a while until I get to them.

---

## License

Open source - [GPL-3.0](LICENSE).

If you use or build upon this project, a credit to **LSX Studios** and a link to
<https://codeberg.org/Losterixx/UntisX> is much appreciated <3

---

Built with Kotlin + Jetpack Compose.
