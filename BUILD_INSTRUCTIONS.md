# NEXUS NET MONITOR — APK Build Guide
## Developer: Faisal Malik © 2026

---

## ✅ PROJECT FILES LIST

```
NexusNetMonitor/
├── AndroidManifest.xml         ← All permissions (phone, GPS, internet)
├── build.gradle                ← Root gradle
├── settings.gradle             ← Project settings
├── gradle.properties           ← Gradle config
├── gradlew / gradlew.bat       ← Gradle wrapper
│
├── app/
│   ├── build.gradle            ← App build config (minSdk 26, targetSdk 34)
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── java/com/faisalmalik/nexusnetmonitor/
│       │   ├── MainActivity.java        ← Main + WebView + Cell data engine
│       │   ├── CellMonitorService.java  ← Background monitoring service
│       │   └── BootReceiver.java        ← Auto-start on boot
│       ├── assets/
│       │   └── index.html               ← Full UI (all 9 screens)
│       └── res/
│           ├── layout/activity_main.xml
│           ├── values/strings.xml
│           ├── values/styles.xml
│           ├── values/colors.xml
│           ├── xml/file_paths.xml
│           └── mipmap-*/ic_launcher.png ← App icons (all densities)
```

---

## 🔨 HOW TO BUILD APK

### METHOD 1: Android Studio (Recommended — Easy)

**Step 1:** Download Android Studio
→ https://developer.android.com/studio

**Step 2:** Install with default settings (includes Android SDK)

**Step 3:** Extract the ZIP → Open Android Studio
→ File → Open → Select `NexusNetMonitor` folder → OK

**Step 4:** Wait for Gradle sync (first time downloads ~500MB)

**Step 5:** Build APK
→ Build menu → Build Bundle(s)/APK(s) → Build APK(s)
→ Wait 1-2 minutes

**Step 6:** APK location:
```
NexusNetMonitor/app/build/outputs/apk/debug/app-debug.apk
```

**Step 7:** Transfer APK to your phone:
- USB cable → copy APK
- Or: Build → Generate Signed Bundle/APK for release

---

### METHOD 2: Command Line (Linux/Mac)

```bash
# 1. Install Java 17+
sudo apt install openjdk-17-jdk

# 2. Download Android command line tools
# https://developer.android.com/studio#command-tools

# 3. Set ANDROID_HOME
export ANDROID_HOME=$HOME/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin

# 4. Install required SDK
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"

# 5. Build
cd NexusNetMonitor
chmod +x gradlew
./gradlew assembleDebug

# 6. APK at:
# app/build/outputs/apk/debug/app-debug.apk
```

---

### METHOD 3: Online Build (No Install Needed)

1. Upload ZIP to **Appetize.io** or **GitHub + GitHub Actions**
2. Use **Buildbot** or **CircleCI** free tier
3. Or use **Codemagic** (free 500 min/month)

---

## 📱 INSTALL ON PHONE

**Step 1:** Enable Unknown Sources on phone:
→ Settings → Security → Unknown Sources → ON
→ (Android 8+): Settings → Apps → Special Access → Install Unknown Apps

**Step 2:** Copy APK to phone via USB or send via WhatsApp/Telegram

**Step 3:** Tap APK file → Install

**Step 4:** Grant all permissions when asked:
- ✅ Phone (READ_PHONE_STATE)
- ✅ Location (ACCESS_FINE_LOCATION)
- ✅ Precise Location
- ✅ Background Location

---

## 📡 WHAT THE APK DOES

### Real Hardware Data Collection:
| Feature | Details |
|---------|---------|
| Cell ID | eNB, LCID, CID, LAC/TAC |
| Signal | RSRP, RSRQ, RSSNR, RSSI |
| Network | 2G/3G/4G/5G auto-detect |
| Operators | Jazz, Warid, Ufone, Zong, Telenor |
| SIM | Dual SIM (SIM1 + SIM2) simultaneously |
| Baseband | Scan all operators without SIM |
| GPS | Lat, Lng, Accuracy, Bearing, Speed |
| TA | Timing Advance (distance to tower) |
| Band | Auto band detection from EARFCN |

### Screens:
1. 🌐 **OVERVIEW** — All 5 operators, every cell with CID/LAC/Band/Neighbors
2. 📡 **GAUGE** — RSRP meters SIM1 + SIM2
3. 🗺️ **MAP** — Tower locations + sector beams
4. 🎯 **GEOFENCE** — GPS zone analysis
5. 🧭 **AZIMUTH** — Direction compass + calculator
6. 📈 **STATS** — History + availability
7. 🤖 **AI** — CDR analysis via Claude AI
8. 📝 **LOG** — All CDR entries
9. ⚙️ **SETTINGS** — All toggles + app info

### Background Service:
- Runs continuously in background
- Notification shows: operator + cells + network type
- Auto-restarts on phone reboot

---

## ⚙️ TECHNICAL SPECS

| Parameter | Value |
|-----------|-------|
| Package | com.faisalmalik.nexusnetmonitor |
| Min Android | 8.0 (API 26) |
| Target Android | 14 (API 34) |
| Architecture | WebView + Java Native |
| Language | Java + HTML/JS |
| Scan Interval | 3 seconds (UI) / 5 seconds (service) |

---

## 🔑 KEY PERMISSIONS EXPLAINED

| Permission | Why Needed |
|-----------|-----------|
| READ_PHONE_STATE | Read CID, LAC, RSRP from SIMs |
| ACCESS_FINE_LOCATION | Required by Android to read cell info |
| READ_PRECISE_PHONE_STATE | LTE/5G signal details |
| INTERNET | AI analysis + map tiles |
| FOREGROUND_SERVICE | Background monitoring |
| RECEIVE_BOOT_COMPLETED | Auto-start on reboot |

---

## 📞 SUPPORT
Developer: **Faisal Malik**
App: **Nexus Net Monitor Pro**
Version: **3.0 — Pakistan Edition**
© 2026 All Rights Reserved
