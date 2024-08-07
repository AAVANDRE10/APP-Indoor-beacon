package com.example.beacon.data.vm

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.example.beacon.data.entities.BeaconPosition
import com.example.beacon.data.repository.BeaconRepository

class BeaconViewModel : ViewModel() {
    private val repository: BeaconRepository = BeaconRepository()

    val beaconPositions: LiveData<List<BeaconPosition>> get() = repository.beaconPositions

    fun loadBeaconPositions(matrixId: Int) {
        repository.getBeaconPositions(matrixId)
    }
}