package org.schabi.newpipe.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.ColorStateList
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.ImageViewCompat
import com.google.android.exoplayer2.PlaybackParameters
import kotlin.math.abs
import kotlin.random.Random
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.ActivityDrivingModeBinding
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.player.event.PlayerEventListener
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.image.CoilHelper

/**
 * Driving mode: a full-screen, audio-only control screen with very large touch targets.
 * It has no playback logic of its own, it only sends commands to the already running [Player]
 * and shows what the player reports back.
 */
class DrivingModeActivity : AppCompatActivity(), PlayerEventListener {

    private lateinit var binding: ActivityDrivingModeBinding
    private var player: Player? = null

    // where the finger first touched a zone, to tell taps and swipes apart
    private var downX = 0f
    private var downY = 0f

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName) = Unit

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            player = (service as? PlayerService.LocalBinder)?.service?.player
            if (player?.playQueue == null || player?.exoPlayerIsNull() != false) {
                finish() // nothing is playing, so there is nothing to control
            } else {
                player?.setActivityListener(this@DrivingModeActivity)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDrivingModeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // true full screen: hide the status and navigation bars
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, binding.root).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        // keep the controls clear of the camera notch and rounded corners
        ViewCompat.setOnApplyWindowInsetsListener(binding.drivingContent) { view, insets ->
            val safe = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }

        // frosted-glass look for the cover image (needs Android 12+, older phones just get it dimmed)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val radius = 40 * resources.displayMetrics.density
            binding.drivingBackground.setRenderEffect(
                RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
            )
        }

        binding.drivingClose.setOnClickListener { finish() }
        binding.drivingShuffle.setOnClickListener { player?.toggleShuffleModeEnabled() }
        binding.drivingPrevious.setOnClickListener { player?.playPrevious() }
        binding.drivingPlayPause.setOnClickListener { player?.playPause() }
        binding.drivingNext.setOnClickListener { player?.playNext() }
        for (zone in listOf(binding.drivingPrevious, binding.drivingPlayPause, binding.drivingNext)) {
            zone.setOnTouchListener(::onZoneTouch)
        }

        val bindIntent = Intent(this, PlayerService::class.java)
            .setAction(PlayerService.BIND_PLAYER_HOLDER_ACTION)
        bindService(bindIntent, serviceConnection, BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.removeActivityListener(this)
        unbindService(serviceConnection)
    }

    // ---------------------------------------------------------------------------------------
    // Touch: tap a zone to press it, swipe sideways anywhere on the zones to skip back/forward
    // ---------------------------------------------------------------------------------------

    private fun onZoneTouch(zone: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
            }

            MotionEvent.ACTION_UP -> {
                // convert pixels to dp so the same finger movement counts the same on every screen
                val density = resources.displayMetrics.density
                val dx = (event.rawX - downX) / density
                val dy = (event.rawY - downY) / density
                when (classifyDrivingGesture(dx, dy)) {
                    DrivingGesture.TAP -> {
                        pulse(zone)
                        zone.performClick()
                    }

                    DrivingGesture.SWIPE_FORWARD -> skip(forward = true)

                    DrivingGesture.SWIPE_BACK -> skip(forward = false)

                    DrivingGesture.NONE -> Unit
                }
            }
        }
        return true
    }

    /** Jumps back or forward by the "seek duration" from the settings and flashes a label. */
    private fun skip(forward: Boolean) {
        val player = player ?: return
        if (forward) player.fastForward() else player.fastRewind()

        val seconds = PlayerHelper.retrieveSeekDurationFromPreferences(player) / 1000
        val amount = resources.getQuantityString(R.plurals.seconds, seconds, seconds)
        binding.drivingSeekLabel.text = if (forward) "+$amount" else "-$amount"
        binding.drivingSeekLabel.animate().cancel()
        binding.drivingSeekLabel.alpha = 1f
        binding.drivingSeekLabel.animate().alpha(0f).setStartDelay(500).setDuration(200).start()
    }

    /** Quick shrink-and-return so a press is noticeable at a glance. */
    private fun pulse(zone: View) {
        zone.animate().cancel()
        zone.scaleX = 0.85f
        zone.scaleY = 0.85f
        zone.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
    }

    // ---------------------------------------------------------------------------------------
    // Updates coming from the player
    // ---------------------------------------------------------------------------------------

    override fun onPlaybackUpdate(
        state: Int,
        repeatMode: Int,
        shuffled: Boolean,
        parameters: PlaybackParameters?
    ) {
        // other states (buffering, seeking) keep the icon they had, to avoid flicker
        when (state) {
            Player.STATE_PLAYING -> showPlayPause(R.drawable.ic_pause, R.string.pause)
            Player.STATE_PAUSED -> showPlayPause(R.drawable.ic_play_arrow, R.string.play)
            Player.STATE_COMPLETED -> showPlayPause(R.drawable.ic_replay, R.string.replay)
        }
        // no box around the icon: off = dim white, on = bright neon green
        binding.drivingShuffle.alpha = if (shuffled) 1f else 0.35f
        val shuffleColor = if (shuffled) R.color.loki_green_neon else R.color.white
        ImageViewCompat.setImageTintList(
            binding.drivingShuffle,
            ColorStateList.valueOf(getColor(shuffleColor))
        )
    }

    private fun showPlayPause(icon: Int, description: Int) {
        binding.drivingPlayPauseIcon.setImageResource(icon)
        binding.drivingPlayPause.contentDescription = getString(description)
    }

    override fun onProgressUpdate(currentProgress: Int, duration: Int, bufferPercent: Int) {
        binding.drivingProgress.max = duration
        binding.drivingProgress.progress = currentProgress
        binding.drivingTime.text = Localization.getDurationString((currentProgress / 1000).toLong()) +
            " / " + Localization.getDurationString((duration / 1000).toLong())
    }

    override fun onMetadataUpdate(info: StreamInfo?, queue: PlayQueue?) {
        if (info == null) {
            return
        }
        binding.drivingTitle.text = info.name
        binding.drivingChannel.text = info.uploaderName
        CoilHelper.loadDetailsThumbnail(binding.drivingBackground, info.thumbnails)
    }

    override fun onQueueUpdate(queue: PlayQueue?) = Unit

    override fun onServiceStopped() {
        finish()
    }

    companion object {
        /**
         * Opens driving mode. It is audio-only, so if a video is showing it is first switched to
         * background playback, which keeps the sound going when the video screen gets covered.
         */
        @JvmStatic
        fun open(context: Context, player: Player?) {
            if (player != null && !player.audioPlayerSelected()) {
                player.setRecovery()
                NavigationHelper.playOnBackgroundPlayer(context, player.playQueue, true)
            }
            context.startActivity(
                Intent(context, DrivingModeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }

        /**
         * Plays a whole list (a playlist, a channel's videos) in shuffle mode in the background,
         * and opens driving mode to control it. Used by the "Driving mode" button on list screens.
         */
        @JvmStatic
        fun startShuffled(context: Context, queue: PlayQueue) {
            if (queue.isEmpty) {
                return
            }
            shuffleFromRandomStart(queue, Random.Default)
            NavigationHelper.playOnBackgroundPlayer(context, queue, false)

            // Android delivers the start request above to the player service before the bind
            // request driving mode makes when it opens, so the new player is ready when it connects
            context.startActivity(
                Intent(context, DrivingModeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

/**
 * Turns shuffle on for [queue] and makes a random item the first one to play. Shuffling alone
 * always keeps the current (first) item in front, so without the random start every drive would
 * begin with the same song. Turning shuffle off later restores the list's own order.
 */
internal fun shuffleFromRandomStart(queue: PlayQueue, random: Random) {
    queue.index = random.nextInt(queue.size())
    queue.shuffle()
}

/** What one finger movement on the driving mode zones means. */
internal enum class DrivingGesture { TAP, SWIPE_BACK, SWIPE_FORWARD, NONE }

/**
 * Decides what one finger movement on the zones means. It lives outside the screen class so it
 * can be tested on its own (see DrivingGestureTest).
 *
 * @param dx how far the finger moved sideways between touching down and lifting, in dp
 *           (positive = moved right, negative = moved left)
 * @param dy how far it moved vertically, in dp (positive = moved down)
 */
internal fun classifyDrivingGesture(dx: Float, dy: Float): DrivingGesture {
    val sideways = abs(dx)
    val vertical = abs(dy)
    return when {
        // the finger barely moved: a tap, even a sloppy one on a bumpy road
        sideways < 30 && vertical < 30 -> DrivingGesture.TAP

        // a long, mostly sideways movement: a deliberate swipe (right = forward)
        sideways >= 90 && sideways > vertical * 2 ->
            if (dx > 0) DrivingGesture.SWIPE_FORWARD else DrivingGesture.SWIPE_BACK

        // anything in between is unclear, and doing nothing is safer than guessing
        else -> DrivingGesture.NONE
    }
}
