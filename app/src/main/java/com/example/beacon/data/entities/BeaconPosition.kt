package com.example.beacon.data.entities

data class BeaconPosition(
    val id: Int,
    val matrixId: Int,
    val identifier: String,
    val x: Double,
    val y: Double,
    val major: Int,
    val minor: Int
)