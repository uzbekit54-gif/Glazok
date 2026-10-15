package com.example.glazok.vision

import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executors

/**
 * Менеджер жизненного цикла камеры CameraX.
 * Привязывает Preview, потоковый анализатор ImageAnalysis с STRATEGY_KEEP_ONLY_LATEST
 * и ImageCapture для создания снимков высокого разрешения (Tier 2 VLM).
 */
class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    val analyzer: GlazokImageAnalyzer = GlazokImageAnalyzer()
) {

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var camera: Camera? = null
    var imageCapture: ImageCapture? = null
        private set

    private var isTorchEnabled = false

    fun startCamera(previewView: PreviewView, onReady: () -> Unit = {}) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, analyzer)
                }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis,
                    imageCapture
                )
                onReady()
            } catch (exc: Exception) {
                exc.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun toggleTorch(): Boolean {
        camera?.let {
            isTorchEnabled = !isTorchEnabled
            it.cameraControl.enableTorch(isTorchEnabled)
        }
        return isTorchEnabled
    }

    fun setZoom(zoomRatio: Float) {
        camera?.cameraControl?.setLinearZoom(zoomRatio.coerceIn(0f, 1f))
    }

    /**
     * Захватывает снимок высокого разрешения для отправки в Контур 2 (Deep VLM).
     */
    fun takeSnapshot(
        onSuccess: (android.graphics.Bitmap) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val capture = imageCapture ?: run {
            onError(IllegalStateException("ImageCapture не инициализирован"))
            return
        }

        capture.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    try {
                        val rotation = imageProxy.imageInfo.rotationDegrees
                        val originalBitmap = imageProxy.toBitmap()

                        val finalBitmap = if (rotation != 0) {
                            val matrix = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
                            android.graphics.Bitmap.createBitmap(
                                originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true
                            )
                        } else {
                            originalBitmap
                        }

                        ContextCompat.getMainExecutor(context).execute {
                            onSuccess(finalBitmap)
                        }
                    } catch (e: Exception) {
                        ContextCompat.getMainExecutor(context).execute {
                            onError(e)
                        }
                    } finally {
                        imageProxy.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    ContextCompat.getMainExecutor(context).execute {
                        onError(exception)
                    }
                }
            }
        )
    }

    fun release() {
        cameraExecutor.shutdown()
        analyzer.release()
    }
}

