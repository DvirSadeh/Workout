# Workout

Android app for dumbbell and bodyweight sessions.

Opening the app does not create a workout. On the home screen, describe what you need and tap Generate workout. After a finished session, that same box plans the next day.

## Install

Connect the phone with USB debugging on, then:

```bash
./gradlew assembleDebug
```

The debug build installs on each connected phone. Emulators are skipped. The package id is `workout.app`.
