import kotlinx.browser.window

object NotificationService {
    private val VAPID_KEY: String get() = getVapidKeyFromJs() ?: ""
    private val DATABASE_URL: String get() = getDatabaseUrlFromJs() ?: ""

    fun registerNotification(username: String) {
        if (!isNotificationSupported()) {
            println("Tarayıcı push bildirimlerini desteklemiyor.")
            return
        }

        val permission = getNotificationPermission()
        if (permission == "granted") {
            fetchAndSaveToken(username)
        } else if (permission == "default") {
            requestNotificationPermission {
                fetchAndSaveToken(username)
            }
        }
    }

    // Tarayıcı ve işletim sisteminin bildirim gösterip gösteremediğini test etmek için yerel fonksiyon
    fun showLocalTestNotification() {
        showLocalTestNotificationJs()
    }

    fun showLocalNotification(title: String, body: String) {
        showLocalNotificationJs(title, body)
    }

    private fun fetchAndSaveToken(username: String) {
        fetchFcmTokenJs(
            VAPID_KEY,
            onSuccess = { token ->
                saveTokenToDb(username, token)
            },
            onError = { error ->
                println("FCM Token alınamadı (HATA): " + error)
            }
        )
    }

    private fun saveTokenToDb(username: String, token: String) {
        val normalizedUsername = username.trim().lowercase()
        val url = "$DATABASE_URL/users/$normalizedUsername/fcmToken.json"
        saveTokenToDbJs(url, token)
    }

    fun saveSubscribedTeams(username: String, teams: List<String>) {
        val normalizedUsername = username.trim().lowercase()
        val url = "$DATABASE_URL/users/$normalizedUsername/subscribedTeams.json"
        
        // JSON formatına dönüştürme (Örn: {"turkey":true,"germany":true})
        val jsonStr = if (teams.isEmpty()) {
            "{}"
        } else {
            teams.joinToString(prefix = "{", postfix = "}") { "\"${it.lowercase().trim()}\":true" }
        }
        saveSubscribedTeamsJs(url, jsonStr)
    }

    fun getSubscribedTeams(username: String, callback: (List<String>) -> Unit) {
        val normalizedUsername = username.trim().lowercase()
        val url = "$DATABASE_URL/users/$normalizedUsername/subscribedTeams.json"
        
        getSubscribedTeamsJs(url) { jsonArrayStr ->
            val list = mutableListOf<String>()
            val clean = jsonArrayStr.replace("[", "").replace("]", "").replace("\"", "")
            if (clean.isNotBlank()) {
                clean.split(",").forEach {
                    list.add(it.trim())
                }
            }
            callback(list)
        }
    }
}

// ── Kotlin WasmJs JavaScript Birlikte Çalışabilirlik (Interop) Yardımcıları ──

@JsFun("() => typeof Notification !== 'undefined'")
private external fun isNotificationSupported(): Boolean

@JsFun("() => Notification.permission")
private external fun getNotificationPermission(): String

@JsFun("(onGranted) => { Notification.requestPermission().then(permission => { if (permission === 'granted') onGranted(); }); }")
private external fun requestNotificationPermission(onGranted: () -> Unit)

@JsFun("(url, token) => { fetch(url, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(token) }).then(r => console.log('FCM Token veritabanına kaydedildi.')).catch(e => console.error(e)); }")
private external fun saveTokenToDbJs(url: String, token: String)

@JsFun("(vapidKey, onSuccess, onError) => { " +
        "if (typeof firebase !== 'undefined') { " +
        "    try { " +
        "        firebase.messaging().getToken({ vapidKey: vapidKey }).then(function(token) { " +
        "            if (token) { onSuccess(token); } else { onError('Boş token döndü'); } " +
        "        }).catch(function(err) { " +
        "            onError(err.message || err.toString()); " +
        "        }); " +
        "    } catch (e) { " +
        "        onError(e.message || e.toString()); " +
        "    } " +
        "} else { " +
        "    onError('Firebase yüklenmemiş'); " +
        "} " +
        "}")
private external fun fetchFcmTokenJs(
    vapidKey: String,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit
)

@JsFun("() => { if (Notification.permission === 'granted') { new Notification('Dünya Kupası 2026', { body: 'Bu bir yerel test bildirimidir! Bildirim altyapınız sorunsuz çalışıyor.' }); } else { alert('Lütfen önce bildirim izni verin.'); } }")
private external fun showLocalTestNotificationJs()

@JsFun("(url, jsonStr) => { fetch(url, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: jsonStr }).then(r => console.log('Tercihler kaydedildi.')).catch(e => console.error(e)); }")
private external fun saveSubscribedTeamsJs(url: String, jsonStr: String)

@JsFun("(url, callback) => { " +
       "  fetch(url)" +
       "    .then(r => r.json())" +
       "    .then(data => { " +
       "      if (data) { callback(JSON.stringify(Object.keys(data))); } " +
       "      else { callback('[]'); } " +
       "    })" +
       "    .catch(e => { console.error(e); callback('[]'); }); " +
       "}")
private external fun getSubscribedTeamsJs(url: String, callback: (String) -> Unit)

@JsFun("(title, body) => { if (Notification.permission === 'granted') { new Notification(title, { body: body }); } }")
private external fun showLocalNotificationJs(title: String, body: String)

@JsFun("() => self.firebaseConfig ? self.firebaseConfig.vapidKey : null")
private external fun getVapidKeyFromJs(): String?

@JsFun("() => self.firebaseConfig ? self.firebaseConfig.databaseURL : null")
private external fun getDatabaseUrlFromJs(): String?
