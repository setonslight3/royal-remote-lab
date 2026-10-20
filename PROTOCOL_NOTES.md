# Royal Remote Lab — Protocol Investigation Notes

**Target Device:** Royal RTV65D8100 65-inch QLED Smart TV  
**Firmware Version:** `v8-nt671la-lf1v680.000`  
**UI Version:** `u_1.0.129.46 0906`  
**Kernel:** `4.9.118+`  
**Configured Local IP:** `192.168.1.38`  
**Last Updated:** 2026-10-20  

---

## 1. Empirical Test Results on 192.168.1.38

During physical Wi-Fi testing against the Royal RTV65D8100 TV at `192.168.1.38`:
1. **TCP Port 80 (HTTP):** **CONNECTION REFUSED (`ECONNREFUSED`)**  
   - The TV does not expose a standard unauthenticated web interface on port 80.
2. **TCP Port 8088 (Default UPnP DMR):** **CONNECTION REFUSED (`ECONNREFUSED`)**  
   - Port 8088 is frequently used by some TV vendors (e.g. older Samsung/LG), but is **not** the listening port for this Novatek NT671LA chassis.
3. **TCP Port 56789 (Vestel Smart Center):** **CONNECTION REFUSED (`ECONNREFUSED`)**  
   - Re-confirms the Bellon diagnostic finding (`outcome=REFUSED, why=vestel_virtual_remote_off`). The TV does not run the Vestel remote service.
4. **ICMP Ping Status:**  
   - Many smart TV network stacks deliberately ignore ICMP Echo requests (ping) for security and power saving. A failed ping **does not** indicate the TV is offline.

---

## 2. Why XCast and Bellon Worked While Static Port Tests Failed

This is the pivotal architectural insight:
- **XCast (DLNA):** Did **not** connect to port 8088. Instead, XCast broadcast an **SSDP M-SEARCH query** over UDP `239.255.255.250:1900`. The TV responded with its dynamically assigned UPnP location URL (e.g. `http://192.168.1.38:49152/description.xml` or `http://192.168.1.38:7676/...`).
- **Bellon (YouTube Launch):** Bellon targeted the **DIAL protocol** on TCP port `8008` (`POST /apps/YouTube`), an open standard designed by Netflix and Google for launching smart TV applications.
- **Why Navigation Did Not Work in Either App:**
  - **DLNA:** UPnP AV specifications explicitly define `RenderingControl` (Volume, Mute) and `AVTransport` (Play, Pause, Stop), but **omit system navigation** (D-Pad, Home, Back, Menu).
  - **DIAL:** Exclusively manages application lifecycles (launching/closing apps like YouTube), but **does not provide directional remote keystrokes**.

---

## 3. Evidence-Backed Port Audit Matrix

| Port | Protocol / Service | Justification & Clue | Expected / Observed Outcome | Confidence Rating |
| :--- | :--- | :--- | :--- | :--- |
| **1900 (UDP)** | SSDP Multicast | Standard UPnP discovery used by XCast to find DLNA DMR | Responding on network | High Confidence |
| **8008 (TCP)** | DIAL REST | Documented standard used by Bellon to launch YouTube | Candidate Open | High Confidence Match |
| **49152 (TCP)** | UPnP Dynamic Port 1 | Standard IANA ephemeral port bound by Novatek/Linux DLNA daemons | Candidate Open | Investigative Match |
| **49153 (TCP)** | UPnP Dynamic Port 2 | Secondary dynamic port for AVTransport / RenderingControl | Candidate Open | Investigative Match |
| **7676 (TCP)** | UPnP Media Server | Commonly assigned to Smart TV DMR daemons | Candidate Open | Investigative Match |
| **52235 (TCP)** | Alternate UPnP DMR | Embedded Linux smart TV DMR alternative port | Candidate Open | Investigative Match |
| **80 (TCP)** | HTTP Web Server | Tested baseline HTTP web management | **Refused** | Confirmed Closed |
| **8088 (TCP)** | Static UPnP DMR | Tested standard static DLNA port | **Refused** | Confirmed Closed |
| **56789 (TCP)** | Vestel Smart Center | Clue from Bellon app diagnostic | **Refused** | Confirmed Incompatible |
| **6466 (TCP)** | Google TV Pairing | Checks for certified Google TV Remote v2 pairing | Closed / Refused | Confirmed Incompatible |
| **6467 (TCP)** | Google TV Remote | Protobuf remote control channel | Closed / Refused | Confirmed Incompatible |

---

## 4. SSDP Device Description Discovery Procedure

The new safe **Diagnostics** screen in Royal Remote Lab executes the exact mechanism required to locate the TV's true endpoints:
1. Acquires Android Wi-Fi `MulticastLock`.
2. Sends bounded UDP M-SEARCH queries to `239.255.255.250:1900` for `ssdp:all`, `upnp:rootdevice`, `urn:schemas-upnp-org:device:MediaRenderer:1`, and `urn:dial-multiscreen-org:service:dial:1`.
3. Dispatches a direct unicast M-SEARCH to `192.168.1.38:1900` to bypass potential router AP-isolation.
4. When a `LOCATION: http://192.168.1.38:<port>/<path>.xml` header is received, the app safely issues an HTTP `GET` request and parses the XML.
5. The parsed XML exposes:
   - `<manufacturer>` (identifies the OEM platform)
   - `<modelName>` and `<modelNumber>`
   - `<friendlyName>`
   - `<serviceList>`: lists the exact control URLs for `RenderingControl`, `AVTransport`, or `DIAL`.

### Critical Protocol Boundary
Even if SSDP successfully discovers a DLNA Media Renderer with full service URLs, **it must not be assumed to support remote navigation**. UPnP DLNA is designed for media rendering and volume, not remote D-Pad navigation.
