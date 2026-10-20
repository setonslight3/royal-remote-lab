# Royal Remote Lab

Native Android application and protocol investigation lab designed to test, discover, and analyze local Wi-Fi control methods for the **Royal RTV65D8100 65-inch QLED Smart TV**.

---

## 1. Overview & Project Purpose

**Royal Remote Lab** is a real network protocol testing tool, not just a decorative TV remote UI. It allows you to discover TV services over SSDP/UPnP, safely probe documented smart TV ports, switch between candidate control adapters, record physical screen reactions honestly, and investigate whether system navigation is possible over local Wi-Fi.

### Target TV Hardware Profile
- **Model:** Royal RTV65D8100 (65" QLED Smart TV)
- **Discovery Name:** `Smart TV`
- **Default Local IP:** `192.168.1.38` (editable in-app)
- **Software Build:** `v8-nt671la-lf1v680.000`
- **UI Version:** `u_1.0.129.46 0906`
- **Kernel Version:** `4.9.118+`
- **Chassis Platform:** Novatek NT671LA (Linux/custom Smart TV OS)
- **Observed Active Services:** Digital Media Renderer (DMR) enabled, T-Link (HDMI-CEC) enabled, Wi-Fi 2.4 GHz enabled.
- **Recent Network Test Finding:** Direct TCP connections to ports 80 and 8088 were **refused**, and port 56789 was **refused**. This confirms the TV does not run standard PC web servers or Vestel virtual remote daemons, requiring SSDP discovery to locate its true dynamic UPnP/DIAL service ports.

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
│   ├── NetworkDiagnosticModels.kt# Reachability, UPnP devices, and port probe models
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
│   ├── NetworkUtils.kt           # Safe bounded socket probes & HTTP client
│   ├── HostReachabilityChecker.kt# ICMP/socket reachability & documented port audit
│   ├── SsdpDiscoveryManager.kt   # Bounded UDP multicast discovery & direct unicast fallback
│   └── DeviceDescriptionParser.kt# Safe XML device description DOM parser
└── ui/
    ├── AppShell.kt               # Scaffold with 5-tab NavigationBar
    ├── screens/
    │   ├── DiagnosticsScreen.kt  # Host reachability, SSDP discovery & device description inspector
    │   ├── RemoteScreen.kt       # D-Pad, Volume, System keys & reaction prompt
    │   ├── ProtocolLabScreen.kt  # Candidate selector & diagnostic runner
    │   ├── ConnectionScreen.kt   # IP input, reachability ping & TV specs
    │   └── LogsScreen.kt         # Log metrics, reaction tagger & report export
    └── theme/                    # Material 3 Color, Theme & Typography
```

---

## 3. Five Core App Screens

### A. Diagnostics (Safe Network Investigation)
- **Host Reachability Audit:**
  - Evaluates ICMP echo ping and fallback socket connections.
  - Explains why a failed ping does **not** mean the TV is offline (many smart TVs drop ICMP pings for security/power saving while actively streaming).
- **SSDP / UPnP Multicast Discovery:**
  - Dispatches standard M-SEARCH queries over UDP `239.255.255.250:1900` with Android `MulticastLock`.
  - Includes direct unicast probe fallback if router AP-isolation blocks multicast.
  - Automatically fetches and parses the advertised XML device description to reveal the true `friendlyName`, `manufacturer`, `modelName`, and service URLs.
  - Displays explicit warning: *"Do NOT assume a DLNA media renderer supports navigation commands. UPnP/DLNA specifications define media rendering and volume, not remote D-Pad navigation."*
- **Evidence-Backed Port Audit:**
  - Tests only ports with documented smart TV justifications: DIAL (8008), dynamic UPnP (49152, 49153, 7676, 52235), Google TV TLS (6466, 6467), Vestel (56789), and baseline HTTP (80, 8088).
  - Assigns confidence ratings (`High Confidence Match`, `Investigative Match`, `Confirmed Incompatible`, `Unconfirmed`).
- **Live Activity Console & Export:**
  - Real-time event log with copy-to-clipboard report export (IP scrubbed by default).

### B. Remote
- Large touch targets for Up, Down, Left, Right, OK/Enter, Back, Home, Menu, Volume +/-, Mute, and Source/Input.
- Unsupported commands are cleanly disabled and explained per protocol.
- Active protocol and target IP banner.
- One-tap feedback buttons: `[✅ TV Reacted]` and `[❌ No Reaction]` immediately tag the test log.

### C. Protocol Lab
- Select between 6 protocol adapters:
  1. **DLNA / UPnP Media Renderer** (Candidate A & D)
  2. **Vestel Smart Center TCP 56789** (Candidate B)
  3. **DIAL REST / Novatek App Launch** (Candidate C2)
  4. **Android TV Remote TLS 6466/6467** (Candidate C1)
  5. **Targeted Smart TV Port Audit** (Candidate C3)
  6. **Demo Mode (Offline Simulation)**
- Displays what each method can and cannot do.
- Prominent honest reminder: *"An open port alone must never be treated as proof that remote commands work."*

### D. Connection
- Editable TV host/IP (default `192.168.1.38`) with instant IPv4 and hostname validation.
- Configurable network timeout slider (500 ms to 30,000 ms).
- Offline Demo Mode toggle.
- 4-point local Wi-Fi checklist.
- Discovered Royal TV hardware profile card.

### E. Logs & Diagnostics
- Summary metrics: Total Tests, Confirmed Reactions, No Reaction, Unconfirmed.
- Scrollable list of timestamped test logs.
- Export Report with default IP scrubbing (`[IP_REDACTED]`) for privacy.

---

## 4. Permissions & Deliberate Network Security

The app requests only minimal, necessary networking permissions in `AndroidManifest.xml`:
- `android.permission.INTERNET`: Socket and HTTP communication with the TV.
- `android.permission.ACCESS_NETWORK_STATE`: Detecting Wi-Fi connectivity.
- `android.permission.ACCESS_WIFI_STATE`: Verifying local network status.
- `android.permission.CHANGE_WIFI_MULTICAST_STATE`: UPnP / SSDP multicast discovery using `WifiManager.createMulticastLock()`.

### Deliberate Cleartext Policy
In `res/xml/network_security_config.xml`, cleartext HTTP is permitted deliberately for local private network communication (DLNA and DIAL endpoints do not support TLS). The app transmits **no telemetry, no analytics, and no credentials** to any external server.

---

## 5. Verified Build Artifact

- **Verified APK File:** `royal-remote-lab-debug.apk` (in project root)
- **Direct Build Output:** `app/build/outputs/apk/debug/app-debug.apk`
- **Package Size:** 12.38 MB (12,384,951 bytes)
- **Minimum SDK:** 24 (Android 7.0+)
- **Target SDK:** 36 (Android 15/16)

---

## 6. Building and Testing from Source

```powershell
# Run all unit tests (16 tests across 5 test suites)
$env:PATH = $env:PATH.Replace('"', ''); .\gradlew.bat test

# Compile debug APK
$env:PATH = $env:PATH.Replace('"', ''); .\gradlew.bat assembleDebug
```
