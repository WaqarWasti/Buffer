# Buffer

A pause before the apps you want to use less. Open a blocked app and a buffer screen appears first:
a breathing prompt, the number of times you tried to open it in the last 24 hours, and a choice.
The big button walks away. Continuing takes a deliberate press-and-hold.

- Pick any number of apps in the dashboard, each with its own buffer length (or the default).
- No internet permission, no analytics, no backups. Everything stays on the phone.
- Detection uses an Accessibility Service that only reads which app is in front, never screen content.

See `PLAN.md` for the design.

## Build and install
1. Push to GitHub; the `Build APK` workflow produces `buffer-apk` (Actions tab > latest run > Artifacts).
2. Optional but recommended, so updates install over the old version: create a keystore, then add repo secrets
   `KEYSTORE_B64` (base64 of the .jks), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Without them the APK is
   signed with a throwaway debug key and you must uninstall before each update.
3. Copy the APK to the phone and install it (allow "install unknown apps" for your file manager).
4. Open Buffer and follow "Finish setup": enable the accessibility service (on Android 13+ first open
   Settings > Apps > Buffer > ⋮ > Allow restricted settings), allow display over other apps, set battery to unrestricted.
5. OnePlus (OxygenOS): Settings > Apps > Buffer > Battery usage > Allow background activity (Unrestricted),
   turn on "Allow auto launch", and lock Buffer in the Recents screen so it isn't cleared.

Local build: `./gradlew assembleDebug` with the Android SDK (platform 35) installed.
