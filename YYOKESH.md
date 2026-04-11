# Driver Safety AI: Technical Deep-Dive (YYOKESH)

This document provides a comprehensive A-Z breakdown of the **Driver Safety AI** system, including the exact code powering each major feature.

---

## 1. System Architecture
The system follows a distributed architecture where the **ESP32** acts as the high-speed data acquisition unit (Sensor Node) and the **Android App** acts as the intelligent processing unit (Master Node).

- **Hardware**: ESP32, IR Eye-Sensor, MQ3 Alcohol Sensor.
- **Communication**: Bluetooth Classic (SPP) with infinite auto-reconnect.
- **App Core**: Kotlin-based Foreground Service for 24/7 background monitoring.

---

## 2. Hardware: Sensor Scanning (ESP32)

The ESP32 runs a continuous loop that checks the IR sensor for eye closure and the MQ3 sensor for alcohol. 

### The Scanning Logic:
```cpp
void loop() {
  // 🔴 Alcohol Detection
  int alcoholValue = analogRead(MQ3_PIN);
  if (alcoholValue > alcoholThreshold) {
    digitalWrite(LED, HIGH); // Immediate visual alert on hardware
    SerialBT.println("ALC_DETECTED"); // Signal to app
  }

  // 😴 Eye Drowsiness Detection
  int eyeState = digitalRead(EYE_SENSOR_PIN); 
  if (eyeState == HIGH) { // HIGH = Obstacle (Eyes Closed)
    if (!isEyeClosed) {
      isEyeClosed = true;
      eyeClosedStartTime = millis(); 
    }
    unsigned long elapsedMillis = millis() - eyeClosedStartTime;
    int durationInSeconds = elapsedMillis / 1000;
    
    SerialBT.println(durationInSeconds); // Send closure time to phone
    digitalWrite(BUZZER, HIGH); // Beep on hardware
  } else {
    isEyeClosed = false;
    digitalWrite(BUZZER, LOW);
    SerialBT.println(0); // Send 0 to phone (Safe)
  }
  delay(100);
}
```
> **File Location:** `ESP32_Firmware.ino` (Arduino IDE)

---

## 3. Connectivity: The "Sticky" Bluetooth Connection

We designed the Bluetooth connection to be **unbreakable**. If the device disconnects, the app enters an infinite retry loop with exponential backoff.

### The Connection Loop (`BluetoothConnectionManager.kt`):
```kotlin
private suspend fun persistentConnect(device: BluetoothDevice) {
    while (!userDisconnected) { // Infinite loop until user clicks "Disconnect"
        val connected = tryConnect(device)
        if (connected) {
            _state.value = State.CONNECTED
            readLoop() // Stay here reading data
        }
        // If connection drops, wait 3 seconds and try again
        delay(3000L)
    }
}
```
> **File Location:** `app/src/main/java/com/driversafety/ai/connectivity/BluetoothConnectionManager.kt`

---

## 4. Intelligence: Data Processing & Level Triggering

The `ForegroundMonitorService` receives raw data strings from Bluetooth and converts them into danger levels.

### The Decision Logic (`ForegroundMonitorService.kt`):
```kotlin
private fun processRawData(raw: String) {
    val value = raw.trim().toIntOrNull() ?: return
    
    // Level Detection Thresholds
    val level = when {
        value >= 5 -> LEVEL_CRITICAL // 5+ seconds = Danger!
        value >= 2 -> LEVEL_MODERATE // 2s - 4s = Warning
        else -> LEVEL_SAFE
    }

    if (level == LEVEL_MODERATE) {
        tts.speak("Warning! You are feeling drowsy.") // Level 1 (TTS Only)
    } else if (level == LEVEL_CRITICAL) {
        triggerAlertScreen() // Level 2 (Full Popup)
    }
}
```
> **File Location:** `app/src/main/java/com/driversafety/ai/service/ForegroundMonitorService.kt`

---

## 5. Emergency Management: Level 2 Alert Sequence

When Level 2 is triggered, the `EmergencyAlertActivity` starts a strict, automated sequence of safety actions to rescue the driver.

### The Sequential Trigger (`EmergencyAlertActivity.kt`):
```kotlin
private fun triggerCriticalActions() {
    // 1. Voice Warning
    tts.speak("Alert! You are very sleepy. Please pull over.")

    // 2. Loud Alarm (1.2s delay)
    Handler(mainLooper).postDelayed({
        buzzer.startAlarm()
    }, 1200)

    // 3. Automated Navigation (4.5s delay)
    Handler(mainLooper).postDelayed({
        locationManager.openNearbyRestArea() // Launches Google Maps
    }, 4500)

    // 4. Cognitive Game (9s delay)
    Handler(mainLooper).postDelayed({
        showGamePanel() // Forces interaction to stop alarm
    }, 9000)
}
```
> **File Location:** `app/src/main/java/com/driversafety/ai/EmergencyAlertActivity.kt`

---

## 6. Proactive Safety: SOS & Location APIs

The system uses standard Android APIs to send rescue messages and track the driver’s location accurately.

### The SOS Implementation (`EmergencyManager.kt`):
```kotlin
fun sendEmergencySms(phone: String, location: Location?) {
    val mapsLink = "https://maps.google.com/?q=${location?.latitude},${location?.longitude}"
    val message = "EMERGENCY! Driver is in critical danger at location: $mapsLink"
    
    val smsManager = SmsManager.getDefault()
    smsManager.sendTextMessage(phone, null, message, null, null)
}
```
> **File Location:** `app/src/main/java/com/driversafety/ai/emergency/EmergencyManager.kt`

---

## 7. App Features Overview
| Feature | Implementation Detail |
|---|---|
| **Drive Time** | A real-time timer tracking total session duration. |
| **Weather Risk** | Uses OpenWeather API to detect if current rain/fog increases risk levels. |
| **Cognitive Game** | A "Spell Awake Backward" challenge that blocks the buzzer mute button until solved. |
| **Auto-Call** | Automatically dials the emergency contact after 14 seconds of no response. |

---

## 8. Summary of Operation (A-Z)
1. ESP32 monitors eyes via **IR pulses**.
2. Duration counts are sent to **Android via BT SPP**.
3. Android **Foreground Service** processes counts in real-time.
4. If counts cross thresholds, **TTS and full-screen alerts** activate.
5. High-danger states trigger **SOS SMS** and **Google Maps** rest area search.

---
**Document Created for: YYOKESH**
*Technical Source-Code Breakdown of the Driver Safety AI System.*
