# DoughDough

A simple Android alarm app for following recipes step-by-step. Load recipes from Google Sheets, then the app schedules alarms and notifications to guide you through each step.

## Features

- **Recipe selector**: Load recipes from any public Google Sheet
- **Step-by-step timing**: Each step shows when to do it (absolute or relative times like +16h, +30m)
- **Alarms & notifications**: Get alerted when it's time for the next step; works even when the app is closed
- **Start early**: Skip the wait and move to the next step anytime

## Default Recipe

The app comes pre-configured with a sourdough recipe from:
https://docs.google.com/spreadsheets/d/1B_gaW3csiWVCZG3FGsQiARSNZ_w_OPh1QZbwWWo-FNY

## Google Sheets Format

Your sheet should have columns:
- **A (start_time)**: `16:00` for absolute time, or `+16h`, `+30m`, `+3h` for relative (from previous step)
- **B (title)**: Step name (e.g. "Wake up", "Fold and turn")
- **C (description)**: Detailed instructions

Row 1 is treated as a header. Each tab in the spreadsheet becomes a separate recipe.

## Multiple Recipes (Optional)

To load multiple recipes from different tabs:

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a project and enable the **Google Sheets API**
3. Create an API key (Credentials → Create credentials → API key)
4. Add to `gradle.properties`:
   ```
   GOOGLE_SHEETS_API_KEY=your_api_key_here
   ```

Without an API key, the app fetches the first sheet only (CSV export).

## Permissions

The app requests:
- **Internet** – to fetch recipes from Google Sheets
- **Notifications** – to remind you of the next step
- **Exact alarm** – for precise timing
- **Vibrate** – for alarm feedback

## Build

```bash
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`
