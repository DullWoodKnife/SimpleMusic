package com.purebeat.service;

import android.app.PendingIntent;
import android.content.Intent;

import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

import com.purebeat.activity.MainActivity;
import com.purebeat.model.PlaybackMode;
import com.purebeat.model.Song;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MusicPlaybackService extends MediaSessionService {

    private MediaSession mediaSession;
    private ExoPlayer player;

    private List<Song> currentPlaylist = new ArrayList<>();
    private List<Song> originalPlaylist = new ArrayList<>();
    private int currentIndex = -1;
    private PlaybackMode playbackMode = PlaybackMode.SEQUENCE;

    @Override
    public void onCreate() {
        super.onCreate();

        player = new ExoPlayer.Builder(this)
            .setAudioAttributes(
                new AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build();

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_ENDED) {
                    handleSongEnded();
                }
            }

            @Override
            public void onPlayerError(Player.ErrorReason errorReason) {
                // 播放出错时自动跳过到下一首
                android.util.Log.e("MusicPlaybackService", "Playback error: " + errorReason);
                if (player.hasNextMediaItem()) {
                    playNext();
                } else if (playbackMode == PlaybackMode.SEQUENCE) {
                    // 循环播放列表
                    player.seekTo(0, 0);
                    currentIndex = 0;
                }
            }

            @Override
            public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    currentIndex = player.getCurrentMediaItemIndex();
                }
            }
        });

        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        mediaSession = new MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build();
    }

    @Nullable
    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (player != null && !player.getPlayWhenReady()) {
            stopSelf();
        }
    }

    @Override
    public void onDestroy() {
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }

    private void handleSongEnded() {
        switch (playbackMode) {
            case REPEAT_ONE:
                player.seekTo(0);
                player.play();
                break;
            case SEQUENCE:
                if (currentIndex < currentPlaylist.size() - 1) {
                    playNextInternal();
                }
                break;
            case SHUFFLE:
                if (player.hasNextMediaItem()) {
                    player.seekToNext();
                    currentIndex = player.getCurrentMediaItemIndex();
                }
                break;
        }
    }

    private void playNextInternal() {
        if (currentIndex < currentPlaylist.size() - 1) {
            currentIndex++;
            player.seekTo(currentIndex, 0);
        }
    }

    // Public methods for external control
    public void setPlaylist(List<Song> songs, int startIndex) {
        originalPlaylist = new ArrayList<>(songs);
        currentPlaylist = new ArrayList<>(songs);
        currentIndex = startIndex;

        if (playbackMode == PlaybackMode.SHUFFLE) {
            Collections.shuffle(currentPlaylist);
            // Move current song to first position
            if (startIndex >= 0 && startIndex < songs.size()) {
                Song currentSong = songs.get(startIndex);
                currentPlaylist.remove(currentSong);
                currentPlaylist.add(0, currentSong);
                currentIndex = 0;
            }
        }

        List<MediaItem> mediaItems = new ArrayList<>();
        for (Song song : currentPlaylist) {
            MediaItem mediaItem = new MediaItem.Builder()
                .setMediaId(String.valueOf(song.getId()))
                .setUri(song.getUri())
                .build();
            mediaItems.add(mediaItem);
        }

        player.setMediaItems(mediaItems, currentIndex, 0);
        player.prepare();
    }

    public void play() {
        player.play();
    }

    public void pause() {
        player.pause();
    }

    public void playNext() {
        if (playbackMode == PlaybackMode.REPEAT_ONE) {
            player.seekTo(0);
            player.play();
            return;
        }

        if (player.hasNextMediaItem()) {
            player.seekToNext();
            currentIndex = player.getCurrentMediaItemIndex();
        } else if (playbackMode == PlaybackMode.SEQUENCE) {
            // Loop back to beginning
            player.seekTo(0, 0);
            currentIndex = 0;
        }
    }

    public void playPrevious() {
        if (player.getCurrentPosition() > 3000) {
            player.seekTo(0);
        } else if (player.hasPreviousMediaItem()) {
            player.seekToPrevious();
            currentIndex = player.getCurrentMediaItemIndex();
        } else {
            //player.seekTo(player.getDuration(), 0);
            // 原错误代码（类似这样）：
            // mediaController.seekTo(mediaController.getDuration(), 0);

            // 修改后代码：
            player.seekTo((int) player.getDuration(), 0); // ✅ 强制转换为 int

            currentIndex = currentPlaylist.size() - 1;
        }
    }

    public void seekTo(long position) {
        player.seekTo(position);
    }

    public void setPlaybackMode(PlaybackMode mode) {
        playbackMode = mode;
        switch (mode) {
            case REPEAT_ONE:
                player.setRepeatMode(Player.REPEAT_MODE_ONE);
                player.setShuffleModeEnabled(false);
                break;
            case SHUFFLE:
                player.setRepeatMode(Player.REPEAT_MODE_OFF);
                player.setShuffleModeEnabled(true);
                // Reshuffle
                if (!originalPlaylist.isEmpty()) {
                    List<Song> shuffled = new ArrayList<>(originalPlaylist);
                    Collections.shuffle(shuffled);
                    currentPlaylist = shuffled;
                    currentIndex = player.getCurrentMediaItemIndex();
                }
                break;
            case SEQUENCE:
                player.setRepeatMode(Player.REPEAT_MODE_OFF);
                player.setShuffleModeEnabled(false);
                currentPlaylist = new ArrayList<>(originalPlaylist);
                break;
        }
    }

    public long getCurrentPosition() {
        return player.getCurrentPosition();
    }

    public long getDuration() {
        return player.getDuration();
    }

    public boolean isPlaying() {
        return player.isPlaying();
    }

    public int getCurrentIndex() {
        return player.getCurrentMediaItemIndex();
    }

    public Song getCurrentSong() {
        if (currentIndex >= 0 && currentIndex < currentPlaylist.size()) {
            return currentPlaylist.get(currentIndex);
        }
        return null;
    }

    public List<Song> getCurrentPlaylist() {
        return currentPlaylist;
    }

    public PlaybackMode getPlaybackMode() {
        return playbackMode;
    }

    public void addToPlaylist(Song song) {
        currentPlaylist.add(song);
        originalPlaylist.add(song);

        MediaItem mediaItem = new MediaItem.Builder()
            .setMediaId(String.valueOf(song.getId()))
            .setUri(song.getUri())
            .build();
        player.addMediaItem(mediaItem);
    }

    public void playSongAt(int index) {
        if (index >= 0 && index < currentPlaylist.size()) {
            player.seekTo(index, 0);
            player.play();
            currentIndex = index;
        }
    }
}
