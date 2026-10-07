package com.example.ui.map

import com.yandex.mapkit.geometry.Point

sealed class MapCommand {
    data class FocusStop(val point: Point, val zoom: Float = 16f) : MapCommand()
    data class FocusPoints(val points: List<Point>) : MapCommand()
    data class FocusUserLocation(val point: Point) : MapCommand()
    object ZoomIn : MapCommand()
    object ZoomOut : MapCommand()
}
