package com.mert.dbrstudios.worldcup.data.repository

import com.mert.dbrstudios.worldcup.data.api.RetrofitInstance
import com.mert.dbrstudios.worldcup.data.model.MatchEvent

class FixtureRepository {

    private val api = RetrofitInstance.api

    suspend fun getFixtures(): Result<List<MatchEvent>> {
        return try {
            val response = api.getWorldCupFixtures()
            val events = response.events
            if (events != null) {
                val sorted = events.sortedBy { it.strTimestamp ?: "" }
                Result.success(sorted)
            } else {
                Result.failure(Exception("Veri bulunamadı"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
