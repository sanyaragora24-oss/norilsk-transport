package com.example.ui.map

import com.example.ui.map.TrafficPolicy.Kind
import com.example.ui.map.TrafficPolicy.LevelColor
import com.example.ui.map.TrafficPolicy.SdkState
import com.yandex.mapkit.map.MapType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficPolicyTest {

    private fun status(
        enabled: Boolean = true,
        network: Boolean = true,
        sdk: SdkState = SdkState.LOADING,
        level: Int? = null,
        color: LevelColor? = null,
        timedOut: Boolean = false,
        denied: Boolean = false
    ) = TrafficPolicy.status(enabled, network, sdk, level, color, timedOut, denied)

    @Test
    fun `map always renders on the vector base where the traffic layer lives`() {
        // MapType.MAP (raster) was the old forced default; nothing may bring it back.
        MapType.values().forEach { stored ->
            assertEquals(MapType.VECTOR_MAP, TrafficPolicy.effectiveMapType(stored))
        }
        assertEquals(MapType.VECTOR_MAP, TrafficPolicy.effectiveMapType(null))
        assertEquals(MapType.VECTOR_MAP, MapUiState().mapType)
    }

    @Test
    fun `traffic layer is off by default and shows no status`() {
        assertFalse(MapUiState().isTrafficVisible)
        val s = status(enabled = false, sdk = SdkState.READY, level = 5, color = LevelColor.RED)
        assertEquals(Kind.OFF, s.kind)
        assertNull(s.message)
    }

    @Test
    fun `no network wins over any stale sdk data`() {
        val s = status(network = false, sdk = SdkState.READY, level = 3, color = LevelColor.YELLOW)
        assertEquals(Kind.NO_NETWORK, s.kind)
        assertEquals("Пробки недоступны: нет интернета", s.message)
    }

    @Test
    fun `loading until mapkit answers`() {
        assertEquals(Kind.LOADING, status(sdk = SdkState.LOADING).kind)
        assertEquals(Kind.LOADING, status(sdk = SdkState.IDLE).kind)
    }

    @Test
    fun `timeout without data is reported honestly, never as traffic`() {
        val s = status(sdk = SdkState.LOADING, timedOut = true)
        assertEquals(Kind.NO_DATA, s.kind)
        assertNull(s.levelColor)
        assertTrue(s.message!!.contains("не прислал"))
    }

    @Test
    fun `real data beats the timeout flag`() {
        val s = status(sdk = SdkState.READY, level = 2, color = LevelColor.GREEN, timedOut = true)
        assertEquals(Kind.READY_WITH_LEVEL, s.kind)
        assertEquals("Пробки: 2 балла по данным Яндекса", s.message)
        assertEquals(LevelColor.GREEN, s.levelColor)
    }

    @Test
    fun `norilsk has road colours but no city score`() {
        // Яндекс не считает балл пробок для Норильска: onTrafficChanged(null).
        val s = status(sdk = SdkState.READY, level = null)
        assertEquals(Kind.READY_WITHOUT_LEVEL, s.kind)
        assertNull(s.level)
        assertNull(s.levelColor)
        assertNotNull(s.message)
        assertTrue(s.message!!.contains("Общего балла пробок для Норильска Яндекс не даёт"))
        assertFalse(s.message!!.contains("цвет"))
    }

    @Test
    fun `expired data says it is refreshing`() {
        assertEquals(Kind.EXPIRED, status(sdk = SdkState.EXPIRED).kind)
    }

    @Test
    fun `russian plural for points`() {
        assertEquals("баллов", TrafficPolicy.pointsWord(0))
        assertEquals("балл", TrafficPolicy.pointsWord(1))
        assertEquals("балла", TrafficPolicy.pointsWord(3))
        assertEquals("баллов", TrafficPolicy.pointsWord(5))
        assertEquals("баллов", TrafficPolicy.pointsWord(10))
        assertEquals("баллов", TrafficPolicy.pointsWord(11))
        assertEquals("балл", TrafficPolicy.pointsWord(21))
    }

    @Test
    fun `route line does not hide the street colour while traffic is on`() {
        val off = TrafficPolicy.routeStroke(trafficVisible = false)
        assertEquals(0xFF000000.toInt(), off.outlineColor)
        assertEquals(8f, off.outlineWidth)
        assertEquals(0xFF, off.lineAlpha)

        val on = TrafficPolicy.routeStroke(trafficVisible = true)
        assertEquals(0f, on.outlineWidth)          // no opaque black band over the road
        assertTrue(on.lineAlpha < 0xFF)            // translucent
        assertTrue(on.lineWidth < off.lineWidth)   // thinner than the road colour band

        val journeyOff = TrafficPolicy.routeStroke(trafficVisible = false, journey = true)
        assertEquals(9f, journeyOff.outlineWidth)
        assertEquals(on, TrafficPolicy.routeStroke(trafficVisible = true, journey = true))
    }

    @Test
    fun `alpha is applied without touching the route colour`() {
        val red = 0xFFE53935.toInt()
        val translucent = TrafficPolicy.withAlpha(red, 0x99)
        assertEquals(0x99, translucent ushr 24)
        assertEquals(red and 0x00FFFFFF, translucent and 0x00FFFFFF)
        assertEquals(0xFF, TrafficPolicy.withAlpha(red, 999) ushr 24)
    }

    @Test
    fun `server refusal of the traffic layer is reported even when a score arrives`() {
        val s = status(sdk = SdkState.READY, level = 3, color = LevelColor.GREEN, denied = true)
        assertEquals(Kind.ACCESS_DENIED, s.kind)
        assertTrue(s.message!!.contains("403"))
        assertEquals(Kind.NO_NETWORK, status(network = false, denied = true).kind)
        assertEquals(Kind.OFF, status(enabled = false, denied = true).kind)
    }

    // Строки из logcat эмулятора с v1.2.10 (MapKit 4.42.0), 12.10.2026.
    private val forbiddenLog = listOf(
        "W/yandex.maps(19028): decltype: Unexpected server response: Forbidden. Body :<html>",
        "W/yandex.maps(19028): <head><title>403 Forbidden</title></head>",
        "W/yandex.maps(19028): <body>",
        "W/yandex.maps(19028): <center><h1>403 Forbidden</h1></center>",
        "W/yandex.maps(19028): <hr><center>nginx/1.26.2</center>",
        "W/yandex.maps(19028): </body>",
        "W/yandex.maps(19028): </html>",
        "W/yandex.maps(19028): Could not update layer, url: [https://proxy.mob.maps.yandex.net:443/mapkit2/layers/2.x/vtrf3/version?lang=en_US&miid=a22a]"
    )

    @Test
    fun `detects the 403 on the vector traffic layer in the mapkit log`() {
        assertTrue(TrafficPolicy.isTrafficLayerForbidden(forbiddenLog))
    }

    @Test
    fun `other mapkit warnings are not a traffic refusal`() {
        assertFalse(TrafficPolicy.isTrafficLayerForbidden(emptyList()))
        // 403 на другом слое (карта) — не про пробки.
        assertFalse(TrafficPolicy.isTrafficLayerForbidden(forbiddenLog.dropLast(1) +
            "W/yandex.maps(1): Could not update layer, url: [https://proxy.mob.maps.yandex.net:443/mapkit2/layers/2.x/vmap3/version]"))
        // Таймаут слоя пробок без 403 — сеть, а не отказ.
        assertFalse(TrafficPolicy.isTrafficLayerForbidden(listOf(
            "W/yandex.maps(1): decltype: Couldn't fetch \"https://proxy.mob.maps.yandex.net:443/mapkit2/layers/2.x/vmap3/tiles\" because of: Operation timeout",
            "W/yandex.maps(1): Could not update layer, url: [https://proxy.mob.maps.yandex.net:443/mapkit2/layers/2.x/vtrf3/version]"
        )))
    }
}
