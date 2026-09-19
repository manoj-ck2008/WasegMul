package com.agrelius.wasegmul.ui.yolo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YoloViewModelTest {

    @Test
    fun testInitialState() {
        val vm = YoloViewModel()
        assertNull(vm.error.value)
        assertEquals(0f, vm.fps.value, 0.001f)
        assertEquals(0, vm.detections.value.size)
    }

    @Test
    fun testSetAndClearError() {
        val vm = YoloViewModel()
        val errorMsg = "No camera hardware detected on this device."
        vm.setError(errorMsg)
        assertEquals(errorMsg, vm.error.value)

        vm.clearError()
        assertNull(vm.error.value)
    }
}
