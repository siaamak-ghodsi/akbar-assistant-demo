package com.akbar.assistant.demo.device

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.core.content.ContextCompat

class FlashlightController(private val context: Context) {

    private val cameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private var torchCameraId: String? = null
    private var enabled = false

    val isOn: Boolean get() = enabled

    fun hasFlash(): Boolean {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
            return false
        }
        return findTorchCameraId() != null
    }

    fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun setEnabled(on: Boolean): Result {
        if (!hasFlash()) return Result.NO_FLASH
        if (!hasCameraPermission()) return Result.NO_PERMISSION
        val id = findTorchCameraId() ?: return Result.NO_FLASH
        return try {
            cameraManager.setTorchMode(id, on)
            enabled = on
            if (on) Result.ON else Result.OFF
        } catch (_: Exception) {
            Result.ERROR
        }
    }

    fun turnOffQuietly() {
        if (!enabled) return
        val id = torchCameraId ?: findTorchCameraId() ?: return
        try {
            cameraManager.setTorchMode(id, false)
        } catch (_: Exception) {
        }
        enabled = false
    }

    private fun findTorchCameraId(): String? {
        torchCameraId?.let { return it }
        return try {
            cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK
            } ?: cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (_: Exception) {
            null
        }.also { torchCameraId = it }
    }

    enum class Result {
        ON,
        OFF,
        NO_FLASH,
        NO_PERMISSION,
        ERROR
    }
}
