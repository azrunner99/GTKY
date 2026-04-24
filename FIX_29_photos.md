# GTKY — Fix 29: User photos

Large fix. This one touches capture, storage, permissions, a new schema column, and every screen that displays a user. Read the whole doc before writing code.

## The problem

GTKY identifies users by name only. At an event, matching a name to a face requires actually knowing the person — which is what GTKY is supposed to help with in the first place. Photos close that gap: seeing a face on the picker, on the Connections rows, and in the quiz makes "who is this?" a glance instead of a deduction.

## What we're building

1. A **"Smile!" popup** with a live front-camera preview, a Capture button, and a Skip button. Appears once per sign-in that lacks a photo, up to 3 such sign-ins. On the third prompt, a "Never ask again" option also appears.
2. **Camera permission handling** — one-time OS prompt on first use (admin handles during setup), with graceful denial fallback if the OS has permanently blocked future prompts.
3. **Storage** — 512×512 JPEG quality 80 in internal app storage (`filesDir/avatars/<userId>.jpg`), tied to `user.id`, deleted when the user is deleted.
4. **Display** — photos (or initial-based avatar placeholders) shown **everywhere a user's name appears**: picker, Active Users, Connections (mutual + one-way), quiz "About X" card, profile screen, home-screen "Signed in as" pill.

**Important prerequisites:**
- Depends on Fix 27 (name normalization) for deriving placeholder initials.
- Depends on Fix 28 (welcome rework) being landed.
- Does **not** gate any feature on having a photo — users without photos see the placeholder avatar and the app works normally.

Ground rules unchanged: bilingual strings via `t()`, no new dependencies except CameraX (justification below), keep the Compose + StateFlow + ViewModel architecture, one commit per section as noted.

## Dependencies

We need **CameraX** for the preview + capture. It's Google's official, recommended camera API for modern Android apps and dramatically simpler than the raw Camera2 API. Adding three Compose-compatible CameraX artifacts:

```kotlin
// app/build.gradle.kts — dependencies block
implementation("androidx.camera:camera-core:1.3.1")
implementation("androidx.camera:camera-camera2:1.3.1")
implementation("androidx.camera:camera-lifecycle:1.3.1")
implementation("androidx.camera:camera-view:1.3.1")
```

(Pin to 1.3.1 unless the existing `libs.versions.toml` already has a CameraX section — check first. If the project has a version catalog, add these as proper catalog entries.)

Add the `CAMERA` permission to `app/src/main/AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera.any" android:required="false" />
```

`android.hardware.camera.any` with `required="false"` means "we use any camera if available but don't require one" — keeps the app installable on emulators or Play Store-listed devices without cameras.

## Implementation

### 29.1 — Schema: add `photoPath` to User

One Room migration.

In `User.kt`:

```kotlin
@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val photoPath: String? = null,           // NEW: absolute path to JPEG in filesDir/avatars/
    val photoPromptCount: Int = 0,            // NEW: how many times we've shown the Smile popup
    val photoPromptOptOut: Boolean = false    // NEW: user tapped "Never ask again"
)
```

Bump `GTKYDatabase.version` by 1 and add a migration:

```kotlin
// In GTKYDatabase.kt
val MIGRATION_N_TO_N1 = object : Migration(N, N + 1) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE users ADD COLUMN photoPath TEXT")
        db.execSQL("ALTER TABLE users ADD COLUMN photoPromptCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE users ADD COLUMN photoPromptOptOut INTEGER NOT NULL DEFAULT 0")
    }
}
```

Wire into the database builder with `.addMigrations(MIGRATION_N_TO_N1)`. Substitute the real version numbers — check the current `version` in `GTKYDatabase.kt` first and bump from there.

**Critical:** do not use `fallbackToDestructiveMigration()` or your real event data disappears. If the build currently has that, this fix removes it.

Add DAO methods in `UserDao.kt`:

```kotlin
@Query("UPDATE users SET photoPath = :path WHERE id = :id")
suspend fun updatePhotoPath(id: Long, path: String?)

@Query("UPDATE users SET photoPromptCount = photoPromptCount + 1 WHERE id = :id")
suspend fun incrementPhotoPromptCount(id: Long)

@Query("UPDATE users SET photoPromptOptOut = 1 WHERE id = :id")
suspend fun setPhotoPromptOptOut(id: Long)
```

Repository wrappers in `GTKYRepository.kt`:

```kotlin
suspend fun setUserPhotoPath(userId: Long, path: String?) =
    db.userDao().updatePhotoPath(userId, path)

suspend fun incrementPhotoPromptCount(userId: Long) =
    db.userDao().incrementPhotoPromptCount(userId)

suspend fun markPhotoPromptOptOut(userId: Long) =
    db.userDao().setPhotoPromptOptOut(userId)
```

Also extend the existing `deleteUser` in the repository to delete the photo file if it exists:

```kotlin
suspend fun deleteUser(user: User) {
    db.surveyAnswerDao().deleteAllAnswersForUser(user.id)
    db.quizResultDao().deleteResultsForUser(user.id)
    user.photoPath?.let { path ->
        try { java.io.File(path).delete() } catch (_: Exception) { /* swallow */ }
    }
    db.userDao().deleteUser(user)
}
```

**Commit for this section:** `fix/29a-photo-schema`. Verify migration by running the app on a device that has the v3.1 schema; existing users should survive the upgrade with `photoPath = null`.

### 29.2 — Photo storage utility

New file `app/src/main/java/com/gtky/app/util/PhotoStorage.kt`:

```kotlin
package com.gtky.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import java.io.File
import java.io.FileOutputStream

object PhotoStorage {

    private const val SIZE = 512
    private const val QUALITY = 80
    private const val DIR = "avatars"

    /**
     * Save a bitmap to internal storage at avatars/<userId>.jpg, downscaled to 512x512
     * (center-cropped square) and JPEG quality 80. Returns the absolute file path.
     */
    fun saveAvatar(context: Context, userId: Long, source: Bitmap): String {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val file = File(dir, "$userId.jpg")
        val scaled = cropAndScale(source, SIZE)
        FileOutputStream(file).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
        }
        if (scaled !== source) scaled.recycle()
        return file.absolutePath
    }

    fun deleteAvatar(context: Context, userId: Long) {
        val file = File(File(context.filesDir, DIR), "$userId.jpg")
        if (file.exists()) file.delete()
    }

    /**
     * Decode an avatar from disk at reasonable in-memory size. Returns null if the file
     * is missing or unreadable (e.g., deleted out from under us).
     */
    fun loadAvatar(path: String): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null
        return try {
            BitmapFactory.decodeFile(path)
        } catch (_: Exception) { null }
    }

    private fun cropAndScale(src: Bitmap, target: Int): Bitmap {
        val w = src.width
        val h = src.height
        val size = minOf(w, h)
        val x = (w - size) / 2
        val y = (h - size) / 2
        val square = Bitmap.createBitmap(src, x, y, size, size)
        if (size == target) return square
        val scale = target.toFloat() / size
        val matrix = Matrix().apply { postScale(scale, scale) }
        val scaled = Bitmap.createBitmap(square, 0, 0, size, size, matrix, true)
        if (scaled !== square) square.recycle()
        return scaled
    }
}
```

No test file — Bitmap operations need an Android environment. If you want to test this, instrumentation tests are the right place, but out of scope for this fix.

**Commit:** `fix/29b-photo-storage`.

### 29.3 — Camera permission helper

New file `app/src/main/java/com/gtky/app/util/CameraPermission.kt`:

```kotlin
package com.gtky.app.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object CameraPermission {
    fun isGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Returns true if the user has permanently blocked future system prompts
     * (checked "Don't ask again" or is on Android 11+ where two denials auto-block).
     * Only meaningful when the permission is currently NOT granted.
     */
    fun isPermanentlyBlocked(activity: Activity): Boolean {
        val granted = isGranted(activity)
        if (granted) return false
        return !ActivityCompat.shouldShowRequestPermissionRationale(
            activity, Manifest.permission.CAMERA
        )
    }
}
```

The "permanently blocked" detection is Android's standard two-step pattern: after a denial, `shouldShowRequestPermissionRationale` returns true (we can still re-prompt); after "Don't ask again" or auto-blocking, it returns false. We only trust the false case *when* permission is ungranted — the same function returns false when the user hasn't been asked yet, which would look identical.

**Commit:** rolls into `fix/29c-photo-capture` below.

### 29.4 — The Smile popup

This is the main new UI. New file `app/src/main/java/com/gtky/app/ui/screens/PhotoPromptDialog.kt`:

```kotlin
package com.gtky.app.ui.screens

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import android.util.Size
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
    showOptOut: Boolean,    // true on the 3rd prompt
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
            // Check if we just got permanently blocked.
            val activity = context as? android.app.Activity
            if (activity != null && CameraPermission.isPermanentlyBlocked(activity)) {
                permanentlyBlocked = true
            } else {
                // Soft denial — treat as skip.
                onResult(PhotoPromptResult.SKIPPED, null)
            }
        }
    }

    // Kick off permission request on first composition if not granted.
    LaunchedEffect(Unit) {
        if (!permissionGranted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Hold a reference to the ImageCapture use case so we can trigger capture on button tap.
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
                    t("A photo helps people recognize you.",
                      "Una foto ayuda a que te reconozcan."),
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
                                t("Camera is blocked for GTKY. Enable it in Settings if you'd like a photo.",
                                  "La cámara está bloqueada. Actívala en Configuración si quieres una foto."),
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
                } catch (_: Exception) { /* Swallow; UI will just show blank preview. */ }
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
    val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

    // Front camera produces mirrored output; un-mirror for the stored selfie so it looks "right."
    val matrix = android.graphics.Matrix().apply {
        postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
        // Also apply rotation if the image is sideways.
        postRotate(image.imageInfo.rotationDegrees.toFloat())
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

// Missing import — add at top of file.
import androidx.compose.foundation.Image
```

A few notes on the CameraX pieces:

- The front camera selector is `CameraSelector.DEFAULT_FRONT_CAMERA`.
- The preview binding happens inside `AndroidView`'s `factory` block. Lifecycle is handled by passing `lifecycleOwner` from the composable — CameraX will unbind automatically when the lifecycle is destroyed. In practice, when the dialog dismisses, the `AndroidView` leaves composition and the lifecycle tear-down cascades.
- Captured image is mirrored because front-camera previews are mirrored by convention but captures are not. We un-mirror at capture time so the saved photo matches what the user saw in the preview.

**Commit:** `fix/29c-photo-capture`.

### 29.5 — Avatar composable (display layer)

New file `app/src/main/java/com/gtky/app/ui/components/Avatar.kt`:

```kotlin
package com.gtky.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gtky.app.data.entity.User
import com.gtky.app.util.PhotoStorage
import kotlin.math.abs

@Composable
fun Avatar(
    user: User,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(user.photoPath) {
        user.photoPath?.let { PhotoStorage.loadAvatar(it) }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
        )
    } else {
        InitialsAvatar(name = user.name, size = size, modifier = modifier)
    }
}

@Composable
private fun InitialsAvatar(name: String, size: Dp, modifier: Modifier = Modifier) {
    val initials = remember(name) {
        val parts = name.trim().split(" ")
        val first = parts.getOrNull(0)?.firstOrNull()?.uppercase() ?: ""
        val last = parts.getOrNull(1)?.firstOrNull()?.uppercase() ?: ""
        "$first$last".ifEmpty { "?" }
    }
    val bg = remember(name) { colorForName(name) }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = Color.White,
            fontSize = (size.value * 0.4f).sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun colorForName(name: String): Color {
    // Deterministic palette pick based on name hash — same name always gets same color.
    val palette = listOf(
        Color(0xFF5B7CBA), Color(0xFFB85BA0), Color(0xFF5BAB6E),
        Color(0xFFC27A3E), Color(0xFF7D5BBA), Color(0xFF3EB3B5),
        Color(0xFFC24E4E), Color(0xFF6B8E23)
    )
    return palette[abs(name.hashCode()) % palette.size]
}
```

**Where `Avatar` gets used** — every place a user's name currently appears:

- **`PickUserScreen`** — replace the current `Icons.Default.Person` with `Avatar(user = user, size = 40.dp)`. Keep the `(1)` / `(2)` disambiguation text next to the name.
- **`ActiveUsersScreen.UserRow`** — replace the current `Icons.Default.Person` with `Avatar(user = userWithCount.user, size = 40.dp)`.
- **`ConnectionsScreen.MutualConnectionRow`** — insert two avatars before the score column, slightly overlapping (left avatar at `-8.dp` offset from the right). For MINE scope rows, show just the other user's avatar (single).
- **`ConnectionsScreen.OneWayConnectionRow`** — single avatar of the target user before the label.
- **`ProfileScreen`** — large avatar (128.dp) centered above the name in the top app bar or below. Decide which feels cleaner; if in doubt, put it below the TopAppBar, centered, with 24dp vertical padding.
- **`QuizScreen.QuizQuestionContent`** — the existing "About Alex" primary-container card gets an avatar to the left of the text. `Avatar(user = subject, size = 32.dp)` — small enough to fit the card.
- **`HomeScreen` "Signed in as" pill** — small avatar to the left of the name. `Avatar(user = user, size = 24.dp)`.

For the `QuizQuestionContent` change, `q.subjectUser` is the `User` object — pass it straight in. Same for the Connections rows and the home pill.

**Commit:** `fix/29d-avatar-component-and-wiring`.

### 29.6 — Prompt logic: when to show, when to stop

Add to `HomeUiState.UserSelected`:

```kotlin
data class UserSelected(
    val user: User,
    val answerCount: Int,
    val readyCount: Int = 0,
    val renameError: String? = null,
    val showPhotoPrompt: Boolean = false    // NEW
) : HomeUiState()
```

In `HomeViewModel.transitionToUserSelected`, after the user is loaded, evaluate whether to fire the prompt:

```kotlin
private fun transitionToUserSelected(user: User) {
    // ... existing cancelable job setup ...

    viewModelScope.launch {
        val shouldPrompt = user.photoPath == null &&
                           !user.photoPromptOptOut &&
                           user.photoPromptCount < 3
        if (shouldPrompt) {
            // Increment the count now so that even if the user force-quits the app
            // mid-prompt, we've recorded this session as "prompted."
            repo.incrementPhotoPromptCount(user.id)
            // Small delay so the home screen renders before the dialog appears.
            kotlinx.coroutines.delay(1000)
            val state = _uiState.value as? HomeUiState.UserSelected ?: return@launch
            _uiState.value = state.copy(showPhotoPrompt = true)
        }
    }

    // ... rest of existing body ...
}
```

Handler functions:

```kotlin
fun dismissPhotoPrompt() {
    val state = _uiState.value as? HomeUiState.UserSelected ?: return
    _uiState.value = state.copy(showPhotoPrompt = false)
}

fun savePhoto(context: android.content.Context, bitmap: android.graphics.Bitmap) {
    val state = _uiState.value as? HomeUiState.UserSelected ?: return
    viewModelScope.launch {
        val path = com.gtky.app.util.PhotoStorage.saveAvatar(context, state.user.id, bitmap)
        repo.setUserPhotoPath(state.user.id, path)
        val updatedUser = repo.getUserById(state.user.id) ?: return@launch
        _uiState.value = state.copy(user = updatedUser, showPhotoPrompt = false)
    }
}

fun optOutOfPhotoPrompts() {
    val state = _uiState.value as? HomeUiState.UserSelected ?: return
    viewModelScope.launch {
        repo.markPhotoPromptOptOut(state.user.id)
        val updatedUser = repo.getUserById(state.user.id) ?: return@launch
        _uiState.value = state.copy(user = updatedUser, showPhotoPrompt = false)
    }
}
```

Passing `Context` into a ViewModel function is a minor anti-pattern but acceptable here because the bitmap → file operation is a one-shot and the context is scoped to the call. Alternative: pass an already-resolved file path and have the Composable do the save. Pick whichever is cleaner in context when you implement it — the spec shows the ViewModel-save path for clarity.

In `HomeScreen.UserHomeScreen`, render the dialog when `state.showPhotoPrompt` is true:

```kotlin
if (state.showPhotoPrompt) {
    val context = LocalContext.current
    PhotoPromptDialog(
        showOptOut = state.user.photoPromptCount >= 3,  // 3rd prompt → show opt-out
        onResult = { result, bitmap ->
            when (result) {
                PhotoPromptResult.CAPTURED -> bitmap?.let { viewModel.savePhoto(context, it) }
                    ?: viewModel.dismissPhotoPrompt()
                PhotoPromptResult.SKIPPED -> viewModel.dismissPhotoPrompt()
                PhotoPromptResult.OPTED_OUT -> viewModel.optOutOfPhotoPrompts()
            }
        }
    )
}
```

Note the check is `photoPromptCount >= 3` — by the time the user sees this third prompt, `incrementPhotoPromptCount` has already run so the count is 3. If count is 1 or 2, no opt-out shown. If count is 3 (third prompt), opt-out shown. After this third prompt, if skipped, `photoPromptCount` will be 3 and the `shouldPrompt` check above fails (`photoPromptCount < 3` is false), so no further prompts ever.

**Commit:** `fix/29e-prompt-logic`.

## Testing

### Unit tests

No new unit tests for this fix — camera/bitmap logic requires an Android test environment. Existing tests must still pass.

### Manual test matrix

**Fresh install, first user:**
1. Install, open app, tap "I'm new here," create user "Alex S."
2. Group picker appears (if any groups) — dismiss/skip.
3. Home screen renders. ~1 second later, Smile popup appears.
4. System camera permission prompt fires. Grant it.
5. Front camera preview appears in the circular window. Tap Capture.
6. Captured bitmap displayed. Tap "Use photo."
7. Popup closes. Home pill now shows Alex's avatar next to his name.

**Sign out and back in:**
1. Sign out. Pick Alex from the picker. Verify his avatar shows up in the picker row.
2. Home screen renders. *No Smile popup* (his photo is set).

**Three-prompt cap:**
1. Clear app data. Create "Bob," grant camera. Skip the popup.
2. Sign out, pick Bob. Popup fires again (this is prompt 2 of 3). Skip.
3. Sign out, pick Bob. Popup fires (prompt 3 of 3). Verify "Never ask again" button is now visible. Skip (regular skip).
4. Sign out, pick Bob. *No popup* — count is at 3, guard passes.

**Opt out:**
1. Clear app data. Create "Carol," skip twice, and on the third prompt tap "Never ask again."
2. Sign out, pick Carol. *No popup.* Verify her `photoPromptOptOut` is true in the DB.

**Permission denial, first time:**
1. Clear app data, reinstall. Create "Dan."
2. On Smile popup, camera permission prompt fires. Tap Deny.
3. Popup dismisses silently (treated as Skip). `photoPromptCount = 1`.

**Permission permanently blocked:**
1. Clear app data, reinstall. Create "Eve."
2. Deny camera twice (or check "Don't ask again"). App detects permanent block.
3. Smile popup shows "Camera is blocked" message with an "Open Settings" button.
4. Tap Skip. Popup dismisses.

**Delete user removes photo:**
1. With a user that has a photo, go to admin, tap the user, tap delete.
2. Confirm deletion. Verify the file `filesDir/avatars/<userId>.jpg` no longer exists.

**Avatars rendered everywhere:**
Walk the app with at least 3 users, some with photos, some without. Verify:
- Picker shows photos or initial-based circles.
- Active Users shows photos or initials.
- Connections mutual rows show two avatars; one-way rows show one avatar.
- Profile screen shows a large avatar.
- Quiz "About X" card shows a small avatar next to the name.
- Home screen "Signed in as" pill shows a tiny avatar.

## Rollout sequence

Because this fix is large, ship it in the five commits listed above (29a through 29e). Each should build and run independently — the schema change is backwards-compatible (new columns are nullable/defaulted), so even after 29a lands on its own, the app works with no photos and no prompts. 29d wires up avatars everywhere before 29e introduces the prompt, so you can test "does the avatar component render correctly for users without photos" (just shows initials circles) before the capture flow exists.

Do **not** squash these into one commit — five smaller diffs are easier to review and easier to bisect if anything breaks at the event.

## Changelog entries

Add to `CHANGELOG.md`, one bullet per commit:

- **Fix 29a — Photo schema** — Added `photoPath`, `photoPromptCount`, `photoPromptOptOut` columns to `users` table via Room migration. DAO + repository wrappers. `deleteUser` now also deletes the avatar file from internal storage.
- **Fix 29b — Photo storage utility** — `util/PhotoStorage.kt` saves/deletes/loads 512×512 JPEG quality 80 avatars in `filesDir/avatars/<userId>.jpg`.
- **Fix 29c — Photo capture dialog** — CameraX front-camera preview + capture in a new `PhotoPromptDialog` composable. Handles runtime permission flow including permanent-block detection and "Open Settings" path. Added CameraX 1.3.1 dependencies and `CAMERA` permission to manifest.
- **Fix 29d — Avatar component** — New `Avatar` composable displays user photo when set, falls back to deterministic colored initials circle. Wired into `PickUserScreen`, `ActiveUsersScreen`, `ConnectionsScreen` (mutual and one-way rows), `ProfileScreen`, `QuizScreen` ("About X" card), and `HomeScreen` "Signed in as" pill.
- **Fix 29e — Prompt logic** — `HomeViewModel` fires the Smile popup once per qualifying sign-in up to 3 times. Third prompt surfaces a "Never ask again" opt-out. Prompt count is incremented at show-time so force-quit mid-prompt still counts. `showPhotoPrompt` state in `HomeUiState.UserSelected` gates the dialog in `HomeScreen`.
