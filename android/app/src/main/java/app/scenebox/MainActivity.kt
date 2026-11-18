package app.scenebox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.scenebox.core.Capability
import app.scenebox.core.CapabilityProbe
import app.scenebox.core.DeviceFingerprint
import app.scenebox.core.ShizukuBridge
import app.scenebox.ui.HomeScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Root()
                }
            }
        }
    }
}

@Composable
private fun Root() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var capability by remember { mutableStateOf<Capability?>(null) }
    var fingerprint by remember { mutableStateOf<DeviceFingerprint?>(null) }
    var busy by remember { mutableStateOf(true) }
    var lastTryRoot by remember { mutableStateOf(false) }

    fun probe(tryRoot: Boolean) {
        lastTryRoot = tryRoot
        busy = true
        scope.launch {
            val fp = withContext(Dispatchers.IO) { DeviceFingerprint.collect() }
            val cap = withContext(Dispatchers.IO) { CapabilityProbe.probe(context, tryRoot) }
            fingerprint = fp
            capability = cap
            busy = false
        }
    }

    LaunchedEffect(Unit) { probe(false) }

    HomeScreen(
        cap = capability,
        fp = fingerprint,
        busy = busy,
        onRequestShizuku = {
            ShizukuBridge.request { granted ->
                if (granted) {
                    scope.launch { probe(lastTryRoot) }
                }
            }
        },
        onProbeRoot = { probe(true) },
        onRefresh = { probe(lastTryRoot) },
    )
}
