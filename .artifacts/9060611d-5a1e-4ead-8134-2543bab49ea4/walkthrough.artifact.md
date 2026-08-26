# Walkthrough - SMS Status Tracking (MVP Stage 1)

I have implemented SMS status tracking in the SMS Gateway application. This includes data persistence for message statuses, a broadcast receiver to handle SMS system events, and updates to the HTTP server to support status queries.

## Changes Made

### 1. Data Model & Persistence
- Created [MessageStatus.kt](file:///C:/Users/Spectr/AndroidStudioProjects/SMSGateway/app/src/main/java/ru/myski/vodokanal/smsgateway/data/MessageStatus.kt): A data class to hold SMS tracking information (ID, recipient, status, parts count, etc.). It includes methods for JSON serialization using `JSONObject`.
- Created [MessageStatusStore.kt](file:///C:/Users/Spectr/AndroidStudioProjects/SMSGateway/app/src/main/java/ru/myski/vodokanal/smsgateway/data/MessageStatusStore.kt): A repository class using `SharedPreferences` to persist `MessageStatus` objects as JSON strings.

### 2. SMS Status Tracking
- Created [SmsStatusReceiver.kt](file:///C:/Users/Spectr/AndroidStudioProjects/SMSGateway/app/src/main/java/ru/myski/vodokanal/smsgateway/service/SmsStatusReceiver.kt): A `BroadcastReceiver` that handles:
    - `ACTION_SMS_SENT`: Updates `sentParts` and sets status to `SENT` or `SEND_FAILED`.
    - `ACTION_SMS_DELIVERED`: Extracts PDU to get raw status and updates `deliveredParts`. Sets status to `DELIVERED` once all parts are received.
- Registered the receiver in [AndroidManifest.xml](file:///C:/Users/Spectr/AndroidStudioProjects/SMSGateway/app/src/main/AndroidManifest.xml) with `exported="false"` for security.

### 3. HTTP Server Updates
- Modified [SmsServer.kt](file:///C:/Users/Spectr/AndroidStudioProjects/SMSGateway/app/src/main/java/ru/myski/vodokanal/smsgateway/service/SmsServer.kt):
    - **GET `/status/{messageId}`**: Added a new endpoint to retrieve the current status of a message. It checks for authorization using the same API key as POST.
    - **POST `/`**:
        - Now generates a unique `messageId` (UUID).
        - Saves an initial `QUEUED` status in the store.
        - Configures `PendingIntent`s for each SMS part to track sending and delivery.
        - Returns the `messageId` and initial `status` in the JSON response.

## Verification Results

### Build
- Successfully performed a full project build: `gradle :app:assembleDebug`.

### Code Review
- Verified that `PendingIntent`s use `FLAG_IMMUTABLE` as required for Android 12+.
- Ensured `BroadcastReceiver` uses the app's package name for targeted delivery of non-exported broadcasts.
- Confirmed JSON handling uses `org.json.JSONObject` as requested.

## Usage

### Sending SMS (POST)
**Endpoint**: `POST /`
**Header**: `Authorization: <API_KEY>`
**Body**: `{"to": "+1234567890", "message": "Hello World"}`
**Response**: `{"success": true, "messageId": "...", "status": "QUEUED"}`

### Checking Status (GET)
**Endpoint**: `GET /status/<messageId>`
**Header**: `Authorization: <API_KEY>`
**Response**: `{"messageId": "...", "status": "SENT", "sentParts": 1, ...}`
