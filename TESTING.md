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
- **Total Test Suites Executed:** 6
- **Total Unit Tests Executed:** 20
- **Failures:** 0
- **Errors:** 0
- **Skipped:** 0
- **Outcome:** **100% PASSED** (`BUILD SUCCESSFUL in 33s`)

### Detailed Test Suites
1. **`VestelAndPort9080Test`** (4 tests):
   - `vestelKeyCodes_allRequiredCommandsMapped`: Confirmed mapping for Volume Up (1016), Volume Down (1017), Mute (1009), D-Pad (1012-1015), OK (1053), Back (1022), Home (1046).
   - `vestelXmlPayload_formatMatchesSpecification`: Validated XML body `<?xml version='1.0' ?><remote><key code='1016'/></remote>`.
   - `port9080ProbePaths_allContainSafeGetEndpoints`: Verified read-only probes for root `/`, `/description.xml`, `/ssdp/device-desc.xml`, `/apps`, `/dial`, `/dd.xml`, and `/upnp`.
   - `port9080ExtractDialUrl_parsesHeaderCorrectly`: Validated case-insensitive parsing of `Application-URL` and whitespace trimming.
2. **`NetworkDiagnosticsTest`** (4 tests):
   - `parseSsdpResponse_extractsHeadersCorrectly`: Validated parsing of SSDP notification headers (`LOCATION`, `SERVER`, `ST`, `USN`).
   - `parseDeviceDescription_extractsMetadataAndServices`: Validated XML DOM parsing of `<friendlyName>`, `<manufacturer>`, `<modelName>`, `<modelNumber>`, and `<serviceList>`.
   - `resolveRelativeUrl_correctlyCombinesBaseAndRelative`: Validated relative URL resolution against the base location URL.
   - `evidenceBackedPorts_allHaveJustifications`: Verified that all candidate ports have documented justifications and valid confidence ratings.
3. **`ConnectionConfigTest`** (4 tests):
   - `defaultHost_isValid`: Validated default IP `192.168.1.46`.
   - `validIpv4Addresses_passValidation`: Validated RFC 1918 IPv4 addresses.
   - `invalidIpv4Addresses_failValidation`: Rejected invalid IPs and octet overruns.
   - `timeoutRange_validation`: Enforced 500ms – 30,000ms bounds.
4. **`ProtocolRegistryAndCommandTest`** (4 tests):
   - `protocolRegistry_hasAllCandidates`: Verified registration of all 6 protocol adapters.
   - `protocolRegistry_defaultSelection`: Confirmed default adapter switches appropriately between normal and Demo mode.
   - `dlnaProtocol_commandSupportDistinction`: Verified DLNA supports Volume/Mute/Media and returns `UNSUPPORTED` for D-Pad navigation.
   - `demoMode_honestSimulationTag`: Verified all simulated commands and diagnostics contain the `[SIMULATED]` tag.
5. **`ExportReportAndLogTest`** (2 tests):
   - `testLogEntry_ipScrubbingDefault`: Verified target IP is scrubbed to `[IP_REDACTED]` by default.
   - `labRepository_exportReportContainsStructure`: Verified report header, TV specs, metrics, and honest assessment notice.
6. **`MainScreenViewModelTest`** (2 tests):
   - Verified initial state and flow emission.

---

## 2. Compilation & Build Verification

The native Android APK was compiled directly in this environment:
```powershell
$env:PATH = $env:PATH.Replace('"', ''); .\gradlew.bat assembleDebug
```

- **Build Result:** **BUILD SUCCESSFUL in 15s**
- **Actionable Tasks:** 36 executed / up-to-date
- **Verified APK Artifact:**
  - Full Path: `C:\Users\Setons\.gemini\antigravity\scratch\royal-remote-lab\royal-remote-lab-debug.apk`
  - Build Path: `C:\Users\Setons\.gemini\antigravity\scratch\royal-remote-lab\app\build\outputs\apk\debug\app-debug.apk`
  - File Size: `12,384,951 bytes (~12.38 MB)`

---

## 3. Physical TV Diagnostics Checklist (For the User)

Follow this step-by-step checklist to test Task A and Task B against your Royal TV at `192.168.1.46`:

### Step 1: Install Updated APK
- [ ] Transfer `royal-remote-lab-debug.apk` to your Android phone.
- [ ] Tap the file and select **Install / Update**.
- [ ] Launch **Royal Remote Lab**.

### Step 2: Test Task A — Vestel Smart Center (Port 56789)
- [ ] Switch to the **Remote** tab.
- [ ] Check the **Task A: Vestel Smart Center HTTP** live inspector card at the top.
- [ ] Tap **Vol +** or **Vol -**.
- [ ] Note the progression in the inspector:
  - Did **TCP Connected** turn green?
  - Did **HTTP Response Received** show an HTTP status code (e.g. 200 OK or 404)?
  - Did **HTTP Status Successful** light up?
  - **Most Importantly:** Did the TV's volume bar actually appear on the TV screen?
- [ ] Tap **[✅ TV Reacted]** if it changed the TV, or **[❌ No Reaction]** if it did not.
- [ ] Test **Mute**, **Up/Down/Left/Right**, **OK**, **Back**, and **Home**.

### Step 3: Test Task B — Port 9080 Investigation & DIAL Audit
- [ ] Switch to the **Diagnostics** tab.
- [ ] Scroll to **Section 2: Task B — Port 9080 Investigation & DIAL Audit**.
- [ ] Tap **🔎 Audit Port 9080 & Probe DIAL Paths**.
- [ ] Review the returned details:
  - What HTTP status was returned for `GET /`?
  - Were there headers such as `Application-URL`, `Server`, or `Location`?
  - Did any of the candidate descriptor paths (`/description.xml`, `/apps`, `/dial`) return HTTP 200?
  - If a DIAL `Application-URL` was discovered, what was the read-only YouTube state (`running` or `stopped`)?

### Step 4: Export Diagnostic Report
- [ ] Switch to the **Logs** tab.
- [ ] Tap **📋 Export Report**.
- [ ] Tap **Copy to Clipboard** and share the findings!
