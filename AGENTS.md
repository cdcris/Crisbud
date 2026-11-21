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

## Default UI Design

Use Crisbud's existing Material 3 Expressive design for new screens and changes to existing screens on Android and iOS, unless the task explicitly calls for a different design. The shared UI sources live in `shared/src/commonMain/kotlin/android/com/crisbud/`.

- **Theme and typography.** Use `CrisbudTheme` from [CrisbudTheme.kt](shared/src/commonMain/kotlin/android/com/crisbud/CrisbudTheme.kt). Its system-aware light and dark schemes define the indigo and lilac primary colors, sage secondary accents, marigold tertiary accents, and warm neutral surfaces. Use `MaterialTheme.colorScheme`, `typography`, and `shapes` for screen styling. Keep the bundled DM Sans type scale, with display styles for page titles and key balances and smaller styles for labels and supporting text. Use the shared `money()` formatter for peso amounts.
- **App shell and layout.** Follow [CrisbudApp.kt](shared/src/commonMain/kotlin/android/com/crisbud/CrisbudApp.kt) for the masthead, primary entry action, bottom navigation on compact widths, and navigation rail on wide layouts. Use the Overview, Activity, Budgets, and Settings screens as layout references. Group related information in rounded tonal cards and use prominent summary cards for key totals.
- **Components and interactions.** Reuse [CrisbudComponents.kt](shared/src/commonMain/kotlin/android/com/crisbud/CrisbudComponents.kt). Use `PageHeading` and `SectionHeading` for hierarchy, `CategoryBadge` for category icons, `EntrySheet` and `ChoiceField` for forms, and `RecordActions` and `ConfirmDelete` for record management. Keep the visible overflow menu available alongside swipe actions. Use Material 3 buttons, fields, and menus with the shared theme.
- **UI verification.** Check light and dark appearances, compact and wide layouts, and large text. Verify that long labels and amounts stay readable and that icon-only controls have accessible labels.

## Testing Guidelines

Add tests beside the source set they exercise, using `kotlin.test` in common tests. Name test classes after the behavior or component under test and use descriptive test method names. Run `./gradlew :shared:allTests` before submitting shared changes; build the Android app with `./gradlew :androidApp:assembleDebug` for Android-facing changes. Include manual iOS verification when changes affect Swift or iOS-specific code.

## Commit & Pull Request Guidelines

Git history is unavailable in this checkout, so no repository-specific commit format could be confirmed. Use short imperative commit subjects (for example, `Add settings screen`). PRs should explain the user-visible change, list validation commands and outcomes, link any related issue, and include screenshots for UI changes on affected platforms.

## Security & Configuration

Keep SDK paths, signing credentials, and local machine configuration out of commits. `local.properties`, Gradle build output, and IDE state are ignored; use the Gradle version catalog for dependency version changes.
