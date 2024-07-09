package com.example.beacon.algorithm

import android.util.Log
import com.example.beacon.data.entities.BeaconPosition
import com.example.beacon.data.entities.Position

class PositionCalculator(
    private var beaconPositions: Map<String, BeaconPosition>
) {
    fun calculatePosition(beaconDistances: Map<String, Double>): Position? {
        if (beaconDistances.size < 3) return null

        val positions = beaconDistances.keys.mapNotNull { beaconPositions[it] }
        val distances = beaconDistances.values.toList()

        if (positions.size < 3) return null

        val weights = distances.map { 1 / (it * it) }
        val sumWeights = weights.sum()

        val x = positions.zip(weights).sumByDouble { it.first.x * it.second } / sumWeights
        val y = positions.zip(weights).sumByDouble { it.first.y * it.second } / sumWeights

        return Position(x, y)
    }

    fun updateBeaconPositions(newBeaconPositions: Map<String, BeaconPosition>) {
        beaconPositions = newBeaconPositions
        Log.d("PositionCalculator", "Updated beacon positions: $beaconPositions")
    }
}