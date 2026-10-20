# Royal Remote Lab

Native Android application and protocol investigation lab designed to test, discover, and analyze local Wi-Fi control methods for the **Royal RTV65D8100 65-inch QLED Smart TV**.

---

## 1. Overview & Project Purpose

**Royal Remote Lab** is a real network protocol testing tool, not just a decorative TV remote UI. It allows you to discover TV services over SSDP/UPnP, safely probe documented smart TV ports, switch between candidate control adapters, record physical screen reactions honestly, and investigate whether system navigation is possible over local Wi-Fi.

### Target TV Hardware Profile
- **Model:** Royal RTV65D8100 (65" QLED Smart TV)
- **Discovery Name:** `Smart TV`
- **Current Target IP:** `192.168.1.46` (updated from initial DHCP assignment `192.168.1.38`)
- **Software Build:** `v8-nt671la-lf1v680.000`
- **UI Version:** `u_1.0.129.46 0906`
- **Kernel Version:** `4.9.118+`
- **Chassis Platform:** Novatek NT671LA (Linux/custom Smart TV OS)
- **Observed Active Services:** Digital Media Renderer (DMR) enabled, T-Link (HDMI-CEC) enabled, Wi-Fi 2.4 GHz enabled.
- **Empirical Network Findings:**
  - **Port 56789 (TCP):** **ACCEPTS CONNECTIONS**. The TV runs an HTTP service here. Raw socket byte writes produced no physical reaction; Task A now tests the standard Vestel Smart Center HTTP POST protocol (`POST /apps/SmartCenter` with XML payload).
  - **Port 9080 (TCP):** **RETURNS HTTP 200**. Active web daemon on the Novatek platform; Task B performs diagnostic probes (`GET /`, header inspection for `Application-URL`, and candidate UPnP/DIAL descriptor paths).
  - **Ports 80 & 8088 (TCP):** **REFUSED**. Confirmed closed in earlier tests.

---

## 2. Architecture & Design Principles

The application is built in **Kotlin** with modern **Jetpack Compose** and **Material 3**, strictly separating protocol logic from UI components:

```
app/src/main/java/com/example/royalremotelab/
├── model/
│   ├── ConnectionConfig.kt       # Host validation, timeouts, demo flag (default 192.168.1.46)
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
│   ├── VestelProtocol.kt         # Task A: Vestel Smart Center HTTP POST (/apps/SmartCenter)
│   ├── DialNovatekProtocol.kt    # DIAL REST YouTube launch (Ports 8008/8080/9080)
│   ├── AndroidTvRemoteProtocol.kt# Google TV Remote v2 (TLS 6466/6467 probe)
│   └── RawTcpProbeProtocol.kt    # Targeted candidate smart TV port audit
├── data/
│   └── LabRepository.kt          # StateFlow single source of truth & diagnostic coordinators
├── network/
│   ├── NetworkUtils.kt           # Safe HTTP client, header parser, bounded socket probes
│   ├── Port9080Investigator.kt   # Task B: Port 9080 HTTP audit, DIAL headers & path probes
│   ├── HostReachabilityChecker.kt# ICMP/socket reachability & documented port audit
│   ├── SsdpDiscoveryManager.kt   # Bounded UDP multicast discovery & direct unicast fallback
│   └── DeviceDescriptionParser.kt# Safe XML device description DOM parser
└── ui/
    ├── AppShell.kt               # Scaffold with 5-tab NavigationBar
    ├── screens/
    │   ├── DiagnosticsScreen.kt  # Host reachability, SSDP discovery & Section 2: Port 9080 Audit
    │   ├── RemoteScreen.kt       # D-Pad, Volume, System keys & Task A Vestel HTTP Inspector
    │   ├── ProtocolLabScreen.kt  # Candidate selector & diagnostic runner
    │   ├── ConnectionScreen.kt   # IP input (192.168.1.46), reachability ping & TV specs
    │   └── LogsScreen.kt         # Log metrics, reaction tagger & report export
    └── theme/                    # Material 3 Color, Theme & Typography
```

---

## 3. Five Core App Screens

### A. Diagnostics (Safe Network Investigation)
- **Host Reachability Audit:**
  - Evaluates ICMP echo ping and fallback socket connections.
  - Explains why a failed ping does **not** mean the TV is offline.
- **SSDP / UPnP Multicast Discovery:**
  - Dispatches standard M-SEARCH queries over UDP `239.255.255.250:1900` with Android `MulticastLock`.
  - Includes direct unicast probe fallback if router AP-isolation blocks multicast.
  - Automatically fetches and parses XML device descriptions.
- **Task B: Port 9080 Investigation & DIAL Audit:**
  - Sends `GET /` to `http://192.168.1.46:9080/` and parses headers (`Application-URL`, `Server`, `Location`).
  - Probes candidate paths: `/description.xml`, `/ssdp/device-desc.xml`, `/apps`, `/dial`, `/dd.xml`, `/upnp`.
  - If a DIAL `Application-URL` is discovered, issues a safe read-only `GET <Application-URL>/YouTube` to inspect app state (no blind POST launches).
- **Evidence-Backed Port Audit:**
  - Tests only ports with documented smart TV justifications: 9080, 56789, 8008, 49152, 49153, 7676, 52235, 6466, 6467, 80, 8088.

### B. Remote & Task A Vestel HTTP Inspector
- Remote keys for Volume Up, Volume Down, Mute, Up, Down, Left, Right, OK, Back, Home.
- **Task A Live HTTP Inspector Card:**
  - Displays target endpoint: `POST http://192.168.1.46:56789/apps/SmartCenter`
  - Shows request headers: `application_name: vestel smart center`, `Content-Type: text/plain; charset=ISO-8859-1`
  - Shows XML payload: `<?xml version='1.0' ?><remote><key code='<CODE>'/></remote>`
  - Explicitly tracks 4 distinct verification stages:
    1. **TCP Connected**
    2. **HTTP Response Received**
    3. **HTTP Status Successful**
    4. **User Confirmed TV Reacted**
  - Physical observation buttons (`[✅ TV Reacted]` / `[❌ No Reaction]`).

### C. Protocol Lab
- Select between 6 protocol adapters:
  1. **Vestel Smart Center HTTP POST** (Candidate B — Port 56789)
  2. **DLNA / UPnP Media Renderer** (Candidate A & D)
  3. **DIAL REST / Novatek App Launch** (Candidate C2 — Ports 8008/9080)
  4. **Android TV Remote TLS 6466/6467** (Candidate C1)
  5. **Targeted Smart TV Port Audit** (Candidate C3)
  6. **Demo Mode (Offline Simulation)**

### D. Connection
- Configured TV host/IP (default `192.168.1.46`) with instant IPv4 and hostname validation.
- Configurable network timeout slider (500 ms to 30,000 ms).
- Offline Demo Mode toggle.
- Discovered Royal TV hardware profile card.

### E. Logs & Diagnostics
- Summary metrics: Total Tests, Confirmed Reactions, No Reaction, Unconfirmed.
- Scrollable list of timestamped test logs with full Vestel HTTP exchange details and Port 9080 findings.
- Export Report with default IP scrubbing (`[IP_REDACTED]`) for privacy.

---

## 4. Permissions & Deliberate Network Security

The app requests only minimal, necessary networking permissions in `AndroidManifest.xml`:
- `android.permission.INTERNET`: Socket and HTTP communication with the TV.
- `android.permission.ACCESS_NETWORK_STATE`: Detecting Wi-Fi connectivity.
- `android.permission.ACCESS_WIFI_STATE`: Verifying local network status.
- `android.permission.CHANGE_WIFI_MULTICAST_STATE`: UPnP / SSDP multicast discovery using `WifiManager.createMulticastLock()`.

### Deliberate Cleartext Policy
In `res/xml/network_security_config.xml`, cleartext HTTP is permitted deliberately for local private network communication (ports 56789, 9080, 8008, DLNA). The app transmits **no telemetry, no analytics, and no credentials** to any external server.

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
# Run all unit tests (20 tests across 6 test suites)
$env:PATH = $env:PATH.Replace('"', ''); .\gradlew.bat test

# Compile debug APK
$env:PATH = $env:PATH.Replace('"', ''); .\gradlew.bat assembleDebug
```
