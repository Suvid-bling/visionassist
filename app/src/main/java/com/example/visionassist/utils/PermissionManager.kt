package com.example.visionassist.utils

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

/**
 * Manages permission requests and status monitoring
 */
class PermissionManager {

    companion object {
        const val PERMISSION_REQUEST_CODE = 100

        /**
         * Check if all permissions are granted
         */
        fun hasPermissions(context: Context, permissions: Array<String>): Boolean {
            return permissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        }

        /**
         * Request permissions through an Activity
         */
        fun requestPermissions(activity: Activity, permissions: Array<String>, requestCode: Int = PERMISSION_REQUEST_CODE): Boolean {
            val missingPermissions = permissions.filter {
                ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
            }.toTypedArray()

            if (missingPermissions.isNotEmpty()) {
                ActivityCompat.requestPermissions(activity, missingPermissions, requestCode)
                return false
            }

            return true
        }

        /**
         * Request permissions through a Fragment
         */
        fun requestPermissions(fragment: Fragment, permissions: Array<String>, requestCode: Int = PERMISSION_REQUEST_CODE): Boolean {
            val missingPermissions = permissions.filter {
                ContextCompat.checkSelfPermission(fragment.requireContext(), it) != PackageManager.PERMISSION_GRANTED
            }.toTypedArray()

            if (missingPermissions.isNotEmpty()) {
                fragment.requestPermissions(missingPermissions, requestCode)
                return false
            }

            return true
        }

        /**
         * Check if any permissions are permanently denied (user selected "Don't ask again")
         */
        fun shouldShowRationale(activity: Activity, permissions: Array<String>): Boolean {
            return permissions.any {
                ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
            }
        }

        /**
         * Check if any permissions are permanently denied through a Fragment
         */
        fun shouldShowRationale(fragment: Fragment, permissions: Array<String>): Boolean {
            return permissions.any {
                fragment.shouldShowRequestPermissionRationale(it)
            }
        }

        /**
         * Handle permission results
         * Returns true if all requested permissions are granted
         */
        fun handlePermissionResult(grantResults: IntArray): Boolean {
            return grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        }
    }
}