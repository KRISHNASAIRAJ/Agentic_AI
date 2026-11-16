# SMS Detector — Privacy-first SMS Organizer

An Android SMS app (Kotlin + Jetpack Compose) with an on-device spam/scam classifier and a Spring Boot threat-intelligence backend. UI follows the Stitch designs in the `SMS` project: **Conversations Inbox, RCS Chat Thread, Security & Spam Protection, Scheduled & Smart Tools**.

> **Privacy principle:** message bodies never leave the device. The backend only receives SHA-256 hashes, sender hashes, and feedback labels.

## Architecture

```
android/                     Kotlin / Compose mobile client
├── app/src/main/java/com/smsdetector/app/
│   ├── data/db/             Room entities, DAOs (conversations, messages, scheduled, blocklist)
│   ├── data/detection/      On-device rule engine (OTP / Bank / Promo / Spam)
│   ├── data/repository/     SMS provider bridge, send/receive, contact names
│   ├── receiver/            SMS_RECEIVED + BOOT_COMPLETED receivers
│   ├── work/                WorkManager scheduled-message worker
│   └── ui/                  inbox, thread, security, scheduled + theme/navigation
└── app/src/{test,androidTest}/  unit, smoke and E2E tests

backend/                     Maven multi-module Spring Boot 3.4 (Java 21)
├── sms-common/              Shared DTOs (hash-only requests/responses)
├── detection-service/       POST /classify, /feedback, GET /signatures, /stats
├── reputation-service/      GET /{senderHash}, POST /batch, /report
└── docker-compose.yml       PostgreSQL + both services
```

## Run locally

### Android
1. Copy `android/local.properties.example` → `android/local.properties` and set your SDK path.
2. Open `android/` in Android Studio, or:
   ```bash
   cd android
   ./gradlew assembleDebug        # APK: app/build/outputs/apk/debug/app-debug.apk
   ./gradlew testDebugUnitTest    # JVM unit tests
   ./gradlew connectedDebugAndroidTest  # instrumented tests (device/emulator)
   ```

### Backend
```bash
cd backend
docker compose up -d postgres
./mvnw verify                     # unit + integration tests (H2 for tests)
./mvnw -pl detection-service spring-boot:run
```

## CI / CD (GitHub Actions)

Workflow: `.github/workflows/android-backend-ci.yml`

| Phase | Job | What it does |
|---|---|---|
| 1 | `validate-env` | Verifies JDK 21, required files, Gradle configuration |
| 2 | `unit-tests` | Android JVM tests + backend Surefire tests |
| 3 | `integration-tests` | Backend Failsafe `*IT` tests with H2 |
| 4 | `smoke-tests` | Instrumented `SmokeTest` on an emulator (KVM) |
| 5 | `e2e-tests` | Instrumented `EndToEndTest` journey on an emulator |
| 6 | `build-apk` | Builds `SMSDetector-debug.apk`, uploads downloadable artifact + SHA-256 |

The APK is downloadable from the **artifacts** section of the successful Actions run (retained 30 days).

## Roadmap
- [x] Phase 1: 4-screen SMS client + default-SMS-handler flow + Room
- [x] Phase 2: On-device detection, spam folder, OTP auto-delete
- [x] Phase 3: Spring Boot microservices (hash-only intel feed)
- [x] Phase 4: CI pipeline with all test phases + APK artifact
- [ ] Phase 5: Python ML scoring service + C++ JNI rule core, signed release APK
