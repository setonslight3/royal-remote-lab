# Royal Remote Lab — Testing & Verification Report

**Date of Execution:** 2026-10-20  
**Environment:** Windows (Antigravity Environment)  
**Host Java:** JetBrains JBR 25.0.3 (`C:\Program Files\Android\Android Studio\jbr`)  
**Gradle Version:** 9.1.0  
**Android Gradle Plugin:** 9.0.1  
**Target SDK:** 36 | **Minimum SDK:** 24 (Android 7.0+)  
**Compile SDK:** 36  

---

## 1. Automated Unit Test Results

Automated unit tests were executed using Gradle:
```powershell
$env:PATH = $env:PATH.Replace('"', ''); .\gradlew.bat test
```

### Test Execution Summary
- **Total Test Suites Executed:** 5
- **Total Unit Tests Executed:** 16
- **Failures:** 0
- **Errors:** 0
- **Skipped:** 0
- **Outcome:** **100% PASSED** (`BUILD SUCCESSFUL in 20s`)

### Detailed Test Suites
1. **`NetworkDiagnosticsTest`** (4 tests):
   - `parseSsdpResponse_extractsHeadersCorrectly`: Validated parsing of SSDP notification headers (`LOCATION`, `SERVER`, `ST`, `USN`).
   - `parseDeviceDescription_extractsMetadataAndServices`: Validated XML DOM parsing of `<friendlyName>`, `<manufacturer>`, `<modelName>`, `<modelNumber>`, and `<serviceList>`.
   - `resolveRelativeUrl_correctlyCombinesBaseAndRelative`: Validated relative URL resolution against the base location URL.
   - `evidenceBackedPorts_allHaveJustifications`: Verified that all candidate ports have documented justifications and valid confidence ratings.
2. **`ConnectionConfigTest`** (4 tests):
   - `defaultHost_isValid`: Validated default IP `192.168.1.38`.
   - `validIpv4Addresses_passValidation`: Validated RFC 1918 IPv4 addresses.
   - `invalidIpv4Addresses_failValidation`: Rejected invalid IPs and octet overruns.
   - `timeoutRange_validation`: Enforced 500ms – 30,000ms bounds.
3. **`ProtocolRegistryAndCommandTest`** (4 tests):
   - `protocolRegistry_hasAllCandidates`: Verified registration of all 6 protocol adapters.
   - `protocolRegistry_defaultSelection`: Confirmed default adapter switches appropriately between normal and Demo mode.
   - `dlnaProtocol_commandSupportDistinction`: Verified DLNA supports Volume/Mute/Media and returns `UNSUPPORTED` for D-Pad navigation.
   - `demoMode_honestSimulationTag`: Verified all simulated commands and diagnostics contain the `[SIMULATED]` tag.
4. **`ExportReportAndLogTest`** (2 tests):
   - `testLogEntry_ipScrubbingDefault`: Verified target IP is scrubbed to `[IP_REDACTED]` by default.
   - `labRepository_exportReportContainsStructure`: Verified report header, TV specs, metrics, and honest assessment notice.
5. **`MainScreenViewModelTest`** (2 tests):
   - Verified initial state and flow emission.

---

## 2. Compilation & Build Verification

The native Android APK was compiled directly in this environment:
```powershell
$env:PATH = $env:PATH.Replace('"', ''); .\gradlew.bat assembleDebug
```

- **Build Result:** **BUILD SUCCESSFUL in 14s**
- **Actionable Tasks:** 36 executed / up-to-date
- **Verified APK Artifact:**
  - Full Path: `C:\Users\Setons\.gemini\antigravity\scratch\royal-remote-lab\royal-remote-lab-debug.apk`
  - Build Path: `C:\Users\Setons\.gemini\antigravity\scratch\royal-remote-lab\app\build\outputs\apk\debug\app-debug.apk`
  - File Size: `12,384,951 bytes (~12.38 MB)`

---

## 3. Physical TV Diagnostics Checklist (For the User)

Follow this step-by-step checklist to test the new safe network diagnostics against your Royal TV at `192.168.1.38`:

### Step 1: Install Updated APK
- [ ] Transfer `royal-remote-lab-debug.apk` to your Android phone.
- [ ] Tap the file and select **Install / Update**.
- [ ] Launch **Royal Remote Lab** (it will open directly to the **Diagnostics** tab).

### Step 2: Audit Host Reachability
- [ ] Ensure phone and TV are on the **same 2.4 GHz Wi-Fi** network.
- [ ] Verify the target IP is `192.168.1.38`.
- [ ] Tap **📡 Audit Host Reachability**.
  - *Observe:* Check the technical analysis. If ICMP ping is dropped, remember that many smart TV stacks deliberately ignore ping packets for security and power saving while streaming or listening on UPnP.

### Step 3: Run SSDP / UPnP Discovery
- [ ] In the **Diagnostics** tab, tap **🔍 Run SSDP Discovery (UDP 1900)**.
  - The app will acquire Android's `MulticastLock`, dispatch M-SEARCH queries, and attempt a direct unicast probe to `192.168.1.38:1900`.
  - *Observe:* When the TV responds, review the **Discovered Device Card**:
    - Does it reveal the true manufacturer (e.g. Novatek/Changhong/Royal)?
    - Does it reveal the true Location URL (e.g. `http://192.168.1.38:49152/...` or `http://192.168.1.38:8008/...`)?
    - Which services are listed (e.g. `RenderingControl`, `AVTransport`, `DIAL`)?

### Step 4: Run Targeted Port Audit
- [ ] In the **Diagnostics** tab, tap **🛡️ Run Evidence-Backed Port Audit**.
  - The app checks ports 8008 (DIAL), 49152, 49153, 7676, 52235, 80, 8088, 56789, 6466, 6467.
  - *Observe:* Does Port 8008 or Port 49152 show **OPEN**?

### Step 5: Export Diagnostic Report
- [ ] Tap **📋 Export Report**.
- [ ] Choose whether to include the TV IP address (scrubbed by default).
- [ ] Tap **Copy to Clipboard** and paste the findings to review next steps!
