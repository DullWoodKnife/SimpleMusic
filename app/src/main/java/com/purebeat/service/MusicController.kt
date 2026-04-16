package com.purebeat.service

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.purebeat.data.model.PlaybackMode
import com.purebeat.data.model.PlaybackState
import com.purebeat.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var currentPlaylist: List<Song> = emptyList()
    private var playbackMode: PlaybackMode = PlaybackMode.SEQUENCE

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlaybackState()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            updatePlaybackState()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updatePlaybackState()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            when (repeatMode) {
                Player.REPEAT_MODE_ONE -> playbackMode = PlaybackMode.REPEAT_ONE
                Player.REPEAT_MODE_OFF -> {
                    if (mediaController?.shuffleModeEnabled == true) {
                        playbackMode = PlaybackMode.SHUFFLE
                    } else {
                        playbackMode = PlaybackMode.SEQUENCE
                    }
                }
                Player.REPEAT_MODE_ALL -> playbackMode = PlaybackMode.SEQUENCE
            }
            updatePlaybackState()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            playbackMode = if (shuffleModeEnabled) {
                PlaybackMode.SHUFFLE
            } else {
                PlaybackMode.SEQUENCE
            }
            updatePlaybackState()
        }
    }

    fun initialize() {
        if (controllerFuture != null) return

        val sessionToken = SessionToken(
            context,
            ComponentName(context, MusicPlaybackService::class.java)
        )

        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            mediaController = controllerFuture?.get()
            mediaController?.addListener(playerListener)
        }, MoreExecutors.directExecutor())
    }

    fun release() {
        mediaController?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        mediaController = null
    }

    fun setPlaylist(songs: List<Song>, startIndex: Int = 0) {
        currentPlaylist = songs

        val mediaItems = songs.map { song ->
            MediaItem.Builder()
                .setMediaId(song.id.toString())
                .setUri(song.uri)
                .build()
        }

        mediaController?.setMediaItems(mediaItems, startIndex, 0)
        mediaController?.prepare()
        updatePlaybackState()
    }

    fun play() {
        mediaController?.play()
    }

    fun pause() {
        mediaController?.pause()
    }

    fun playNext() {
        mediaController?.seekToNextMediaItem()
    }

    fun playPrevious() {
        if ((mediaController?.currentPosition ?: 0) > 3000) {
            mediaController?.seekTo(0)
        } else {
            mediaController?.seekToPreviousMediaItem()
        }
    }

    fun seekTo(position: Long) {
        mediaController?.seekTo(position)
    }

    fun setPlaybackMode(mode: PlaybackMode) {
        playbackMode = mode
        when (mode) {
            PlaybackMode.REPEAT_ONE -> {
                mediaController?.repeatMode = Player.REPEAT_MODE_ONE
                mediaController?.shuffleModeEnabled = false
            }
            PlaybackMode.SHUFFLE -> {
                mediaController?.repeatMode = Player.REPEAT_MODE_OFF
                mediaController?.shuffleModeEnabled = true
            }
            PlaybackMode.SEQUENCE -> {
                mediaController?.repeatMode = Player.REPEAT_MODE_OFF
                mediaController?.shuffleModeEnabled = false
            }
        }
        updatePlaybackState()
    }

    fun getCurrentPosition(): Long = mediaController?.currentPosition ?: 0

    fun getDuration(): Long = mediaController?.duration ?: 0

    fun isPlaying(): Boolean = mediaController?.isPlaying ?: false

    private fun updatePlaybackState() {
        val controller = mediaController ?: return

        val currentMediaItem = controller.currentMediaItem
        val currentSong = if (currentMediaItem != null && currentPlaylist.isNotEmpty()) {
            val index = controller.currentMediaItemIndex
            if (index in currentPlaylist.indices) {
                currentPlaylist[index]
            } else null
        } else null

        _playbackState.value = PlaybackState(
            currentSong = currentSong,
            isPlaying = controller.isPlaying,
            currentPosition = controller.currentPosition,
            duration = controller.duration.takeIf { it > 0 } ?: 0,
            playbackMode = playbackMode,
            currentPlaylist = currentPlaylist,
            currentIndex = controller.currentMediaItemIndex
        )
    }

    fun getController(): MediaController? = mediaController
}
