package com.example.myjetpackcompose

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast

import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.myjetpackcompose.ui.theme.MyJetpackComposeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val vaultRoot = File(filesDir, "vault").apply { mkdirs() }
        val thumbRoot = File(filesDir, ".thumbnails").apply { mkdirs() }
        seedTestData(filesDir)
        setContent {
            MyJetpackComposeTheme {
                MainScreen(rootDir = vaultRoot, thumbRoot = thumbRoot)
            }
        }
    }
}

private fun seedTestData(rootDir: File) {
    val folder1 = File(rootDir, "Vacation Photos").apply { mkdirs() }
    val folder2 = File(rootDir, "Screenshots").apply { mkdirs() }
    File(rootDir, "notes.txt").apply { if (!exists()) writeText("test file") }
    File(folder1, "beach.txt").apply { if (!exists()) writeText("pretend image") }
}

@Composable
fun Greeting(name: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .background(Color.Blue)
            .fillMaxSize()

    ) {
        Text(
            text = "Hello $name!",
            color = Color.White


        )

    }
}


@Composable
fun MainScreen(rootDir: File,  thumbRoot: File){
    val context = LocalContext.current
    var currentDir by remember { mutableStateOf(rootDir) }
    var pendingDelete by remember { mutableStateOf<File?>(null) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableStateOf(0) }
    var pendingImportUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    var videoToPlay by remember { mutableStateOf<File?>(null) }
    var videoListToPlay by remember { mutableStateOf<List<File>>(emptyList()) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) pendingImportUris = uris  // just store, LaunchedEffect handles the copy
    }

    LaunchedEffect(pendingImportUris) {
        if (pendingImportUris.isEmpty()) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            pendingImportUris.forEach { uri ->
                copyUriToSandbox(context, uri, currentDir)
            }
        }
        refreshTrigger++
        pendingImportUris = emptyList()
    }


    Scaffold(
        topBar = {
            TopBar(
                currentDir = currentDir,
                rootDir = rootDir,
                onNavigateUp = { currentDir = currentDir.parentFile ?: rootDir },
                onAddFolder = {showCreateFolderDialog = true},
                onImport = {
                    importLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    )
                }

            )
        }
    ) {
        padding ->
        FileList(
            dir = currentDir,
            thumbRoot = thumbRoot,       // NEW
            refreshTrigger = refreshTrigger,
            modifier = Modifier.padding(padding),
            onClick = { folder -> currentDir = folder },
            onVideoClick = { file ->
                val videosInFolder = file.parentFile
                    ?.listFiles()
                    ?.filter { it.extension.lowercase() in setOf("mp4", "mkv", "avi", "mov", "webm") }
                    ?.sortedBy { it.name }
                    ?: listOf(file)
                videoToPlay = file
                videoListToPlay = videosInFolder   // new state var
            },
            onDelete = { target -> pendingDelete = target }
        )

    }
    videoToPlay?.let { file ->
        VideoPlayerScreen(
            videoFiles = videoListToPlay,
            startIndex = videoListToPlay.indexOf(file),
            onBack = { videoToPlay = null }
        )
    }

    if (showCreateFolderDialog) {
        CreateFolderDialog(
            currentDir = currentDir,
            onConfirm = { name ->
                File(currentDir, name).mkdirs()
                refreshTrigger++
                showCreateFolderDialog = false
            },
            onDismiss = { showCreateFolderDialog = false }
        )
    }
    pendingDelete?.let { target ->
        DeleteConfirmDialog(
            target = target,
            onConfirm = {
                File(thumbRoot, "${target.name}.jpg").delete()
                val success = if (target.isDirectory) target.deleteRecursively() else target.delete()
                pendingDelete = null
                refreshTrigger++
                if (!success) {
                    Toast.makeText(
                        context,
                        "Couldn't fully delete \"${target.name}\" — some items may remain",
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

fun copyUriToSandbox(context: Context, uri: Uri, targetDir: File): File? {
    return try {

        val fileName = context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            } else null
        } ?: "import_${System.currentTimeMillis()}"

        // avoid overwrite
        var destFile = File(targetDir, fileName)
        var counter = 1

        while (destFile.exists()) {
            val name = fileName.substringBeforeLast(".")
            val ext = fileName.substringAfterLast(".", "")
            destFile = if (fileName.contains(".")) {
                File(targetDir, "$name($counter).$ext")
            } else {
                File(targetDir, "$name($counter)")
            }
            counter++
        }

        val tempFile = File(targetDir, "${destFile.name}.tmp")

        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Cannot open input stream")

        input.use { inputStream ->
            tempFile.outputStream().use { output ->
                inputStream.copyTo(output)
            }
        }

        if (!tempFile.exists() || tempFile.length() == 0L) {
            tempFile.delete()
            throw IOException("Copy failed or empty file")
        }

        tempFile.renameTo(destFile)

        Log.d("Import", "Success: ${destFile.name} (${destFile.length()} bytes)")

        destFile

    } catch (e: Exception) {
        Log.e("Import", "Error: ${e.message}")
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(
    currentDir : File,
    rootDir : File,
    onNavigateUp : ()-> Unit,
    onAddFolder: () -> Unit,
    onImport : () -> Unit
){
   val isAtRoot = currentDir.absolutePath == rootDir.absolutePath

    TopAppBar(
        title = {
            Text(
                if(isAtRoot) "Vault App"
                else currentDir.name
            )
        },
        navigationIcon = {
            if (!isAtRoot){
                IconButton(onClick = onNavigateUp) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        },
        actions = {
            IconButton(onClick = onImport) {
                Icon(Icons.Default.Add, contentDescription = "Import file")
            }
            IconButton(onClick = onAddFolder) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = "New folder")
            }
        }
    )
}

@Composable
fun CreateFolderDialog(
    currentDir: File,           // NEW — needed to check for existing names
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var folderName by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New folder") },
        text = {
            Column {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = {
                        folderName = it
                        errorMessage = null   // clear error as soon as they start retyping
                    },
                    label = { Text("Folder name") },
                    singleLine = true,
                    isError = errorMessage != null
                )
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmedName = folderName.trim()
                    val targetFolder = File(currentDir, trimmedName)

                    when {
                        trimmedName.isBlank() ->
                            errorMessage = "Folder name can't be empty"
                        trimmedName == "." || trimmedName == ".." ->
                            errorMessage = "That's not a valid folder name"
                        trimmedName.contains("/") ->
                            errorMessage = "Folder name can't contain /"
                        targetFolder.exists() ->
                            errorMessage = "\"$trimmedName\" already exists here"
                        else ->
                            onConfirm(trimmedName)
                    }
                },
                enabled = folderName.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun DeleteConfirmDialog(
    target: File,
    onConfirm: () -> Unit,
    onDismiss: ()-> Unit

){
    AlertDialog(
        onDismissRequest = onDismiss,
        title =  { Text("Delete \" ${target.name}\" ?") },
        text = {
            Text(
                if (target.isDirectory)
                    "This folder and everything inside it will be permanently deleted."
                else
                    "This file will be permanently deleted."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }

    )

}
fun getOrGenerateVideoThumbnail(videoFile: File, thumbRoot: File): File? {
    val thumbFile = File(thumbRoot, "${videoFile.name}.jpg")

    if (thumbFile.exists()) return thumbFile

    return try {
        val bitmap = MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(videoFile.absolutePath)
//            retriever.embeddedPicture?.let {
//                BitmapFactory.decodeByteArray(it, 0, it.size)
//            } ?: retriever.getFrameAtTime(0)
            retriever.getFrameAtTime(
                1_000_000,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )
        } ?: return null

        thumbFile.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }
        bitmap.recycle()
        thumbFile
    } catch (e: Exception) {
        Log.d("Thumbnail Error!","Thumbnail Generate error: ${e.message}")
        null
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItem(
    entry: FileEntry,
    thumbRoot: File,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var thumbnailFile by remember(entry.file) { mutableStateOf(entry.thumbnailFile) }

    // Generate thumbnail if needed — once per video, off main thread
    LaunchedEffect(entry.file) {
        if (entry.isVideo && thumbnailFile == null) {
            withContext(Dispatchers.IO) {
                thumbnailFile = getOrGenerateVideoThumbnail(entry.file, thumbRoot)
            }
        }
    }

    ListItem(
        headlineContent = { Text(entry.name) },
        leadingContent = {
            when {
                entry.isDirectory -> Icon(
                    Icons.Default.Folder,
                    contentDescription = null
                )
                entry.isImage -> AsyncImage(
                    model = entry.file,
                    contentDescription = entry.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp)
                )
                entry.isVideo -> if (thumbnailFile != null) {
                    AsyncImage(
                        model = thumbnailFile,
                        contentDescription = entry.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(48.dp)
                    )
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                }
                else -> Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null)
            }
        },
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onDelete
        )
    )
}




@Composable
fun FileList(
    dir: File,
    thumbRoot: File,
    refreshTrigger: Int,
    modifier: Modifier = Modifier,
    onClick: (File) -> Unit,
    onVideoClick: (File) -> Unit,
    onDelete: (File) -> Unit
){
    val entries = remember(dir, refreshTrigger) {
        dir.listFiles()
            ?.filterNot { it.name.startsWith(".") }  // hide .thumbnails + any hidden files
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
            ?.map { it.toFileEntry(thumbRoot) }       // convert to FileEntry here
            ?: emptyList()
    }
    LazyColumn(modifier = modifier) {
        items(entries, key = { it.file.absolutePath }) { entry ->
            FileListItem(
                entry = entry,
                thumbRoot = thumbRoot,
                onClick = {
                    when {
                        entry.isDirectory -> onClick(entry.file)
                        entry.isVideo -> onVideoClick(entry.file)   // ADD THIS
                    }
                },
                onDelete = { onDelete(entry.file) }
            )
        }
    }

//    LazyColumn(modifier = modifier) {
//        items(entries, key = {it.absolutePath}){
//            entry ->
//            ListItem(
//                headlineContent = {Text(entry.name)},
//                leadingContent = {
//                    Icon(
//                        imageVector =
//                            if (entry.isDirectory) Icons.Default.Folder
//                            else Icons.Default.Image,
//                        contentDescription = null
//                    )
//                },
//                modifier = Modifier.combinedClickable(       // CHANGED — was plain .clickable
//                    onClick = { if (entry.isDirectory) onClick(entry) },
//                    onLongClick = { onDelete(entry) }
//                )
//            )
//        }
//    }
}




@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyJetpackComposeTheme {
//        Greeting("Android")

    }
}