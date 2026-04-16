package com.purebeat.fragment;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.purebeat.PureBeatApplication;
import com.purebeat.R;
import com.purebeat.activity.MainActivity;
import com.purebeat.adapter.SongAdapter;
import com.purebeat.database.AppDatabase;
import com.purebeat.database.PlaylistEntity;
import com.purebeat.model.Playlist;
import com.purebeat.model.Song;
import com.purebeat.service.MusicController;
import com.purebeat.util.MusicScanner;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SongsFragment extends Fragment implements SongAdapter.OnSongClickListener {

    private RecyclerView recyclerView;
    private SongAdapter adapter;
    private TextView tvEmpty;
    private List<Song> songs = new ArrayList<>();
    private MusicController musicController;
    private ExecutorService executor;
    private Handler mainHandler;
    private Runnable updatePlayingRunnable;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_songs, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.recycler_view);
        tvEmpty = view.findViewById(R.id.tv_empty);

        adapter = new SongAdapter();
        adapter.setOnSongClickListener(this);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        PureBeatApplication app = (PureBeatApplication) requireActivity().getApplication();
        musicController = app.getMusicController();

        loadSongs();
        startPlayingStateUpdates();
    }

    private void loadSongs() {
        executor.execute(() -> {
            PureBeatApplication app = (PureBeatApplication) requireActivity().getApplication();
            MusicScanner scanner = app.getMusicScanner();
            List<Song> scannedSongs = scanner.scanAllSongs();

            mainHandler.post(() -> {
                songs = scannedSongs;
                adapter.setSongs(songs);

                if (songs.isEmpty()) {
                    tvEmpty.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                } else {
                    tvEmpty.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                }

                updatePlayingState();
            });
        });
    }

    private void startPlayingStateUpdates() {
        updatePlayingRunnable = new Runnable() {
            @Override
            public void run() {
                updatePlayingState();
                mainHandler.postDelayed(this, 500);
            }
        };
        mainHandler.post(updatePlayingRunnable);
    }

    private void updatePlayingState() {
        if (musicController == null) return;

        Song currentSong = musicController.getCurrentSong();
        if (currentSong != null) {
            int index = -1;
            for (int i = 0; i < songs.size(); i++) {
                if (songs.get(i).getId() == currentSong.getId()) {
                    index = i;
                    break;
                }
            }
            adapter.setCurrentPlayingIndex(index);
        } else {
            adapter.setCurrentPlayingIndex(-1);
        }
    }

    @Override
    public void onSongClick(Song song, int position) {
        musicController.setPlaylist(songs, position);
        musicController.play();
    }

    @Override
    public void onSongLongClick(Song song, int position) {
        showAddToPlaylistDialog(song);
    }

    private void showAddToPlaylistDialog(Song song) {
        executor.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(requireContext());
            List<PlaylistEntity> playlistEntities = db.playlistDao().getAllPlaylists();

            if (playlistEntities.isEmpty()) {
                mainHandler.post(() -> {
                    Toast.makeText(getContext(), R.string.no_playlists, Toast.LENGTH_SHORT).show();
                });
                return;
            }

            List<Playlist> playlists = new ArrayList<>();
            for (PlaylistEntity entity : playlistEntities) {
                int songCount = db.playlistSongDao().getSongCountForPlaylist(entity.getId());
                playlists.add(new Playlist(entity.getId(), entity.getName(),
                    entity.getCreatedAt(), songCount));
            }

            String[] names = new String[playlists.size()];
            for (int i = 0; i < playlists.size(); i++) {
                names[i] = playlists.get(i).getName();
            }

            mainHandler.post(() -> {
                new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.add_to_playlist)
                    .setItems(names, (dialog, which) -> {
                        Playlist selected = playlists.get(which);
                        addSongToPlaylist(song, selected.getId());
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            });
        });
    }

    private void addSongToPlaylist(Song song, long playlistId) {
        executor.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(requireContext());
            Integer maxOrder = db.playlistSongDao().getMaxSortOrder(playlistId);
            int newOrder = (maxOrder != null ? maxOrder : -1) + 1;

            com.purebeat.database.PlaylistSongCrossRef ref =
                new com.purebeat.database.PlaylistSongCrossRef(playlistId, song.getId(), newOrder);
            db.playlistSongDao().insertPlaylistSong(ref);

            mainHandler.post(() -> {
                Toast.makeText(getContext(), R.string.song_added, Toast.LENGTH_SHORT).show();
            });
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mainHandler.removeCallbacks(updatePlayingRunnable);
        executor.shutdown();
    }
}
