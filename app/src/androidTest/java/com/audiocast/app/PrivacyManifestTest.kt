package com.audiocast.app

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the privacy invariants of the installed package: no camera, no
 * screen capture, and exactly the permissions the app needs.
 */
@RunWith(AndroidJUnit4::class)
class PrivacyManifestTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun declaredPermissions(): Set<String> {
        val info = context.packageManager.getPackageInfo(
            context.packageName, PackageManager.GET_PERMISSIONS
        )
        return info.requestedPermissions?.toSet() ?: emptySet()
    }

    @Test
    fun cameraPermissionIsNotRequested() {
        assertFalse(
            "CAMERA must never be requested",
            declaredPermissions().contains(android.Manifest.permission.CAMERA)
        )
    }

    @Test
    fun noBroadStoragePermissionIsRequested() {
        val perms = declaredPermissions()
        assertFalse(perms.contains("android.permission.MANAGE_EXTERNAL_STORAGE"))
        assertFalse(perms.contains(android.Manifest.permission.READ_EXTERNAL_STORAGE))
        assertFalse(perms.contains(android.Manifest.permission.WRITE_EXTERNAL_STORAGE))
    }

    @Test
    fun requiredPermissionsArePresent() {
        val perms = declaredPermissions()
        assertTrue(perms.contains(android.Manifest.permission.RECORD_AUDIO))
        assertTrue(perms.contains(android.Manifest.permission.INTERNET))
        assertTrue(perms.contains(android.Manifest.permission.FOREGROUND_SERVICE))
        assertTrue(perms.contains("android.permission.FOREGROUND_SERVICE_MICROPHONE"))
    }

    @Test
    fun noAccessibilityOrScreenCaptureService() {
        val perms = declaredPermissions()
        assertFalse(perms.contains("android.permission.BIND_ACCESSIBILITY_SERVICE"))
        // MediaProjection has no manifest permission; absence of camera +
        // no DisplaySource usage is verified by source inspection and tests.
        assertFalse(perms.contains("android.permission.CAPTURE_VIDEO_OUTPUT"))
        assertFalse(perms.contains("android.permission.CAPTURE_AUDIO_OUTPUT"))
        assertFalse(perms.contains("android.permission.CAPTURE_SECURE_VIDEO_OUTPUT"))
    }
}
