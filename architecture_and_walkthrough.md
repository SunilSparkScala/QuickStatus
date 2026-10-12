# WhatsApp Status Card Maker - Architecture & Walkthrough

This document outlines the directory structure, design parameters, native Graphics Canvas engine logic, and compiling instructions for the lightweight, native Android app built using **Jetpack Compose**, **Android DataStore**, and the native **Android Canvas API**.

---

## 📂 Project Directory Structure

Here is the complete, rate-limit defensive directory structure generated for immediate compilation:

```text
StatusApp/
├── gradle.properties
├── settings.gradle.kts
├── build.gradle.kts (Project-level)
└── app/
    ├── build.gradle.kts (App-level)
    └── src/
        └── main/
            ├── AndroidManifest.xml
            ├── java/
            │   └── com/
            │       └── shopkeeper/
            │           └── statusmaker/
            │               └── MainActivity.kt
            └── res/
                ├── values/
                │   ├── strings.xml
                │   └── themes.xml
                └── xml/
                    ├── backup_rules.xml
                    ├── data_extraction_rules.xml
                    └── file_paths.xml
```

---

## 🏗️ Technical Architecture Highlights

### 1. Zero Third-Party Image Dependencies
To safeguard compile-time safety and runtime performance, we bypassed libraries like Glide or Coil. The entire layout rendering is performed by `StatusCardCanvasEngine` which initializes a high-resolution Bitmap (`1080 x 1920` px, ideal for `9:16` WhatsApp Statuses) and draws:
- Custom multi-color LinearGradients.
- Programmatic vector patterns (e.g., recursive rotated Mandalas, neon sparkles/stars, custom borders).
- Custom drawn Android Vector icons (Rupee symbol `₹`, smartphone container for phone numbers, pin-drop shape for shop address locator) so the app requires **zero graphic files** to render professionally.
- A custom wrapped-text paragraph parser to prevent long strings from cutting off on device screens.

### 2. Standard Android DataStore Preferences
Integrated seamlessly inside the single-activity architecture. On startup, the onboarding profile is loaded from standard local storage asynchronously via Kotlin Coroutines. Saving immediately updates the profile state and syncs to memory, re-triggering the Canvas rendering instantly!

### 3. FileProvider Secure Image Sharing
Since Android 7.0, raw `file://` paths inside Intents trigger a `FileUriExposedException`. We configured a standard `FileProvider` with secure caching inside `cacheDir/images/`. Sharing saves the generated PNG to cache and issues a sandboxed content URI `content://com.shopkeeper.statusmaker.fileprovider/...` with `FLAG_GRANT_READ_URI_PERMISSION` enabled.

---

## 🎨 Premium Visual Templates

We designed **four distinct programmatic templates** that Indian shopkeepers will love:

| Template Name | Design Style & Aesthetic | Programmatic Elements |
|:---|:---|:---|
| **1. Festive Saffron** | Traditional Indian Festive & Daily Greetings | Deep Saffron to Golden Yellow Gradient, a center radial golden Mandala centerpiece drawn with rotated spokes/petals, solid maroon footer bar with gold lines. |
| **2. Neon Sale** | Modern High-Energy Vibrant Promo / Sale Banner | Deep Charcoal to Royal Violet Gradient, floating bright yellow & pink 4-point neon sparkles, a rotated `-8°` electric yellow banner for `"EXCITING DEAL"`, bold typography with pink neon shadow glow. |
| **3. Emerald Gold** | Premium, Luxurious Corporate & Traditional | Deep Emerald Green to Forest Green Gradient, intricate dual-line golden outer frames, gold solid corner ornate triangles, elegant golden crown logo top center. |
| **4. Glassmorphic Card** | Ultra-Sleek Modern Apple-style Glass Design | Royal Blue background overlayed with three massive soft blurred colorful circles (Indigo, Cyan, Purple) acting as abstract light. A card with semi-transparent glassy white backing (`#25FFFFFF`) and reflection gradient border overlayed on top. |

---

## 🚀 How to Build & Run

### Prerequisites
- **Android Studio Jellyfish / Koala (or newer)** or **Gradle command-line tool**.
- **Java Development Kit (JDK) 17** configured in your environment.

### Build Instructions
1. Open Android Studio and choose **Open an Existing Project**, selecting the `StatusApp` directory.
2. Let Gradle sync and download standard dependencies.
3. Click the **Run** button or execute the following in your terminal to build the debug APK:
   ```bash
   ./gradlew assembleDebug
   ```

### Sharing in Action
- Click **Shop Profile** to input your business info and UPI handle. Saving locks this data in local DataStore.
- Switch to **Card Studio**, choose your template, customize the texts dynamically, and tap **Share to WhatsApp Status**. The app will target WhatsApp automatically, falling back gracefully to standard sharing if not installed!
