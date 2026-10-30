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
- **Wear OS companion** - view your timetable and lesson details on a paired watch, including offline access
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

The phone also contacts this project's releases for updates. Watch synchronization uses the Google Play services Data Layer, which may relay data through Google's infrastructure when a direct connection is unavailable.

The watch receives timetable and display data for the active account, not passwords or login sessions. Its local cache is encrypted with Android Keystore. Account changes replace the watch snapshot when the devices synchronize; a disconnected watch can still show its previously received data until then.

If you enable notifications, UntisX periodically checks your timetable on your school's Untis server in the background (you can choose the interval). The data is compared locally on your device to detect changes - nothing is sent anywhere else.

---

## Unofficial client

UntisX is not affiliated with or endorsed by Untis. It talks to your school's Untis server through partially undocumented interfaces (the same ones other community clients use). These can change or be restricted at any time - if that happens, parts of the app may stop working until they are adapted. The app only reads data and never changes anything on the server.

---

## Requirements

**App**

- Android 8.0 (API 26) or newer, on a phone or emulator
- A student account for your school's Untis server (teacher accounts are not supported)

**Optional watch companion**

- Wear OS with Android 13 (API 33) or newer
- A paired phone with Google Play services and the watch companion installed
- Phone and watch APKs must use the same application ID and signing certificate

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
./gradlew :app:assembleDebug
```

The APK ends up in `app/build/outputs/apk/debug/app-debug.apk` and can be installed on a connected device with `adb install -r <apk>`.

Release builds are signed with the project keystore, so you need to use the debug key.

```bash
./gradlew assembleRelease
```

Without the keystore, the release build asks for confirmation and falls back to the debug key.

### Wear OS

The watch APK is a companion, not a standalone Untis client: first sign in and open the timetable on the phone, then open UntisX on the watch. Swipe horizontally between days, use the crown to scroll, and tap a lesson for details. Refresh requests are handled by the phone through WorkManager and may be delayed in the background.

The watch checks freshness when reopened and updates the current day while visible. Refresh feedback distinguishes transferred phone cache, refreshed school data, failed school refreshes and delayed requests. Days without a cached week are shown as not loaded. Phone and watch refreshes share a per-week cache; exam markings also use the phone's saved exam list.

**Tested only with my Google Pixel Watch 2 paired with a Google Pixel 10 Pro** (stock Wear OS on the watch, GrapheneOS with Google Play Services on the phone). Other watches, phones and firmware versions have not been tested; compatibility is not guaranteed.

#### Install or update an APK via ADB

There is no in-app watch installer, updater, APK export or bundled watch APK in the phone app. First installation and every watch update use the same ADB procedure:

1. Install Android SDK **platform-tools** on your computer; `adb` must be on `PATH`. The script needs Bash (Linux/macOS, or an appropriate Bash environment on Windows).
2. Enable developer options on the watch by tapping its build number seven times, then enable ADB debugging. Connect it by USB if supported and confirm the debugging prompt. Alternatively, enable and use **Wireless debugging** in the settings on the watch (computer and watch must be on the same network):

   ```bash
   adb pair WATCH_IP:PAIRING_PORT
   # enter the pairing code shown by the watch
   adb connect WATCH_IP:DEBUGGING_PORT
   adb devices -l
   ```

   Use the actual address and ports shown on the watch. The pairing and debugging ports are different. The watch must appear as `device`, not `unauthorized` or `offline`.

3. Download the **signed `wear-release.apk` from the matching [UntisX release on Codeberg](https://codeberg.org/Losterixx/UntisX/releases)**, not the phone APK or a locally debug-signed build. Use the phone and watch APKs from the same release; they must have the same application ID and signing certificate. Place `wear-release.apk` in the repository root and run:

   ```bash
   ./install_wear.sh wear-release.apk
   ```

   **This command installs the signed Watch APK downloaded from the Codeberg release onto the connected watch.** It does not build an APK or download one automatically. If you saved the release APK elsewhere, pass its actual path instead.

   With multiple watches connected, explicitly select the ADB serial or wireless address printed by `adb devices -l`:

   ```bash
   WATCH_SERIAL=WATCH_IP:DEBUGGING_PORT ./install_wear.sh wear-release.apk
   ```

The script selects only an authorized watch, installs with `adb install -r` and opens UntisX on it. For **updates**, download the signed `wear-release.apk` from the newer Codeberg release, replace your previous downloaded APK and run `./install_wear.sh wear-release.apk` again. Existing app data is retained on a successful compatible update. Signing mismatches and downgrades are rejected by Android; the script never automatically uninstalls anything or grants special installation permissions. You can disable debugging again afterwards; reconnect/re-enable it for the next update.

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
