package com.purebeat.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.purebeat.data.model.PlaybackMode
import com.purebeat.data.model.PlaybackState
import com.purebeat.data.model.Playlist
import com.purebeat.data.model.Song
import com.purebeat.data.repository.MusicRepository
import com.purebeat.service.MusicController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MusicViewModel @Inject constructor(
    application: Application,
    private val repository: MusicRepository,
    private val controller: MusicController
) : AndroidViewModel(application) {

    // All songs from device
    private val _allSongs = MutableStateFlow<List<Song>>(emptyList())
    val allSongs: StateFlow<List<Song>> = _allSongs.asStateFlow()

    // Current playlist being displayed
    private val _currentPlaylist = MutableStateFlow<List<Song>>(emptyList())
    val currentPlaylist: StateFlow<List<Song>> = _currentPlaylist.asStateFlow()

    // Playlists
    val playlists: StateFlow<List<Playlist>> = repository.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Playback state
    val playbackState: StateFlow<PlaybackState> = controller.playbackState

    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Error state
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Selected tab
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    // Position update job
    private var positionUpdateJob: Job? = null

    init {
        controller.initialize()
    }

    override fun onCleared() {
        super.onCleared()
        positionUpdateJob?.cancel()
        controller.release()
    }

    fun loadSongs() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val songs = repository.getAllSongs()
                _allSongs.value = songs
                _currentPlaylist.value = songs
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun playSong(song: Song, playlist: List<Song>? = null) {
        val playlistToPlay = playlist ?: _currentPlaylist.value
        val index = playlistToPlay.indexOfFirst { it.id == song.id }
        if (index >= 0) {
            _currentPlaylist.value = playlistToPlay
            controller.setPlaylist(playlistToPlay, index)
            controller.play()
            startPositionUpdates()
        }
    }

    fun playPlaylist(playlist: List<Song>, startIndex: Int = 0) {
        _currentPlaylist.value = playlist
        controller.setPlaylist(playlist, startIndex)
        controller.play()
        startPositionUpdates()
    }

    fun play() {
        controller.play()
        startPositionUpdates()
    }

    fun pause() {
        controller.pause()
        positionUpdateJob?.cancel()
    }

    fun playNext() {
        controller.playNext()
    }

    fun playPrevious() {
        controller.playPrevious()
    }

    fun seekTo(position: Long) {
        controller.seekTo(position)
    }

    fun setPlaybackMode(mode: PlaybackMode) {
        controller.setPlaybackMode(mode)
    }

    fun cyclePlaybackMode() {
        val currentMode = playbackState.value.playbackMode
        val newMode = when (currentMode) {
            PlaybackMode.SEQUENCE -> PlaybackMode.SHUFFLE
            PlaybackMode.SHUFFLE -> PlaybackMode.REPEAT_ONE
            PlaybackMode.REPEAT_ONE -> PlaybackMode.SEQUENCE
        }
        controller.setPlaybackMode(newMode)
    }

    // Playlist operations
    fun createPlaylist(name: String) {
        viewModelScope.launch {
            repository.createPlaylist(name)
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
        }
    }

    fun addSongToPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            repository.addSongToPlaylist(playlistId, songId)
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            repository.removeSongFromPlaylist(playlistId, songId)
        }
    }

    fun getPlaylistSongs(playlistId: Long, onResult: (List<Song>) -> Unit) {
        viewModelScope.launch {
            val songs = repository.getSongsForPlaylist(playlistId)
            onResult(songs)
        }
    }

    fun setSelectedTab(tab: Int) {
        _selectedTab.value = tab
    }

    // Filter songs
    fun filterSongsByArtist(artist: String) {
        _currentPlaylist.value = _allSongs.value.filter { it.artist == artist }
    }

    fun filterSongsByAlbum(album: String) {
        _currentPlaylist.value = _allSongs.value.filter { it.album == album }
    }

    fun clearFilter() {
        _currentPlaylist.value = _allSongs.value
    }

    // Get unique artists and albums
    fun getAllArtists(): List<String> {
        return _allSongs.value.map { it.artist }.distinct().sorted()
    }

    fun getAllAlbums(): List<String> {
        return _allSongs.value.map { it.album }.distinct().sorted()
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                // Position is automatically updated via controller.playbackState
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
