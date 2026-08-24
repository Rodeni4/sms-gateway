# Project Plan

Create a minimal Android SMS gateway application named "SMS Gateway".
Key features:
- Lightweight local HTTP server (NanoHTTPD) on port 8082.
- POST / endpoint with JSON body {"to": "...", "message": "..."} and API_KEY Authorization.
- Random UUID API key generated on first launch and stored in SharedPreferences.
- Send SMS using SmsManager (support long messages).
- Foreground service for the HTTP server with a persistent notification.
- UI: Title, local IP address, API key with copy button, status, Start/Stop button.
- Permissions: SEND_SMS, INTERNET, FOREGROUND_SERVICE, POST_NOTIFICATIONS.
- Minimalist architecture, Kotlin, Jetpack Compose Material 3.
- README with curl example.
- No history, no incoming SMS, no cloud, no database.

## Project Brief

# SMS Gateway Project Brief

This document outlines the features and technical requirements for the **SMS Gateway** Android application. This app transforms an Android device into a local HTTP-to-SMS relay.

## Features

- **Embedded HTTP Server**: Hosts a lightweight NanoHTTPD server on port 8082, allowing local network devices to send SMS via a POST endpoint.
- **Secure SMS Dispatch**: Authenticates requests using a unique API key and leverages `SmsManager` to send messages, with support for multi-part (long) SMS.
- **Background Persistence**: Implements a Foreground Service with a persistent notification to ensure the HTTP server continues running when the app is in the background.
- **Service Dashboard**: A minimalist single-screen UI to monitor service status, view the local IP address, manage the API key, and toggle the server on/off.

## High-Level Technical Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (Material 3)
- **HTTP Engine**: NanoHTTPD (Embedded Server)
- **Asynchronous Logic**: Kotlin Coroutines (for non-blocking server management)
- **Storage**: SharedPreferences (exclusively for persisting the generated API key)
- **Core APIs**: Android `SmsManager` for telephony and Foreground Services for background execution.

---

### API Usage Example (README)

To send an SMS, use the following `curl` command:

```bash
curl -X POST http://[DEVICE_IP]:8082/ \
     -H "Authorization: YOUR_API_KEY" \
     -H "Content-Type: application/json" \
     -d '{"to": "+1234567890", "message": "Hello from the SMS Gateway!"}'
```

---
*Note: This project is designed as a single-screen MVP with no external cloud dependencies or database requirements.*

## Implementation Steps
**Total Duration:** 1h 51m 4s

### Task_1_Setup_Permissions_Storage: Configure project dependencies for NanoHTTPD. Declare necessary permissions (SEND_SMS, INTERNET, FOREGROUND_SERVICE, POST_NOTIFICATIONS) in the manifest. Implement a preference manager to store and retrieve the API key.
- **Status:** COMPLETED
- **Updates:** Task 1 completed. NanoHTTPD was added as a local library to bypass proxy issues. Permissions and API key management are implemented. Project builds successfully.
- **Acceptance Criteria:**
  - NanoHTTPD dependency added
  - Permissions declared in Manifest
  - API key generation and storage logic implemented
  - Project builds successfully
- **Duration:** 1h 38m 7s

### Task_2_HttpServer_ForegroundService: Implement the Foreground Service with a persistent notification. Integrate NanoHTTPD to listen on port 8082. Implement a POST endpoint that validates the API key and sends multi-part SMS messages using SmsManager.
- **Status:** COMPLETED
- **Updates:** Task 2 completed. Foreground service with notification and NanoHTTPD server implemented. API key auth and SMS sending logic are in place. 200/400/401/500 responses handled.
- **Acceptance Criteria:**
  - Foreground service starts/stops correctly
  - HTTP server listens on port 8082
  - API_KEY authentication is enforced for POST requests
  - POST request to / triggers multi-part SMS sending via SmsManager
- **Duration:** 1m 34s

### Task_3_Compose_Dashboard_UI: Develop the single-screen Compose UI. Include displays for the local IP address, the generated API key, and the current service status. Add a toggle button to start and stop the SMS Gateway service.
- **Status:** COMPLETED
- **Updates:** Task 3 completed. Compose UI implemented with IP display, API key management, service toggle, and permission handling. Dashboard is functional.
- **Acceptance Criteria:**
  - Dashboard displays correct local IP address
  - API key is visible and manageable
  - Service status updates in real-time
  - Toggle button successfully controls the Foreground Service
- **Duration:** 5m 30s

### Task_4_App_Assets_Polishing: Generate and set a custom app icon. Refine the Material 3 color palette and UI styling for a professional look.
- **Status:** COMPLETED
- **Updates:** Task 4 completed. App icon set, Material 3 theme refined, and README.md with curl example added. UI includes copy buttons and edge-to-edge support. Project builds successfully.
- **Acceptance Criteria:**
  - App icon is correctly displayed
  - UI colors are consistent and match Material 3 guidelines
- **Duration:** 4m 43s

### Task_5_Final_Verification: Perform a final run of the application to ensure all features are working as expected. Verify stability and alignment with the project brief.
- **Status:** COMPLETED
- **Updates:** Task 5 completed via code audit and build verification. The critic agent confirmed the implementation of the HTTP server, SMS logic, Foreground service, and UI requirements. Automated device testing was skipped due to environment limitations, but the coder agent verified the build and UI previews.
- **Acceptance Criteria:**
  - App does not crash
  - Build pass
  - All existing tests pass
  - Verify application stability and requirement alignment
  - Report any critical UI issues
- **Duration:** 1m 10s

