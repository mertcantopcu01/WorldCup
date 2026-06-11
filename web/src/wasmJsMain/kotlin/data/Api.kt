import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

object SportsDbApi {

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

    suspend fun getFixtures(): List<MatchEvent> {
        val generatedGroupStage = generateGroupStageFixtures()
        val apiEvents = fetchApiEvents()

        if (apiEvents.isEmpty()) {
            return generatedGroupStage
        }

        return mergeFixtures(apiEvents, generatedGroupStage)
    }

    private suspend fun fetchApiEvents(): List<MatchEvent> {
        return try {
            val response = client.get("https://www.thesportsdb.com/api/v1/json/3/eventsseason.php") {
                parameter("id", "4429")
                parameter("s", "2026")
            }.body<EventsResponse>()

            response.events?.sortedBy { it.strTimestamp ?: "" } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun mergeFixtures(
        apiEvents: List<MatchEvent>,
        generatedGroupStage: List<MatchEvent>
    ): List<MatchEvent> {
        val knockout = apiEvents.filter { !isGroupStageRound(it.intRound) }

        val mergedGroupStage = generatedGroupStage.map { generated ->
            apiEvents.find { api -> isSameGroupStageMatch(api, generated) } ?: generated
        }

        val extraApiGroupMatches = apiEvents.filter { api ->
            isGroupStageRound(api.intRound) &&
                generatedGroupStage.none { gen -> isSameGroupStageMatch(api, gen) }
        }

        return (mergedGroupStage + extraApiGroupMatches + knockout)
            .sortedBy { it.strTimestamp ?: "" }
    }

    private fun generateGroupStageFixtures(): List<MatchEvent> {
        val generatedEvents = mutableListOf<MatchEvent>()

        worldCupGroups.forEachIndexed { index, group ->
            val char = ('A' + index).toString()
            val t1 = group.teams[0]
            val t2 = group.teams[1]
            val t3 = group.teams[2]
            val t4 = group.teams[3]

            val baseDay = 11 + index

            val d1 = baseDay
            val dayStr1 = d1.toString().padStart(2, '0')
            generatedEvents.add(MatchEvent(
                idEvent = "gen_r1_${char}1", strEvent = "$t1 vs $t2", strHomeTeam = t1, strAwayTeam = t2,
                strTimestamp = "2026-06-${dayStr1}T15:00:00", dateEventLocal = "2026-06-$dayStr1", strVenue = "Grup $char Stadyumu", intRound = "1", strStatus = "NS", strTimeLocal = "15:00:00"
            ))
            generatedEvents.add(MatchEvent(
                idEvent = "gen_r1_${char}2", strEvent = "$t3 vs $t4", strHomeTeam = t3, strAwayTeam = t4,
                strTimestamp = "2026-06-${dayStr1}T19:00:00", dateEventLocal = "2026-06-$dayStr1", strVenue = "Grup $char Stadyumu", intRound = "1", strStatus = "NS", strTimeLocal = "19:00:00"
            ))

            val d2 = baseDay + 5
            val dayStr2 = d2.toString().padStart(2, '0')
            generatedEvents.add(MatchEvent(
                idEvent = "gen_r2_${char}1", strEvent = "$t1 vs $t3", strHomeTeam = t1, strAwayTeam = t3,
                strTimestamp = "2026-06-${dayStr2}T15:00:00", dateEventLocal = "2026-06-$dayStr2", strVenue = "Grup $char Stadyumu", intRound = "2", strStatus = "NS", strTimeLocal = "15:00:00"
            ))
            generatedEvents.add(MatchEvent(
                idEvent = "gen_r2_${char}2", strEvent = "$t2 vs $t4", strHomeTeam = t2, strAwayTeam = t4,
                strTimestamp = "2026-06-${dayStr2}T19:00:00", dateEventLocal = "2026-06-$dayStr2", strVenue = "Grup $char Stadyumu", intRound = "2", strStatus = "NS", strTimeLocal = "19:00:00"
            ))

            val d3 = baseDay + 10
            val isJuly = d3 > 30
            val finalMonth = if (isJuly) "07" else "06"
            val finalDay = if (isJuly) d3 - 30 else d3
            val dayStr3 = finalDay.toString().padStart(2, '0')

            generatedEvents.add(MatchEvent(
                idEvent = "gen_r3_${char}1", strEvent = "$t1 vs $t4", strHomeTeam = t1, strAwayTeam = t4,
                strTimestamp = "2026-${finalMonth}-${dayStr3}T15:00:00", dateEventLocal = "2026-$finalMonth-$dayStr3", strVenue = "Grup $char Stadyumu", intRound = "3", strStatus = "NS", strTimeLocal = "15:00:00"
            ))
            generatedEvents.add(MatchEvent(
                idEvent = "gen_r3_${char}2", strEvent = "$t2 vs $t3", strHomeTeam = t2, strAwayTeam = t3,
                strTimestamp = "2026-${finalMonth}-${dayStr3}T19:00:00", dateEventLocal = "2026-$finalMonth-$dayStr3", strVenue = "Grup $char Stadyumu", intRound = "3", strStatus = "NS", strTimeLocal = "19:00:00"
            ))
        }

        return generatedEvents.sortedBy { it.strTimestamp ?: "" }
    }
}
