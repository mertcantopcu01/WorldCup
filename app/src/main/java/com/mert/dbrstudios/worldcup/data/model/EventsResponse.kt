package com.mert.dbrstudios.worldcup.data.model

import com.google.gson.annotations.SerializedName

data class EventsResponse(
    @SerializedName("events") val events: List<MatchEvent>? = null
)
