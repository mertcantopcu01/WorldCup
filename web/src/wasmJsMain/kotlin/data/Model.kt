import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Serializable
data class MatchEvent(
    @SerialName("idEvent")          val idEvent: String = "",
    @SerialName("strEvent")         val strEvent: String = "",
    @SerialName("strHomeTeam")      val strHomeTeam: String? = null,
    @SerialName("strAwayTeam")      val strAwayTeam: String? = null,
    @SerialName("strHomeTeamBadge") val strHomeTeamBadge: String? = null,
    @SerialName("strAwayTeamBadge") val strAwayTeamBadge: String? = null,
    @SerialName("intHomeScore")     val intHomeScore: String? = null,
    @SerialName("intAwayScore")     val intAwayScore: String? = null,
    @SerialName("strTimestamp")     val strTimestamp: String? = null,
    @SerialName("dateEvent")        val dateEvent: String? = null,
    @SerialName("dateEventLocal")   val dateEventLocal: String? = null,
    @SerialName("strVenue")         val strVenue: String? = null,
    @SerialName("intRound")         val intRound: String? = null,
    @SerialName("strStatus")        val strStatus: String? = null,
    @SerialName("strTimeLocal")     val strTimeLocal: String? = null
)

@Serializable
data class EventsResponse(
    val events: List<MatchEvent>? = null
)

// ── Status ────────────────────────────────────────────────────────────────────

enum class MatchStatus { UPCOMING, LIVE, DONE }

fun matchStatus(evt: MatchEvent): MatchStatus {
    val s = (evt.strStatus ?: "").lowercase()
    return when {
        s == "ft" || s == "aet" || s == "pen" || s == "finished" -> MatchStatus.DONE
        s == "live" || s == "1h" || s == "ht" || s == "2h" || s == "et" -> MatchStatus.LIVE
        else -> MatchStatus.UPCOMING
    }
}

private val predictionLockWindow = 1.hours

@OptIn(ExperimentalTime::class)
fun parseMatchKickoffInstant(evt: MatchEvent): Instant? {
    val raw = evt.strTimestamp?.trim()?.takeIf { it.length >= 16 } ?: return null
    val iso = when {
        raw.endsWith('Z') -> raw
        raw.contains('+') -> raw
        else -> "${raw}Z"
    }
    return try {
        Instant.parse(iso)
    } catch (_: Exception) {
        null
    }
}

@OptIn(ExperimentalTime::class)
fun isPredictionLocked(evt: MatchEvent, now: Instant = Clock.System.now()): Boolean {
    when (matchStatus(evt)) {
        MatchStatus.DONE, MatchStatus.LIVE -> return true
        MatchStatus.UPCOMING -> Unit
    }

    val kickoff = parseMatchKickoffInstant(evt) ?: return false
    return now >= kickoff - predictionLockWindow
}

@OptIn(ExperimentalTime::class)
fun predictionLockMessage(evt: MatchEvent): String? {
    if (!isPredictionLocked(evt)) return null
    return when (matchStatus(evt)) {
        MatchStatus.LIVE -> "Maç başladı — tahmin kilitli"
        MatchStatus.DONE -> null
        MatchStatus.UPCOMING -> "Maç başlamasına 1 saatten az kaldı — tahmin kilitli"
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

fun roundLabel(round: String?): String = when (round) {
    "1" -> "GRUP – 1. TUR"
    "2" -> "GRUP – 2. TUR"
    "3" -> "GRUP – 3. TUR"
    "4" -> "SON 32"
    "5" -> "SON 16"
    "6" -> "ÇEYREK FİNAL"
    "7" -> "YARI FİNAL"
    "8" -> "3. YER"
    "9" -> "FİNAL"
    else -> round?.let { "TUR $it" } ?: ""
}

fun formatLocalTime(raw: String?): String {
    if (raw.isNullOrBlank()) return "–"
    return raw.take(5) // HH:mm
}

fun formatDateKey(dateKey: String): String {
    val parts = dateKey.split("-")
    if (parts.size != 3) return dateKey
    val day = parts[2].trimStart('0').ifEmpty { "0" }
    val month = parts[1].toIntOrNull() ?: return dateKey
    val year = parts[0]
    val months = listOf(
        "", "Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
        "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"
    )
    return "$day ${months.getOrElse(month) { "" }} $year"
}

enum class FilterType { ALL, UPCOMING, DONE }

// ── Translation & Flags & TimeZone Helpers ────────────────────────────────────

data class TeamInfo(val code: String, val trName: String)

val teamInfoMap = mapOf(
    "australia" to TeamInfo("au", "Avustralya"),
    "belgium" to TeamInfo("be", "Belçika"),
    "bosnia-herzegovina" to TeamInfo("ba", "Bosna Hersek"),
    "brazil" to TeamInfo("br", "Brezilya"),
    "canada" to TeamInfo("ca", "Kanada"),
    "cape verde" to TeamInfo("cv", "Yeşil Burun Adaları"),
    "curaçao" to TeamInfo("cw", "Curaçao"),
    "curacao" to TeamInfo("cw", "Curaçao"),
    "czech republic" to TeamInfo("cz", "Çekya"),
    "ecuador" to TeamInfo("ec", "Ekvador"),
    "egypt" to TeamInfo("eg", "Mısır"),
    "germany" to TeamInfo("de", "Almanya"),
    "haiti" to TeamInfo("ht", "Haiti"),
    "ivory coast" to TeamInfo("ci", "Fildişi Sahili"),
    "japan" to TeamInfo("jp", "Japonya"),
    "mexico" to TeamInfo("mx", "Meksika"),
    "morocco" to TeamInfo("ma", "Fas"),
    "netherlands" to TeamInfo("nl", "Hollanda"),
    "paraguay" to TeamInfo("py", "Paraguay"),
    "qatar" to TeamInfo("qa", "Katar"),
    "saudi arabia" to TeamInfo("sa", "Suudi Arabistan"),
    "scotland" to TeamInfo("gb-sct", "İskoçya"),
    "south africa" to TeamInfo("za", "Güney Afrika"),
    "south korea" to TeamInfo("kr", "Güney Kore"),
    "spain" to TeamInfo("es", "İspanya"),
    "sweden" to TeamInfo("se", "İsveç"),
    "switzerland" to TeamInfo("ch", "İsviçre"),
    "tunisia" to TeamInfo("tn", "Tunus"),
    "turkey" to TeamInfo("tr", "Türkiye"),
    "uruguay" to TeamInfo("uy", "Uruguay"),
    "usa" to TeamInfo("us", "ABD"),
    "argentina" to TeamInfo("ar", "Arjantin"),
    "france" to TeamInfo("fr", "Fransa"),
    "iran" to TeamInfo("ir", "İran"),
    "new zealand" to TeamInfo("nz", "Yeni Zelanda"),
    "senegal" to TeamInfo("sn", "Senegal"),
    "iraq" to TeamInfo("iq", "Irak"),
    "norway" to TeamInfo("no", "Norveç"),
    "algeria" to TeamInfo("dz", "Cezayir"),
    "austria" to TeamInfo("at", "Avusturya"),
    "jordan" to TeamInfo("jo", "Ürdün"),
    "portugal" to TeamInfo("pt", "Portekiz"),
    "dr congo" to TeamInfo("cd", "Kongo DC"),
    "demokratik k" to TeamInfo("cd", "Kongo DC"),
    "uzbekistan" to TeamInfo("uz", "Özbekistan"),
    "colombia" to TeamInfo("co", "Kolombiya"),
    "england" to TeamInfo("gb-eng", "İngiltere"),
    "croatia" to TeamInfo("hr", "Hırvatistan"),
    "ghana" to TeamInfo("gh", "Gana"),
    "panama" to TeamInfo("pa", "Panama"),
    
    // Turkish name mappings & typos support
    "meksika" to TeamInfo("mx", "Meksika"),
    "güney afrika" to TeamInfo("za", "Güney Afrika"),
    "güney kore" to TeamInfo("kr", "Güney Kore"),
    "çekya" to TeamInfo("cz", "Çekya"),
    "kanada" to TeamInfo("ca", "Kanada"),
    "bosna hersek" to TeamInfo("ba", "Bosna Hersek"),
    "katar" to TeamInfo("qa", "Katar"),
    "isviçre" to TeamInfo("ch", "İsviçre"),
    "brezilya" to TeamInfo("br", "Brezilya"),
    "fas" to TeamInfo("ma", "Fas"),
    "iskoçya" to TeamInfo("gb-sct", "İskoçya"),
    "abd" to TeamInfo("us", "ABD"),
    "avustralya" to TeamInfo("au", "Avustralya"),
    "türkiye" to TeamInfo("tr", "Türkiye"),
    "almanya" to TeamInfo("de", "Almanya"),
    "cuaçao" to TeamInfo("cw", "Curaçao"),
    "fildişi sahili" to TeamInfo("ci", "Fildişi Sahili"),
    "ekvador" to TeamInfo("ec", "Ekvador"),
    "hollanda" to TeamInfo("nl", "Hollanda"),
    "japonya" to TeamInfo("jp", "Japonya"),
    "isveç" to TeamInfo("se", "İsveç"),
    "tunus" to TeamInfo("tn", "Tunus"),
    "belçika" to TeamInfo("be", "Belçika"),
    "mısır" to TeamInfo("eg", "Mısır"),
    "yeni zellanda" to TeamInfo("nz", "Yeni Zelanda"),
    "yeni zelanda" to TeamInfo("nz", "Yeni Zelanda"),
    "ispanya" to TeamInfo("es", "İspanya"),
    "yeşil burun" to TeamInfo("cv", "Yeşil Burun Adaları"),
    "suudi arabistan" to TeamInfo("sa", "Suudi Arabistan"),
    "urugay" to TeamInfo("uy", "Uruguay"),
    "fransa" to TeamInfo("fr", "Fransa"),
    "ırak" to TeamInfo("iq", "Irak"),
    "norveç" to TeamInfo("no", "Norveç"),
    "arjanntin" to TeamInfo("ar", "Arjantin"),
    "cezayir" to TeamInfo("dz", "Cezayir"),
    "avusturya" to TeamInfo("at", "Avusturya"),
    "ürdün" to TeamInfo("jo", "Ürdün"),
    "portekiz" to TeamInfo("pt", "Portekiz"),
    "özbekistan" to TeamInfo("uz", "Özbekistan"),
    "kolombiya" to TeamInfo("co", "Kolombiya"),
    "ingiltere" to TeamInfo("gb-eng", "İngiltere"),
    "hırvatistan" to TeamInfo("hr", "Hırvatistan"),
    "gana" to TeamInfo("gh", "Gana")
)

fun getTeamFlagUrl(teamName: String): String? {
    val key = teamName.lowercase().trim()
    val info = teamInfoMap[key] ?: teamInfoMap.entries.firstOrNull { it.key.contains(key) || key.contains(it.key) }?.value
    return if (info != null) {
        "https://flagcdn.com/w160/${info.code}.png"
    } else {
        null
    }
}

fun translateTeam(teamName: String): String {
    val key = teamName.lowercase().trim()
    val info = teamInfoMap[key] ?: teamInfoMap.entries.firstOrNull { it.key.contains(key) || key.contains(it.key) }?.value
    return info?.trName ?: teamName
}

data class TurkeyDateTime(val dateKey: String, val timeStr: String)

fun convertToTurkeyDateTime(timestamp: String?, dateFallback: String?, timeFallback: String?): TurkeyDateTime {
    if (timestamp == null || timestamp.length < 16) {
        return TurkeyDateTime(dateFallback ?: "?", timeFallback?.take(5) ?: "–")
    }
    try {
        val datePart = timestamp.substringBefore("T")
        val timePart = timestamp.substringAfter("T")
        
        val dateSplit = datePart.split("-")
        val timeSplit = timePart.split(":")
        
        var year = dateSplit[0].toInt()
        var month = dateSplit[1].toInt()
        var day = dateSplit[2].toInt()
        
        var hour = timeSplit[0].toInt()
        val minute = timeSplit[1].toInt()
        
        // Add 3 hours for Turkey Time (UTC+3)
        hour += 3
        if (hour >= 24) {
            hour -= 24
            // Move to next day
            val daysInMonth = getDaysInMonth(year, month)
            day += 1
            if (day > daysInMonth) {
                day = 1
                month += 1
                if (month > 12) {
                    month = 1
                    year += 1
                }
            }
        }
        
        val newDateKey = "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
        val timeStr = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
        return TurkeyDateTime(newDateKey, timeStr)
    } catch (e: Exception) {
        return TurkeyDateTime(dateFallback ?: "?", timeFallback?.take(5) ?: "–")
    }
}

private fun getDaysInMonth(year: Int, month: Int): Int {
    return when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (isLeapYear(year)) 29 else 28
        else -> 30
    }
}

private fun isLeapYear(year: Int): Boolean {
    return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
}

fun formatShortDate(dateKey: String): String {
    val parts = dateKey.split("-")
    if (parts.size != 3) return dateKey
    val day = parts[2].trimStart('0').ifEmpty { "0" }
    val month = parts[1].toIntOrNull() ?: return dateKey
    val monthsShort = listOf(
        "", "Oca", "Şub", "Mar", "Nis", "May", "Haz",
        "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara"
    )
    return "$day ${monthsShort.getOrElse(month) { "" }}"
}

data class WorldCupGroup(val label: String, val teams: List<String>)

val worldCupGroups = listOf(
    WorldCupGroup("A Grubu", listOf("Mexico", "South Africa", "South Korea", "Czech Republic")),
    WorldCupGroup("B Grubu", listOf("Canada", "Bosnia-Herzegovina", "Qatar", "Switzerland")),
    WorldCupGroup("C Grubu", listOf("Brazil", "Morocco", "Haiti", "Scotland")),
    WorldCupGroup("D Grubu", listOf("USA", "Paraguay", "Australia", "Turkey")),
    WorldCupGroup("E Grubu", listOf("Germany", "Curaçao", "Ivory Coast", "Ecuador")),
    WorldCupGroup("F Grubu", listOf("Netherlands", "Japan", "Sweden", "Tunisia")),
    WorldCupGroup("G Grubu", listOf("Belgium", "Egypt", "Iran", "New Zealand")),
    WorldCupGroup("H Grubu", listOf("Spain", "Cape Verde", "Saudi Arabia", "Uruguay")),
    WorldCupGroup("I Grubu", listOf("France", "Senegal", "Iraq", "Norway")),
    WorldCupGroup("J Grubu", listOf("Argentina", "Algeria", "Austria", "Jordan")),
    WorldCupGroup("K Grubu", listOf("Portugal", "DR Congo", "Uzbekistan", "Colombia")),
    WorldCupGroup("L Grubu", listOf("England", "Croatia", "Ghana", "Panama"))
)

private fun buildHardcodedTeamToGroup(): Map<String, String> {
    val map = mutableMapOf<String, String>()
    worldCupGroups.forEach { group ->
        group.teams.forEach { team ->
            val key = team.lowercase().trim()
            map[key] = group.label
            if (team == "Curaçao") map["curacao"] = group.label
            if (team == "USA") map["united states"] = group.label

            val info = teamInfoMap[key]
            if (info != null) {
                teamInfoMap.filter { it.value.code == info.code }.forEach { (alias, _) ->
                    map[alias] = group.label
                }
            }
        }
    }
    return map
}

val hardcodedTeamToGroup: Map<String, String> = buildHardcodedTeamToGroup()

private val teamCodeToGroup: Map<String, String> = buildMap {
    worldCupGroups.forEach { group ->
        group.teams.forEach { team ->
            teamInfoMap[team.lowercase().trim()]?.code?.let { put(it, group.label) }
        }
    }
}

fun resolveTeamGroup(teamName: String?): String? {
    if (teamName.isNullOrBlank()) return null
    val key = teamName.lowercase().trim()
    hardcodedTeamToGroup[key]?.let { return it }

    val info = teamInfoMap[key]
        ?: teamInfoMap.entries.firstOrNull { key.contains(it.key) || it.key.contains(key) }?.value
    return info?.code?.let { teamCodeToGroup[it] }
}

fun resolveCanonicalTeamName(teamName: String?): String? {
    if (teamName.isNullOrBlank()) return null
    val key = teamName.lowercase().trim()

    worldCupGroups.forEach { group ->
        group.teams.forEach { team ->
            if (team.lowercase() == key) return team
        }
    }

    val info = teamInfoMap[key]
        ?: teamInfoMap.entries.firstOrNull { key.contains(it.key) || it.key.contains(key) }?.value
        ?: return teamName

    worldCupGroups.forEach { group ->
        group.teams.forEach { team ->
            if (teamInfoMap[team.lowercase()]?.code == info.code) return team
        }
    }
    return teamName
}

fun getMatchGroup(homeTeam: String?, awayTeam: String?): String {
    val groupH = resolveTeamGroup(homeTeam)
    val groupA = resolveTeamGroup(awayTeam)
    return if (groupH != null && groupH == groupA) {
        groupH
    } else {
        groupH ?: groupA ?: "Grup Maçı"
    }
}

fun matchTeamSet(evt: MatchEvent): Set<String>? {
    val home = resolveCanonicalTeamName(evt.strHomeTeam)?.lowercase() ?: return null
    val away = resolveCanonicalTeamName(evt.strAwayTeam)?.lowercase() ?: return null
    return setOf(home, away)
}

fun isSameGroupStageMatch(a: MatchEvent, b: MatchEvent): Boolean {
    if ((a.intRound ?: "") != (b.intRound ?: "")) return false
    val aTeams = matchTeamSet(a) ?: return false
    val bTeams = matchTeamSet(b) ?: return false
    return aTeams == bTeams
}

fun isGroupStageRound(round: String?): Boolean = round == "1" || round == "2" || round == "3"

@Serializable
data class MatchPrediction(
    val homeScore: Int,
    val awayScore: Int
)

fun calculatePredictionPoints(prediction: MatchPrediction, homeScoreStr: String?, awayScoreStr: String?): Int {
    val actHome = homeScoreStr?.toIntOrNull() ?: return 0
    val actAway = awayScoreStr?.toIntOrNull() ?: return 0

    if (prediction.homeScore == actHome && prediction.awayScore == actAway) {
        return 20
    }

    val predDiff = prediction.homeScore - prediction.awayScore
    val actDiff = actHome - actAway

    val predOutcome = when {
        predDiff > 0 -> 1
        predDiff < 0 -> -1
        else -> 0
    }

    val actOutcome = when {
        actDiff > 0 -> 1
        actDiff < 0 -> -1
        else -> 0
    }

    return if (predOutcome == actOutcome) 5 else 0
}

data class TeamStanding(
    val teamName: String,
    val played: Int,
    val won: Int,
    val drawn: Int,
    val lost: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val points: Int
) {
    val goalDifference: Int get() = goalsFor - goalsAgainst
}

fun calculateStandings(allEvents: List<MatchEvent>): Map<String, List<TeamStanding>> {
    val standingsMap = mutableMapOf<String, List<TeamStanding>>()

    val groupTeams = mutableMapOf<String, MutableSet<String>>()
    val teamStats = mutableMapOf<String, TeamStanding>()

    worldCupGroups.forEach { group ->
        groupTeams[group.label] = group.teams.toMutableSet()
        group.teams.forEach { team ->
            teamStats[team] = TeamStanding(
                teamName = team,
                played = 0, won = 0, drawn = 0, lost = 0,
                goalsFor = 0, goalsAgainst = 0, points = 0
            )
        }
    }

    allEvents.forEach { evt ->
        val r = evt.intRound ?: ""
        if (r == "1" || r == "2" || r == "3") {
            val s = (evt.strStatus ?: "").lowercase()
            val isDone = s == "ft" || s == "aet" || s == "pen" || s == "finished"
            if (isDone) {
                val home = resolveCanonicalTeamName(evt.strHomeTeam) ?: return@forEach
                val away = resolveCanonicalTeamName(evt.strAwayTeam) ?: return@forEach
                val homeScore = evt.intHomeScore?.toIntOrNull()
                val awayScore = evt.intAwayScore?.toIntOrNull()

                if (homeScore != null && awayScore != null) {
                    val grpH = resolveTeamGroup(home)
                    val grpA = resolveTeamGroup(away)
                    if (grpH == null || grpA == null || grpH != grpA) return@forEach

                    val sHome = teamStats[home] ?: TeamStanding(home, 0, 0, 0, 0, 0, 0, 0)
                    val sAway = teamStats[away] ?: TeamStanding(away, 0, 0, 0, 0, 0, 0, 0)

                    teamStats[home] = sHome.copy(
                        played = sHome.played + 1,
                        goalsFor = sHome.goalsFor + homeScore,
                        goalsAgainst = sHome.goalsAgainst + awayScore,
                        won = sHome.won + (if (homeScore > awayScore) 1 else 0),
                        drawn = sHome.drawn + (if (homeScore == awayScore) 1 else 0),
                        lost = sHome.lost + (if (homeScore < awayScore) 1 else 0),
                        points = sHome.points + (if (homeScore > awayScore) 3 else if (homeScore == awayScore) 1 else 0)
                    )

                    teamStats[away] = sAway.copy(
                        played = sAway.played + 1,
                        goalsFor = sAway.goalsFor + awayScore,
                        goalsAgainst = sAway.goalsAgainst + homeScore,
                        won = sAway.won + (if (awayScore > homeScore) 1 else 0),
                        drawn = sAway.drawn + (if (homeScore == awayScore) 1 else 0),
                        lost = sAway.lost + (if (awayScore < homeScore) 1 else 0),
                        points = sAway.points + (if (awayScore > homeScore) 3 else if (homeScore == awayScore) 1 else 0)
                    )
                }
            }
        }
    }

    worldCupGroups.forEach { group ->
        val grp = group.label
        val teams = groupTeams[grp] ?: group.teams.toSet()
        val sortedList = teams.map { teamStats[it] ?: TeamStanding(it, 0, 0, 0, 0, 0, 0, 0) }
            .sortedWith(
                compareByDescending<TeamStanding> { it.points }
                    .thenByDescending { it.goalDifference }
                    .thenByDescending { it.goalsFor }
                    .thenBy { it.teamName }
            )
        standingsMap[grp] = sortedList
    }

    return standingsMap
}

@Serializable
data class UserLeaderboardEntry(
    val username: String,
    val totalPoints: Int,
    val predictionCount: Int,
    val exactMatchesCount: Int,
    val outcomeMatchesCount: Int,
    val wrongMatchesCount: Int,
    val groupPredictionPoints: Int = 0
)

// ── Group Ranking Prediction ──────────────────────────────────────────────────

/**
 * Stores a user's predicted final standings for one group.
 * [rankings] is a list of 4 canonical team names in predicted order (1st→4th).
 */
@Serializable
data class GroupRankingPrediction(
    val rankings: List<String> = emptyList()
)

/**
 * Calculates points earned for a group ranking prediction vs the actual standings.
 * Scoring per team slot:
 *   Exact position  → 10 pts
 *   All 4 correct   → 50 pts total
 */
fun calculateGroupPredictionPoints(
    predicted: List<String>,
    actual: List<String>
): Int {
    if (predicted.isEmpty() || actual.isEmpty()) return 0
    val actualNorm = actual.map { resolveCanonicalTeamName(it)?.lowercase() ?: it.lowercase() }
    var exactMatches = 0
    predicted.forEachIndexed { index, rawTeam ->
        val team = resolveCanonicalTeamName(rawTeam)?.lowercase() ?: rawTeam.lowercase()
        val actualPos = actualNorm.indexOf(team)
        if (actualPos == index) {
            exactMatches++
        }
    }
    return if (exactMatches == 4) 50 else exactMatches * 10
}

/**
 * Returns true when the group's first match has started or is within 1 hour.
 */
@OptIn(kotlin.time.ExperimentalTime::class)
fun isGroupPredictionLocked(
    groupLabel: String,
    allEvents: List<MatchEvent>,
    now: kotlin.time.Instant = kotlin.time.Clock.System.now()
): Boolean {
    val groupMatches = allEvents.filter { evt ->
        isGroupStageRound(evt.intRound) && getMatchGroup(evt.strHomeTeam, evt.strAwayTeam) == groupLabel
    }
    val earliest = groupMatches
        .mapNotNull { parseMatchKickoffInstant(it) }
        .minOrNull() ?: return false
    return now >= earliest - 1.hours
}

fun calculatePredictedStandings(
    allEvents: List<MatchEvent>,
    predictions: Map<String, MatchPrediction>
): Map<String, List<TeamStanding>> {
    val standingsMap = mutableMapOf<String, List<TeamStanding>>()
    val groupTeams = mutableMapOf<String, MutableSet<String>>()
    val teamStats = mutableMapOf<String, TeamStanding>()

    worldCupGroups.forEach { group ->
        groupTeams[group.label] = group.teams.toMutableSet()
        group.teams.forEach { team ->
            teamStats[team] = TeamStanding(
                teamName = team,
                played = 0, won = 0, drawn = 0, lost = 0,
                goalsFor = 0, goalsAgainst = 0, points = 0
            )
        }
    }

    allEvents.forEach { evt ->
        val r = evt.intRound ?: ""
        if (r == "1" || r == "2" || r == "3") {
            val home = resolveCanonicalTeamName(evt.strHomeTeam) ?: return@forEach
            val away = resolveCanonicalTeamName(evt.strAwayTeam) ?: return@forEach
            val grpH = resolveTeamGroup(home)
            val grpA = resolveTeamGroup(away)
            if (grpH == null || grpA == null || grpH != grpA) return@forEach

            val pred = predictions[evt.idEvent]
            val homeScore = pred?.homeScore ?: evt.intHomeScore?.toIntOrNull()
            val awayScore = pred?.awayScore ?: evt.intAwayScore?.toIntOrNull()

            if (homeScore != null && awayScore != null) {
                val sHome = teamStats[home] ?: TeamStanding(home, 0, 0, 0, 0, 0, 0, 0)
                val sAway = teamStats[away] ?: TeamStanding(away, 0, 0, 0, 0, 0, 0, 0)

                teamStats[home] = sHome.copy(
                    played = sHome.played + 1,
                    goalsFor = sHome.goalsFor + homeScore,
                    goalsAgainst = sHome.goalsAgainst + awayScore,
                    won = sHome.won + (if (homeScore > awayScore) 1 else 0),
                    drawn = sHome.drawn + (if (homeScore == awayScore) 1 else 0),
                    lost = sHome.lost + (if (homeScore < awayScore) 1 else 0),
                    points = sHome.points + (if (homeScore > awayScore) 3 else if (homeScore == awayScore) 1 else 0)
                )

                teamStats[away] = sAway.copy(
                    played = sAway.played + 1,
                    goalsFor = sAway.goalsFor + awayScore,
                    goalsAgainst = sAway.goalsAgainst + homeScore,
                    won = sAway.won + (if (awayScore > homeScore) 1 else 0),
                    drawn = sAway.drawn + (if (homeScore == awayScore) 1 else 0),
                    lost = sAway.lost + (if (awayScore < homeScore) 1 else 0),
                    points = sAway.points + (if (awayScore > homeScore) 3 else if (homeScore == awayScore) 1 else 0)
                )
            }
        }
    }

    worldCupGroups.forEach { group ->
        val grp = group.label
        val teams = groupTeams[grp] ?: group.teams.toSet()
        val sortedList = teams.map { teamStats[it] ?: TeamStanding(it, 0, 0, 0, 0, 0, 0, 0) }
            .sortedWith(
                compareByDescending<TeamStanding> { it.points }
                    .thenByDescending { it.goalDifference }
                    .thenByDescending { it.goalsFor }
                    .thenBy { it.teamName }
            )
        standingsMap[grp] = sortedList
    }

    return standingsMap
}

fun matchThirdPlaceTeams(
    winners: List<String>,
    thirdPlaces: List<String>
): List<String> {
    val result = mutableListOf<String>()
    val used = BooleanArray(thirdPlaces.size)

    fun backtrack(index: Int): Boolean {
        if (index == winners.size) return true
        val winnerGroup = resolveTeamGroup(winners[index])
        for (i in thirdPlaces.indices) {
            if (!used[i]) {
                val thirdGroup = resolveTeamGroup(thirdPlaces[i])
                if (winnerGroup != thirdGroup) {
                    used[i] = true
                    result.add(thirdPlaces[i])
                    if (backtrack(index + 1)) return true
                    result.removeAt(result.lastIndex)
                    used[i] = false
                }
            }
        }
        return false
    }

    if (backtrack(0)) {
        return result
    }
    return thirdPlaces
}


