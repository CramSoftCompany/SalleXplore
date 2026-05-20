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
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
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
                            ?: "San_Juan_Bautista"
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
    val azul = Color(0xFF0033A0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {

        Spacer(modifier = Modifier.height(60.dp))

        // ── Bloque superior: logo + título ─────────────────
        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            // Espacio reservado para el logo
            // Reemplaza el Box por:
            Image(
                painter = painterResource(id = R.drawable.logo_salle),
                contentDescription = "Logo",
                modifier = Modifier
                    .size(230.dp),
                contentScale = ContentScale.Fit
            )
            /*Box(
                modifier = Modifier
                    .size(110.dp)
                    .background(azul.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                    .border(1.5.dp, azul.copy(alpha = 0.25f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text  = "LOGO",
                    color = azul.copy(alpha = 0.4f),
                    fontWeight = FontWeight.Bold,
                    fontSize   = 18.sp
                )
            }

             */

            Spacer(modifier = Modifier.height(20.dp))
 /*
            Text(
                text       = "SalleXplore",
                fontSize   = 40.sp,
                fontWeight = FontWeight.Bold,
                color      = azul
            )

  */

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text      = "Escanea y conversa con un\npersonaje de La Salle",
                fontSize  = 16.sp,
                color     = Color.DarkGray,
                textAlign = TextAlign.Center
            )
        }

        // ── Botón QR ───────────────────────────────────────
        Button(
            onClick = { navController.navigate("qr") },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape  = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = azul)
        ) {
            Icon(
                imageVector         = Icons.Default.QrCodeScanner,
                contentDescription  = null,
                tint                = Color.White,
                modifier            = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text("Escanear QR", fontSize = 17.sp, color = Color.White)
        }

        // ── Footer ─────────────────────────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 24.dp)
        ) {
            Divider(color = Color.LightGray, thickness = 0.8.dp)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text     = "Desarrollado por Cramsoft Company",
                fontSize = 11.sp,
                color    = Color.Gray
            )
            Text(
                text     = "© Derechos reservados 2026",
                fontSize = 11.sp,
                color    = Color.Gray
            )
        }
    }
}

// ======================================================
// PANTALLA QR SCANNER
// ======================================================
@OptIn(ExperimentalGetImage::class)
@Composable
fun PantallaQR(navController: NavController) {
    val context       = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val yaEscaneado   = remember { AtomicBoolean(false) }

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Preview cámara ─────────────────────────────────
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory  = { ctx ->
                val previewView = PreviewView(ctx)
                val executor    = ContextCompat.getMainExecutor(ctx)

                ProcessCameraProvider.getInstance(ctx).addListener({
                    val provider = ProcessCameraProvider.getInstance(ctx).get()

                    val preview = Preview.Builder().build()
                        .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                    val scanner = BarcodeScanning.getClient(
                        BarcodeScannerOptions.Builder()
                            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                            .build()
                    )

                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    analysis.setAnalyzer(executor) { imageProxy ->
                        val media = imageProxy.image
                        if (media != null && !yaEscaneado.get()) {
                            val img = InputImage.fromMediaImage(
                                media, imageProxy.imageInfo.rotationDegrees
                            )
                            scanner.process(img)
                                .addOnSuccessListener { codigos ->
                                    codigos.firstOrNull()?.rawValue?.let { valor ->
                                        if (yaEscaneado.compareAndSet(false, true)) {

                                            // ✅ Valida que el ID exista
                                            if (PERSONAJES_INFO.containsKey(valor)) {
                                                navController.navigate("ar/$valor") {
                                                    popUpTo("qr") { inclusive = true }
                                                }
                                            } else {
                                                Toast.makeText(
                                                    ctx,
                                                    "Este QR no pertenece a ningún personaje. " +
                                                            "Escanea el QR de un personaje lasallista.",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                                // Permite volver a escanear
                                                yaEscaneado.set(false)
                                            }
                                        }
                                    }
                                }
                                .addOnCompleteListener { imageProxy.close() }
                        } else {
                            imageProxy.close()
                        }
                    }

                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                }, executor)

                previewView
            }
        )

        // ── Overlay: marco + texto ─────────────────────────
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .border(3.dp, Color(0xFF0033A0), RoundedCornerShape(16.dp))
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text    = "Apunta al código QR del personaje",
                color   = Color.White,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // ── Botón atrás (esquina superior izquierda) ───────
        IconButton(
            onClick  = { navController.popBackStack() },
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.50f), CircleShape)
                .size(42.dp)
        ) {
            Icon(
                imageVector        = Icons.Default.ArrowBack,
                contentDescription = "Volver",
                tint               = Color.White
            )
        }
    }
}

// ======================================================
// PANTALLA AR — agrega LocalView para la captura
// ======================================================
@Composable
fun PantallaAR(personajeId: String) {
    val context       = LocalContext.current
    val rootView      = LocalView.current
    val audioManager  = remember { AudioManager(context) }

    val engine        = rememberEngine()
    val modelLoader   = rememberModelLoader(engine)

    var anchor        by remember { mutableStateOf<Anchor?>(null) }
    var mostrarChat   by remember { mutableStateOf(false) }
    var hintVisible   by remember { mutableStateOf(false) }
    var mostrarFrase by remember { mutableStateOf(true) }

    val modelInstance  = rememberModelInstance(modelLoader, "models/$personajeId.glb")
    val tapTimes       = remember { mutableListOf<Long>() }
    val personajeInfo  = PERSONAJES_INFO[personajeId]   // nunca null: QR ya validado

    // Muestra el hint "toca 3 veces" durante 4 s al colocar el modelo
    LaunchedEffect(anchor) {
        if (anchor != null) {
            hintVisible = true
            kotlinx.coroutines.delay(4000)
            hintVisible = false
        }
    }

    DisposableEffect(Unit) {
        audioManager.playAmbiente()
        onDispose { audioManager.stopAmbiente(); audioManager.release() }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Vista AR ───────────────────────────────────────
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

        // ── Tarjeta de info del personaje (siempre visible) ──
        if (personajeInfo != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(12.dp),
                shape  = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.68f)
                )
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(
                        text       = personajeInfo.nombre,
                        color      = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 14.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text     = personajeInfo.descripcionCorta,
                        color    = Color.White.copy(alpha = 0.72f),
                        fontSize = 11.sp
                    )
                }
            }
        }

        // ── "Buscando superficie…" mientras no hay modelo ──
        if (anchor == null) {
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.60f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(16.dp),
                    color       = Color(0xFF0033A0),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Buscando superficie plana…", color = Color.White, fontSize = 13.sp)
            }
        }

        // ── Frase del personaje flotando ──
        if (
            anchor != null &&
            !hintVisible &&
            !mostrarChat &&
            personajeInfo != null &&
            mostrarFrase
        ) {
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp)
                    .offset(y = (-200).dp) // más arriba
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                mostrarFrase = false
                            }
                        )
                    },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF0033A0).copy(alpha = 0.82f)
                )
            ) {
                Text(
                    text = personajeInfo.frase,
                    color = Color.White,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }

        // ── Hint: "Toca 3 veces…" (aparece 4 s al colocar modelo) ──
        AnimatedVisibility(
            visible = hintVisible,
            enter   = fadeIn(),
            exit    = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.58f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(
                    "👆 Toca 3 veces al personaje\npara hablar con él",
                    color     = Color.White,
                    fontSize  = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }




        // ── Botón foto (oculto cuando el chat está abierto) ──
        if (!mostrarChat) {
            FloatingActionButton(
                onClick = { capturarPantalla(context, rootView, audioManager) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
                containerColor = Color(0xFF0033A0)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = "Foto", tint = Color.White)
            }
        }

        // ── Chat overlay ───────────────────────────────────
        if (mostrarChat) {
            ChatTikTokOverlay(
                personajeId   = personajeId,
                personajeInfo = personajeInfo,
                onCerrar      = { mostrarChat = false }
            )
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