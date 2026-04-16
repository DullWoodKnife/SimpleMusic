package com.purebeat.service;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.purebeat.model.PlaybackMode;
import com.purebeat.model.Song;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

public class MusicController {

    private Context context;
    private MediaController mediaController;
    private ListenableFuture<MediaController> controllerFuture;
    private MusicControllerCallback callback;

    private List<Song> currentPlaylist = new ArrayList<>();
    private PlaybackMode playbackMode = PlaybackMode.SEQUENCE;
    private Song currentSong;
    private boolean isPlaying = false;
    private long currentPosition = 0;
    private long duration = 0;

    public interface MusicControllerCallback {
        void onPlaybackStateChanged(Song currentSong, boolean isPlaying, long position, long duration);
        void onPlaylistChanged(List<Song> playlist);
        void onPlaybackModeChanged(PlaybackMode mode);
    }

    public MusicController(Context context) {
        this.context = context.getApplicationContext();
    }

    public void setCallback(MusicControllerCallback callback) {
        this.callback = callback;
    }

    public void connect() {
        SessionToken sessionToken = new SessionToken(
            context,
            new ComponentName(context, MusicPlaybackService.class)
        );

        controllerFuture = new MediaController.Builder(context, sessionToken).buildAsync();
        controllerFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    mediaController = controllerFuture.get();
                    setupPlayerListener();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, MoreExecutors.directExecutor());
    }

    public void disconnect() {
        if (mediaController != null) {
            mediaController.removeListener(playerListener);
            MediaController.releaseFuture(controllerFuture);
            mediaController = null;
        }
    }

    private void setupPlayerListener() {
        if (mediaController != null) {
            mediaController.addListener(playerListener);
        }
    }

    private Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onIsPlayingChanged(boolean playing) {
            isPlaying = playing;
            updateState();
        }

        @Override
        public void onPlaybackStateChanged(int playbackState) {
            updateState();
        }

        @Override
        public void onMediaItemTransition(MediaItem mediaItem, int reason) {
            updateCurrentSong();
            updateState();
        }

        @Override
        public void onRepeatModeChanged(int repeatMode) {
            switch (repeatMode) {
                case Player.REPEAT_MODE_ONE:
                    playbackMode = PlaybackMode.REPEAT_ONE;
                    break;
                case Player.REPEAT_MODE_OFF:
                    playbackMode = PlaybackMode.SEQUENCE;
                    break;
            }
            if (callback != null) {
                callback.onPlaybackModeChanged(playbackMode);
            }
        }

        @Override
        public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
            if (shuffleModeEnabled) {
                playbackMode = PlaybackMode.SHUFFLE;
            }
            if (callback != null) {
                callback.onPlaybackModeChanged(playbackMode);
            }
        }
    };

    private void updateState() {
        if (mediaController != null) {
            currentPosition = mediaController.getCurrentPosition();
            duration = mediaController.getDuration();
            if (duration < 0) duration = 0;
        }

        if (callback != null) {
            callback.onPlaybackStateChanged(currentSong, isPlaying, currentPosition, duration);
        }
    }

    private void updateCurrentSong() {
        if (mediaController != null && currentPlaylist != null) {
            int index = mediaController.getCurrentMediaItemIndex();
            if (index >= 0 && index < currentPlaylist.size()) {
                currentSong = currentPlaylist.get(index);
            }
        }
    }

    // Public control methods
    public void setPlaylist(List<Song> songs, int startIndex) {
        this.currentPlaylist = new ArrayList<>(songs);

        if (mediaController != null) {
            List<MediaItem> mediaItems = new ArrayList<>();
            for (Song song : songs) {
                MediaItem mediaItem = new MediaItem.Builder()
                    .setMediaId(String.valueOf(song.getId()))
                    .setUri(song.getUri())
                    .build();
                mediaItems.add(mediaItem);
            }

            mediaController.setMediaItems(mediaItems, startIndex, 0);
            mediaController.prepare();

            if (startIndex >= 0 && startIndex < songs.size()) {
                currentSong = songs.get(startIndex);
            }
        }

        if (callback != null) {
            callback.onPlaylistChanged(currentPlaylist);
        }
    }

    public void play() {
        if (mediaController != null) {
            mediaController.play();
        }
    }

    public void pause() {
        if (mediaController != null) {
            mediaController.pause();
        }
    }

    public void playNext() {
        if (mediaController != null) {
            if (mediaController.hasNextMediaItem()) {
                mediaController.seekToNext();
            } else if (playbackMode == PlaybackMode.SEQUENCE) {
                mediaController.seekTo(0, 0);
            }
        }
    }

    public void playPrevious() {
        if (mediaController != null) {
            if (mediaController.getCurrentPosition() > 3000) {
                mediaController.seekTo(0);
            } else if (mediaController.hasPreviousMediaItem()) {
                mediaController.seekToPrevious();
            } else {
                mediaController.seekTo(mediaController.getDuration(), 0);
            }
        }
    }

    public void seekTo(long position) {
        if (mediaController != null) {
            mediaController.seekTo(position);
        }
    }

    public void setPlaybackMode(PlaybackMode mode) {
        playbackMode = mode;
        if (mediaController != null) {
            switch (mode) {
                case REPEAT_ONE:
                    mediaController.setRepeatMode(Player.REPEAT_MODE_ONE);
                    mediaController.setShuffleModeEnabled(false);
                    break;
                case SHUFFLE:
                    mediaController.setRepeatMode(Player.REPEAT_MODE_OFF);
                    mediaController.setShuffleModeEnabled(true);
                    break;
                case SEQUENCE:
                    mediaController.setRepeatMode(Player.REPEAT_MODE_OFF);
                    mediaController.setShuffleModeEnabled(false);
                    break;
            }
        }
        if (callback != null) {
            callback.onPlaybackModeChanged(mode);
        }
    }

    public void cyclePlaybackMode() {
        PlaybackMode newMode = playbackMode.next();
        setPlaybackMode(newMode);
    }

    // Getters
    public boolean isPlaying() {
        return isPlaying;
    }

    public long getCurrentPosition() {
        if (mediaController != null) {
            return mediaController.getCurrentPosition();
        }
        return 0;
    }

    public long getDuration() {
        if (mediaController != null) {
            long dur = mediaController.getDuration();
            return dur > 0 ? dur : duration;
        }
        return duration;
    }

    public Song getCurrentSong() {
        return currentSong;
    }

    public List<Song> getCurrentPlaylist() {
        return currentPlaylist;
    }

    public PlaybackMode getPlaybackMode() {
        return playbackMode;
    }

    public int getCurrentIndex() {
        if (mediaController != null) {
            return mediaController.getCurrentMediaItemIndex();
        }
        return -1;
    }

    public void playSongAt(int index) {
        if (mediaController != null && index >= 0 && index < currentPlaylist.size()) {
            mediaController.seekTo(index, 0);
            mediaController.play();
        }
    }

    public void startService() {
        Intent intent = new Intent(context, MusicPlaybackService.class);
        context.startService(intent);
    }
}
