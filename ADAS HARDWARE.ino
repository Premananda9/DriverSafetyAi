#include "BluetoothSerial.h"

BluetoothSerial SerialBT;

#define EYE_SENSOR_PIN 25
#define MQ3_PIN 34
#define LED 5
#define BUZZER 4
#define IN1 26
#define IN2 27

int alcoholThreshold = 3700;

bool alcoholState = false;
bool motorState = true;

unsigned long eyeClosedStartTime = 0;
bool isEyeClosed = false;

void setup() {
  Serial.begin(115200);
  SerialBT.begin("ESP32_DRIVER");

  pinMode(EYE_SENSOR_PIN, INPUT);
  pinMode(LED, OUTPUT);
  pinMode(BUZZER, OUTPUT);
  pinMode(IN1, OUTPUT);
  pinMode(IN2, OUTPUT);

  digitalWrite(LED, LOW);
  digitalWrite(BUZZER, LOW);

  digitalWrite(IN1, HIGH);
  digitalWrite(IN2, LOW);

  Serial.println("System Ready...");
}

void loop() {

  // ALCOHOL SECTION
  int alcoholValue = analogRead(MQ3_PIN);

  bool alcoholDetected = false;

  if (alcoholValue > alcoholThreshold) {
    alcoholDetected = true;
    digitalWrite(LED, HIGH);

    if (!alcoholState) {
      Serial.println("Alcohol Detected (Above Limit)");
      Serial.print("Sensor Value: ");
      Serial.println(alcoholValue);

      alcoholState = true;
    }

  } else {
    digitalWrite(LED, LOW);

    if (alcoholState) {
      Serial.println("Alcohol Cleared (Safe)");
      alcoholState = false;
    }
  }

  // DROWSINESS SECTION
  int eyeState = digitalRead(EYE_SENSOR_PIN);
  bool drowsyDetected = false;

  if (eyeState == HIGH) {

    if (!isEyeClosed) {
      isEyeClosed = true;
      eyeClosedStartTime = millis();
    }

    digitalWrite(BUZZER, HIGH);

    unsigned long elapsed = millis() - eyeClosedStartTime;
    int seconds = elapsed / 1000;

    Serial.print("Eye Closed: ");
    Serial.print(seconds);
    Serial.println(" sec");

    SerialBT.println(seconds);

    if (elapsed >= 4000) {   // 4 sec condition
      drowsyDetected = true;
    }

  } else {
    isEyeClosed = false;
    digitalWrite(BUZZER, LOW);
    SerialBT.println(0);
  }

  // MOTOR CONTROL (Alcohol & Drowsy)
  if (alcoholDetected && drowsyDetected) {

    if (motorState == true) {
      digitalWrite(IN1, LOW);
      digitalWrite(IN2, LOW);

      Serial.println("Motor OFF (Alcohol + Drowsy)");
      motorState = false;
    }

  } else {

    if (motorState == false) {
      digitalWrite(IN1, HIGH);
      digitalWrite(IN2, LOW);

      Serial.println("Motor ON");
      motorState = true;
    }
  }

  delay(100);
}