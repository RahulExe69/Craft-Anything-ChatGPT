package ai.storytold.craftanything

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import java.io.File
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Keeps user-approved rebuilds alive and resumes their saved project after process recreation. */
class BuildExecutionService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var buildJob: Job? = null
    private val preferences by lazy { getSharedPreferences(PREFERENCES, MODE_PRIVATE) }
    private val bridge by lazy { NativeBuildBridge() }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground("Preparing your project…", null)
        when (intent?.action) {
            ACTION_STOP -> stopRequestedBuild()
            ACTION_START -> {
                if (buildJob?.isActive == true) {
                    updateNotification("A project build is already running…", preferences.getString(KEY_PROJECT_FOLDER, null))
                    return START_STICKY
                }
                val params = intent.getStringExtra(EXTRA_PARAMS)
                if (params.isNullOrBlank()) {
                    finishWithMessage("Couldn't start the build: its request was missing.", null)
                } else {
                    preferences.edit().putString(KEY_ACTIVE_PARAMS, params).apply()
                    beginBuild(params, recovering = false)
                }
            }
            else -> {
                val params = preferences.getString(KEY_ACTIVE_PARAMS, null)
                if (params.isNullOrBlank()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                } else {
                    beginBuild(params, recovering = true)
                }
            }
        }
        return START_STICKY
    }

    private fun beginBuild(originalParams: String, recovering: Boolean) {
        if (buildJob?.isActive == true) return
        buildJob = serviceScope.launch {
            try {
                val initialized = bridge.nativeInitialize(filesDir.absolutePath)
                if (initialized != "ok") throw IllegalStateException(initialized.removePrefix("error:"))

                val original = JSONObject(originalParams)
                val projectFolder = original.optString("project")
                    .takeIf { it.isNotBlank() }
                    ?: original.optString("project_folder").takeIf { it.isNotBlank() }
                val shouldResume = recovering && projectFolder != null &&
                    File(projectFolder, PROJECT_MANIFEST).isFile
                val params = if (shouldResume) {
                    BuildRecoveryParams.forResume(originalParams, projectFolder!!)
                } else {
                    originalParams
                }
                updateNotification(if (shouldResume) "Resuming your build…" else "Starting your build…", projectFolder)
                val started = readEnvelope(
                    bridge.nativeCallTool("rebuild_start", params, true),
                    "Craft Anything couldn't start this build."
                )
                val result = started.optJSONObject("output")?.optJSONObject("result")
                    ?: started.optJSONObject("output") ?: JSONObject()
                val folder = result.optString("project").takeIf { it.isNotBlank() } ?: projectFolder
                    ?: throw IllegalStateException("The build started without a project folder.")
                preferences.edit().putString(KEY_PROJECT_FOLDER, folder).apply()
                updateNotification("Build started · \${File(folder).name}", folder)

                while (serviceScope.isActive) {
                    delay(POLL_INTERVAL_MS)
                    val statusEnvelope = readEnvelope(
                        bridge.nativeCallTool("rebuild_status", JSONObject().put("project", folder).toString(), false),
                        "Couldn't read the build status."
                    )
                    val status = statusEnvelope.optJSONObject("output")?.optJSONObject("result")
                        ?: throw IllegalStateException("The build status response was incomplete.")
                    val queuedValue = status.opt("queued")
                    val queued = queuedValue != null && queuedValue != JSONObject.NULL
                    val running = status.optBoolean("running")
                    val label = status.optString("status_label").takeIf { it.isNotBlank() }
                    when {
                        queued -> updateNotification("Queued · position \${queuedValue}", folder)
                        running -> updateNotification(label?.let { "Building · $it" } ?: "Your project is building…", folder)
                        else -> {
                            finishWithMessage("Build finished · \${File(folder).name}", folder)
                            return@launch
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                finishWithMessage(
                    error.message?.take(180)?.ifBlank { null } ?: "Craft Anything couldn't continue this build.",
                    preferences.getString(KEY_PROJECT_FOLDER, null)
                )
            }
        }
    }

    private fun stopRequestedBuild() {
        val folder = preferences.getString(KEY_PROJECT_FOLDER, null) ?: runCatching {
            val params = JSONObject(preferences.getString(KEY_ACTIVE_PARAMS, "{}") ?: "{}")
            params.optString("project").ifBlank { params.optString("project_folder") }
        }.getOrNull()
        serviceScope.launch {
            val message = if (folder.isNullOrBlank()) "Build stopped." else runCatching {
                readEnvelope(
                    bridge.nativeCallTool("rebuild_stop", JSONObject().put("project", folder).toString(), false),
                    "Couldn't stop this build."
                )
                "Stopping build · \${File(folder).name}"
            }.getOrElse { it.message ?: "Couldn't stop this build." }
            buildJob?.cancel()
            finishWithMessage(message, folder)
        }
    }

    private fun readEnvelope(raw: String, fallback: String): JSONObject {
        val payload = JSONObject(raw)
        if (!payload.optBoolean("ok")) throw IllegalStateException(payload.optString("error").ifBlank { fallback })
        return payload
    }

    private fun startAsForeground(message: String, folder: String?) {
        val notification = buildNotification(message, folder, ongoing = true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(message: String, folder: String?) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(message, folder, ongoing = true))
    }

    private fun buildNotification(message: String, folder: String?, ongoing: Boolean): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!folder.isNullOrBlank()) putExtra(EXTRA_PROJECT_FOLDER, folder)
        }
        val openPending = PendingIntent.getActivity(
            this, REQUEST_OPEN, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Craft Anything")
            .setContentText(message)
            .setStyle(Notification.BigTextStyle().bigText(message))
            .setContentIntent(openPending)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
        if (ongoing) {
            val stopIntent = Intent(this, BuildExecutionService::class.java).apply { action = ACTION_STOP }
            val stopPending = PendingIntent.getService(
                this, REQUEST_STOP, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_media_pause, "Stop build", stopPending)
        }
        return builder.build()
    }

    private fun finishWithMessage(message: String, folder: String?) {
        preferences.edit().remove(KEY_ACTIVE_PARAMS).remove(KEY_PROJECT_FOLDER).apply()
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(message, folder, ongoing = false))
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Project builds", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Progress and controls for Craft Anything project builds"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        buildJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "ai.storytold.craftanything.action.START_BUILD"
        const val EXTRA_PARAMS = "ai.storytold.craftanything.extra.BUILD_PARAMS"
        const val EXTRA_PROJECT_FOLDER = "ai.storytold.craftanything.extra.PROJECT_FOLDER"
        private const val ACTION_STOP = "ai.storytold.craftanything.action.STOP_BUILD"
        private const val PREFERENCES = "craft_anything_build_service"
        private const val KEY_ACTIVE_PARAMS = "active_params"
        private const val KEY_PROJECT_FOLDER = "project_folder"
        private const val PROJECT_MANIFEST = "craft.json"
        private const val CHANNEL_ID = "craft_anything_builds"
        private const val NOTIFICATION_ID = 5909
        private const val REQUEST_OPEN = 5910
        private const val REQUEST_STOP = 5911
        private const val POLL_INTERVAL_MS = 3_000L
    }
}
