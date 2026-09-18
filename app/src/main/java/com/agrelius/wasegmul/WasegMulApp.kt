package com.agrelius.wasegmul

import android.app.Application
import com.agrelius.wasegmul.data.WasteDatabase
import com.agrelius.wasegmul.network.OpenFoodFactsApi
import com.agrelius.wasegmul.repository.BarcodeRepository
import com.agrelius.wasegmul.repository.WasteRepository
import com.agrelius.wasegmul.utils.ConnectivityChecker
import com.agrelius.wasegmul.utils.SettingsManager

class WasegMulApp : Application() {
    private val database by lazy { WasteDatabase.getDatabase(this) }
    val repository by lazy { WasteRepository(database.wasteDao()) }
    val barcodeRepository by lazy {
        BarcodeRepository(
            dao = database.barcodeProductDao(),
            api = OpenFoodFactsApi(),
            isOnline = { ConnectivityChecker.isOnline(this) },
            context = this
        )
    }
    val modelManager by lazy { com.agrelius.wasegmul.ml.ModelManager(this) }
    val settingsManager by lazy { SettingsManager(this) }
}
