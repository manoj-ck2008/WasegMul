package com.agrelius.wasegmul.repository

import com.agrelius.wasegmul.data.BarcodeProduct
import com.agrelius.wasegmul.data.BarcodeProductDao
import com.agrelius.wasegmul.network.OpenFoodFactsApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeRepositoryTest {

    private class FakeBarcodeProductDao : BarcodeProductDao {
        val products = mutableListOf<BarcodeProduct>()

        override suspend fun findByBarcode(barcode: String): BarcodeProduct? =
            products.find { it.barcode == barcode }

        override suspend fun insertOrReplace(product: BarcodeProduct) {
            products.removeAll { it.barcode == product.barcode }
            products.add(product)
        }

        override suspend fun insertAll(newProducts: List<BarcodeProduct>) {
            newProducts.forEach { product ->
                if (products.none { it.barcode == product.barcode }) {
                    products.add(product)
                }
            }
        }

        override suspend fun updateLastAccessed(barcode: String, now: Long) {
            val idx = products.indexOfFirst { it.barcode == barcode }
            if (idx != -1) {
                products[idx] = products[idx].copy(lastAccessed = now)
            }
        }

        override fun getRecentScans(limit: Int): Flow<List<BarcodeProduct>> =
            flowOf(products.sortedByDescending { it.lastAccessed }.take(limit))

        override suspend fun getCacheSize(): Int = products.size

        override suspend fun getPreloadedCount(): Int =
            products.count { it.source == "preloaded" }

        override suspend fun pruneOldEntries(cutoff: Long) {
            products.removeAll { it.cachedAt < cutoff && it.source != "preloaded" }
        }

        // Bounded prune-batch mirror (data batch): at most [limit] stale rows.
        override suspend fun pruneOldEntriesPaged(cutoff: Long, limit: Int) {
            products
                .filter { it.cachedAt < cutoff && it.source != "preloaded" && it.source != "user_identified" }
                .take(limit)
                .forEach { products.remove(it) }
        }
    }

    @Test
    fun resolve_returnsCachedProduct_whenPresentInRoom() = runBlocking {
        val dao = FakeBarcodeProductDao()
        val cachedProduct = BarcodeProduct(
            barcode = "5449000000996",
            productName = "Coca-Cola Original 330ml",
            brand = "Coca-Cola",
            category = "Recyclable",
            subclass = "Metal",
            materials = "Aluminium",
            componentsJson = null,
            weightGrams = 15.0,
            ecoscore = "b",
            source = "api_cache"
        )
        dao.insertOrReplace(cachedProduct)

        val repository = BarcodeRepository(
            dao = dao,
            api = OpenFoodFactsApi(),
            isOnline = { false }
        )

        val result = repository.resolve("5449000000996")
        assertTrue(result is BarcodeResolutionResult.Found)
        val found = result as BarcodeResolutionResult.Found
        assertEquals("Coca-Cola Original 330ml", found.product.productName)
        assertEquals("cached", found.source)
    }

    @Test
    fun resolve_returnsPreloadedProduct_whenPresentInPreloadedDb() = runBlocking {
        val dao = FakeBarcodeProductDao()
        val preloadedProduct = BarcodeProduct(
            barcode = "7622210449283",
            productName = "Oreo Original Sandwich Cookies",
            brand = "Oreo",
            category = "Trash",
            subclass = "Plastic",
            materials = "Plastic film (PP)",
            componentsJson = null,
            weightGrams = 8.0,
            ecoscore = "e",
            source = "preloaded"
        )
        dao.insertOrReplace(preloadedProduct)

        val repository = BarcodeRepository(
            dao = dao,
            api = OpenFoodFactsApi(),
            isOnline = { false }
        )

        val result = repository.resolve("7622210449283")
        assertTrue(result is BarcodeResolutionResult.Found)
        val found = result as BarcodeResolutionResult.Found
        assertEquals("Oreo Original Sandwich Cookies", found.product.productName)
        assertEquals("preloaded", found.source)
    }

    @Test
    fun resolve_returnsNotFound_whenOfflineAndNotInLocalDb() = runBlocking {
        val dao = FakeBarcodeProductDao()
        val repository = BarcodeRepository(
            dao = dao,
            api = OpenFoodFactsApi(),
            isOnline = { false }
        )

        val result = repository.resolve("0000000000000")
        assertTrue(result is BarcodeResolutionResult.NotFound)
    }

    @Test
    fun pruneCache_removesOldApiEntries_butPreservesPreloadedEntries() = runBlocking {
        val dao = FakeBarcodeProductDao()
        val oldTimestamp = System.currentTimeMillis() - (100L * 24 * 60 * 60 * 1000)

        val oldApiProduct = BarcodeProduct(
            barcode = "1111111111111",
            productName = "Old Snack",
            brand = "SnackCo",
            category = "Trash",
            subclass = "Plastic",
            materials = "Plastic",
            componentsJson = null,
            weightGrams = 5.0,
            ecoscore = "d",
            source = "api_cache",
            cachedAt = oldTimestamp
        )

        val preloadedProduct = BarcodeProduct(
            barcode = "2222222222222",
            productName = "Curated Beverage",
            brand = "DrinkCo",
            category = "Recyclable",
            subclass = "Glass",
            materials = "Glass",
            componentsJson = null,
            weightGrams = 200.0,
            ecoscore = "b",
            source = "preloaded",
            cachedAt = oldTimestamp
        )

        dao.insertOrReplace(oldApiProduct)
        dao.insertOrReplace(preloadedProduct)
        assertEquals(2, dao.getCacheSize())

        val repository = BarcodeRepository(
            dao = dao,
            api = OpenFoodFactsApi(),
            isOnline = { false }
        )

        repository.pruneCache(days = 90)

        assertEquals(1, dao.getCacheSize())
        val remaining = dao.findByBarcode("2222222222222")
        assertNotNull(remaining)
        assertEquals("preloaded", remaining?.source)
    }

    @Test
    fun updateLastAccessed_updatesTimestampOnResolve() = runBlocking {
        val dao = FakeBarcodeProductDao()
        val initialTime = 1000L
        val product = BarcodeProduct(
            barcode = "3333333333333",
            productName = "Test Item",
            brand = "TestBrand",
            category = "Recyclable",
            subclass = "Cardboard",
            materials = "Cardboard",
            componentsJson = null,
            weightGrams = 30.0,
            ecoscore = "a",
            source = "preloaded",
            lastAccessed = initialTime
        )
        dao.insertOrReplace(product)

        val repository = BarcodeRepository(
            dao = dao,
            api = OpenFoodFactsApi(),
            isOnline = { false }
        )

        repository.resolve("3333333333333")

        val updated = dao.findByBarcode("3333333333333")
        assertNotNull(updated)
        assertTrue((updated?.lastAccessed ?: 0L) > initialTime)
    }

    @Test
    fun saveUserIdentifiedProduct_persistsWithUserIdentifiedSource() = runBlocking {
        val dao = FakeBarcodeProductDao()
        val repository = BarcodeRepository(
            dao = dao,
            api = OpenFoodFactsApi(),
            isOnline = { false }
        )

        val product = BarcodeProduct(
            barcode = "999888777",
            productName = "Custom Laptop",
            brand = "Generic",
            category = "E-Waste",
            subclass = "E-Waste",
            materials = "Electronic",
            componentsJson = null,
            weightGrams = 1500.0,
            ecoscore = null,
            source = "user_identified"
        )

        repository.saveUserIdentifiedProduct(product)

        val saved = dao.findByBarcode("999888777")
        assertNotNull(saved)
        assertEquals("user_identified", saved?.source)
        assertEquals("Custom Laptop", saved?.productName)
        assertEquals("E-Waste", saved?.category)
    }

    @Test
    fun inferPackagingFromProduct_infersPackagingFromKeywords() {
        val repository = BarcodeRepository(
            dao = FakeBarcodeProductDao(),
            api = OpenFoodFactsApi(),
            isOnline = { false }
        )

        val laptopProduct = BarcodeProduct(
            barcode = "123",
            productName = "MacBook Pro Laptop M3",
            brand = "Apple",
            category = "Trash",
            subclass = "Trash",
            materials = null,
            componentsJson = null,
            weightGrams = 1500.0,
            ecoscore = null,
            source = "api"
        )
        val inferredLaptop = repository.inferPackagingFromProduct(laptopProduct)
        assertEquals("E-Waste", inferredLaptop.category)
        assertEquals("Electronic Device", inferredLaptop.subclass)

        val unoProduct = BarcodeProduct(
            barcode = "456",
            productName = "Mattel UNO Card Game Box",
            brand = "Mattel",
            category = "Trash",
            subclass = "Trash",
            materials = null,
            componentsJson = null,
            weightGrams = 150.0,
            ecoscore = null,
            source = "api"
        )
        val inferredUno = repository.inferPackagingFromProduct(unoProduct)
        assertEquals("Recyclable", inferredUno.category)
        assertEquals("Cardboard", inferredUno.subclass)

        val washerProduct = BarcodeProduct(
            barcode = "789",
            productName = "LG Front Load Washing Machine",
            brand = "LG",
            category = "Trash",
            subclass = "Trash",
            materials = null,
            componentsJson = null,
            weightGrams = 50000.0,
            ecoscore = null,
            source = "api"
        )
        val inferredWasher = repository.inferPackagingFromProduct(washerProduct)
        assertEquals("E-Waste", inferredWasher.category)
        assertEquals("Electronic Device", inferredWasher.subclass)

        val sodaCanProduct = BarcodeProduct(
            barcode = "101",
            productName = "Cola Soda Can 330ml",
            brand = "Coca-Cola",
            category = "Trash",
            subclass = "Trash",
            materials = null,
            componentsJson = null,
            weightGrams = 15.0,
            ecoscore = null,
            source = "api"
        )
        val inferredCan = repository.inferPackagingFromProduct(sodaCanProduct)
        assertEquals("Recyclable", inferredCan.category)
        assertEquals("Metal", inferredCan.subclass)

        val snackProduct = BarcodeProduct(
            barcode = "102",
            productName = "Potato Chips Snack Pack",
            brand = "Lays",
            category = "Recyclable",
            subclass = "Plastic",
            materials = null,
            componentsJson = null,
            weightGrams = 5.0,
            ecoscore = null,
            source = "api"
        )
        val inferredSnack = repository.inferPackagingFromProduct(snackProduct)
        assertEquals("Trash", inferredSnack.category)
        assertEquals("Plastic", inferredSnack.subclass)
    }
}
