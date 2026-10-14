# Repository Guidelines

## Project Structure & Module Organization

This is a Kotlin Multiplatform app. `shared/` contains Compose UI and platform-aware Kotlin code; its `src/commonMain` is shared across platforms, with `androidMain` and `iosMain` for platform implementations. Tests are grouped under `shared/src/commonTest`, `androidUnitTest`, and `iosTest`. `Crisbud/` is the Android application module (registered as `:androidApp`) and also contains the Xcode project and Swift iOS entry points under `Crisbud/Crisbud/`. Gradle configuration and the version catalog are at the repository root and in `gradle/`.

## Build, Test, and Development Commands

Use the checked-in Gradle wrapper from the repository root:

- `./gradlew :androidApp:assembleDebug` builds the Android debug app (Windows: `gradlew.bat :androidApp:assembleDebug`).
- `./gradlew :shared:allTests` runs available shared-module tests; iOS test targets require macOS and Xcode.
- `./gradlew :shared:tasks` lists module tasks when checking platform-specific build options.

Open `Crisbud/Crisbud.xcodeproj` in Xcode to build and run the iOS app. Android SDK configuration is local; do not commit `local.properties`.

## Coding Style & Naming Conventions

Follow existing Kotlin conventions: four-space indentation, `UpperCamelCase` for classes and composables, `lowerCamelCase` for functions and properties, and descriptive `*Test.kt` test filenames. Keep shared behavior in `commonMain`; isolate platform APIs in the matching source set. Use Swift’s standard four-space indentation and `UpperCamelCase` type names in the iOS entry points. No formatter or linter is configured in the current Gradle files.

## Testing Guidelines

Add tests beside the source set they exercise, using `kotlin.test` in common tests. Name test classes after the behavior or component under test and use descriptive test method names. Run `./gradlew :shared:allTests` before submitting shared changes; build the Android app with `./gradlew :androidApp:assembleDebug` for Android-facing changes. Include manual iOS verification when changes affect Swift or iOS-specific code.

## Commit & Pull Request Guidelines

Git history is unavailable in this checkout, so no repository-specific commit format could be confirmed. Use short imperative commit subjects (for example, `Add settings screen`). PRs should explain the user-visible change, list validation commands and outcomes, link any related issue, and include screenshots for UI changes on affected platforms.

## Security & Configuration

Keep SDK paths, signing credentials, and local machine configuration out of commits. `local.properties`, Gradle build output, and IDE state are ignored; use the Gradle version catalog for dependency version changes.
