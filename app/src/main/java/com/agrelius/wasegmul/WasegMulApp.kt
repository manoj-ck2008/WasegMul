package com.agrelius.wasegmul

import android.app.Application
import com.agrelius.wasegmul.data.WasteDatabase
import com.agrelius.wasegmul.repository.WasteRepository
import com.agrelius.wasegmul.utils.SettingsManager

class WasegMulApp : Application() {
    private val database by lazy { WasteDatabase.getDatabase(this) }
    val repository by lazy { WasteRepository(database.wasteDao()) }
    val settingsManager by lazy { SettingsManager(this) }
}
