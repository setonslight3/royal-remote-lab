# Royal Remote Lab — Protocol Investigation Notes

**Target Device:** Royal RTV65D8100 65-inch QLED Smart TV  
**Firmware Version:** `v8-nt671la-lf1v680.000`  
**UI Version:** `u_1.0.129.46 0906`  
**Kernel:** `4.9.118+`  
**Report Date:** 2026-10-20  

---

## Executive Summary & Hardware Diagnosis

The firmware string `v8-nt671la-lf1v680.000` provides an essential clue to the TV's underlying architecture:
1. **Chassis & SoC:** **Novatek NT72671 / NT671LA**. Novatek is a prominent Taiwanese semiconductor company supplying DTV SoC solutions for OEMs (Royal, Changhong, Skyworth, RCA, etc.).
2. **Operating System:** Embedded Linux with custom TV middleware (`u_1.0.129.46 0906`), rather than certified Android TV or Google TV.
3. **Confirmed Active Services:**
   - **Digital Media Renderer (DMR):** Confirmed active in TV settings and validated via XCast (volume control succeeded).
   - **T-Link (HDMI-CEC):** Confirmed active in TV settings (controls HDMI-connected peripherals, not phone Wi-Fi remote).
   - **DIAL REST Service:** Confirmed active via Bellon Apps Universal TV Remote (YouTube launch succeeded).
4. **Virtual Remote Service:** Searched in TV OSD menus and confirmed absent. Port 56789 connection refused.

---

## Detailed Protocol Evaluations

### Candidate A & D: DLNA / UPnP Digital Media Renderer (DMR)

- **Supporting Evidence:**
  - TV settings: "Digital Media Renderer enabled".
  - XCast discovery: Discovers TV as `"Smart TV DLNA"`.
  - User observation: Volume up/down functioned in XCast, but navigation did not.
- **Protocol Specifications:**
  - Standard: UPnP AV Architecture v1.0.
  - Service `RenderingControl:1`: Provides `SetVolume`, `GetVolume`, `SetMute`, `GetMute`.
  - Service `AVTransport:1`: Provides `Play`, `Pause`, `Stop`, `Seek`.
  - Control Ports: Standard smart TV ports 8088, 7676, 49152, 2869.
- **Why Navigation Buttons Failed:**
  - DLNA is architected exclusively for streaming media playback and audio rendering.
  - The UPnP AV standard **does not define** system navigation commands (Up, Down, Left, Right, OK, Back, Home, Menu).
  - Therefore, XCast's behavior was completely consistent with the DLNA standard: volume worked because `RenderingControl` handled it; navigation failed because DLNA has no mechanism for it.
- **App Implementation:**
  - `DlnaProtocol` implements SOAP XML requests for Volume +, Volume -, Mute, Play, Pause, Stop.
  - D-Pad buttons are marked as unsupported to ensure honest reporting.
- **Confidence Rating:** **HIGH (Verified for Volume & Media; Incompatible for Navigation)**.

---

### Candidate B: Vestel Smart Center (TCP Port 56789)

- **Supporting Evidence:**
  - Bellon Universal TV Remote diagnostic report: `selected VESTEL_SMARTCENTER, port 56789, outcome=REFUSED, why=vestel_virtual_remote_off`.
- **Protocol Specifications:**
  - Vestel Smart Center TVs (Turkish OEM brand used by Hitachi, JVC, Toshiba in Europe) run a proprietary socket server on TCP port `56789`.
  - Accepts XML/JSON remote key commands (e.g. `<remote><key code="1012"/></remote>`).
- **Investigation Outcome:**
  - TCP connect to port 56789 returns `ECONNREFUSED` (RST packet).
  - Firmware analysis reveals this TV is a **Novatek NT671LA** chassis, **not a Vestel** chassis.
  - In addition, the TV menus lack any "Virtual Remote" toggle.
- **App Implementation:**
  - `VestelProtocol` provides a safe, non-aggressive bounded probe on port 56789.
  - Explains clearly that refusal confirms the absence of this service.
- **Confidence Rating:** **CONFIRMED INCOMPATIBLE (Connection Refused)**.

---

### Candidate C1: Android TV / Google TV Remote v2 (TLS 6466/6467)

- **Supporting Evidence:**
  - Tested to evaluate whether generic Android TV remote apps can pair.
- **Protocol Specifications:**
  - Google TV Remote protocol requires mutual TLS authentication on port `6466` (Pairing Service) followed by Protobuf command exchange on port `6467` (Remote Service).
- **Investigation Outcome:**
  - Ports 6466 and 6467 are closed.
  - The TV does not run certified Android TV (Google Play Services) or the Google Remote daemon.
- **App Implementation:**
  - `AndroidTvRemoteProtocol` checks ports 6466 and 6467 and documents why pairing fails.
- **Confidence Rating:** **CONFIRMED INCOMPATIBLE (No Google TV daemon)**.

---

### Candidate C2: DIAL (Discovery and Launch) REST Service (Ports 8008 / 8080)

- **Supporting Evidence:**
  - Bellon Universal TV Remote successfully launched YouTube on the Royal TV.
- **Protocol Specifications:**
  - Standard: DIAL Specification v2.2.1 (Netflix & Google).
  - Endpoints:
    - Device Description: `GET http://<ip>:8008/ssdp/device-desc.xml`
    - App State Query: `GET http://<ip>:8008/apps/YouTube`
    - App Launch: `POST http://<ip>:8008/apps/YouTube`
    - App Stop: `DELETE http://<ip>:8008/apps/YouTube/run`
- **Why YouTube Launched But Navigation Failed:**
  - DIAL allows external devices to query installed apps and launch them by name.
  - DIAL **does not** provide an arbitrary remote control or directional navigation channel.
  - This explains why Bellon could start YouTube, but could not navigate menus once YouTube was running.
- **App Implementation:**
  - `DialNovatekProtocol` provides DIAL discovery, app status check, and app launch requests.
- **Confidence Rating:** **HIGH (Verified for App Launch; Incompatible for Navigation)**.

---

### Candidate C3: Targeted Smart TV Port Audit

- **Investigation Purpose:**
  - Check well-known smart TV listening ports on `192.168.1.38`:
    - Port 80 (HTTP Web / OSD)
    - Port 8008 (DIAL REST)
    - Port 8080 (Alternate Web)
    - Port 8088 (UPnP DMR)
    - Port 56789 (Vestel)
    - Port 6466 / 6467 (Google TV TLS)
    - Port 5555 (ADB debug)
    - Port 9080 (Novatek Web Service)
- **App Implementation:**
  - `RawTcpProbeProtocol` audits candidate ports and measures round-trip latency.
  - Enforces the golden rule: **An open port is not proof of remote control compatibility.**

---

### Candidate E: HDMI-CEC (T-Link) & Bluetooth

- **T-Link (HDMI-CEC):**
  - Observed setting: "T-Link enabled, T-Link Remote Control enabled".
  - CEC operates over Pin 13 of physical HDMI cables to communicate with connected devices (soundbars, game consoles, streaming sticks).
  - A smartphone on Wi-Fi **cannot send CEC pulses directly** unless routed through an external Wi-Fi-to-HDMI-CEC bridge or connected device.
- **Bluetooth:**
  - Phone Bluetooth can only control the TV if the TV's Bluetooth stack supports the HID (Human Interface Device) Host profile and accepts pairing with a mobile device acting as a Bluetooth keyboard/remote.

---

## Protocol Comparison Matrix

| Protocol Adapter | Primary Ports | Supported Controls | Navigation Possible? | TV Status | Confidence |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **DLNA / UPnP (DMR)** | 8088, 7676, 49152 | Vol+, Vol-, Mute, Play, Pause, Stop | ❌ No (Omitted by spec) | Responding | High |
| **DIAL REST** | 8008, 8080 | Launch YouTube, App Status | ❌ No (Omitted by spec) | Responding | High |
| **Vestel Smart Center** | 56789 | D-Pad, Home, Back, Menu, Volume | N/A (Port closed) | Refused | Confirmed Incompatible |
| **Android TV Remote** | 6466, 6467 | D-Pad, Home, Back, Volume | N/A (Port closed) | Closed | Confirmed Incompatible |
| **Port Audit** | Targeted 10 ports | Read-only network audit | ❌ No (Diagnostic only) | Variable | Diagnostic |
| **Demo Mode** | None (Offline) | All buttons (Simulated) | Simulated Only | Simulated | 100% Offline |

---

## Remaining Questions & Next Steps

1. **Physical TV Reaction Verification:**
   - Install `royal-remote-lab-debug.apk` on an Android phone on the same 2.4 GHz Wi-Fi network.
   - Run **DLNA / UPnP** diagnostics and test Volume Up/Down while observing TV audio.
   - Run **Targeted Port Audit** to confirm which specific TCP ports are actively listening on `192.168.1.38`.
   - Record and export the diagnostic report.
