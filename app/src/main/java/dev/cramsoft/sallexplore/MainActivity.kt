package dev.cramsoft.sallexplore

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.ar.core.Config
import com.google.ar.core.Plane
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.arcore.createAnchorOrNull
import io.github.sceneview.ar.arcore.getUpdatedPlanes
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.node.ModelNode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PantallaAR() }
    }
}

@Composable
fun PantallaAR() {
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            ARSceneView(context).apply {
                this.lifecycle = lifecycleOwner.lifecycle

                // ✅ HDR lighting — fix para el modelo negro
                configureSession { _, config ->
                    config.lightEstimationMode =
                        Config.LightEstimationMode.ENVIRONMENTAL_HDR
                    config.planeFindingMode =
                        Config.PlaneFindingMode.HORIZONTAL
                }

                planeRenderer.isEnabled = true

                var modelColocado = false

                onSessionUpdated = { _, frame ->
                    if (!modelColocado) {
                        frame.getUpdatedPlanes()
                            .firstOrNull { it.type == Plane.Type.HORIZONTAL_UPWARD_FACING }
                            ?.let { plane ->
                                plane.createAnchorOrNull(plane.centerPose)?.let { anchor ->
                                    modelColocado = true

                                    // ✅ Cargar modelo GLB con texturas
                                    val modelInstance = modelLoader
                                        .createModelInstance("models/juan_ok2.glb")

                                    val modelNode = ModelNode(
                                        modelInstance = modelInstance,
                                        scaleToUnits  = 1.0f,
                                        autoAnimate   = true
                                    )

                                    val anchorNode = AnchorNode(
                                        engine = engine,
                                        anchor = anchor
                                    ).apply {
                                        addChildNode(modelNode)
                                    }

                                    addChildNode(anchorNode)
                                }
                            }
                    }
                }
            }
        }
    )
}
