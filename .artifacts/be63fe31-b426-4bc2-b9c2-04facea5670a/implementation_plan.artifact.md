# Implementation Plan - Task_3_Compose_Dashboard_UI

Develop a Material 3 dashboard for the SMS Gateway app, including IP display, API key management, service status, and control.

## Proposed Changes

### 1. Update `SmsGatewayService`
- Add a static flag to track service state.
- Update the flag in `onStartCommand` and `onDestroy`.

### 2. Implement Dashboard UI in `MainActivity.kt`
- **Permission Handling**: Use `rememberLauncherForActivityResult` for `SEND_SMS` and `POST_NOTIFICATIONS`.
- **IP Detection**: Utility to find local IPv4.
- **UI Components**:
  - `LargeTopAppBar` with title "SMS Gateway".
  - `Card` for IP Address with "http://<ip>:8082".
  - `Card` for API Key with a "Copy" icon.
  - `Card` or `Row` for Status ("Running" / "Stopped").
  - `Button` for Start/Stop control.
- **State Management**:
  - `mutableStateOf` for service status, IP address, and API key.
  - `LifecycleEventObserver` or `onResume` to refresh status when activity is brought to foreground.

## Verification Plan

### Manual Verification
- Launch the app and check if it asks for permissions.
- Verify the local IP address is correctly displayed.
- Test the "Copy" API key button.
- Start the service and verify the status changes to "Running".
- Stop the service and verify the status changes to "Stopped".
- Check if the service notification appears when running.
