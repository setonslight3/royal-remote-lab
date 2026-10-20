# Royal Remote Lab

Native Android application and protocol investigation lab designed to test and analyze local Wi-Fi control methods for the **Royal RTV65D8100 65-inch QLED Smart TV**.

---

## 1. Overview & Project Purpose

**Royal Remote Lab** is a real network protocol testing tool, not just a decorative TV remote UI. It allows you to switch between multiple smart TV control protocols, test network responsiveness honestly, record physical screen reactions, and safely investigate whether system navigation is possible over local Wi-Fi.

### Target TV Hardware Profile
- **Model:** Royal RTV65D8100 (65" QLED Smart TV)
- **Discovery Name:** `Smart TV`
- **Default Local IP:** `192.168.1.38` (editable in-app)
- **Software Build:** `v8-nt671la-lf1v680.000`
- **UI Version:** `u_1.0.129.46 0906`
- **Kernel Version:** `4.9.118+`
- **Chassis Platform:** Novatek NT671LA (Linux/custom Smart TV OS)
- **Observed Active Services:** Digital Media Renderer (DMR) enabled, T-Link (HDMI-CEC) enabled, Wi-Fi 2.4 GHz enabled.

---

## 2. Architecture & Design Principles

The application is built in **Kotlin** with modern **Jetpack Compose** and **Material 3**, strictly separating protocol logic from UI components:

```
app/src/main/java/com/example/royalremotelab/
├── model/
│   ├── ConnectionConfig.kt       # Host validation, timeouts, demo flag
│   ├── RemoteCommand.kt          # Explicit command enum (D-Pad, Volume, System, Media)
│   ├── CommandResult.kt          # Categorized outcome & network latency
│   ├── DiagnosticResult.kt       # Bounded endpoint checks & port table
│   └── TestLogEntry.kt           # Log record with IP-scrubbing export
├── protocols/
│   ├── TvProtocol.kt             # Common protocol adapter interface
│   ├── ProtocolRegistry.kt       # Registry and protocol selector
│   ├── DemoProtocol.kt           # Offline simulation for UI testing
│   ├── DlnaProtocol.kt           # UPnP DMR RenderingControl & AVTransport
│   ├── VestelProtocol.kt         # Vestel Smart Center (TCP 56789 investigation)
│   ├── DialNovatekProtocol.kt    # DIAL REST YouTube launch (Ports 8008/8080)
│   ├── AndroidTvRemoteProtocol.kt# Google TV Remote v2 (TLS 6466/6467 probe)
│   └── RawTcpProbeProtocol.kt    # Targeted candidate smart TV port audit
├── data/
│   └── LabRepository.kt          # StateFlow single source of truth
├── network/
│   └── NetworkUtils.kt           # Safe bounded socket probes & HTTP client
└── ui/
    ├── AppShell.kt               # Scaffold with 4-tab NavigationBar
    ├── screens/
    │   ├── RemoteScreen.kt       # D-Pad, Volume, System keys & reaction prompt
    │   ├── ProtocolLabScreen.kt  # Candidate selector & diagnostic runner
    │   ├── ConnectionScreen.kt   # IP input, reachability ping & TV specs
    │   └── LogsScreen.kt         # Log metrics, reaction tagger & report export
    └── theme/                    # Material 3 Color, Theme & Typography
```

---

## 3. Four Core App Screens

### A. Connection
- Editable TV host/IP (default `192.168.1.38`) with instant IPv4 and hostname validation.
- Configurable timeout slider (500 ms to 30,000 ms).
- **Test TV Reachability** button that performs safe TCP socket pings to port 8088 (DLNA) and port 80 (Web).
- Offline Demo Mode toggle.
- Clear 4-point Wi-Fi setup checklist.
- Discovered Royal TV hardware and software profile card.

### B. Protocol Lab
- Select between 6 protocol adapters:
  1. **DLNA / UPnP Media Renderer** (Candidate A & D)
  2. **Vestel Smart Center TCP 56789** (Candidate B)
  3. **DIAL REST / Novatek App Launch** (Candidate C2)
  4. **Android TV Remote TLS 6466/6467** (Candidate C1)
  5. **Targeted Smart TV Port Audit** (Candidate C3)
  6. **Demo Mode (Offline Simulation)**
- Displays what each method can and cannot do.
- **Run Safe Diagnostics** button with live port checks and latency metrics.
- Prominent honest reminder: *"An open port alone must never be treated as proof that remote commands work."*

### C. Remote
- Large, thumb-friendly touch targets:
  - **D-Pad:** Up, Down, Left, Right, OK/Enter
  - **System Keys:** Back, Home, Menu, Input Source
  - **Volume & Audio:** Vol +, Vol -, Mute
  - **Media Playback:** Play, Pause, Stop
- Unsupported commands are cleanly disabled and explained per protocol.
- Active protocol and target IP banner.
- Immediate action result card with latency and outcome badge.
- **One-tap feedback buttons:** `[✅ TV Reacted]` and `[❌ No Reaction]` immediately tag the test log.

### D. Logs & Diagnostics
- Summary metrics: Total Tests, Confirmed Reactions, No Reaction, Unconfirmed.
- Scrollable list of timestamped test logs.
- Reaction tagger directly on each log card.
- **Export Report:**
  - Excludes target IP by default (`[IP_REDACTED]`) for privacy.
  - Option to include IP if desired.
  - Copies comprehensive Markdown report to clipboard.
- Clear logs button.

---

## 4. Permissions & Deliberate Network Security

The app requests only minimal, necessary networking permissions in `AndroidManifest.xml`:
- `android.permission.INTERNET`: Socket and HTTP communication with the TV.
- `android.permission.ACCESS_NETWORK_STATE`: Detecting Wi-Fi connectivity.
- `android.permission.ACCESS_WIFI_STATE`: Verifying local network status.
- `android.permission.CHANGE_WIFI_MULTICAST_STATE`: UPnP / SSDP multicast discovery.

### Deliberate Cleartext Policy
In `res/xml/network_security_config.xml`, cleartext HTTP is permitted deliberately for local private network communication (DLNA and DIAL endpoints do not support TLS). The app transmits **no telemetry, no analytics, and no credentials** to any external server.

---

## 5. Verified Build Artifact

A debug APK has been compiled and verified in this environment:
- **Verified APK Path:** `C:\Users\Setons\.gemini\antigravity\scratch\royal-remote-lab\royal-remote-lab-debug.apk`
- **Build Output Path:** `app\build\outputs\apk\debug\app-debug.apk`
- **Package Size:** 12.38 MB (12,384,951 bytes)
- **Minimum SDK:** 24 (Android 7.0+)
- **Target SDK:** 36 (Android 15/16)

### How to Install on Your Android Phone

#### Option 1: Via ADB (if USB debugging is available)
```powershell
adb install -r "C:\Users\Setons\.gemini\antigravity\scratch\royal-remote-lab\royal-remote-lab-debug.apk"
```

#### Option 2: Direct Phone Transfer
1. Copy `royal-remote-lab-debug.apk` to your Android phone via USB cable, Google Drive, WhatsApp, or local share.
2. Tap the APK file on your phone and select **Install**.
3. Open **Royal Remote Lab**.

---

## 6. Building and Testing from Source

```powershell
# Run all unit tests
.\gradlew.bat test

# Compile debug APK
.\gradlew.bat assembleDebug
```
