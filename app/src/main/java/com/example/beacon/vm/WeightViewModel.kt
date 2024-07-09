package com.example.beacon.vm

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.beacon.data.entities.Weight
import com.example.beacon.repository.WeightRepository
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class WeightViewModel : ViewModel() {
    private val repository = WeightRepository()
    private val _weights = MutableLiveData<List<Weight>>()
    val weights: LiveData<List<Weight>> get() = _weights

    fun loadWeights(matrixId: Int) {
        repository.getWeights(matrixId).enqueue(object : Callback<List<Weight>> {
            override fun onResponse(call: Call<List<Weight>>, response: Response<List<Weight>>) {
                if (response.isSuccessful) {
                    _weights.value = response.body()
                } else {
                    // Log error or handle error case
                }
            }

            override fun onFailure(call: Call<List<Weight>>, t: Throwable) {
                // Log error or handle error case
            }
        })
    }
}