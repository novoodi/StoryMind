package com.example.storymind.ai

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnDeviceEngineSmokeTest {

    @Test
    fun generateReturnsNonEmptyResponse() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = OnDeviceEngine(context)

        assertTrue("Model file not found at ${engine.modelPath}", engine.isModelAvailable)

        engine.initialize()
        try {
            val response = engine.generate("한국의 수도는 어디야? 한 문장으로 답해줘.")
            Log.i("OnDeviceEngineSmokeTest", "Response: $response")
            assertTrue("Response should not be blank", response.isNotBlank())
        } finally {
            engine.release()
        }
    }
}