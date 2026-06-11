package com.mert.dbrstudios.worldcup.data.api

import com.mert.dbrstudios.worldcup.data.model.EventsResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface SportsDbApi {

    @GET("eventsseason.php")
    suspend fun getWorldCupFixtures(
        @Query("id") leagueId: String = "4429",
        @Query("s") season: String = "2026"
    ): EventsResponse
}
