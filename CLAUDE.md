# Range

The user never writes code here; Claude does all of it and shows results as screenshots or builds on the phone.

- Screens are built from Material 3 components (top bars, list items, chips, buttons, sheets, dialogs) so the apps feel native. `RangeTheme` sets Space Grotesk and the colours: Material You by default, the app's `RangeAccents` fallback otherwise, light/dark/pure black per the user's Appearance settings. Use `MaterialTheme.colorScheme`/`typography`, never hard-coded colours or fonts.
- Write a custom component only when Material has none (e.g. `RangeCheckbox`, `RangeStepper`), and put it in `range-ui`.
- Each app differs only by its accent (`RangeAccents`) and its screens. Page transitions come from `RangeTransitions`.
- No proprietary dependencies (Google Play Services, Firebase, analytics): the apps must stay F-Droid compatible.
- Timers and reminders use AlarmManager + local notifications, never push.
- New app: add `apps/<name>` with the `range.android.application` plugin; SDK levels and build settings live in `build-logic`.
- After any UI change, run `./gradlew :apps:<app>:recordRoborazziDebug` and look at the PNGs in `apps/<app>/screenshots` before calling it done. Every screen needs a screenshot test.
- Icons are Material Symbols (Rounded) only. Add the symbol's name to `range-ui/icons.txt` and run `python3 -I scripts/icons.py`; never hand-draw icons or add an icon library.
- Room schemas in `apps/<app>/schemas` are committed. Bumping a database version needs a migration, not a destructive fallback: users' tasks live there.
- Every user-facing string exists in English (`values/`) and Swedish (`values-sv/`). Shared strings live in `range-ui` with a `range_` prefix.
- Format dates and times through the app's formatter (e.g. Tasks' `TaskFormat`), never `DateTimeFormatter` directly: it combines the app language with the phone's region.
- A repeating task is one row that moves from day to day; never insert a row per occurrence. A task's times are reminders: progress is a tick count (`doneCount`), never per-time state.
- Finished days are logged in `completions`, one row per finished day, written only by `TaskRepository` alongside the task. It's history for listing what was ticked off on each day, not a task row: never schedule or tick from it.
- Every tick in the list offers Undo via a snackbar; keep that the one way to take back a tick from the list.
- The task list shows one day at a time (`dayView` in Tasks). Future days list every repeat that falls on them as untickable previews, plus undated (Anytime) tasks, which show on every day until done; past days list what was ticked off on them, read-only. Keep that logic in `DayView.kt`, covered by `DayViewTest`.
- Lift: a workout's sets count only once ticked off, and Finish needs every set ticked; ticking an empty set takes the placeholder (last time's set, else the set above). At most one workout is in progress. An exercise's `Measure` (reps/time), `Load` and `perSide` decide its columns, best set (`Exercise.setOrder`) and volume. Removing an exercise archives it, so past workouts keep it. Progress graphs, records and the finish screen's comparisons all pick their number per exercise through `progressMetric`/`recordMetrics` in `Progress.kt`; a first time is never a record. Built-in exercises are rows with a `key`, named in the app's language through `LiftFormat.BUILT_IN_NAMES` unless renamed; add new ones to `BuiltInExercises`, which fills them in on launch without a migration.
- Lift's rest timer lives on the workout row (`restEndsAt`), started when a set is ticked; `RestAlarm` mirrors it to an exact alarm and a self-counting notification so it works with the app closed. Templates keep exercises and set counts only; the numbers come from last time as placeholders.

## Commits

Use Conventional Commits: `type(scope): summary`, lowercase, imperative, no trailing period, under 72 characters. Add a body when the why isn't obvious from the summary.

- Types: `feat` (something users notice), `fix`, `refactor`, `perf`, `test`, `docs`, `build` (Gradle, dependencies, signing), `chore` (anything else).
- Scope is the part of the repo the commit touches: an app's folder name (`tasks`, `lift`), `ui` for `range-ui`, `build` for `build-logic` and `gradle/`, `scripts` for `scripts/`. Leave the scope out for repo-wide changes, e.g. `docs: rewrite README`.
- One commit per scope where practical. A change to `range-ui` made for one app is still `feat(ui)`, with the app's own changes in a separate `feat(tasks)` commit.
- Release tags are per app: `tasks-v1.2.0`.
- Commits are the user's alone: no `Co-Authored-By` or other Claude attribution lines in commit messages or PR descriptions.

Examples: `feat(tasks): add week strip for browsing future days`, `fix(ui): keep checkbox ring visible in pure black`, `build: bump compose bom to 2026.09.00`.
