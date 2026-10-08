package com.example.data

/**
 * Some industrial-route stop pairs are not represented correctly in the public
 * road graph.  For these routes the bundled polyline is curated from the bus
 * route itself, so replacing it with an on-device road-router response can
 * create a large, fictional detour.
 */
object RouteGeometryPolicy {
    private val verifiedAssetRouteNumbers = setOf("31", "31Э")

    /** Keeps every direction and variant of 31/31Э on its vetted asset geometry. */
    fun useVerifiedAssetGeometry(route: Route): Boolean =
        route.number in verifiedAssetRouteNumbers && route.polyline.size >= 2
}
