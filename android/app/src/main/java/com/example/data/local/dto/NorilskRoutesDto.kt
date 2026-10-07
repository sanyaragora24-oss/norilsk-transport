package com.example.data.local.dto

import kotlinx.serialization.Serializable

@Serializable
data class NorilskRoutesDto(
    val routes: List<RouteDto>
)

@Serializable
data class RouteDto(
    val id: String,
    val busId: Int,
    val number: String,
    val slug: String,
    val direction: Int,
    val origin: String,
    val destination: String,
    val colorArgb: Long,
    val stops: List<StopDto>,
    val polyline: List<LatLonDto>
)

@Serializable
data class StopDto(
    val id: Int,
    val name: String,
    val lat: Double,
    val lon: Double
)

@Serializable
data class LatLonDto(
    val lat: Double,
    val lon: Double
)
