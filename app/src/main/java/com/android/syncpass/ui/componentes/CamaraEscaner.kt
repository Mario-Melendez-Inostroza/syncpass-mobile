package com.android.syncpass.ui.componentes

import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * Vista de cámara que lee códigos QR (CameraX + ML Kit). Llama a [onCodigo] con el texto de cada QR que ve;
 * quien la usa debe filtrar las repeticiones, porque la misma etiqueta se lee varias veces por segundo.
 *
 * La cámara se libera al salir de la pantalla (no solo al destruirse la actividad).
 */
@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
fun CamaraEscaner(modifier: Modifier = Modifier, onCodigo: (String) -> Unit) {
    val contexto = LocalContext.current
    val cicloDeVida = LocalLifecycleOwner.current
    val alLeer by rememberUpdatedState(onCodigo)

    val escaner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build(),
        )
    }
    val ejecutor = remember { Executors.newSingleThreadExecutor() }
    val proveedor = remember { arrayOfNulls<ProcessCameraProvider>(1) }

    DisposableEffect(Unit) {
        onDispose {
            proveedor[0]?.unbindAll()
            escaner.close()
            ejecutor.shutdown()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val vista = PreviewView(ctx)
            val futuro = ProcessCameraProvider.getInstance(ctx)
            futuro.addListener(
                {
                    val camara = futuro.get()
                    proveedor[0] = camara

                    val vistaPrevia = Preview.Builder().build().also { it.surfaceProvider = vista.surfaceProvider }
                    val analisis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analisis.setAnalyzer(ejecutor) { imagen ->
                        val medio = imagen.image
                        if (medio == null) {
                            imagen.close()
                            return@setAnalyzer
                        }
                        val entrada = InputImage.fromMediaImage(medio, imagen.imageInfo.rotationDegrees)
                        escaner.process(entrada)
                            .addOnSuccessListener { codigos ->
                                codigos.firstNotNullOfOrNull { it.rawValue }?.let(alLeer)
                            }
                            .addOnCompleteListener { imagen.close() }
                    }

                    try {
                        camara.unbindAll()
                        camara.bindToLifecycle(cicloDeVida, CameraSelector.DEFAULT_BACK_CAMERA, vistaPrevia, analisis)
                    } catch (e: Exception) {
                        // Sin cámara disponible: la pantalla ofrece elegir al asistente de una lista.
                    }
                },
                ContextCompat.getMainExecutor(ctx),
            )
            vista
        },
    )
}
