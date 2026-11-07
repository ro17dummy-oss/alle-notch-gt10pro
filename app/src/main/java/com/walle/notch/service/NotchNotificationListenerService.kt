package com.walle.notch.service

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.walle.notch.core.model.IslandMode
import com.walle.notch.core.model.NotificationPayload
import com.walle.notch.core.state.IslandRepository

class NotchNotificationListenerService : NotificationListenerService() {

    private var mediaSessionManager: MediaSessionManager? = null
    private var activeController: MediaController? = null

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        bindToMediaControllers(controllers)
    }

    private val mediaCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            syncMediaControllerState()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            syncMediaControllerState()
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        IslandRepository.setNotificationListenerConnected(true)
        setupMediaSessions()
        wireMediaCallbacks()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        IslandRepository.setNotificationListenerConnected(false)
        try {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionListener)
        } catch (_: Exception) {
        }
    }

    private fun setupMediaSessions() {
        try {
            val mgr = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
            mediaSessionManager = mgr
            val componentName = ComponentName(this, NotchNotificationListenerService::class.java)
            val controllers = mgr.getActiveSessions(componentName)
            bindToMediaControllers(controllers)
            mgr.addOnActiveSessionsChangedListener(sessionListener, componentName)
        } catch (_: SecurityException) {
        }
    }

    private fun bindToMediaControllers(controllers: List<MediaController>?) {
        activeController?.unregisterCallback(mediaCallback)
        val chosen = controllers?.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: controllers?.firstOrNull()

        activeController = chosen
        chosen?.registerCallback(mediaCallback)
        syncMediaControllerState()
    }

    private fun syncMediaControllerState() {
        val controller = activeController ?: return
        val metadata = controller.metadata
        val playback = controller.playbackState

        val isPlaying = playback?.state == PlaybackState.STATE_PLAYING
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?.takeIf { it.isNotBlank() } ?: "Put On Your Sunday Clothes"
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?.takeIf { it.isNotBlank() } ?: "Wall-E Archive Tape"
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)
            ?.takeIf { it > 0L } ?: 194_000L
        val position = playback?.position?.coerceAtLeast(0L) ?: 0L
        val art: Bitmap? = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)

        IslandRepository.updateMusic { prev ->
            prev.copy(
                isPlaying = isPlaying,
                title = title,
                artist = artist,
                packageName = controller.packageName ?: "",
                albumArt = art,
                durationMs = duration,
                positionMs = position
            )
        }
    }

    private fun wireMediaCallbacks() {
        IslandRepository.onMediaPlayPause = {
            val ctrl = activeController
            if (ctrl != null) {
                val pb = ctrl.playbackState?.state
                if (pb == PlaybackState.STATE_PLAYING) {
                    ctrl.transportControls.pause()
                } else {
                    ctrl.transportControls.play()
                }
            } else {
                IslandRepository.updateMusic { it.copy(isPlaying = !it.isPlaying) }
            }
        }
        IslandRepository.onMediaNext = {
            val ctrl = activeController
            if (ctrl != null) {
                ctrl.transportControls.skipToNext()
            } else {
                IslandRepository.updateMusic {
                    it.copy(
                        title = if (it.title.startsWith("Put On")) "Define Dancing (EVE Flight)" else "Put On Your Sunday Clothes",
                        artist = if (it.title.startsWith("Put On")) "Thomas Newman" else "Wall-E Archive Tape",
                        positionMs = 0L,
                        isPlaying = true
                    )
                }
            }
        }
        IslandRepository.onMediaPrevious = {
            val ctrl = activeController
            if (ctrl != null) {
                ctrl.transportControls.skipToPrevious()
            } else {
                IslandRepository.updateMusic { it.copy(positionMs = 0L, isPlaying = true) }
            }
        }
        IslandRepository.onMediaSeekTo = { posMs ->
            activeController?.transportControls?.seekTo(posMs)
            IslandRepository.updateMusic { it.copy(positionMs = posMs) }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.isOngoing) return
        val pkg = sbn.packageName ?: return
        if (pkg == packageName || pkg == "com.android.systemui") return

        val notif = sbn.notification ?: return
        val extras = notif.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        // Check if incoming call
        if (notif.category == Notification.CATEGORY_CALL) {
            val actions = notif.actions.orEmpty()
            val acceptIntent = actions.firstOrNull {
                it.title?.toString()?.contains("Answer", true) == true ||
                    it.title?.toString()?.contains("Terima", true) == true
            }?.actionIntent
            val declineIntent = actions.firstOrNull {
                it.title?.toString()?.contains("Decline", true) == true ||
                    it.title?.toString()?.contains("Tolak", true) == true
            }?.actionIntent

            IslandRepository.updateCallTimer { ct ->
                ct.copy(
                    callerName = title.ifBlank { "Panggilan Masuk" },
                    isIncomingCall = true,
                    isCallConnected = false,
                    acceptIntent = acceptIntent,
                    declineIntent = declineIntent
                )
            }
            IslandRepository.setMode(IslandMode.CALL_TIMER, expand = false)
            return
        }

        // Find RemoteInput quick reply action if available (WhatsApp, Telegram, Messages)
        var replyIntent: PendingIntent? = null
        var foundRemoteInput: android.app.RemoteInput? = null
        notif.actions?.forEach { action ->
            val remoteInputs = action.remoteInputs
            if (!remoteInputs.isNullOrEmpty() && action.actionIntent != null) {
                replyIntent = action.actionIntent
                foundRemoteInput = remoteInputs.first()
            }
        }

        val appShort = when {
            pkg.contains("whatsapp", true) -> "WA"
            pkg.contains("telegram", true) -> "TG"
            pkg.contains("instagram", true) -> "IG"
            pkg.contains("messaging", true) || pkg.contains("mms", true) -> "SMS"
            else -> "MSG"
        }

        IslandRepository.postNotification(
            NotificationPayload(
                key = sbn.key ?: "",
                packageName = pkg,
                appLabel = appShort,
                sender = title.ifBlank { appShort },
                message = text,
                contentIntent = notif.contentIntent,
                replyActionIntent = replyIntent,
                remoteInput = foundRemoteInput
            )
        )
    }

    companion object {
        fun sendQuickReply(context: Context, payload: NotificationPayload, replyText: String): Boolean {
            val actionIntent = payload.replyActionIntent
            val remoteInput = payload.remoteInput
            if (actionIntent != null && remoteInput != null) {
                return try {
                    val localIntent = Intent()
                    val bundle = Bundle()
                    bundle.putCharSequence(remoteInput.resultKey, replyText)
                    android.app.RemoteInput.addResultsToIntent(arrayOf(remoteInput), localIntent, bundle)
                    actionIntent.send(context, 0, localIntent)
                    IslandRepository.postFeedback("Balasan terkirim ke ${payload.sender}")
                    IslandRepository.setExpanded(false)
                    IslandRepository.setMode(IslandMode.IDLE, expand = false)
                    true
                } catch (_: Exception) {
                    false
                }
            } else {
                // Demo/Simulated mode feedback when testing without real WA notification
                IslandRepository.postFeedback("Simulasi balasan terkirim: \"$replyText\"")
                IslandRepository.setExpanded(false)
                IslandRepository.setMode(IslandMode.IDLE, expand = false)
                return true
            }
        }
    }
}
