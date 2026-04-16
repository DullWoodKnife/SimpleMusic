package com.purebeat.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.purebeat.data.model.PlaybackMode
import com.purebeat.data.model.Song
import com.purebeat.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MusicPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer

    // Current playlist
    private var currentPlaylist: List<Song> = emptyList()
    private var currentIndex: Int = -1
    private var playbackMode: PlaybackMode = PlaybackMode.SEQUENCE

    override fun onCreate() {
        super.onCreate()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        // Set up player listener
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    // Handle auto transition based on playback mode
                    when (playbackMode) {
                        PlaybackMode.REPEAT_ONE -> {
                            // Repeat the same song
                            currentIndex = currentIndex
                            loadCurrentSong()
                        }
                        PlaybackMode.SHUFFLE -> {
                            // In shuffle mode, player handles the random order
                        }
                        PlaybackMode.SEQUENCE -> {
                            // Move to next song
                            if (currentIndex < currentPlaylist.size - 1) {
                                currentIndex++
                            } else {
                                // End of playlist
                            }
                        }
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    when (playbackMode) {
                        PlaybackMode.REPEAT_ONE -> {
                            player.seekTo(0)
                            player.play()
                        }
                        PlaybackMode.SEQUENCE -> {
                            if (currentIndex < currentPlaylist.size - 1) {
                                playNext()
                            }
                        }
                        PlaybackMode.SHUFFLE -> {
                            // Media3 handles shuffle automatically
                        }
                    }
                }
            }
        })

        // Create pending intent for notification click
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Create media session
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player != null && !player.playWhenReady) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    // Public methods for controlling playback
    fun setPlaylist(songs: List<Song>, startIndex: Int = 0) {
        currentPlaylist = songs
        currentIndex = startIndex

        val mediaItems = songs.map { song ->
            MediaItem.Builder()
                .setMediaId(song.id.toString())
                .setUri(song.uri)
                .build()
        }

        player.setMediaItems(mediaItems, startIndex, 0)
        player.prepare()
    }

    fun play() {
        player.play()
    }

    fun pause() {
        player.pause()
    }

    fun playNext() {
        if (currentPlaylist.isEmpty()) return

        when (playbackMode) {
            PlaybackMode.REPEAT_ONE -> {
                player.seekTo(0)
                player.play()
            }
            PlaybackMode.SHUFFLE -> {
                player.seekToNextMediaItem()
                currentIndex = player.currentMediaItemIndex
            }
            PlaybackMode.SEQUENCE -> {
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    currentIndex = player.currentMediaItemIndex
                }
            }
        }
    }

    fun playPrevious() {
        if (currentPlaylist.isEmpty()) return

        // If more than 3 seconds played, restart current song
        if (player.currentPosition > 3000) {
            player.seekTo(0)
            return
        }

        when (playbackMode) {
            PlaybackMode.REPEAT_ONE -> {
                player.seekTo(0)
            }
            PlaybackMode.SHUFFLE -> {
                player.seekToPreviousMediaItem()
                currentIndex = player.currentMediaItemIndex
            }
            PlaybackMode.SEQUENCE -> {
                if (player.hasPreviousMediaItem()) {
                    player.seekToPreviousMediaItem()
                    currentIndex = player.currentMediaItemIndex
                } else {
                    player.seekTo(0)
                }
            }
        }
    }

    fun seekTo(position: Long) {
        player.seekTo(position)
    }

    fun setPlaybackMode(mode: PlaybackMode) {
        playbackMode = mode
        when (mode) {
            PlaybackMode.REPEAT_ONE -> {
                player.repeatMode = Player.REPEAT_MODE_ONE
                player.shuffleModeEnabled = false
            }
            PlaybackMode.SHUFFLE -> {
                player.repeatMode = Player.REPEAT_MODE_OFF
                player.shuffleModeEnabled = true
            }
            PlaybackMode.SEQUENCE -> {
                player.repeatMode = Player.REPEAT_MODE_OFF
                player.shuffleModeEnabled = false
            }
        }
    }

    fun getCurrentPosition(): Long = player.currentPosition

    fun getDuration(): Long = player.duration

    fun isPlaying(): Boolean = player.isPlaying

    fun getCurrentSongIndex(): Int = player.currentMediaItemIndex

    fun getCurrentPlaylist(): List<Song> = currentPlaylist
}
