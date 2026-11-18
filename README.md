# DepthLock 3D — Android live wallpaper with automatic person cut-out

DepthLock 3D creates an iPhone-inspired depth effect by drawing a photo, a live clock/date, and then a transparent person layer over the clock.

## Features
- Pick one photo from the phone.
- **Automatically remove the photo background from the person** using Google ML Kit Selfie Segmentation on-device.
- Save the transparent person PNG locally in the app's private storage.
- Draw the background first, the live clock/date second, and the person cut-out last.
- Open Android's live-wallpaper picker to preview/apply the wallpaper.
- Clock refreshes at minute boundaries.

## Automatic cut-out notes
- The segmentation model is bundled in the app; no separate API key is needed. Processing is performed on the device.
- The ML Kit Selfie Segmentation API is currently beta. It works best with a sharp photo where the person is clearly visible and not heavily blended into the background.
- The app downsizes the input to a maximum 1600 px long edge before segmentation to limit memory use. The generated transparent PNG uses that processed resolution.
- Segmentation may not perfectly preserve fine hair, transparent objects, or complicated edges. Inspect the result on the wallpaper; try a clearer photo if needed.

## Important Android limitation
Android/ColorOS decides whether a third-party live wallpaper can be applied to the lock screen, home screen, or both. The app cannot force ColorOS to replace its native lock-screen UI. The clock is drawn by the live wallpaper and may be hidden or covered by the system lock-screen clock depending on the device.

## Build locally
Requires JDK 21, Android SDK platform 35, build-tools 35.0.0, and Gradle 8.9.

```bash
gradle --no-daemon :app:assembleDebug
```

The debug APK is created at `app/build/outputs/apk/debug/app-debug.apk`.

## Build on GitHub Actions
The included `.github/workflows/android-build.yml` builds the debug APK on push, pull request, or manual workflow dispatch, then uploads it as the `DepthLock3D-debug-apk` workflow artifact.
