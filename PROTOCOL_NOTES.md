# Royal Remote Lab — Protocol Investigation Notes

**Target Device:** Royal RTV65D8100 65-inch QLED Smart TV  
**Firmware Version:** `v8-nt671la-lf1v680.000`  
**UI Version:** `u_1.0.129.46 0906`  
**Kernel:** `4.9.118+`  
**Current Local IP:** `192.168.1.46` (previously `192.168.1.38`)  
**Last Updated:** 2026-10-20  

---

## 1. Empirical Test Results on 192.168.1.46

During network probes against the Royal RTV65D8100 TV at `192.168.1.46`:
1. **TCP Port 56789 (Vestel Smart Center):** **ACCEPTS CONNECTIONS (`OPEN`)**  
   - Earlier tests sent raw numeric byte arrays directly to the socket, which failed to elicit a reaction from the TV.
   - Genuine Vestel Smart Center protocol communicates via an HTTP/1.1 daemon listening on this port, accepting XML payloads via HTTP POST.
2. **TCP Port 9080 (Web Daemon / DIAL Candidate):** **HTTP 200 OK**  
   - Port 9080 responds with standard HTTP 200, confirming an active web service on the Novatek NT671LA platform.
   - Probed to discover whether it hosts device description XML, DIAL REST endpoints (`Application-URL`), or smart TV web portals.
3. **TCP Port 80 & 8088:** **CONNECTION REFUSED (`ECONNREFUSED`)**  
   - Closed on this TV chassis.
4. **ICMP Ping Status:**  
   - Smart TV network stacks often drop ICMP Echo requests for power saving or security. A failed ping does **not** indicate the TV is offline.

---

## 2. Task A: Vestel Smart Center HTTP Specification

### Exact Request Format
```http
POST http://192.168.1.46:56789/apps/SmartCenter HTTP/1.1
Host: 192.168.1.46:56789
application_name: vestel smart center
Content-Type: text/plain; charset=ISO-8859-1
Content-Length: 53
Connection: close

<?xml version='1.0' ?><remote><key code='1016'/></remote>
```

### Hypothesized Key Codes (Vestel Smart Center)
| Function | Key Code | Hypothesis Status |
| :--- | :--- | :--- |
| **Volume Up** | `1016` | Key hypothesis test |
| **Volume Down** | `1017` | Key hypothesis test |
| **Mute** | `1009` | Toggle mute |
| **D-Pad Up** | `1012` | Directional navigation |
| **D-Pad Down** | `1013` | Directional navigation |
| **D-Pad Left** | `1014` | Directional navigation |
| **D-Pad Right** | `1015` | Directional navigation |
| **OK / Enter** | `1053` | Confirmation |
| **Back** | `1022` | Navigation return |
| **Home** | `1046` | Smart TV Launcher |

### 4-Stage Verification Policy
A command is only considered physically successful if stage 4 is confirmed:
1. `TCP Connected` — Socket successfully opened within 3000ms.
2. `HTTP Response Received` — Status code and response headers returned by the server.
3. `HTTP Status Successful` — HTTP 200/204 response from `/apps/SmartCenter`.
4. `User Confirmed TV Reacted` — Human visual/audible confirmation that the TV reacted.

---

## 3. Task B: Port 9080 Investigation Specification

### Diagnostic Audit Steps
1. **Root Audit (`GET /`):**
   - Query `http://192.168.1.46:9080/`
   - Record HTTP status, response headers (`Application-URL`, `Server`, `Location`), and a bounded (512-byte) body preview.
2. **Candidate Path Probes:**
   - `GET /description.xml` — Root UPnP device description.
   - `GET /ssdp/device-desc.xml` — SSDP advertised descriptor.
   - `GET /apps` — DIAL standard application root.
   - `GET /dial` — Alternative DIAL root.
   - `GET /dd.xml` — Compact UPnP device description.
   - `GET /upnp` — UPnP service directory.
3. **DIAL YouTube State Query (Read-Only):**
   - If `Application-URL` header is discovered (e.g. `http://192.168.1.46:9080/apps/`):
   - Issue read-only `GET <Application-URL>/YouTube` to inspect app state (`<state>running</state>` or `<state>stopped</state>`).
   - **Safety Boundary:** Never issue blind `POST` app-launch requests until the service and endpoint have been verified.

---

## 4. Evidence-Backed Port Matrix

| Port | Protocol / Service | Justification & Clue | Expected / Observed Outcome | Confidence Rating |
| :--- | :--- | :--- | :--- | :--- |
| **56789 (TCP)** | Vestel Smart Center HTTP | Open port on 192.168.1.46 | **OPEN (Accepts TCP)** | Active Investigation |
| **9080 (TCP)** | HTTP Web / DIAL Daemon | Returns HTTP 200 on 192.168.1.46 | **OPEN (HTTP 200)** | Active Investigation |
| **1900 (UDP)** | SSDP Multicast | Standard UPnP discovery | Responding on network | High Confidence |
| **8008 (TCP)** | DIAL REST | Documented standard used by Bellon | Candidate Open | High Confidence Match |
| **49152 (TCP)** | UPnP Dynamic Port 1 | Standard ephemeral port bound by DMR | Candidate Open | Investigative Match |
| **49153 (TCP)** | UPnP Dynamic Port 2 | Secondary dynamic port for AVTransport | Candidate Open | Investigative Match |
| **7676 (TCP)** | UPnP Media Server | Commonly assigned to Smart TV DMR daemons | Candidate Open | Investigative Match |
| **52235 (TCP)** | Alternate UPnP DMR | Embedded Linux smart TV DMR alternative port | Candidate Open | Investigative Match |
| **80 (TCP)** | HTTP Web Server | Tested baseline HTTP web management | **Refused** | Confirmed Closed |
| **8088 (TCP)** | Static UPnP DMR | Tested standard static DLNA port | **Refused** | Confirmed Closed |
| **6466 (TCP)** | Google TV Pairing | Checks for certified Google TV Remote v2 | Closed / Refused | Confirmed Incompatible |
| **6467 (TCP)** | Google TV Remote | Protobuf remote control channel | Closed / Refused | Confirmed Incompatible |

