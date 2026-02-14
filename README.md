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

---

## GitHub Actions: Build and Release

- **Build** (`.github/workflows/build.yml`): runs on every push and pull request to `main`; compiles debug and release.
- **Release** (`.github/workflows/release.yml`): runs when you **publish a GitHub Release**; builds a signed AAB, deploys it to the **Google Play Store** (using [Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756)), and attaches the AAB to the release.

### GitHub secrets and keys (where to add them)

Add all of these under the repo: **Settings → Secrets and variables → Actions → New repository secret**.

#### For Build workflow (optional)

| Secret name | Where to get it | Required? |
|-------------|-----------------|------------|
| `GOOGLE_SHEETS_API_KEY` | [Google Cloud Console](https://console.cloud.google.com/) → your project → APIs & Services → Credentials → Create credentials → API key. Enable **Google Sheets API** first. | No. Omit for CSV-only builds. |

#### For Release workflow (required for Play Store)

| Secret name | Where to get it | Required? |
|-------------|-----------------|------------|
| `KEYSTORE_BASE64` | Your **upload keystore** (`.jks` or `.keystore`) encoded as base64. See below. | Yes (for release). |
| `KEYSTORE_PASSWORD` | Password you set when creating the keystore. | Yes. |
| `KEY_ALIAS` | Alias of the key inside the keystore (e.g. `upload` or `key0`). | Yes. |
| `KEY_PASSWORD` | Password for that key (often same as `KEYSTORE_PASSWORD`). | Yes. |
| `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` | JSON key for a **service account** that can upload to Play Console. See below. | Yes (for Play deploy). |

**Creating the upload keystore (one-time)**

Use this as your **Play upload key** (Google will use it with Play App Signing):

```bash
keytool -genkey -v -keystore upload-keystore.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
```

Store the file and passwords safely. Then encode the keystore for GitHub:

- **Linux / macOS / Git Bash:**  
  `base64 -w 0 upload-keystore.jks`  
  Copy the whole output → paste as value of `KEYSTORE_BASE64`.
- **PowerShell:**  
  `[Convert]::ToBase64String([IO.File]::ReadAllBytes("upload-keystore.jks"))`  
  Copy the whole output → paste as value of `KEYSTORE_BASE64`.

**Creating the Play Console service account (one-time)**

1. **Google Cloud Console**  
   - Go to [Google Cloud Console](https://console.cloud.google.com/) and select (or create) the project linked to your Play app.
2. **Enable API**  
   - APIs & Services → Library → search **Google Play Android Developer API** → Enable.
3. **Service account**  
   - APIs & Services → Credentials → Create credentials → Service account.  
   - Name it (e.g. “Play Store deploy”), create, then open it → Keys → Add key → Create new key → JSON → Download.  
   - You get a `.json` file; keep it private.
4. **Play Console**  
   - [Google Play Console](https://play.google.com/console/) → your app → **Users and permissions** → **Invite new users**.  
   - Email: the **service account email** from the JSON (e.g. `...@....iam.gserviceaccount.com`).  
   - Role: at least **Release to production, exclude devices, and use Play App Signing** (or “Admin” if you prefer).  
   - Send invite and accept.  
   - It can take a short time for access to apply.
5. **GitHub secret**  
   - Open the downloaded JSON file in a text editor.  
   - Copy the **entire** JSON (one object).  
   - GitHub → repo → Settings → Secrets and variables → Actions → New repository secret.  
   - Name: `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`  
   - Value: paste the full JSON.

**Summary**

- **Build**: only `GOOGLE_SHEETS_API_KEY` is optional; no other secrets needed.  
- **Release**: you must set `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, and `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` for the workflow to sign the AAB and deploy to Play.  
- `GITHUB_TOKEN` is provided automatically by GitHub; you do not add it.

### How to release to Play Store

1. Bump **version** in `app/build.gradle.kts`: `versionCode` and `versionName`.
2. Commit, push, then create a **tag** and push it:
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```
3. On GitHub: **Releases → Draft a new release**, choose that tag, add notes, then **Publish release**.
4. The **Release** workflow will run: build signed AAB → upload to Google Play (production track) → attach the AAB to the GitHub release. In Play Console you can then roll out the release to users.
