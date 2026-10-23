# Range

Range is a set of small Android apps that share one design and one UI library. They are free software with no ads or tracking. They use no Google services, so every app can ship on F-Droid.

| App | What it does | Status |
|---|---|---|
| Range Tasks | One to-do list with repeating tasks, reminders and a week view | In development |
| Range Lift | A workout log in the style of StrengthLog: log sets, see past workouts and personal bests | In development |

Each app takes its colours from your wallpaper with Material You and has light, dark and pure black themes. The apps come in English and Swedish and need Android 12 or newer.

## Repository layout

Every app lives in this repository and uses the same UI library and build setup.

| Path | Contents |
|---|---|
| `apps/tasks/` | Range Tasks |
| `apps/lift/` | Range Lift |
| `range-ui/` | The shared UI: theme, components, icons, and the Settings, About and licence screens |
| `build-logic/` | Gradle convention plugins with the build settings every app shares |
| `gradle/libs.versions.toml` | Dependency versions |
| `scripts/icons.py` | Regenerates the Material Symbols icons listed in `range-ui/icons.txt` |

The apps use Kotlin, Jetpack Compose with Material 3, Navigation 3 and Room.

## Building

You need the Android SDK. Point `local.properties` at it with `sdk.dir=...`. Gradle runs on any JDK 17 or newer and downloads JDK 21 to compile the apps if you don't have it.

Each app builds the same way; swap `tasks` for `lift`.

```sh
./gradlew :apps:tasks:assembleDebug         # debug APK
./gradlew :apps:tasks:assembleRelease       # minified release APK
./gradlew :apps:tasks:installDebug          # install on a connected phone
./gradlew :apps:tasks:testDebugUnitTest     # unit and screenshot tests
./gradlew :apps:tasks:recordRoborazziDebug  # re-render apps/tasks/screenshots
```

## Releases

Each app has its own version number. An app's release tags start with its name, such as `tasks-v1.0.0`.

## Licence

GPL-3.0-or-later. See [LICENSE](LICENSE).
