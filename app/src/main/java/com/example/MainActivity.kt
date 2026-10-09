package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.ProcessingState
import com.example.ui.ServerConnectionState
import com.example.ui.TitanAnimeViewModel
import com.example.ui.components.CachedClipsSection
import com.example.ui.components.ResultPlayerCard
import com.example.ui.components.ServerConfigCard
import com.example.ui.components.StylizeProgressSection
import com.example.ui.components.VideoPickerCard
import com.example.ui.theme.AnimeCrimson
import com.example.ui.theme.DarkCharcoal
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceContainer
import com.example.ui.theme.DimText
import com.example.ui.theme.LightText
import com.example.ui.theme.MediumText
import com.example.ui.theme.OutlineDark
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusGrey
import com.example.ui.theme.TitanAnimeTheme
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {

    private val viewModel: TitanAnimeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            TitanAnimeTheme {
                TitanAnimeScreen(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TitanAnimeScreen(
    viewModel: TitanAnimeViewModel
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cachedClips by viewModel.cachedClips.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showHelpDialog by remember { mutableStateOf(false) }

    // Toast/Snackbar notifications
    LaunchedEffect(Unit) {
        viewModel.toastEvents.collectLatest { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val isServerConnected = uiState.connectionState is ServerConnectionState.Connected
    val isVideoSelected = uiState.selectedVideo != null

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = DarkCharcoal,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // Brand eye icon badge
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(AnimeCrimson),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "T",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Text(
                            text = "Titan Anime",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = LightText,
                                letterSpacing = 0.5.sp
                            )
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        // Status dot indicator: Green = Connected, Grey = Not Connected
                        TopBarStatusIndicator(isOnline = isServerConnected)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showHelpDialog = true },
                        modifier = Modifier.testTag("help_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = "Help & Colab API Info",
                            tint = MediumText
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = DarkSurface,
                    titleContentColor = LightText
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Server URL Card
            item {
                ServerConfigCard(
                    serverUrl = uiState.serverUrl,
                    connectionState = uiState.connectionState,
                    onUrlChange = { viewModel.onServerUrlChange(it) },
                    onConnectClick = { viewModel.checkConnection() }
                )
            }

            // 2. Video Picker Card
            item {
                VideoPickerCard(
                    selectedVideo = uiState.selectedVideo,
                    isResolving = uiState.isResolvingVideo,
                    onVideoSelected = { viewModel.onVideoSelected(it) },
                    onClearVideo = { viewModel.clearSelectedVideo() }
                )
            }

            // 3. Convert CTA & Progress Section
            item {
                StylizeProgressSection(
                    isServerOnline = isServerConnected,
                    isVideoSelected = isVideoSelected,
                    processingState = uiState.processingState,
                    onStylizeClick = { viewModel.stylizeVideo() },
                    onCancelClick = { viewModel.cancelStylization() }
                )
            }

            // 4. Result Card (Media3 ExoPlayer + Save to Gallery button)
            if (uiState.activeResultClip != null) {
                item {
                    ResultPlayerCard(
                        clip = uiState.activeResultClip!!,
                        isSaving = uiState.isSavingToGallery,
                        isSaved = uiState.isGallerySaved,
                        onSaveToGalleryClick = { viewModel.saveActiveClipToGallery() }
                    )
                }
            }

            // 5. Offline Library (Room DB Cache)
            item {
                CachedClipsSection(
                    clips = cachedClips,
                    activeClipId = uiState.activeResultClip?.id,
                    onClipSelect = { viewModel.setActiveResultClip(it) },
                    onClipDelete = { viewModel.deleteCachedClip(it) }
                )
            }

            // Bottom padding for comfortable scrolling
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showHelpDialog) {
        ColabHelpDialog(onDismiss = { showHelpDialog = false })
    }
}

/**
 * Top App Bar Status Dot:
 * Green = Connected, Grey = Not Connected
 */
@Composable
fun TopBarStatusIndicator(
    isOnline: Boolean,
    modifier: Modifier = Modifier
) {
    val dotColor = if (isOnline) StatusGreen else StatusGrey
    val label = if (isOnline) "Connected" else "Not Connected"

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurfaceContainer)
            .border(1.dp, OutlineDark, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = if (isOnline) StatusGreen else DimText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

@Composable
fun ColabHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = AnimeCrimson,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Google Colab Backend Setup",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = LightText
                    )
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "Titan Anime connects to your Colab GPU notebook via Cloudflare / Ngrok tunnel.",
                    color = MediumText,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Required Colab REST Endpoints:",
                    color = LightText,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurfaceContainer)
                        .padding(10.dp)
                ) {
                    Text(
                        text = "1. GET  /         -> 200 OK\n2. POST /stylize  -> form-data 'video'\n   Returns: stylized anime .mp4 binary",
                        fontFamily = FontFamily.Monospace,
                        color = AnimeCrimson,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Timeouts are pre-configured up to 300s to support heavy frame-by-frame PyTorch rendering.",
                    color = DimText,
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got It", color = AnimeCrimson)
            }
        }
    )
}
