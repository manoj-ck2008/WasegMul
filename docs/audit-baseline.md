# Baseline Build Audit - 2026-07-16

## Build Command
```
./gradlew clean assembleDebug
```

## Build Result
**SUCCESS** - Build completed in 58s

## Warnings (non-fatal)

### Kotlin/Native iOS targets disabled
```
w: The following Kotlin/Native targets cannot be built on this machine and are disabled:
iosArm64, iosSimulatorArm64
```
This is expected on non-macOS machines.

### Kotlin Deprecation Warnings
1. **FeedbackSection.kt:148** - `'val Icons.Filled.ArrowBack: ImageVector' is deprecated. Use the AutoMirrored version at Icons.AutoMirrored.Filled.ArrowBack.`

2. **SettingsViewModel.kt:53** - `Unchecked cast of 'com.agrelius.wasegmul.ui.settings.SettingsViewModel' to 'T'.`

3. **Theme.kt:64** - `'var statusBarColor: Int' is deprecated. Deprecated in Java.`

4. **Theme.kt:65** - `'var navigationBarColor: Int' is deprecated. Deprecated in Java.`

## Errors
None - build successful.

## Configuration Notes
- AGP: 8.7.3
- Kotlin: 2.1.0
- Compose BOM: 2024.11.00
- compileSdk/targetSdk: 35
- minSdk: 29
- JVM target: 11

## Pre-existing Issues (from initial audit)
1. `ModelInitException` import was incorrect in `ClassificationViewModel.kt` - fixed in this build
2. iOS targets cannot be built on Windows (expected)
3. No unit tests beyond placeholder
4. No Detekt/ktlint configured
5. `media3-exoplayer` dependency appears unused
6. `SoundManager` stubs not implemented
7. Theme live-reload not working (requires app restart)
8. No ProGuard rules for release build
9. `android:pageSizeCompat` attribute was in manifest but removed (not in final manifest)

## Files Modified During Baseline
- `app/src/main/java/com/agrelius/wasegmul/ui/classify/ClassificationViewModel.kt` - Fixed import for `ModelInitException`