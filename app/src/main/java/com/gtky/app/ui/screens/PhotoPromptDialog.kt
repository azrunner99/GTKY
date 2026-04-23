package com.gtky.app.ui.screens

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.Settings
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.gtky.app.ui.t
import com.gtky.app.util.CameraPermission
import java.util.concurrent.Executors

enum class PhotoPromptResult {
    CAPTURED,
    SKIPPED,
    OPTED_OUT
}

@Composable
fun PhotoPromptDialog(
    showOptOut: Boolean,
    onResult: (PhotoPromptResult, Bitmap?) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionGranted by remember { mutableStateOf(CameraPermission.isGranted(context)) }
    var permanentlyBlocked by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        if (!granted) {
            val activity = context as? android.app.Activity
            if (activity != null && CameraPermission.isPermanentlyBlocked(activity)) {
                permanentlyBlocked = true
            } else {
                onResult(PhotoPromptResult.SKIPPED, null)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!permissionGranted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val imageCapture = remember { ImageCapture.Builder().build() }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    AlertDialog(
        onDismissRequest = { onResult(PhotoPromptResult.SKIPPED, null) },
        title = {
            Text(
                t("Smile!", "¡Sonríe!"),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    t(
                        "A photo helps people recognize you.",
                        "Una foto ayuda a que te reconozcan."
                    ),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )

                when {
                    capturedBitmap != null -> {
                        Image(
                            bitmap = capturedBitmap!!.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(240.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }
                    permanentlyBlocked -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                t(
                                    "Camera is blocked for GTKY. Enable it in Settings if you'd like a photo.",
                                    "La cámara está bloqueada. Actívala en Configuración si quieres una foto."
                                ),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.error
                            )
                            TextButton(onClick = {
                                val intent = Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null)
                                )
                                context.startActivity(intent)
                            }) {
                                Text(t("Open Settings", "Abrir Configuración"))
                            }
                        }
                    }
                    permissionGranted -> {
                        CameraPreview(
                            lifecycleOwner = lifecycleOwner,
                            imageCapture = imageCapture,
                            modifier = Modifier
                                .size(240.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }
                    else -> {
                        CircularProgressIndicator()
                    }
                }
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (capturedBitmap != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onResult(PhotoPromptResult.CAPTURED, capturedBitmap) },
                            modifier = Modifier.weight(1f)
                        ) { Text(t("Use photo", "Usar foto")) }
                        OutlinedButton(
                            onClick = { capturedBitmap = null },
                            modifier = Modifier.weight(1f)
                        ) { Text(t("Retake", "Volver a tomar")) }
                    }
                } else if (permissionGranted) {
                    Button(
                        onClick = {
                            captureImage(context, imageCapture) { bitmap ->
                                capturedBitmap = bitmap
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(t("Capture", "Capturar")) }
                }
            }
        },
        dismissButton = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(
                    onClick = { onResult(PhotoPromptResult.SKIPPED, null) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(t("Skip", "Omitir")) }
                if (showOptOut) {
                    TextButton(
                        onClick = { onResult(PhotoPromptResult.OPTED_OUT, null) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            t("Never ask again", "No preguntar de nuevo"),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun CameraPreview(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    imageCapture: ImageCapture,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val selector = CameraSelector.DEFAULT_FRONT_CAMERA
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner, selector, preview, imageCapture
                    )
                } catch (_: Exception) { }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        }
    )
}

private fun captureImage(
    context: android.content.Context,
    imageCapture: ImageCapture,
    onResult: (Bitmap) -> Unit
) {
    val executor = Executors.newSingleThreadExecutor()
    imageCapture.takePicture(
        executor,
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bitmap = imageProxyToBitmap(image)
                image.close()
                ContextCompat.getMainExecutor(context).execute {
                    onResult(bitmap)
                }
            }
            override fun onError(e: ImageCaptureException) {
                // Swallow — user can tap Skip.
            }
        }
    )
}

private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
    val buffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

    // Front camera produces mirrored output; un-mirror for the stored selfie.
    val matrix = Matrix().apply {
        postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
        postRotate(image.imageInfo.rotationDegrees.toFloat())
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
