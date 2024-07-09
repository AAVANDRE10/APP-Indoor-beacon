package com.example.beacon.api

import com.example.beacon.data.entities.BeaconPosition
import com.example.beacon.data.entities.Weight
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @GET("weights/weights")
    fun getWeights(@Query("matrixId") matrixId: Int): Call<List<Weight>>

    @GET("beacons/matrices/{matrixId}/beacons")
    fun getBeaconPositions(@Path("matrixId") matrixId: Int): Call<List<BeaconPosition>>
}