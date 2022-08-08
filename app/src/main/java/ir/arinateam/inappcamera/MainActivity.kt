package ir.arinateam.inappcamera

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.ContentValues.TAG
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.animation.doOnCancel
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import ir.arinateam.inappcamera.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executor

private var PERMISSIONS_REQUIRED = arrayOf(
    Manifest.permission.CAMERA,
    Manifest.permission.RECORD_AUDIO
)

class MainActivity : AppCompatActivity() {

    private lateinit var activityMainBinding: ActivityMainBinding

    private val outputDirectory: String by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//            TODO("Change save folder name")
            "${Environment.DIRECTORY_DCIM}/InAppCamera/"
        } else {
//            TODO("Change save folder name")
            "${getExternalFilesDir(Environment.DIRECTORY_DCIM)}/InAppCamera/"
        }
    }

    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var videoCapture: VideoCapture? = null

    private var lensFacing = CameraSelector.DEFAULT_FRONT_CAMERA

    private var isRecording = false
    private val animateRecord by lazy {
        ObjectAnimator.ofFloat(activityMainBinding.btnRecordVideo, View.ALPHA, 1f, 0.5f).apply {
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            doOnCancel { activityMainBinding.btnRecordVideo.alpha = 1f }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityMainBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(activityMainBinding.root)

        getPermission()

        activityMainBinding.btnRecordVideo.setOnClickListener { recordVideo() }

        activityMainBinding.btnSwitchCamera.setOnClickListener { toggleCamera() }

        activityMainBinding.btnRemoveVideo.setOnClickListener { removeVideo() }

    }

    @SuppressLint("RestrictedApi")
    private fun startCamera() {
        val viewFinder = activityMainBinding.viewFinder

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val rotation = viewFinder.display.rotation

            val localCameraProvider = cameraProvider
                ?: throw IllegalStateException("Camera initialization failed.")

            preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(rotation)
                .build()

            //TODO("Change size of video")
            val size = Size(400, 400)

            //TODO("Change setVideoFrameRate and setAudioBitRate")
            val videoCaptureConfig =
                VideoCapture.DEFAULT_CONFIG.config
            videoCapture = VideoCapture.Builder
                .fromConfig(videoCaptureConfig)
                .setVideoFrameRate(10)
                .setMaxResolution(size)
                .build()

            localCameraProvider.unbindAll()

            try {
                camera = localCameraProvider.bindToLifecycle(
                    this@MainActivity,
                    lensFacing,
                    preview,
                    videoCapture,
                )

                preview?.setSurfaceProvider(viewFinder.surfaceProvider)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind use cases", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("RestrictedApi")
    private fun recordVideo() {
        val localVideoCapture =
            videoCapture ?: throw IllegalStateException("Camera initialization failed.")


        val outputOptions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, System.currentTimeMillis())
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH, outputDirectory)
            }

            contentResolver.run {
                val contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

                VideoCapture.OutputFileOptions.Builder(this, contentUri, contentValues)
            }
        } else {
            File(outputDirectory).mkdirs()
            val file = File("$outputDirectory/${System.currentTimeMillis()}.mp4")

            VideoCapture.OutputFileOptions.Builder(file)
        }.build()



        if (!isRecording) {
            if (activityMainBinding.videoView.isPlaying) {
                activityMainBinding.btnRemoveVideo.visibility = View.GONE
                activityMainBinding.videoView.pause()
                activityMainBinding.videoView.visibility = View.GONE
            }

            animateRecord.start()
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }

            val timestamp = System.currentTimeMillis()

            val contentValues = ContentValues()
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, timestamp)
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")

            localVideoCapture.startRecording(
                VideoCapture.OutputFileOptions.Builder(
                    contentResolver,
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                ).build(),
                mainExecutor(),
                object : VideoCapture.OnVideoSavedCallback {
                    override fun onVideoSaved(outputFileResults: VideoCapture.OutputFileResults) {
                        Toast.makeText(
                            this@MainActivity,
                            "Video has been saved successfully.",
                            Toast.LENGTH_SHORT
                        ).show()

                        activityMainBinding.btnRemoveVideo.visibility = View.VISIBLE
                        playVideo(outputFileResults.savedUri!!)

                    }

                    override fun onError(
                        videoCaptureError: Int,
                        message: String,
                        cause: Throwable?
                    ) {
                        Toast.makeText(
                            this@MainActivity,
                            "Error saving video: $message",
                            Toast.LENGTH_SHORT
                        ).show()
                        Toast.makeText(
                            this@MainActivity,
                            "Ino baram aks begir:  ${cause!!.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )

            /*localVideoCapture.startRecording(
                outputOptions,
                mainExecutor(),
                object : VideoCapture.OnVideoSavedCallback {
                    override fun onVideoSaved(outputFileResults: VideoCapture.OutputFileResults) {
                        // Create small preview
                        outputFileResults.savedUri
                            ?.let { uri ->
                                activityMainBinding.btnRemoveVideo.visibility = View.VISIBLE
                                playVideo(uri)
                                Log.d(TAG, "Video saved in $uri")
                            }
                    }

                    override fun onError(
                        videoCaptureError: Int,
                        message: String,
                        cause: Throwable?
                    ) {
                        // This function is called if there is an error during recording process
                        animateRecord.cancel()
                        val msg = "Video capture failed: $message"
                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                        Log.e(TAG, msg)
                        cause?.printStackTrace()
                    }
                })*/
        } else {
            animateRecord.cancel()
            localVideoCapture.stopRecording()
        }
        isRecording = !isRecording
    }

    private fun playVideo(uri: Uri) {

        activityMainBinding.videoView.visibility = View.VISIBLE


        activityMainBinding.videoView.setVideoURI(uri)
        activityMainBinding.videoView.requestFocus()
        activityMainBinding.videoView.start()

        pauseVideo()

    }

    private var videoPosition = 0

    private fun pauseVideo() {

        activityMainBinding.videoView.setOnClickListener {

            if (activityMainBinding.videoView.isPlaying) {
                videoPosition = activityMainBinding.videoView.currentPosition
                activityMainBinding.videoView.pause()
            } else {
                activityMainBinding.videoView.seekTo(videoPosition)
                activityMainBinding.videoView.start()
            }

        }

    }

    private fun removeVideo() {

        activityMainBinding.videoView.visibility = View.GONE
        activityMainBinding.btnRemoveVideo.visibility = View.GONE

        startCamera()

    }

    private fun toggleCamera() = activityMainBinding.btnSwitchCamera.toggleButton(
        flag = lensFacing == CameraSelector.DEFAULT_FRONT_CAMERA,
        rotationAngle = 180f,
        firstIcon = R.drawable.ic_outline_camera_rear,
        secondIcon = R.drawable.ic_outline_camera_front,
    ) {
        lensFacing = if (it) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        startCamera()
    }

    private fun ImageButton.toggleButton(
        flag: Boolean,
        rotationAngle: Float,
        @DrawableRes firstIcon: Int,
        @DrawableRes secondIcon: Int,
        action: (Boolean) -> Unit
    ) {
        if (flag) {
            if (rotationY == 0f) rotationY = rotationAngle
            animate().rotationY(0f).apply {
                setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator?) {
                        super.onAnimationEnd(animation)
                        action(!flag)
                    }
                })
            }.duration = 200
            GlobalScope.launch(Dispatchers.Main) {
                delay(100)
                setImageResource(firstIcon)
            }
        } else {
            if (rotationY == rotationAngle) rotationY = 0f
            animate().rotationY(rotationAngle).apply {
                setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator?) {
                        super.onAnimationEnd(animation)
                        action(!flag)
                    }
                })
            }.duration = 200
            GlobalScope.launch(Dispatchers.Main) {
                delay(100)
                setImageResource(secondIcon)
            }
        }
    }

    private fun getPermission() {

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            val permissionList = PERMISSIONS_REQUIRED.toMutableList()
            permissionList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            PERMISSIONS_REQUIRED = permissionList.toTypedArray()
        }

        if (!hasPermissions(this)) {
            activityResultLauncher.launch(PERMISSIONS_REQUIRED)

            activityMainBinding.btnRecordVideo.visibility = View.GONE
            activityMainBinding.btnSwitchCamera.visibility = View.GONE

        } else {

            activityMainBinding.btnRecordVideo.visibility = View.VISIBLE
            activityMainBinding.btnSwitchCamera.visibility = View.VISIBLE

            startCamera()

        }

    }

    private fun Context.mainExecutor(): Executor =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            mainExecutor
        } else {
            MainExecutor()
        }

    companion object {
        fun hasPermissions(context: Context) = PERMISSIONS_REQUIRED.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private val activityResultLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions())
        { permissions ->
            // Handle Permission granted/rejected
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in PERMISSIONS_REQUIRED && !it.value)
                    permissionGranted = false
            }
            if (!permissionGranted) {
                activityMainBinding.btnRecordVideo.visibility = View.GONE
                activityMainBinding.btnSwitchCamera.visibility = View.GONE
                Toast.makeText(this, "Permission request denied", Toast.LENGTH_LONG).show()
            } else {

                activityMainBinding.btnRecordVideo.visibility = View.VISIBLE
                activityMainBinding.btnSwitchCamera.visibility = View.VISIBLE

                startCamera()

            }
        }

    override fun onStop() {

        super.onStop()
    }

}
