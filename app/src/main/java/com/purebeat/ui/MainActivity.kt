package com.purebeat.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.purebeat.data.model.Playlist
import com.purebeat.data.model.Song
import com.purebeat.ui.components.MiniPlayer
import com.purebeat.ui.screens.LibraryScreen
import com.purebeat.ui.screens.NowPlayingScreen
import com.purebeat.ui.screens.PlaylistDetailScreen
import com.purebeat.ui.screens.PlaylistsScreen
import com.purebeat.ui.screens.SettingsScreen
import com.purebeat.ui.theme.PureBeatTheme
import com.purebeat.viewmodel.MusicViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PureBeatApp()
        }
    }
}

@Composable
fun PureBeatApp(
    viewModel: MusicViewModel = hiltViewModel()
) {
    val isDarkMode = remember { mutableStateOf(false) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showNowPlaying by remember { mutableStateOf(false) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var songToAddToPlaylist by remember { mutableStateOf<Song?>(null) }
    var showPlaylistPicker by remember { mutableStateOf(false) }

    val playbackState by viewModel.playbackState.collectAsState()
    val playlists by viewModel.playlists.collectAsState()

    // Permission handling
    val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.loadSongs()
        }
    }

    LaunchedEffect(Unit) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        val hasPermission = ContextCompat.checkSelfPermission(
            viewModel.getApplication(),
            permission
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            viewModel.loadSongs()
        } else {
            permissionLauncher.launch(permission)
        }
    }

    PureBeatTheme(darkTheme = isDarkMode.value) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            if (showNowPlaying) {
                NowPlayingScreen(
                    playbackState = playbackState,
                    onBackClick = { showNowPlaying = false },
                    onPlayPauseClick = {
                        if (playbackState.isPlaying) {
                            viewModel.pause()
                        } else {
                            viewModel.play()
                        }
                    },
                    onNextClick = { viewModel.playNext() },
                    onPreviousClick = { viewModel.playPrevious() },
                    onSeekTo = { viewModel.seekTo(it) },
                    onModeChange = { viewModel.cyclePlaybackMode() }
                )
            } else {
                Scaffold(
                    bottomBar = {
                        if (playbackState.currentSong != null) {
                            MiniPlayer(
                                playbackState = playbackState,
                                onPlayPauseClick = {
                                    if (playbackState.isPlaying) {
                                        viewModel.pause()
                                    } else {
                                        viewModel.play()
                                    }
                                },
                                onNextClick = { viewModel.playNext() },
                                onPlayerClick = { showNowPlaying = true }
                            )
                        }
                    }
                ) { paddingValues ->
                    Box(modifier = Modifier.padding(paddingValues)) {
                        when (selectedTabIndex) {
                            0 -> {
                                if (selectedPlaylist != null) {
                                    PlaylistDetailScreen(
                                        playlist = selectedPlaylist!!,
                                        viewModel = viewModel,
                                        onBackClick = { selectedPlaylist = null }
                                    )
                                } else {
                                    LibraryScreen(
                                        viewModel = viewModel,
                                        onAddToPlaylist = { song ->
                                            songToAddToPlaylist = song
                                            showPlaylistPicker = true
                                        }
                                    )
                                }
                            }
                            1 -> {
                                PlaylistsScreen(
                                    viewModel = viewModel,
                                    onPlaylistClick = { playlist ->
                                        selectedPlaylist = playlist
                                    }
                                )
                            }
                            2 -> {
                                SettingsScreen(
                                    viewModel = viewModel,
                                    isDarkMode = isDarkMode.value,
                                    onDarkModeChange = { isDarkMode.value = it }
                                )
                            }
                        }

                        // Bottom navigation
                        if (selectedPlaylist == null) {
                            BottomNavigationBar(
                                selectedIndex = selectedTabIndex,
                                onTabSelected = { selectedTabIndex = it }
                            )
                        }
                    }
                }
            }

            // Playlist picker dialog
            if (showPlaylistPicker && playlists.isNotEmpty()) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = {
                        showPlaylistPicker = false
                        songToAddToPlaylist = null
                    },
                    title = { Text("添加到歌单") },
                    text = {
                        androidx.compose.foundation.layout.Column {
                            playlists.forEach { playlist ->
                                androidx.compose.material3.TextButton(
                                    onClick = {
                                        songToAddToPlaylist?.let { song ->
                                            viewModel.addSongToPlaylist(playlist.id, song.id)
                                        }
                                        showPlaylistPicker = false
                                        songToAddToPlaylist = null
                                    },
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Text(playlist.name)
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                showPlaylistPicker = false
                                songToAddToPlaylist = null
                            }
                        ) {
                            Text("取消")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun BottomNavigationBar(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    val items = listOf(
        NavigationItem("本地音乐", Icons.Default.LibraryMusic),
        NavigationItem("歌单", Icons.Default.PlaylistPlay),
        NavigationItem("设置", Icons.Default.Settings)
    )

    NavigationBar {
        items.forEachIndexed { index, item ->
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title
                    )
                },
                label = { Text(item.title) },
                selected = selectedIndex == index,
                onClick = { onTabSelected(index) }
            )
        }
    }
}

data class NavigationItem(
    val title: String,
    val icon: ImageVector
)
