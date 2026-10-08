package com.example.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherRepositoryTest {
    @Test
    fun metNoUserAgentIdentifiesTheAppAndProjectContact() {
        assertEquals("NorilskTransit/1.2.10 (nightdriveai.ru)", WeatherRepository.MET_NO_USER_AGENT)
        assertTrue(WeatherRepository.MET_NO_USER_AGENT.contains("NorilskTransit/1.2.10"))
        assertTrue(WeatherRepository.MET_NO_USER_AGENT.contains("nightdriveai.ru"))
    }

    @Test
    fun requestsUseTheTwoRealWeatherProvidersAndNorilskCoordinates() {
        val repository = WeatherRepository()
        val metNo = repository.createMetNoRequest()
        val openMeteo = repository.createOpenMeteoRequest()

        assertEquals("api.met.no", metNo.url.host)
        assertEquals("weatherapi/locationforecast/2.0/compact", metNo.url.encodedPath.trimStart('/'))
        assertEquals("69.3498", metNo.url.queryParameter("lat"))
        assertEquals("88.2005", metNo.url.queryParameter("lon"))
        assertEquals(WeatherRepository.MET_NO_USER_AGENT, metNo.header("User-Agent"))

        assertEquals("api.open-meteo.com", openMeteo.url.host)
        assertEquals("69.3498", openMeteo.url.queryParameter("latitude"))
        assertEquals("88.2005", openMeteo.url.queryParameter("longitude"))
        assertEquals("12", openMeteo.url.queryParameter("forecast_hours"))
    }
}
