package com.example.beacon.data.repository

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.beacon.api.RetrofitInstance
import com.example.beacon.data.entities.BeaconPosition
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class BeaconRepository {

    private val _beaconPositions = MutableLiveData<List<BeaconPosition>>()
    val beaconPositions: LiveData<List<BeaconPosition>> get() = _beaconPositions

    fun getBeaconPositions(matrixId: Int) {
        Log.d("BeaconRepository", "Fetching beacon positions for matrixId: $matrixId")
        RetrofitInstance.api.getBeaconPositions(matrixId).enqueue(object : Callback<List<BeaconPosition>> {
            override fun onResponse(call: Call<List<BeaconPosition>>, response: Response<List<BeaconPosition>>) {
                if (response.isSuccessful) {
                    response.body()?.let {
                        _beaconPositions.postValue(it)
                        Log.d("BeaconRepository", "Fetched beacon positions: $it")
                    }
                } else {
                    Log.e("BeaconRepository", "Failed to fetch beacon positions: ${response.code()} ${response.message()} ${response.errorBody()?.string()}")
                }
            }

            override fun onFailure(call: Call<List<BeaconPosition>>, t: Throwable) {
                Log.e("BeaconRepository", "Error fetching beacon positions", t)
            }
        })
    }
}