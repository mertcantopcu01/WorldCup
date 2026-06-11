import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import io.ktor.http.ContentType
import io.ktor.http.contentType

@Serializable
data class UserData(
    val username: String,
    val email: String,
    val password: String
)

object AuthService {
    private const val DATABASE_URL = "https://worldcup-2d62a-default-rtdb.firebaseio.com"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
    }

    private fun checkAndThrowIfError(bodyText: String) {
        if (bodyText.contains("\"error\"")) {
            val errorMessage = try {
                val jsonElement = json.parseToJsonElement(bodyText)
                jsonElement.jsonObject["error"]?.jsonPrimitive?.content ?: bodyText
            } catch (e: Exception) {
                bodyText
            }
            throw Exception(errorMessage)
        }
    }

    suspend fun registerUser(username: String, email: String, password: String): Boolean {
        val normalizedUsername = username.trim().lowercase()
        if (normalizedUsername.isEmpty() || password.isEmpty() || email.isEmpty()) {
            throw Exception("Tüm alanları doldurmanız gerekmektedir.")
        }
        
        val checkUrl = "$DATABASE_URL/users/$normalizedUsername.json"
        
        val existingUser: UserData? = try {
            val response: HttpResponse = client.get(checkUrl)
            if (response.status.value in 200..299) {
                val bodyText = response.bodyAsText().trim()
                checkAndThrowIfError(bodyText)
                if (bodyText == "null" || bodyText.isEmpty()) {
                    null
                } else {
                    json.decodeFromString<UserData>(bodyText)
                }
            } else {
                val errorBody = try { response.bodyAsText() } catch (e: Exception) { "" }
                checkAndThrowIfError(errorBody)
                throw Exception("Sunucu hatası: ${response.status.value}")
            }
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("Sunucu hatası") || msg.contains("Permission denied") || msg.contains("auth") || msg.contains("Firebase")) {
                throw e
            }
            null
        }

        if (existingUser != null) {
            throw Exception("Bu kullanıcı adı zaten alınmış.")
        }

        val user = UserData(username.trim(), email.trim(), password)
        try {
            val response: HttpResponse = client.put(checkUrl) {
                contentType(ContentType.Application.Json)
                setBody(user)
            }
            val bodyText = response.bodyAsText()
            checkAndThrowIfError(bodyText)
            if (response.status.value !in 200..299) {
                throw Exception("Firebase hatası (${response.status.value})")
            }
            return true
        } catch (e: Exception) {
            val msg = e.message ?: "Bilinmeyen hata"
            throw Exception("Kayıt işlemi sırasında bir hata oluştu: $msg")
        }
    }

    suspend fun loginUser(username: String, password: String): UserData {
        val normalizedUsername = username.trim().lowercase()
        if (normalizedUsername.isEmpty() || password.isEmpty()) {
            throw Exception("Kullanıcı adı ve şifre boş bırakılamaz.")
        }

        val checkUrl = "$DATABASE_URL/users/$normalizedUsername.json"
        try {
            val response: HttpResponse = client.get(checkUrl)
            val bodyText = response.bodyAsText().trim()
            checkAndThrowIfError(bodyText)
            if (response.status.value !in 200..299) {
                throw Exception("Firebase hatası (${response.status.value})")
            }
            
            if (bodyText == "null" || bodyText.isEmpty()) {
                throw Exception("Kullanıcı bulunamadı.")
            }
            
            val user = json.decodeFromString<UserData>(bodyText)
            if (user.password != password) {
                throw Exception("Hatalı şifre.")
            }
            return user
        } catch (e: Exception) {
            val msg = e.message ?: "Bilinmeyen hata"
            if (msg.contains("Kullanıcı bulunamadı") || msg.contains("Hatalı şifre") || msg.contains("Permission denied") || msg.contains("auth")) {
                throw e
            }
            throw Exception("Giriş işlemi başarısız: $msg")
        }
    }

    suspend fun migrateGuestPredictions(guestUsername: String, targetUsername: String): Boolean {
        val guest = guestUsername.lowercase().trim()
        val target = targetUsername.lowercase().trim()
        if (guest == target || guest.isEmpty() || target.isEmpty()) return true

        // 1. Migrate match predictions
        try {
            val predUrl = "$DATABASE_URL/predictions/$guest.json"
            val response: HttpResponse = client.get(predUrl)
            val bodyText = response.bodyAsText().trim()
            if (response.status.value in 200..299 && bodyText != "null" && bodyText.isNotEmpty()) {
                client.put("$DATABASE_URL/predictions/$target.json") {
                    contentType(ContentType.Application.Json)
                    setBody(bodyText)
                }
            }
        } catch (_: Exception) {}

        // 2. Migrate group predictions
        try {
            val groupUrl = "$DATABASE_URL/group_predictions/$guest.json"
            val response: HttpResponse = client.get(groupUrl)
            val bodyText = response.bodyAsText().trim()
            if (response.status.value in 200..299 && bodyText != "null" && bodyText.isNotEmpty()) {
                client.put("$DATABASE_URL/group_predictions/$target.json") {
                    contentType(ContentType.Application.Json)
                    setBody(bodyText)
                }
            }
        } catch (_: Exception) {}

        // 3. Migrate knockout predictions
        try {
            val koUrl = "$DATABASE_URL/knockout_predictions/$guest.json"
            val response: HttpResponse = client.get(koUrl)
            val bodyText = response.bodyAsText().trim()
            if (response.status.value in 200..299 && bodyText != "null" && bodyText.isNotEmpty()) {
                client.put("$DATABASE_URL/knockout_predictions/$target.json") {
                    contentType(ContentType.Application.Json)
                    setBody(bodyText)
                }
            }
        } catch (_: Exception) {}

        // 4. Delete guest predictions to clean up
        try {
            client.delete("$DATABASE_URL/predictions/$guest.json")
            client.delete("$DATABASE_URL/group_predictions/$guest.json")
            client.delete("$DATABASE_URL/knockout_predictions/$guest.json")
        } catch (_: Exception) {}

        return true
    }
}

