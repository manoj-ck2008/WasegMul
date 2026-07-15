# WasegMul iOS App

This folder contains the iOS entry point for the WasegMul Kotlin Multiplatform project.

## How to Run

1.  Open this folder on a Mac with **Xcode** installed.
2.  The shared logic is located in the `:shared` module of the parent directory.
3.  To link the shared logic, you can use the **KMM Bridge** or manually export the XCFramework using:
    `./gradlew :shared:assembleXCFramework`
4.  Import the resulting `shared.xcframework` into your Xcode project.

## Architecture

-   **Logic**: Shared via Kotlin in `shared/src/main/kotlin`.
-   **UI**: SwiftUI entry point in `iosApp/ContentView.swift`.
-   **Bridge**: Use `IOSBridge` from the Kotlin code to access classification and knowledge base logic.
