package com.xxluciferinxx.scanit

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import kotlinx.android.synthetic.main.activity_main.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {

    private lateinit var cameraExecutor: ExecutorService

    companion object {
        private const val TAG = "SCAN-IT"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private const val READ_TEXT = 101
        private const val SCAN_CODE = 102
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        bnReadText.setOnClickListener {
            ivClear.performClick()
            recognize(READ_TEXT)
        }
        bnScanCode.setOnClickListener {
            ivClear.performClick()
            recognize(SCAN_CODE)
        }
        bnOpenLink.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(tvTextDisplay.text.toString())))
        }

        ivClear.setOnClickListener {
            tvTextDisplay.text = ""
            bnOpenLink.visibility = View.GONE
            tvTextDisplay.scrollTo(0, 0)
        }
        ivCopyText.setOnClickListener {
            val clipboard: ClipboardManager =
                getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip =
                ClipData.newPlainText(resources.getString(R.string.app_name), tvTextDisplay.text)
            clipboard.setPrimaryClip(clip)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_AZTEC
            ).build()

        tvTextDisplay.movementMethod = ScrollingMovementMethod()
    }

    private fun recognize(state: Int) {
        val image = InputImage.fromBitmap(viewFinder.bitmap!!, viewFinder.rotation.toInt())
        when (state) {
            READ_TEXT -> recognizeText(image)
            SCAN_CODE -> recognizeBarCode(image)
        }
    }

    private fun recognizeText(image: InputImage) {
        val recognizer = TextRecognition.getClient()
        recognizer.process(image)
            .addOnSuccessListener {
                val resultText = it.text
                if (resultText.isNotEmpty()) {
                    tvTextDisplay.text = resultText
                } else {
                    tvTextDisplay.text = resources.getString(R.string.noData)
                }
            }
            .addOnFailureListener {
                Log.e(TAG, "Recognition Failed ${it.message}")
            }
    }

    private fun recognizeBarCode(image: InputImage) {
        val scanner = BarcodeScanning.getClient()
        scanner.process(image)
            .addOnSuccessListener {
                for (barcode in it) {
//                    val bounds = barcode.boundingBox
//                    val corners = barcode.cornerPoints
//                    val rawValue = barcode.rawValue
                    when (barcode.valueType) {
                        Barcode.TYPE_URL -> {
//                            val title = barcode.url!!.title
                            val url = barcode.url!!.url
                            if (url!!.isNotEmpty()) {
                                tvTextDisplay.text = url
                            } else {
                                tvTextDisplay.text = resources.getString(R.string.noData)
                            }
                            bnOpenLink.visibility = View.VISIBLE
                        }
                    }
                }
            }
            .addOnFailureListener {
                Log.e(TAG, "Recognition Failed ${it.message}")
            }

    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder().build()
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()
                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}