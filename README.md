<p align="center">
  <img src="docs/icon.png" width="104" alt="Thermida icon">
</p>

<h1 align="center">Thermida</h1>

<p align="center">
  <b>Snap your meal. Know your calories.</b><br>
  An Android calorie tracker that reads photos of your food, or a few words about it, with the AI you choose.<br>
  <sub>Thermida is θερμίδα, Greek for calorie.</sub>
</p>

<p align="center">
  <a href="https://github.com/Joji228/Thermida/releases/latest"><img src="https://img.shields.io/badge/Download-APK-6C8CFF?style=for-the-badge&logo=android&logoColor=black" width="230" alt="Download APK"></a>
</p>

<p align="center">
  <img src="docs/screenshots/1-diary.png?v=6" width="23%" alt="Diary: calories, macros, water and weight trend">
  <img src="docs/screenshots/2-scan.jpg?v=6" width="23%" alt="Scan: a souvlaki plate in the viewfinder, one big button to scan it">
  <img src="docs/screenshots/3-meals.png?v=6" width="23%" alt="The day's meals with their macros">
  <img src="docs/screenshots/4-coach.png?v=6" width="23%" alt="The AI coach, ready for a question">
</p>

## What it does

- 📸 **Scan in one tap.** Point at a plate or nutrition label and tap the big button: calories, protein, carbs and fat in seconds. Add up to 4 photos of one meal first, or use a volume key. Barcodes are read as soon as they're in view.
- ✍️ **Or describe it.** Type or say what you ate ("2 eggs, toast and a latte") and it's split into foods with grams you can adjust.
- 📊 **One clean diary.** Calories, macros, water and a weight trend, day by day. Swipe past today to jump straight to the camera.
- 💬 **AI coach (beta).** Pull down on the Diary or Scan to ask how you're doing. It knows your meals and weigh-ins, talks in English or Greek, and can log food or change a target when you accept (or just say "yes").
- 🔑 **Bring your own AI.** DeepSeek, Gemini, OpenAI, Claude, OpenRouter or any OpenAI-compatible service, with daily and weekly token limits. You pay the provider directly.
- ⚖️ **Xiaomi smart scale.** Step on a Mi Body Composition Scale 2 (XMTZC05HM) with the app open and the weigh-in and body composition are saved by themselves.
- 🔒 **Private.** No account, no ads, no tracking. Your data stays on your phone; photos go only to the AI you pick.

Packaged foods are checked against [Open Food Facts](https://world.openfoodfacts.org) (only food names and barcodes are sent, never photos). Everything optional can be switched off in Settings.

## Install

1. [Download the latest APK](https://github.com/Joji228/Thermida/releases/latest) and open it on your phone (allow "Install unknown apps" if asked).
2. Answer a few setup questions, then add an AI engine (or later in **Settings → AI engine**).
3. Tap **Scan**, or **Log food** to describe a meal.

Needs Android 12+. Figures are estimates, not medical advice. The app checks here for new versions and offers to install them ([details](docs/updates.md)).

## Build

```bash
git clone https://github.com/Joji228/Thermida.git
cd Thermida
./gradlew assembleDebug     # JDK 17+ and the Android SDK
```

Kotlin, Jetpack Compose, Room, CameraX.

## License

[MIT](LICENSE). [Inter](https://rsms.me/inter/) font under the [SIL Open Font License](licenses/Inter-OFL.txt). Souvlaki photo in the Scan screenshot: [Souvlaki in Athens](https://commons.wikimedia.org/wiki/File:Souvlaki_in_Athens.JPG) by Miyagawa, [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/).
