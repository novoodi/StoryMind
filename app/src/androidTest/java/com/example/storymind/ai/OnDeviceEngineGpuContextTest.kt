package com.example.storymind.ai

import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.storymind.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Diagnostic-only test: checks whether GPU backend init succeeds when a real
 * foregrounded Activity (with a live window/EGL surface) is present, as opposed
 * to a bare instrumentation process with no window.
 */
@RunWith(AndroidJUnit4::class)
class OnDeviceEngineGpuContextTest {

    @Test
    fun generateWithForegroundActivity() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            Thread.sleep(1500)
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val engine = OnDeviceEngine(context)
            runBlocking {
                engine.initialize()
                val response = engine.generate("한국의 수도는 어디야? 한 문장으로 답해줘.")
                Log.i("OnDeviceEngineGpuContextTest", "Response: $response")
                engine.release()
            }
        } finally {
            scenario.close()
        }
    }
}