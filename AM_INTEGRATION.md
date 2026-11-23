# AntiGem AMP (com.amp.antigem)

## 📌 Repository Information
- **GitHub Repository**: [https://github.com/abiedadam007-glitch/antigem-amp](https://github.com/abiedadam007-glitch/antigem-amp)
- **Original Upstream**: [https://github.com/santoshkurmi/antigem](https://github.com/santoshkurmi/antigem)
- **Branch**: `main`

---

## 🚀 Key Modifications & Features Added

### 1. DeepSeek Harness (`dsh`) Full Integration
- **Go Bridge Backend (`agy_ide_bridge/pkg/harness/`)**:
  - `install.go`: Automatically detects package manager (`pnpm` / `npm`) and installs `@deepseek-ai/dsh@latest`.
  - `manager.go`: Manages ACP (Agent Client Protocol) process over stdio JSON-RPC, handles event broadcasting, and provides WebSocket updates.
  - `handlers.go`: HTTP endpoints for `/api/harness/status`, `/api/harness/start`, `/api/harness/restart`, `/api/harness/rpc`, `/api/harness/requests`, `/api/harness/ws`, and `/api/harness/cli/install`.
  - `main.go`: Added CLI flags `--harness-bin` and `--no-harness`, initialized the harness manager, and added shutdown hooks.
- **Android Client & UI (`app/src/main/java/com/amp/antigem/`)**:
  - `AgentKind.HARNESS`: Added to agent enum with full routing.
  - `HarnessPreferences.kt`: Preferences for DeepSeek models (`deepseek-chat`, `deepseek-reasoner`, `deepseek-coder`), API keys, and ACP execution profiles.
  - `HarnessBridgeClient.kt`: HTTP and WebSocket communications with the local bridge.
  - `HarnessTranscript.kt`: Turn data models and chat message mapper.
  - `HarnessChatBackend.kt`: Implementation of `AgentChatBackend` supporting streaming, turn management, and cancelation.
  - `SettingsDialog.kt`: Dedicated DeepSeek Harness settings page with model selector, API key input, and profile chooser.
  - `AgentsSetupUi.kt`: Allows toggling DeepSeek Harness on or off from the Agents settings menu.
  - `AgentPicker.kt` & `AgentStatusUi.kt`: Added custom Harness branding (`#0066FF`) and whale symbol (`🐋`).

### 2. Package Name Transition (`ai.antigem` → `com.amp.antigem`)
- Relocated Java/Kotlin source directories to `com/amp/antigem`.
- Updated package identifiers, imports, and references across all 450+ files (`.kt`, `.java`, `.xml`, `.kts`, `.sh`).
- Modified `app/build.gradle.kts` (`applicationId = "com.amp.antigem"` and `namespace = "com.amp.antigem"`).
- Updated internal asset and relocation scripts (`backup_projects`, `git_clone`, `restore_projects`, `chrootctl.sh`).

### 3. Bilingual Support (Arabic & English)
- **Language Management**:
  - `AppLanguageManager.kt`: Controls runtime locale switching between Arabic (`ar`) and English (`en`), maintaining reactive `isRtl` state.
  - `MainActivity.kt`: Wraps Compose hierarchy with `CompositionLocalProvider(LocalLayoutDirection)` for dynamic RTL/LTR support.
- **Localization Files**:
  - `app/src/main/res/values/strings.xml`: Complete English string resources.
  - `app/src/main/res/values-ar/strings.xml`: Full Arabic translations for the UI, settings, agents, and marketplace.
- **In-App Language Switcher**:
  - Embedded in **Settings → Appearance → Language (اللغة · Language)**, allowing instantaneous language switching without app restart.

### 4. Professional Skills & Plugins Marketplace
- **Marketplace Screen (`MarketplaceScreen.kt`)**:
  - Built to match the desired design with a floating search bar, top icons carousel, and categorized lists ("الأكثر رواجًا" / Most Popular, "الجديد والجدير بالاهتمام" / New & Noteworthy).
  - Built-in GitHub, Canva, Gmail, Google Drive, Remote Desktop Commander, and Health integrations.
  - OAuth and authentication bottom sheet (`MarketplaceAuthDialog.kt`) for connecting third-party services.
  - Directly accessible from **Settings → Plugins & Skills (المكونات الإضافية · Plugins & Skills)**.

---

## 🛠️ Building the APK

### Method 1: Automatic Cloud Build via GitHub Actions
1. Visit the repository: [https://github.com/abiedadam007-glitch/antigem-amp/actions](https://github.com/abiedadam007-glitch/antigem-amp/actions)
2. Select the **Build Debug APK** workflow.
3. Click **Run workflow** on the `main` branch.
4. When finished, download the compiled `.apk` directly from the workflow artifacts or GitHub Releases.

### Method 2: Local Build (Linux / Termux with JDK 21 and Go 1.22)
```bash
./scripts/build_all_apks.sh
# The generated APK will be in output/AntiGem-v2.0.0-debug.apk
```
