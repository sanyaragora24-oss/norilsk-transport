package com.example.data

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Маршруты и расписание разбираются вне главного потока и ровно один раз,
 * даже если карта, поиск и геолокация попросили данные одновременно.
 */
class RouteRepositoryLoadingTest {
    private val assets = File("src/main/assets")

    @Test
    fun concurrentFirstAccessParsesRoutesOnceAndSharesTheResult() {
        val opens = AtomicInteger()
        val repository = RouteRepository { name ->
            opens.incrementAndGet()
            Thread.sleep(50) // расширяем окно гонки
            File(assets, name).inputStream()
        }
        val threads = 8
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(threads)
        val results = (0 until threads).map {
            pool.submit<List<Route>> { start.await(); repository.getRoutes() }
        }
        start.countDown()
        val loaded = results.map { it.get(30, TimeUnit.SECONDS) }
        pool.shutdown()

        assertEquals("asset must be read once", 1, opens.get())
        assertTrue(loaded.all { it === loaded.first() })
        assertEquals(61, loaded.first().size)
        assertTrue(repository.getAllStops().isNotEmpty())
        assertTrue(repository.getRoutesForStop(repository.getAllStops().first().id).isNotEmpty())
    }

    @Test
    fun loadingRunsOnTheCallerDispatcherNotOnTheMainThread() = runBlocking {
        var loaderThread: Thread? = null
        val routes = RouteRepository { name ->
            loaderThread = Thread.currentThread()
            File(assets, name).inputStream()
        }
        val schedules = ScheduleRepository { name -> File(assets, name).inputStream() }
        val caller = Thread.currentThread()

        val (loadedRoutes, dataDate) = withContext(Dispatchers.IO) {
            routes.getRoutes() to schedules.dataDate
        }

        assertNotEquals(caller, loaderThread)
        assertTrue(loadedRoutes.isNotEmpty())
        assertEquals("04.10.2026", dataDate)
        assertTrue(schedules.getScheduleMap().isNotEmpty())
    }
}
