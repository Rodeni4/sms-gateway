# SMS Gateway

A simple and efficient Android SMS Gateway that allows you to send SMS messages via a REST API. Built with Jetpack Compose and Material 3.

## Features

- **REST API**: Send SMS messages using a POST request.
- **API Key Security**: Secure your gateway with a custom API key.
- **Foreground Service**: Runs in the background to ensure reliability.
- **Modern UI**: Clean and professional Material 3 interface with dynamic color support.
- **Status Monitoring**: Real-time status of the gateway and sent messages log.

## How to Use

1. **Install the App**: Install the APK on an Android device with a SIM card.
2. **Configure**: Open the app and set your desired **API Key** and **Port** (default is 8082).
3. **Start the Gateway**: Tap the "Start Gateway" button.
4. **Send SMS**: Use the REST API to send messages.

## API Usage

### Send SMS

**Endpoint:** `POST /`

**Headers:**
- `Authorization`: Your API Key
- `Content-Type`: application/json

**Body:**
```json
{
  "to": "+79000000000",
  "message": "Test message"
}
```

### Example (Windows PowerShell/CMD)

To test the gateway from a Windows computer on the same local network, use the following `curl` command:

```bash
curl.exe -X POST http://[DEVICE_IP]:8082/ -H "Authorization: YOUR_API_KEY" -H "Content-Type: application/json" -d "{\"to\": \"+79000000000\", \"message\": \"Test message\"}"
```

*Replace `[DEVICE_IP]` with the IP address shown in the app, and `YOUR_API_KEY` with the key you set.*

## Requirements

- Android 8.0 (API level 26) or higher.
- SIM card with SMS plan.
- Local network connectivity.

## Development

Built using:
- Kotlin
- Jetpack Compose
- Ktor (HTTP Server)
- Material 3
- DataStore (Settings)
- WorkManager (Background Tasks)
- Navigation 3
