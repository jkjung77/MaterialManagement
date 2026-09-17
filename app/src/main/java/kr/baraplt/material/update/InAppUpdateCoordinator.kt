package kr.baraplt.material.update

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kr.baraplt.material.R

/**
 * Google Play in-app update. Same flow as JayooEdit: prompt first, then flexible/immediate,
 * or open the store if Play cannot start a flow.
 */
class InAppUpdateCoordinator(
    private val activity: ComponentActivity,
) {
    private var appUpdateManager: AppUpdateManager? = null
    private var updateLauncher: ActivityResultLauncher<IntentSenderRequest>? = null
    private var showingDownloadedPrompt = false

    private val installStateListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            activity.runOnUiThread { showUpdateDownloadedPrompt() }
        }
    }

    fun register() {
        updateLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult(),
        ) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                Log.w(TAG, "In-app update flow result: ${result.resultCode}")
            }
        }
    }

    fun start() {
        val debuggable = (activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!UpdatePolicy.shouldCheck(debuggable)) return
        val manager = AppUpdateManagerFactory.create(activity.applicationContext)
        appUpdateManager = manager
        manager.registerListener(installStateListener)
        manager.appUpdateInfo
            .addOnSuccessListener { info ->
                if (info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE) {
                    return@addOnSuccessListener
                }
                val flexible = info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                val immediate = info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                activity.runOnUiThread {
                    when (UpdatePolicy.path(flexible, immediate)) {
                        UpdatePolicy.Path.STORE -> showUpdateAvailableDialogOpenStore()
                        UpdatePolicy.Path.FLEXIBLE,
                        UpdatePolicy.Path.IMMEDIATE -> showUpdateAvailableDialog(info)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "App update check failed", e)
            }
    }

    fun onResume() {
        val manager = appUpdateManager ?: return
        manager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.installStatus() == InstallStatus.DOWNLOADED) {
                activity.runOnUiThread { showUpdateDownloadedPrompt() }
            }
        }
    }

    fun onDestroy() {
        appUpdateManager?.unregisterListener(installStateListener)
        appUpdateManager = null
    }

    private fun showUpdateAvailableDialog(info: AppUpdateInfo) {
        if (activity.isFinishing || activity.isDestroyed) return
        AlertDialog.Builder(activity)
            .setTitle(R.string.update_title)
            .setMessage(R.string.update_message)
            .setPositiveButton(R.string.update_now) { _, _ ->
                startInAppUpdateFlow(info)
            }
            .setNegativeButton(R.string.update_later, null)
            .setCancelable(true)
            .show()
    }

    private fun showUpdateAvailableDialogOpenStore() {
        if (activity.isFinishing || activity.isDestroyed) return
        AlertDialog.Builder(activity)
            .setTitle(R.string.update_title)
            .setMessage(R.string.update_message)
            .setPositiveButton(R.string.update_now) { _, _ ->
                openPlayStoreForUpdate()
            }
            .setNegativeButton(R.string.update_later, null)
            .setCancelable(true)
            .show()
    }

    private fun startInAppUpdateFlow(info: AppUpdateInfo) {
        val manager = appUpdateManager
        val launcher = updateLauncher
        if (manager == null || launcher == null) {
            openPlayStoreForUpdate()
            return
        }
        try {
            val type = when (UpdatePolicy.path(
                info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE),
                info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE),
            )) {
                UpdatePolicy.Path.FLEXIBLE -> AppUpdateType.FLEXIBLE
                UpdatePolicy.Path.IMMEDIATE -> AppUpdateType.IMMEDIATE
                UpdatePolicy.Path.STORE -> {
                    openPlayStoreForUpdate()
                    return
                }
            }
            manager.startUpdateFlowForResult(
                info,
                launcher,
                AppUpdateOptions.newBuilder(type).build(),
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start update flow", e)
            openPlayStoreForUpdate()
        }
    }

    private fun openPlayStoreForUpdate() {
        val pkg = activity.packageName
        try {
            activity.startActivity(
                Intent(Intent.ACTION_VIEW, "market://details?id=$pkg".toUri()),
            )
        } catch (_: Exception) {
            try {
                activity.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        "https://play.google.com/store/apps/details?id=$pkg".toUri(),
                    ),
                )
            } catch (ignored: Exception) {
                Log.w(TAG, "Unable to open Play Store", ignored)
            }
        }
    }

    private fun showUpdateDownloadedPrompt() {
        if (activity.isFinishing || activity.isDestroyed || showingDownloadedPrompt) return
        showingDownloadedPrompt = true
        AlertDialog.Builder(activity)
            .setTitle(R.string.update_title)
            .setMessage(R.string.update_downloaded)
            .setPositiveButton(R.string.update_restart) { _, _ ->
                appUpdateManager?.completeUpdate()
            }
            .setNegativeButton(R.string.update_later) { _, _ ->
                showingDownloadedPrompt = false
            }
            .setOnDismissListener { showingDownloadedPrompt = false }
            .setCancelable(true)
            .show()
    }

    companion object {
        private const val TAG = "InAppUpdate"
    }
}
