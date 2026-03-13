# AGENTS.md

## Purpose
- This repository is a single-module Android TV app named `WalacTV`.
- The codebase mixes Leanback fragments, some Compose theme scaffolding, Firebase Firestore data loading, Glide image loading, and Media3/ExoPlayer playback.
- Agents should prefer small, targeted changes that preserve current runtime behavior on Android TV devices.

## Repository Shape
- Gradle project root contains `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, and the wrapper scripts `gradlew` / `gradlew.bat`.
- Only one module is present: `:app`.
- Main Kotlin sources live under `app/src/main/java/com/example/walactv`.
- Resources live under `app/src/main/res`.
- There are currently no `app/src/test` unit tests and no `app/src/androidTest` instrumentation tests.
- There is no existing `AGENTS.md` elsewhere in the repo.

## Rule Files
- No `.cursorrules` file was found.
- No files were found under `.cursor/rules/`.
- No `.github/copilot-instructions.md` file was found.
- If any of those files are added later, treat them as higher-priority repo guidance and update this file.

## Build System Notes
- Build system: Gradle Kotlin DSL.
- Android application plugin is configured in `app/build.gradle.kts`.
- Kotlin Android and Kotlin Compose plugins are enabled.
- Compose is enabled, but the current UI flow is primarily Leanback fragment based.
- `kotlin.code.style=official` is set in `gradle.properties`.
- `android.nonTransitiveRClass=true` is enabled, so reference only this module's own resources unless explicitly imported from a dependency.

## Main Dependencies
- AndroidX Leanback for TV browsing UI.
- AndroidX TV Material / Compose artifacts for theme scaffolding.
- Firebase Firestore KTX.
- Kotlin coroutines.
- Media3 ExoPlayer, DASH, HLS, and UI.
- Glide for image loading.

## Preferred Command Style
- Always use the Gradle wrapper: `./gradlew ...`.
- Run commands from the repository root.
- Prefer module-qualified tasks like `:app:build` when possible.
- For CI-like verification, use one focused command instead of many overlapping commands.

## Build Commands
- Full app build: `./gradlew :app:build`
- Assemble debug APK: `./gradlew :app:assembleDebug`
- Assemble release APK: `./gradlew :app:assembleRelease`
- Install debug APK on a connected device: `./gradlew :app:installDebug`
- Clean build outputs: `./gradlew :app:clean`
- Show available tasks: `./gradlew tasks --all`

## Lint Commands
- Run default Android lint task: `./gradlew :app:lint`
- Run debug lint only: `./gradlew :app:lintDebug`
- Run release lint only: `./gradlew :app:lintRelease`
- Apply safe lint autofixes when available: `./gradlew :app:lintFix`

## Test Commands
- Run all unit tests: `./gradlew :app:test`
- Run debug unit tests: `./gradlew :app:testDebugUnitTest`
- Run release unit tests: `./gradlew :app:testReleaseUnitTest`
- Run all connected instrumentation tests: `./gradlew :app:connectedAndroidTest`
- Run debug instrumentation tests on a connected device/emulator: `./gradlew :app:connectedDebugAndroidTest`

## Running A Single Test
- Single JVM unit test class: `./gradlew :app:testDebugUnitTest --tests 'com.example.walactv.ExampleUnitTest'`
- Single JVM unit test method: `./gradlew :app:testDebugUnitTest --tests 'com.example.walactv.ExampleUnitTest.someMethod'`
- Single instrumentation test class: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.walactv.ExampleInstrumentedTest`
- Single instrumentation test method: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.walactv.ExampleInstrumentedTest#someMethod`
- Because the repo currently has no tests, add the test source set first before relying on these commands.

## Fast Verification Recommendations
- For Kotlin or XML-only edits that do not touch playback or Firestore wiring, start with `./gradlew :app:assembleDebug`.
- For manifest, resource, or general Android changes, prefer `./gradlew :app:lintDebug`.
- For dependency or build-script changes, prefer `./gradlew :app:build`.
- For new tests, run the narrowest possible single-test command first.

## Source Layout Guidance
- Keep app logic in `app/src/main/java/com/example/walactv` unless there is a strong reason to introduce a new package tree.
- Put theme-only Compose files in `app/src/main/java/com/example/walactv/ui/theme`.
- Keep layout XML in `app/src/main/res/layout`.
- Keep XML config files like network security rules in `app/src/main/res/xml`.

## Kotlin Style
- Follow Kotlin official style; the repo already opts into it through Gradle properties.
- Use 4-space indentation and keep existing Kotlin DSL formatting patterns.
- Prefer trailing commas where Kotlin already supports them and surrounding code uses them.
- Prefer expression-oriented Kotlin when it improves readability, but do not compress complex control flow.
- Keep functions small when possible; this codebase currently has very large classes, so extract helpers instead of adding more nested logic.

## Imports
- Keep imports explicit; do not use wildcard imports.
- Group imports in the normal Kotlin order: standard library / platform, AndroidX, third-party, then local package imports.
- Remove unused imports immediately.
- If a file is missing a `package` declaration, fix it only when touching that file for a related change and verify references afterward.

## Naming Conventions
- Classes, fragments, presenters, and data classes: `PascalCase`.
- Functions and properties: `camelCase`.
- Constants: `UPPER_SNAKE_CASE` only for true constants.
- Resource IDs and XML files: `snake_case`.
- Prefer English for new identifiers unless matching an established Firestore schema or existing domain wording.
- Preserve external schema names exactly, such as Firestore fields like `nombre`, `grupo`, `hora`, or `eventos`.

## Types And State
- Prefer explicit types for public or non-obvious declarations.
- Local inference is fine when the right-hand side is obvious.
- Favor immutable `val` over `var`; use `var` only for true mutable UI/player state.
- Model structured data with data classes instead of raw `Map<String, Any>` once a shape is stable.
- Avoid unchecked casts when you can parse defensively.

## Nullability
- Use Kotlin nullability instead of sentinel values when practical.
- Prefer early returns over deeply nested null checks.
- Use `requireContext()` / `requireActivity()` only when lifecycle guarantees are clear.
- If a fragment may outlive a callback, guard context-sensitive work carefully.

## Coroutines
- Continue using coroutines for asynchronous Firestore work.
- Tie scopes to lifecycle owners when possible; avoid introducing long-lived ad hoc scopes without cancellation.
- Cancel custom scopes in the matching lifecycle callback.
- Prefer `Dispatchers.IO` for blocking or network-heavy work if adding new manual I/O.

## Error Handling
- Do not swallow exceptions silently.
- Log enough context to diagnose playback, parsing, and network failures.
- Show user-facing `Toast` messages only for actionable or user-visible failures.
- Prefer narrow `try/catch` blocks around risky calls instead of wrapping large unrelated sections.
- When parsing Firestore payloads, fail soft: skip malformed entries rather than crashing the screen.

## Android And UI Guidance
- This is an Android TV app; preserve D-pad navigation behavior.
- Avoid touch-centric UI assumptions.
- Keep player lifecycle changes conservative; Media3/ExoPlayer behavior is sensitive to fragment lifecycle timing.
- Preserve focusability and back-navigation semantics when editing playback UI.
- Prefer resource references over hard-coded dimensions and strings when making broader UI changes.

## Fragment And Activity Conventions
- Keep fragment transactions localized and easy to follow.
- Avoid introducing new fragment constructors that rely on non-default arguments unless you also handle recreation carefully.
- For new fragments, prefer `newInstance()` plus arguments `Bundle` patterns over raw constructor params.
- If you touch `PlayerFragment`, verify lifecycle cleanup paths (`onPause`, `onStop`, `onDestroyView`).

## Logging
- Use `Log.d`, `Log.w`, and `Log.e` consistently with a stable tag.
- Prefer a single tag constant per class for new code.
- Do not leave noisy per-item debug logging in hot loops unless it is necessary for playback diagnostics.

## Resources And Strings
- New user-visible text should usually go in `app/src/main/res/values/strings.xml`.
- Keep resource names descriptive and `snake_case`.
- Reuse existing theme and color resources when possible.
- If adding icons, banners, or drawables, keep naming consistent with Android conventions.

## Build Script Conventions
- Prefer version catalog entries in `gradle/libs.versions.toml` for new dependencies.
- Avoid mixing direct dependency versions and catalog aliases in the same area unless there is a clear reason.
- Keep plugin and dependency blocks ordered and easy to scan.
- Do not add new repositories unless absolutely required.

## Current Codebase Smells To Avoid Making Worse
- Do not add more logic to already very large files when a helper or extracted class would suffice.
- Avoid duplicating playback navigation logic between browse and search flows.
- Avoid adding more raw string literals for URLs, tags, or repeated messages when a constant would help.
- Be careful with typoed or inconsistent names in existing code; preserve compatibility first, then refactor deliberately.

## What To Verify After Changes
- Kotlin compiles: `./gradlew :app:assembleDebug`
- Android lint is clean enough for the touched area: `./gradlew :app:lintDebug`
- If playback code changed, verify on a device/emulator with D-pad input.
- If Firestore parsing changed, verify both missing-field and valid-data paths.

## Agent Expectations
- Make the smallest change that fully solves the task.
- Prefer repo-consistent patterns over idealized rewrites.
- Call out missing tests when relevant.
- If you introduce new architecture, explain why and keep it incremental.
