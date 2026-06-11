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

object PredictionService {
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

    // ── Match Predictions ─────────────────────────────────────────────────────

    suspend fun savePrediction(username: String, matchId: String, homeScore: Int, awayScore: Int): Boolean {
        val checkUrl = "$DATABASE_URL/predictions/${username.lowercase().trim()}/$matchId.json"
        val prediction = MatchPrediction(homeScore, awayScore)
        try {
            val response: HttpResponse = client.put(checkUrl) {
                contentType(ContentType.Application.Json)
                setBody(prediction)
            }
            val bodyText = response.bodyAsText()
            checkAndThrowIfError(bodyText)
            if (response.status.value !in 200..299) {
                throw Exception("Firebase hatası (${response.status.value})")
            }
            return true
        } catch (e: Exception) {
            val msg = e.message ?: "Bilinmeyen hata"
            throw Exception("Tahmin kaydedilirken hata oluştu: $msg")
        }
    }

    suspend fun getPredictions(username: String): Map<String, MatchPrediction> {
        val checkUrl = "$DATABASE_URL/predictions/${username.lowercase().trim()}.json"
        try {
            val response: HttpResponse = client.get(checkUrl)
            val bodyText = response.bodyAsText().trim()
            checkAndThrowIfError(bodyText)
            if (response.status.value !in 200..299) {
                throw Exception("Firebase hatası (${response.status.value})")
            }
            if (bodyText == "null" || bodyText.isEmpty()) {
                return emptyMap()
            }
            return json.decodeFromString<Map<String, MatchPrediction>>(bodyText)
        } catch (e: Exception) {
            val msg = e.message ?: "Bilinmeyen hata"
            if (msg.contains("Permission denied") || msg.contains("auth")) {
                throw e
            }
            return emptyMap()
        }
    }

    // ── Group Ranking Predictions ─────────────────────────────────────────────

    /**
     * Saves the predicted ranking for a group.
     * Path: /group_predictions/{username}/{encodedGroupLabel}
     * [rankings]: list of 4 canonical team names in predicted order (1st → 4th).
     */
    suspend fun saveGroupPrediction(
        username: String,
        groupLabel: String,
        rankings: List<String>
    ): Boolean {
        val encodedGroup = groupLabel.replace(" ", "_").lowercase()
        val url = "$DATABASE_URL/group_predictions/${username.lowercase().trim()}/$encodedGroup.json"
        val payload = GroupRankingPrediction(rankings)
        try {
            val response: HttpResponse = client.put(url) {
                contentType(ContentType.Application.Json)
                setBody(payload)
            }
            val bodyText = response.bodyAsText()
            checkAndThrowIfError(bodyText)
            if (response.status.value !in 200..299) {
                throw Exception("Firebase hatası (${response.status.value})")
            }
            return true
        } catch (e: Exception) {
            val msg = e.message ?: "Bilinmeyen hata"
            throw Exception("Grup tahmini kaydedilirken hata oluştu: $msg")
        }
    }

    /**
     * Fetches all group ranking predictions for a given user.
     * Returns a map of encoded group label → GroupRankingPrediction.
     */
    suspend fun getGroupPredictions(username: String): Map<String, GroupRankingPrediction> {
        val url = "$DATABASE_URL/group_predictions/${username.lowercase().trim()}.json"
        return try {
            val response: HttpResponse = client.get(url)
            val bodyText = response.bodyAsText().trim()
            checkAndThrowIfError(bodyText)
            if (response.status.value !in 200..299 || bodyText == "null" || bodyText.isEmpty()) {
                emptyMap()
            } else {
                json.decodeFromString<Map<String, GroupRankingPrediction>>(bodyText)
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    // ── Leaderboard ───────────────────────────────────────────────────────────

    suspend fun getLeaderboard(allEvents: List<MatchEvent>): List<UserLeaderboardEntry> {
        val usersUrl = "$DATABASE_URL/users.json"
        val predictionsUrl = "$DATABASE_URL/predictions.json"
        val groupPredictionsUrl = "$DATABASE_URL/group_predictions.json"

        val users: Map<String, UserData> = try {
            val response: HttpResponse = client.get(usersUrl)
            val bodyText = response.bodyAsText().trim()
            checkAndThrowIfError(bodyText)
            if (response.status.value !in 200..299 || bodyText == "null" || bodyText.isEmpty()) {
                emptyMap()
            } else {
                json.decodeFromString<Map<String, UserData>>(bodyText)
            }
        } catch (e: Exception) {
            emptyMap()
        }

        val allPredictions: Map<String, Map<String, MatchPrediction>> = try {
            val response: HttpResponse = client.get(predictionsUrl)
            val bodyText = response.bodyAsText().trim()
            checkAndThrowIfError(bodyText)
            if (response.status.value !in 200..299 || bodyText == "null" || bodyText.isEmpty()) {
                emptyMap()
            } else {
                json.decodeFromString<Map<String, Map<String, MatchPrediction>>>(bodyText)
            }
        } catch (e: Exception) {
            emptyMap()
        }

        // All users' group predictions: { username -> { encoded_group -> GroupRankingPrediction } }
        val allGroupPredictions: Map<String, Map<String, GroupRankingPrediction>> = try {
            val response: HttpResponse = client.get(groupPredictionsUrl)
            val bodyText = response.bodyAsText().trim()
            checkAndThrowIfError(bodyText)
            if (response.status.value !in 200..299 || bodyText == "null" || bodyText.isEmpty()) {
                emptyMap()
            } else {
                json.decodeFromString<Map<String, Map<String, GroupRankingPrediction>>>(bodyText)
            }
        } catch (e: Exception) {
            emptyMap()
        }

        val completedMatches = allEvents.filter { matchStatus(it) == MatchStatus.DONE }

        // Pre-compute actual standings for finished groups (all 3 rounds done)
        val actualStandings = calculateStandings(allEvents)
        // A group is "finished" when all C(4,2)=6 matches are done
        val finishedGroups = worldCupGroups
            .filter { group ->
                val groupMatches = allEvents.filter { evt ->
                    isGroupStageRound(evt.intRound) &&
                    getMatchGroup(evt.strHomeTeam, evt.strAwayTeam) == group.label
                }
                val totalExpected = group.teams.size * (group.teams.size - 1) / 2
                val done = groupMatches.count { matchStatus(it) == MatchStatus.DONE }
                done >= totalExpected
            }
            .map { it.label }
            .toSet()

        return users.map { (normalizedUsername, userData) ->
            val userPredictions = allPredictions[normalizedUsername.lowercase().trim()] ?: emptyMap()
            val userGroupPreds = allGroupPredictions[normalizedUsername.lowercase().trim()] ?: emptyMap()

            var points = 0
            var exactCount = 0
            var outcomeCount = 0
            var wrongCount = 0

            completedMatches.forEach { match ->
                val pred = userPredictions[match.idEvent]
                if (pred != null) {
                    val pts = calculatePredictionPoints(pred, match.intHomeScore, match.intAwayScore)
                    points += pts
                    when (pts) {
                        20 -> exactCount++
                        5  -> outcomeCount++
                        0  -> wrongCount++
                    }
                }
            }

            // Group prediction points — only scored when the group is fully finished
            var groupPredPts = 0
            worldCupGroups.forEach { group ->
                if (group.label in finishedGroups) {
                    val encodedGroup = group.label.replace(" ", "_").lowercase()
                    val userGroupPred = userGroupPreds[encodedGroup]
                    if (userGroupPred != null && userGroupPred.rankings.isNotEmpty()) {
                        val actual = actualStandings[group.label]?.map { it.teamName } ?: emptyList()
                        groupPredPts += calculateGroupPredictionPoints(userGroupPred.rankings, actual)
                    }
                }
            }

            UserLeaderboardEntry(
                username = userData.username,
                totalPoints = points + groupPredPts,
                predictionCount = userPredictions.size,
                exactMatchesCount = exactCount,
                outcomeMatchesCount = outcomeCount,
                wrongMatchesCount = wrongCount,
                groupPredictionPoints = groupPredPts
            )
        }.sortedWith(
            compareByDescending<UserLeaderboardEntry> { it.totalPoints }
                .thenByDescending { it.exactMatchesCount }
                .thenByDescending { it.outcomeMatchesCount }
                .thenBy { it.username.lowercase() }
        )
    }

    // ── Knockout Stage Predictions ─────────────────────────────────────────────

    suspend fun saveKnockoutPredictions(
        username: String,
        predictions: Map<String, String>
    ): Boolean {
        val url = "$DATABASE_URL/knockout_predictions/${username.lowercase().trim()}.json"
        try {
            val response: HttpResponse = client.put(url) {
                contentType(ContentType.Application.Json)
                setBody(predictions)
            }
            val bodyText = response.bodyAsText()
            checkAndThrowIfError(bodyText)
            if (response.status.value !in 200..299) {
                throw Exception("Firebase hatası (${response.status.value})")
            }
            return true
        } catch (e: Exception) {
            val msg = e.message ?: "Bilinmeyen hata"
            throw Exception("Eleme tahminleri kaydedilirken hata oluştu: $msg")
        }
    }

    suspend fun getKnockoutPredictions(username: String): Map<String, String> {
        val url = "$DATABASE_URL/knockout_predictions/${username.lowercase().trim()}.json"
        return try {
            val response: HttpResponse = client.get(url)
            val bodyText = response.bodyAsText().trim()
            checkAndThrowIfError(bodyText)
            if (response.status.value !in 200..299 || bodyText == "null" || bodyText.isEmpty()) {
                emptyMap()
            } else {
                json.decodeFromString<Map<String, String>>(bodyText)
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }
}

