package com.purebeat.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.purebeat.data.model.Playlist
import com.purebeat.data.model.Song
import com.purebeat.ui.components.SongListItem
import com.purebeat.viewmodel.MusicViewModel

@Composable
fun LibraryScreen(
    viewModel: MusicViewModel,
    onAddToPlaylist: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    val allSongs by viewModel.allSongs.collectAsState()
    val currentPlaylist by viewModel.currentPlaylist.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()

    var selectedArtist by remember { mutableStateOf<String?>(null) }
    var selectedAlbum by remember { mutableStateOf<String?>(null) }

    val artists = remember(allSongs) { allSongs.map { it.artist }.distinct().sorted() }
    val albums = remember(allSongs) { allSongs.map { it.album }.distinct().sorted() }

    val tabTitles = listOf("歌曲", "艺术家", "专辑")

    Column(modifier = modifier.fillMaxSize()) {
        // Tabs
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.fillMaxWidth(),
            edgePadding = 16.dp
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = {
                        viewModel.setSelectedTab(index)
                        when (index) {
                            0 -> {
                                selectedArtist = null
                                selectedAlbum = null
                                viewModel.clearFilter()
                            }
                        }
                    },
                    text = { Text(title) }
                )
            }
        }

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            selectedTab == 0 -> {
                // Songs tab
                if (allSongs.isEmpty()) {
                    EmptyState(message = "暂无音乐文件")
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(allSongs, key = { it.id }) { song ->
                            SongListItem(
                                song = song,
                                isPlaying = playbackState.currentSong?.id == song.id,
                                onClick = { viewModel.playSong(song) },
                                onAddToPlaylist = { onAddToPlaylist(song) }
                            )
                        }
                    }
                }
            }
            selectedTab == 1 -> {
                // Artists tab
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(artists) { artist ->
                        val artistSongs = allSongs.filter { it.artist == artist }
                        SongListItem(
                            song = artistSongs.first(),
                            isPlaying = false,
                            onClick = {
                                selectedArtist = artist
                                viewModel.filterSongsByArtist(artist)
                                viewModel.setSelectedTab(0)
                            }
                        )
                    }
                }
            }
            selectedTab == 2 -> {
                // Albums tab
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(albums) { album ->
                        val albumSongs = allSongs.filter { it.album == album }
                        SongListItem(
                            song = albumSongs.first(),
                            isPlaying = false,
                            onClick = {
                                selectedAlbum = album
                                viewModel.filterSongsByAlbum(album)
                                viewModel.setSelectedTab(0)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
