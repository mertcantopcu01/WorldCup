package com.mert.dbrstudios.worldcup.data.model

import com.google.gson.annotations.SerializedName

data class MatchEvent(
    @SerializedName("idEvent")       val idEvent: String = "",
    @SerializedName("strEvent")      val strEvent: String = "",
    @SerializedName("strHomeTeam")   val strHomeTeam: String? = null,
    @SerializedName("strAwayTeam")   val strAwayTeam: String? = null,
    @SerializedName("strHomeTeamBadge") val strHomeTeamBadge: String? = null,
    @SerializedName("strAwayTeamBadge") val strAwayTeamBadge: String? = null,
    @SerializedName("intHomeScore")  val intHomeScore: String? = null,
    @SerializedName("intAwayScore")  val intAwayScore: String? = null,
    @SerializedName("strTimestamp")  val strTimestamp: String? = null,
    @SerializedName("dateEvent")     val dateEvent: String? = null,
    @SerializedName("strVenue")      val strVenue: String? = null,
    @SerializedName("intRound")      val intRound: String? = null,
    @SerializedName("strStatus")     val strStatus: String? = null,
    @SerializedName("strTimeLocal")  val strTimeLocal: String? = null,
    @SerializedName("dateEventLocal") val dateEventLocal: String? = null,
    @SerializedName("strCountry")    val strCountry: String? = null
)
