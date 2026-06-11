# ⚽ FIFA World Cup 2026 Prediction App

This project is a modern prediction application for the **FIFA World Cup 2026** where users can predict group stage standings, match scores, and knockout brackets, earn points, and compete on a live leaderboard.

The project features a multiplatform architecture supporting both **Android (Native)** and **Web (Kotlin WasmJs / Compose Multiplatform)**.

---

## 🚀 Key Features

*   **Fixtures & Live Search:** Advanced fixture screen filterable by tournament stages (Group Matches, Round of 32/16/8, Knockouts) and searchable by teams.
*   **Live Group Standings:** Real-time calculated group standing tables updated dynamically based on predicted/played match results.
*   **Score Predictions:** Make match score predictions and earn points (+20 points for an exact score match, +5 points for predicting the correct outcome).
*   **Drag & Drop Group Predictions:** An interactive drag-and-drop mechanism to predict group standings from 1st to 4th place.
*   **Knockout Bracket:** A dynamic prediction tree showcasing match progressions from the Round of 32 all the way to the Finals.
*   **Live Leaderboard:** A real-time ranking table displaying all users' prediction points.
*   **Smart Team Notifications (FCM):**
    *   Custom notification preference dialogs letting users choose which teams they want to subscribe to.
    *   Automated push notifications delivered **exactly 1 hour before kickoff** to the browser/device.
    *   An instant local notification calculated upon saving to count down the exact remaining time to the user's nearest subscribed match.

---

## 🛠️ Tech Stack

### Web Application
*   **Core:** Kotlin WasmJs (WebAssembly)
*   **UI Framework:** Jetpack Compose Multiplatform (WasmJs)
*   **Network:** Ktor HTTP Client (REST API) & Kotlinx Serialization
*   **Image Loading:** Coil 3 (WasmJs compatible)
*   **Database:** Firebase Realtime Database
*   **Notification System:** Firebase Cloud Messaging (FCM) & JavaScript Service Worker interop

### Android Application
*   **Core:** Kotlin & Native Android SDK
*   **UI Framework:** Jetpack Compose (Material 3)
*   **Architecture:** ViewModel & LiveData
*   **Network:** Retrofit & OkHttp (TheSportsDB API)
