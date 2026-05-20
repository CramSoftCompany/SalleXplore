package dev.cramsoft.sallexplore

import android.content.Context
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Plane
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.arcore.createAnchorOrNull
import io.github.sceneview.ar.arcore.getUpdatedPlanes
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import java.util.concurrent.atomic.AtomicBoolean
import android.app.Activity
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.ui.platform.LocalView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "bienvenida") {
                    composable("bienvenida") {
                        PantallaBienvenida(navController)
                    }
                    composable("qr") {
                        PantallaQR(navController)
                    }
                    // AR recibe el ID del personaje como argumento de ruta
                    composable("ar/{personajeId}") { backStackEntry ->
                        val personajeId = backStackEntry.arguments
                            ?.getString("personajeId")
                            ?: "juan_ok2"
                        PantallaAR(personajeId = personajeId)
                    }
                }
            }
        }
    }
}

// ======================================================
// PANTALLA BIENVENIDA
// ======================================================
@Composable
fun PantallaBienvenida(navController: NavController) {
    val colorLaSalle = Color(0xFF0033A0)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "SalleXplore",
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            color = colorLaSalle
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Escanea el código QR del personaje para comenzar.",
            fontSize = 18.sp,
            color = Color.DarkGray,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(40.dp))
        Button(
            // ← ahora va a la pantalla QR, no directo a AR
            onClick = { navController.navigate("qr") },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colorLaSalle)
        ) {
            Text("Escanear QR", color = Color.White, fontSize = 18.sp)
        }
    }
}

// ======================================================
// PANTALLA QR SCANNER
// ======================================================
@OptIn(ExperimentalGetImage::class)
@Composable
fun PantallaQR(navController: NavController) {
    val lifecycleOwner = LocalLifecycleOwner.current
    // AtomicBoolean para evitar navegaciones dobles si el scanner dispara varias veces
    val yaEscaneado = remember { AtomicBoolean(false) }

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Preview de cámara ──────────────────────────────
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val executor = ContextCompat.getMainExecutor(ctx)

                ProcessCameraProvider.getInstance(ctx).addListener({
                    val cameraProvider = ProcessCameraProvider.getInstance(ctx).get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val scanner = BarcodeScanning.getClient(
                        BarcodeScannerOptions.Builder()
                            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                            .build()
                    )

                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    analysis.setAnalyzer(executor) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage != null && !yaEscaneado.get()) {
                            val imagen = InputImage.fromMediaImage(
                                mediaImage,
                                imageProxy.imageInfo.rotationDegrees
                            )
                            scanner.process(imagen)
                                .addOnSuccessListener { codigos ->
                                    codigos.firstOrNull()?.rawValue?.let { valor ->
                                        // compareAndSet garantiza que solo naveguemos una vez
                                        if (yaEscaneado.compareAndSet(false, true)) {
                                            navController.navigate("ar/$valor") {
                                                // Al volver con Back desde AR, regresa a Bienvenida
                                                popUpTo("qr") { inclusive = true }
                                            }
                                        }
                                    }
                                }
                                .addOnCompleteListener { imageProxy.close() }
                        } else {
                            imageProxy.close()
                        }
                    }

                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                }, executor)

                previewView
            }
        )

        // ── Overlay: marco + instrucción ───────────────────
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Marco visual tipo viewfinder
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .border(
                        width = 3.dp,
                        color = Color(0xFF0033A0),
                        shape = RoundedCornerShape(16.dp)
                    )
            )
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = "Apunta al código QR del personaje",
                color = Color.White,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(
                        color = Color.Black.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

// ======================================================
// PANTALLA AR  — ahora recibe el ID del personaje
// ======================================================
// ======================================================
// PANTALLA AR — con zoom, triple toque y captura
// ======================================================
// ======================================================
// PANTALLA AR — agrega LocalView para la captura
// ======================================================
@Composable
fun PantallaAR(personajeId: String) {
    val context      = LocalContext.current
    val rootView     = LocalView.current          // ← nuevo: referencia al árbol de vistas
    val audioManager = remember { AudioManager(context) }

    val engine       = rememberEngine()
    val modelLoader  = rememberModelLoader(engine)

    var anchor       by remember { mutableStateOf<Anchor?>(null) }
    var mostrarChat  by remember { mutableStateOf(false) }
    val modelInstance = rememberModelInstance(modelLoader, "models/$personajeId.glb")
    val tapTimes      = remember { mutableListOf<Long>() }

    DisposableEffect(Unit) {
        audioManager.playAmbiente()
        onDispose {
            audioManager.stopAmbiente()
            audioManager.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        ARSceneView(
            modifier      = Modifier.fillMaxSize(),
            engine        = engine,
            modelLoader   = modelLoader,
            planeRenderer = true,
            sessionConfiguration = { _, config ->
                config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                config.planeFindingMode    = Config.PlaneFindingMode.HORIZONTAL
            },
            onSessionUpdated = { _, frame ->
                if (anchor == null) {
                    anchor = frame
                        .getUpdatedPlanes()
                        .firstOrNull { it.type == Plane.Type.HORIZONTAL_UPWARD_FACING }
                        ?.let { plane -> plane.createAnchorOrNull(plane.centerPose) }
                        ?.also { audioManager.playScanSound() }
                }
            },
            onTouchEvent = { event, hitResult ->
                if (hitResult != null && event.action == MotionEvent.ACTION_UP) {
                    val ahora = System.currentTimeMillis()
                    tapTimes.removeAll { ahora - it > 600L }
                    tapTimes.add(ahora)
                    if (tapTimes.size >= 3) {
                        tapTimes.clear()
                        mostrarChat = true
                    }
                }
                false
            }
        ) {
            anchor?.let { arAnchor ->
                modelInstance?.let { model ->
                    AnchorNode(anchor = arAnchor) {
                        ModelNode(
                            modelInstance = model,
                            scaleToUnits  = 1.0f,
                            autoAnimate   = true,
                            isEditable    = true
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            // ← pasa rootView en lugar de solo context
            onClick = { capturarPantalla(context, rootView, audioManager) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = Color(0xFF0033A0)
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = "Capturar foto",
                tint = Color.White
            )
        }

        if (mostrarChat) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(24.dp)
            ) {
                Text(
                    text = "💬 Chat activado para: $personajeId",
                    color = Color.White,
                    fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

// ======================================================
// CAPTURA — apunta a la SurfaceView de ARCore directamente
// ======================================================
private fun capturarPantalla(context: Context, rootView: View, audioManager: AudioManager) {
    val handler = Handler(Looper.getMainLooper())

    // Busca la SurfaceView donde ARCore renderiza
    val surfaceView = encontrarSurfaceView(rootView)

    if (surfaceView != null) {
        // ✅ Captura la surface de ARCore (cámara + modelo 3D)
        val bitmap = Bitmap.createBitmap(
            surfaceView.width, surfaceView.height, Bitmap.Config.ARGB_8888
        )
        PixelCopy.request(surfaceView, bitmap, { resultado ->
            if (resultado == PixelCopy.SUCCESS) {
                guardarEnGaleria(context, bitmap)
                audioManager.playFotoSound()
            }
        }, handler)

    } else {
        // Fallback: captura toda la ventana (sin AR, pero no falla)
        val activity = context as? Activity ?: return
        val decor    = activity.window.decorView
        val bitmap   = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        PixelCopy.request(activity.window, bitmap, { resultado ->
            if (resultado == PixelCopy.SUCCESS) {
                guardarEnGaleria(context, bitmap)
                audioManager.playFotoSound()
            }
        }, handler)
    }
}

// Recorre el árbol de vistas hasta encontrar la SurfaceView de SceneView
private fun encontrarSurfaceView(view: View): SurfaceView? {
    if (view is SurfaceView) return view
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            encontrarSurfaceView(view.getChildAt(i))?.let { return it }
        }
    }
    return null
}

private fun guardarEnGaleria(context: Context, bitmap: Bitmap) {
    val nombre = "SalleXplore_${System.currentTimeMillis()}.jpg"
    val valores = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME,  nombre)
        put(MediaStore.Images.Media.MIME_TYPE,     "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SalleXplore")
        put(MediaStore.Images.Media.IS_PENDING,    1)
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, valores) ?: return
    resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
    valores.clear()
    valores.put(MediaStore.Images.Media.IS_PENDING, 0)
    resolver.update(uri, valores, null, null)
}

// ======================================================
// AUDIO MANAGER  (sin cambios)
// ======================================================
class AudioManager(context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private val soundPool = SoundPool.Builder().setMaxStreams(3).build()
    private var soundScanId: Int = 0
    private var soundFotoId:  Int = 0

    init {
        try {
            soundScanId = soundPool.load(context, R.raw.scan,    1)
            soundFotoId = soundPool.load(context, R.raw.foto,    1)
            mediaPlayer = MediaPlayer.create(context, R.raw.ambiente).apply {
                isLooping = true
                setVolume(0.4f, 0.4f)
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun playAmbiente()  { mediaPlayer?.start() }
    fun stopAmbiente()  { mediaPlayer?.takeIf { it.isPlaying }?.pause() }
    fun playScanSound() { if (soundScanId != 0) soundPool.play(soundScanId, 1f, 1f, 1, 0, 1f) }
    fun playFotoSound() { if (soundFotoId  != 0) soundPool.play(soundFotoId,  1f, 1f, 1, 0, 1f) }
    fun release()       { mediaPlayer?.release(); soundPool.release() }
}