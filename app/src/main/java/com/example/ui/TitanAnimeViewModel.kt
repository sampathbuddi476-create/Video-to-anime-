package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.StylizedClip
import com.example.data.network.ColabApiService
import com.example.data.network.ServerHealth
import com.example.data.network.StylizeProgress
import com.example.data.repository.ClipRepository
import com.example.util.SelectedVideoInfo
import com.example.util.VideoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

sealed class ServerConnectionState {
    object Disconnected : ServerConnectionState()
    object Checking : ServerConnectionState()
    data class Connected(val latencyMs: Long, val message: String) : ServerConnectionState()
    data class Error(val message: String) : ServerConnectionState()
}

sealed class ProcessingState {
    object Idle : ProcessingState()
    data class InProgress(
        val stage: StylizeProgress,
        val stageDescription: String,
        val progressPercent: Float?
    ) : ProcessingState()
    data class Success(val clip: StylizedClip) : ProcessingState()
    data class Failed(val error: String) : ProcessingState()
}

data class TitanAnimeUiState(
    val serverUrl: String = "",
    val connectionState: ServerConnectionState = ServerConnectionState.Disconnected,
    val selectedVideo: SelectedVideoInfo? = null,
    val isResolvingVideo: Boolean = false,
    val processingState: ProcessingState = ProcessingState.Idle,
    val activeResultClip: StylizedClip? = null,
    val isSavingToGallery: Boolean = false,
    val isGallerySaved: Boolean = false
)

class TitanAnimeViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("titan_anime_prefs", Context.MODE_PRIVATE)
    private val PREF_KEY_SERVER_URL = "saved_server_url"

    private val apiService = ColabApiService()
    private val repository = ClipRepository(AppDatabase.getDatabase(application).clipDao())

    val cachedClips: StateFlow<List<StylizedClip>> = repository.allClips
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _uiState = MutableStateFlow(
        TitanAnimeUiState(
            serverUrl = prefs.getString(PREF_KEY_SERVER_URL, "") ?: ""
        )
    )
    val uiState: StateFlow<TitanAnimeUiState> = _uiState.asStateFlow()

    private val _toastEvents = MutableSharedFlow<String>()
    val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

    init {
        // If a saved URL exists, test connection automatically
        val savedUrl = _uiState.value.serverUrl
        if (savedUrl.isNotBlank()) {
            checkConnection()
        }
    }

    fun onServerUrlChange(newUrl: String) {
        _uiState.update {
            it.copy(
                serverUrl = newUrl,
                connectionState = if (it.connectionState is ServerConnectionState.Connected && it.serverUrl != newUrl)
                    ServerConnectionState.Disconnected else it.connectionState
            )
        }
    }

    fun checkConnection() {
        val rawUrl = _uiState.value.serverUrl
        val cleanUrl = apiService.sanitizeUrl(rawUrl)
        if (cleanUrl.isBlank()) {
            _uiState.update {
                it.copy(connectionState = ServerConnectionState.Error("Please enter a Colab server URL"))
            }
            return
        }

        // Save sanitized URL to SharedPreferences
        prefs.edit().putString(PREF_KEY_SERVER_URL, cleanUrl).apply()
        _uiState.update {
            it.copy(
                serverUrl = cleanUrl,
                connectionState = ServerConnectionState.Checking
            )
        }

        viewModelScope.launch {
            val health: ServerHealth = apiService.checkServerHealth(cleanUrl)
            if (health.isOnline) {
                _uiState.update {
                    it.copy(
                        connectionState = ServerConnectionState.Connected(
                            latencyMs = health.latencyMs,
                            message = health.message
                        )
                    )
                }
                _toastEvents.emit("Connected to Colab GPU (${health.latencyMs}ms)")
            } else {
                _uiState.update {
                    it.copy(
                        connectionState = ServerConnectionState.Error(health.message)
                    )
                }
                _toastEvents.emit("Connection failed: ${health.message}")
            }
        }
    }

    fun onVideoSelected(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isResolvingVideo = true) }
            val videoInfo = VideoUtils.resolveVideoInfo(getApplication(), uri)
            _uiState.update {
                it.copy(
                    selectedVideo = videoInfo,
                    isResolvingVideo = false
                )
            }
            _toastEvents.emit("Loaded: ${videoInfo.fileName} (${VideoUtils.formatDuration(videoInfo.durationMs)})")
        }
    }

    fun clearSelectedVideo() {
        _uiState.value.selectedVideo?.localFile?.delete()
        _uiState.update { it.copy(selectedVideo = null) }
    }

    fun stylizeVideo() {
        val currentState = _uiState.value
        val isOnline = currentState.connectionState is ServerConnectionState.Connected
        val video = currentState.selectedVideo
        val serverUrl = currentState.serverUrl

        if (!isOnline) {
            viewModelScope.launch { _toastEvents.emit("Server is not connected") }
            return
        }
        if (video == null || video.localFile == null || !video.localFile.exists()) {
            viewModelScope.launch { _toastEvents.emit("Please select a video clip first") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    processingState = ProcessingState.InProgress(
                        stage = StylizeProgress.Uploading(0, 0, video.localFile.length()),
                        stageDescription = "Uploading clip... 0%",
                        progressPercent = 0f
                    ),
                    isGallerySaved = false
                )
            }

            val result = apiService.stylizeVideo(
                context = getApplication(),
                baseUrl = serverUrl,
                videoFile = video.localFile,
                onProgress = { progress ->
                    val (desc, pct) = when (progress) {
                        is StylizeProgress.Uploading -> {
                            val percent = progress.progressPercent
                            Pair("Uploading clip... $percent%", percent / 100f * 0.35f)
                        }
                        is StylizeProgress.RenderingOnGpu -> {
                            Pair("Colab GPU rendering anime frames...", 0.65f)
                        }
                        is StylizeProgress.Downloading -> {
                            val percent = if (progress.progressPercent >= 0) "${progress.progressPercent}%" else "streaming"
                            val factor = if (progress.progressPercent >= 0) progress.progressPercent / 100f * 0.35f else 0.2f
                            Pair("Downloading result... $percent", 0.65f + factor)
                        }
                        is StylizeProgress.Completed -> {
                            Pair("Stylization complete!", 1.0f)
                        }
                    }

                    _uiState.update {
                        it.copy(
                            processingState = ProcessingState.InProgress(
                                stage = progress,
                                stageDescription = desc,
                                progressPercent = pct
                            )
                        )
                    }
                }
            )

            result.fold(
                onSuccess = { generatedFile ->
                    // Insert into Room database
                    val clip = StylizedClip(
                        sourceFileName = video.fileName,
                        localFilePath = generatedFile.absolutePath,
                        createdAt = System.currentTimeMillis(),
                        durationMs = video.durationMs,
                        fileSizeBytes = generatedFile.length(),
                        serverUrl = serverUrl
                    )
                    val insertedId = repository.insertClip(clip)
                    val savedClip = clip.copy(id = insertedId)

                    _uiState.update {
                        it.copy(
                            processingState = ProcessingState.Success(savedClip),
                            activeResultClip = savedClip,
                            isGallerySaved = false
                        )
                    }
                    _toastEvents.emit("Anime stylization finished successfully!")
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            processingState = ProcessingState.Failed(error.localizedMessage ?: "Unknown error")
                        )
                    }
                    _toastEvents.emit("Error: ${error.localizedMessage}")
                }
            )
        }
    }

    fun cancelStylization() {
        apiService.cancelActiveRequest()
        _uiState.update {
            it.copy(processingState = ProcessingState.Idle)
        }
        viewModelScope.launch {
            _toastEvents.emit("Stylization canceled")
        }
    }

    fun saveActiveClipToGallery() {
        val clip = _uiState.value.activeResultClip ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingToGallery = true) }
            val file = File(clip.localFilePath)
            val result = VideoUtils.saveVideoToGallery(getApplication(), file)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isSavingToGallery = false,
                            isGallerySaved = true
                        )
                    }
                    _toastEvents.emit("Saved to device Gallery in Movies/TitanAnime!")
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isSavingToGallery = false) }
                    _toastEvents.emit("Failed to save: ${error.localizedMessage}")
                }
            )
        }
    }

    fun setActiveResultClip(clip: StylizedClip) {
        _uiState.update {
            it.copy(
                activeResultClip = clip,
                isGallerySaved = false
            )
        }
    }

    fun deleteCachedClip(clip: StylizedClip) {
        viewModelScope.launch {
            repository.deleteClip(clip)
            if (_uiState.value.activeResultClip?.id == clip.id) {
                _uiState.update { it.copy(activeResultClip = null) }
            }
            _toastEvents.emit("Removed from cache")
        }
    }
}
