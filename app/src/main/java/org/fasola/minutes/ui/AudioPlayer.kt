package org.fasola.minutes.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.fasola.minutes.data.Lesson
import org.fasola.minutes.data.SingingSummary
import org.fasola.minutes.data.SongRecording

data class AudioTrack(
    val lesson: Lesson,
    val singingName: String,
    val singingDate: String,
)

class AudioPlayerController(context: Context) {
    private val appContext = context.applicationContext
    private val progressHandler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null
    private var queue: List<AudioTrack> = emptyList()
    private val progressUpdater = object : Runnable {
        override fun run() {
            val player = mediaPlayer
            if (player != null && !isLoading) {
                positionMillis = runCatching { player.currentPosition }.getOrDefault(positionMillis)
            }
            if (player != null) progressHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL_MS)
        }
    }

    var currentTrack by mutableStateOf<AudioTrack?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var queueIndex by mutableIntStateOf(-1)
        private set
    var positionMillis by mutableIntStateOf(0)
        private set
    var durationMillis by mutableIntStateOf(0)
        private set

    val hasNext: Boolean get() = queueIndex in 0 until queue.lastIndex

    fun playLessons(lessons: List<Lesson>, startIndex: Int, singing: SingingSummary) {
        queue = lessons.drop(startIndex).mapNotNull { lesson ->
            lesson.audioUrl?.let { AudioTrack(lesson, singing.name, singing.date) }
        }
        if (queue.isEmpty()) return
        queueIndex = 0
        prepareCurrent(autoPlay = true)
    }

    fun playRecordings(recordings: List<SongRecording>, startIndex: Int) {
        queue = recordings.drop(startIndex).mapNotNull { recording ->
            recording.lesson.audioUrl?.let {
                AudioTrack(recording.lesson, recording.singing.name, recording.singing.date)
            }
        }
        if (queue.isEmpty()) return
        queueIndex = 0
        prepareCurrent(autoPlay = true)
    }

    fun toggle() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) {
            player.pause()
            isPlaying = false
        } else if (!isLoading) {
            player.start()
            isPlaying = true
        }
    }

    fun next() {
        if (!hasNext) return
        queueIndex += 1
        prepareCurrent(autoPlay = true)
    }

    fun seekTo(positionMillis: Int) {
        val player = mediaPlayer ?: return
        if (isLoading || durationMillis <= 0) return
        val target = positionMillis.coerceIn(0, durationMillis)
        runCatching { player.seekTo(target) }
            .onSuccess { this.positionMillis = target }
    }

    fun close() {
        progressHandler.removeCallbacks(progressUpdater)
        mediaPlayer?.release()
        mediaPlayer = null
        queue = emptyList()
        queueIndex = -1
        currentTrack = null
        isPlaying = false
        isLoading = false
        positionMillis = 0
        durationMillis = 0
    }

    private fun prepareCurrent(autoPlay: Boolean) {
        progressHandler.removeCallbacks(progressUpdater)
        mediaPlayer?.release()
        val track = queue.getOrNull(queueIndex) ?: return close()
        currentTrack = track
        isLoading = true
        isPlaying = false
        positionMillis = 0
        durationMillis = 0
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build(),
            )
            setDataSource(appContext, android.net.Uri.parse(track.lesson.audioUrl))
            setOnPreparedListener { prepared ->
                this@AudioPlayerController.isLoading = false
                this@AudioPlayerController.durationMillis = prepared.duration.coerceAtLeast(0)
                progressHandler.post(progressUpdater)
                if (autoPlay) {
                    prepared.start()
                    this@AudioPlayerController.isPlaying = true
                }
            }
            setOnCompletionListener {
                if (hasNext) next() else close()
            }
            setOnErrorListener { _, _, _ ->
                if (hasNext) next() else close()
                true
            }
            prepareAsync()
        }
    }


    private companion object {
        const val PROGRESS_UPDATE_INTERVAL_MS = 500L
    }
}
