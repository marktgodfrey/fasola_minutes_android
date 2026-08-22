package org.fasola.minutes

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.fasola.minutes.data.DatabaseInstaller
import org.fasola.minutes.data.DatabaseUpdater
import org.fasola.minutes.data.LeaderSummary
import org.fasola.minutes.data.MinutesRepository
import org.fasola.minutes.ui.MinutesApp
import org.fasola.minutes.ui.theme.MinutesTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val repositories = mutableListOf<MinutesRepository>()

    private data class StartupData(
        val repository: MinutesRepository,
        val leaders: List<LeaderSummary>,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_FaSoLaMinutes)
        super.onCreate(savedInstanceState)
        setContent {
            var startupData by remember { mutableStateOf<StartupData?>(null) }

            LaunchedEffect(Unit) {
                val startedAt = SystemClock.elapsedRealtime()
                val loadedRepository = withContext(Dispatchers.IO) {
                    DatabaseInstaller.install(applicationContext)
                    MinutesRepository(applicationContext).let { it to it.leaders() }
                }
                val remainingDisplayTime = 1_000L - (SystemClock.elapsedRealtime() - startedAt)
                if (remainingDisplayTime > 0) delay(remainingDisplayTime)
                repositories += loadedRepository.first
                startupData = StartupData(loadedRepository.first, loadedRepository.second)

                val updated = withContext(Dispatchers.IO) {
                    runCatching { DatabaseUpdater.checkForUpdate(applicationContext) }
                        .onFailure { Log.w("DatabaseUpdater", "Database update check failed", it) }
                        .getOrDefault(false)
                }
                if (updated) {
                    val updatedRepository = withContext(Dispatchers.IO) {
                        MinutesRepository(applicationContext).let { it to it.leaders() }
                    }
                    repositories += updatedRepository.first
                    startupData = StartupData(updatedRepository.first, updatedRepository.second)
                }
            }

            startupData?.let {
                key(it.repository) { MinutesTheme { MinutesApp(it.repository, it.leaders) } }
            } ?: Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF115740)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.splash_image),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 3.dp,
                )
            }
        }
    }

    override fun onDestroy() {
        repositories.forEach(MinutesRepository::close)
        repositories.clear()
        super.onDestroy()
    }
}
