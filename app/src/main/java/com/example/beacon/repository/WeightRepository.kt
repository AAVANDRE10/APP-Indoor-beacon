package com.example.beacon.repository

import com.example.beacon.api.RetrofitInstance
import com.example.beacon.data.entities.Weight
import retrofit2.Call

class WeightRepository {
    fun getWeights(matrixId: Int): Call<List<Weight>> {
        return RetrofitInstance.api.getWeights(matrixId)
    }
}