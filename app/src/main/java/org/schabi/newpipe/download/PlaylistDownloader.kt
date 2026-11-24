package org.schabi.newpipe.download

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.io.File
import java.io.IOException
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.settings.NewPipeSettings
import org.schabi.newpipe.streams.io.StoredDirectoryHelper
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.FilenameUtils
import org.schabi.newpipe.util.ListHelper
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.PermissionHelper
import org.schabi.newpipe.util.SecondaryStreamHelper
import us.shandian.giga.get.MissionRecoveryInfo
import us.shandian.giga.postprocessing.Postprocessing
import us.shandian.giga.service.DownloadManager
import us.shandian.giga.service.DownloadManagerService

/**
 * Loki: downloads every video of a playlist, as audio or as video, into the download folder set
 * in the settings. It picks the same format and quality the single-video download dialog would
 * pick by default, and skips songs whose file is already in the folder, so running it again
 * later only fetches what was added to the playlist since.
 *
 * Each video is added to the normal Downloads screen as soon as it is ready, so progress,
 * pausing and retrying all work as usual.
 */
object PlaylistDownloader {
    private const val TAG = "PlaylistDownloader"

    /** Jobs keep running after the playlist screen is closed; this only keeps them referenced. */
    private val running = CompositeDisposable()

    /** One video to download: which site it is on, and its address. */
    private data class Entry(val serviceId: Int, val url: String)

    private class Outcome(val added: Int, val alreadySaved: Int, val unavailable: Int)

    /** Which file to download for one video, and how to finish it afterwards. */
    private class Choice(
        val urls: Array<String>,
        val recovery: List<MissionRecoveryInfo>,
        val suffix: String,
        val mime: String,
        val postprocessing: String?
    )

    /** For an online playlist: every page is fetched, not only the part shown on screen. */
    @JvmStatic
    fun showForOnlinePlaylist(activity: AppCompatActivity, serviceId: Int, url: String) {
        askAndStart(activity) { fetchAllPlaylistEntries(serviceId, url) }
    }

    /** For a list whose items are all known already, like a playlist saved in the app. */
    @JvmStatic
    fun showForItems(activity: AppCompatActivity, items: List<PlayQueueItem>) {
        val entries = items.map { Entry(it.serviceId, it.url) }
        askAndStart(activity) { entries }
    }

    private fun askAndStart(activity: AppCompatActivity, loadEntries: () -> List<Entry>) {
        val options = arrayOf(
            activity.getString(R.string.download_playlist_audio),
            activity.getString(R.string.download_playlist_video)
        )
        AlertDialog.Builder(activity)
            .setTitle(R.string.download_playlist)
            .setItems(options) { _, which -> start(activity, which == 0, loadEntries) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun start(activity: AppCompatActivity, audio: Boolean, loadEntries: () -> List<Entry>) {
        // asks for the permission if needed; the user then taps the download button again
        if (!PermissionHelper.checkStoragePermissions(
                activity,
                PermissionHelper.DOWNLOAD_DIALOG_REQUEST_CODE
            )
        ) {
            return
        }

        val folder = loadDownloadFolder(activity, audio)
        if (folder == null) {
            AlertDialog.Builder(activity)
                .setTitle(R.string.download_playlist)
                .setMessage(R.string.download_playlist_no_folder)
                .setPositiveButton(R.string.settings) { _, _ ->
                    NavigationHelper.openSettings(activity)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return
        }

        val appContext = activity.applicationContext
        Toast.makeText(appContext, R.string.download_playlist_started, Toast.LENGTH_LONG).show()

        running.add(
            Single.fromCallable { downloadAll(appContext, loadEntries(), audio, folder) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { outcome ->
                        val message = appContext.getString(
                            R.string.download_playlist_done,
                            outcome.added,
                            outcome.alreadySaved,
                            outcome.unavailable
                        )
                        Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
                    },
                    { error ->
                        ErrorUtil.createNotification(
                            appContext,
                            ErrorInfo(error, UserAction.DOWNLOAD_FAILED, "Downloading a playlist")
                        )
                    }
                )
        )
    }

    /**
     * The download folder for audio or video from the settings, or null when none is set or it
     * can no longer be used. Uses the same checks as the single-video download dialog.
     */
    private fun loadDownloadFolder(context: Context, audio: Boolean): StoredDirectoryHelper? {
        val key = if (audio) R.string.download_path_audio_key else R.string.download_path_video_key
        val tag = if (audio) DownloadManager.TAG_AUDIO else DownloadManager.TAG_VIDEO
        val path = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(context.getString(key), null)
        // a path starting with "/" is an old, no longer supported style
        if (path.isNullOrEmpty() || path.startsWith(File.separator)) {
            return null
        }

        val folder = try {
            StoredDirectoryHelper(context, path.toUri(), tag)
        } catch (e: IOException) {
            Log.w(TAG, "Download folder $path cannot be opened", e)
            return null
        }
        val matchesStorageSetting =
            folder.isDirect != NewPipeSettings.useStorageAccessFramework(context)
        return folder.takeIf { matchesStorageSetting && !folder.isInvalidSafStorage }
    }

    /** Runs on a background thread: the playlist pages are loaded one after the other. */
    private fun fetchAllPlaylistEntries(serviceId: Int, url: String): List<Entry> {
        val firstPage = ExtractorHelper.getPlaylistInfo(serviceId, url, false).blockingGet()
        val items = firstPage.relatedItems.toMutableList()
        var nextPage: Page? = firstPage.nextPage
        while (Page.isValid(nextPage)) {
            val more = ExtractorHelper.getMorePlaylistItems(serviceId, url, nextPage).blockingGet()
            items += more.items
            nextPage = more.nextPage
        }
        return items.map { Entry(it.serviceId, it.url) }
    }

    /** Runs on a background thread. One video that cannot be loaded does not stop the rest. */
    private fun downloadAll(
        context: Context,
        entries: List<Entry>,
        audio: Boolean,
        folder: StoredDirectoryHelper
    ): Outcome {
        if (!folder.mkdirs()) {
            throw IOException("Could not create the download folder")
        }
        val threads = PreferenceManager.getDefaultSharedPreferences(context)
            .getInt(context.getString(R.string.default_download_threads), 3)

        var added = 0
        var alreadySaved = 0
        var unavailable = 0
        // the same video can be in a playlist twice; download it once
        for (entry in entries.distinct()) {
            try {
                val info = ExtractorHelper.getStreamInfo(entry.serviceId, entry.url, false)
                    .blockingGet()
                if (queueDownload(context, info, audio, folder, threads)) added++ else alreadySaved++
            } catch (e: Exception) {
                // deleted, private, region-locked or no downloadable format
                Log.w(TAG, "Skipping ${entry.url}", e)
                unavailable++
            }
        }
        return Outcome(added, alreadySaved, unavailable)
    }

    /** @return false if a file with this name is already in the folder, true once queued */
    private fun queueDownload(
        context: Context,
        info: StreamInfo,
        audio: Boolean,
        folder: StoredDirectoryHelper,
        threads: Int
    ): Boolean {
        // the sound of the video's default language (some videos have several audio tracks)
        val audioStreams = ListHelper.getStreamsOfSpecifiedDelivery(
            info.audioStreams,
            DeliveryMethod.PROGRESSIVE_HTTP
        )
        val tracks = ListHelper.getGroupedAudioStreams(context, audioStreams)
        val trackStreams =
            tracks.getOrNull(ListHelper.getDefaultAudioTrackGroup(context, tracks)) ?: audioStreams

        val choice = if (audio) {
            pickAudio(context, trackStreams)
        } else {
            pickVideo(context, info, trackStreams, tracks.size > 1)
        } ?: throw IOException("No downloadable format for ${info.url}")

        val fileName = FilenameUtils.createFilename(context, info.name) + "." + choice.suffix
        if (folder.findFile(fileName) != null) {
            return false
        }
        val file = folder.createFile(fileName, choice.mime)
            ?: throw IOException("Could not create $fileName")

        DownloadManagerService.startMission(
            context, choice.urls, file, if (audio) 'a' else 'v', threads, info,
            choice.postprocessing, null, 0, ArrayList(choice.recovery)
        )
        return true
    }

    /** The audio format and quality chosen in the settings, as the download dialog does. */
    private fun pickAudio(context: Context, streams: List<AudioStream>): Choice? {
        val stream = streams.getOrNull(ListHelper.getDefaultAudioFormat(context, streams))
            ?: return null
        val format = stream.format ?: return null
        val urls = arrayOf(stream.content)
        val recovery = listOf(MissionRecoveryInfo(stream))
        return when (format) {
            MediaFormat.WEBMA_OPUS -> Choice(
                urls,
                recovery,
                "opus",
                "audio/ogg",
                Postprocessing.ALGORITHM_OGG_FROM_WEBM_DEMUXER
            )

            MediaFormat.M4A -> Choice(
                urls,
                recovery,
                format.suffix,
                format.mimeType,
                Postprocessing.ALGORITHM_M4A_NO_DASH
            )

            else -> Choice(urls, recovery, format.suffix, format.mimeType, null)
        }
    }

    /** The default video resolution from the settings, merged with its sound if needed. */
    private fun pickVideo(
        context: Context,
        info: StreamInfo,
        audioStreams: List<AudioStream>,
        severalAudioTracks: Boolean
    ): Choice? {
        val videos = ListHelper.getSortedStreamVideosList(
            context,
            ListHelper.getStreamsOfSpecifiedDelivery(
                info.videoStreams,
                DeliveryMethod.PROGRESSIVE_HTTP
            ),
            ListHelper.getStreamsOfSpecifiedDelivery(
                info.videoOnlyStreams,
                DeliveryMethod.PROGRESSIVE_HTTP
            ),
            false,
            severalAudioTracks
        )
        val video = videos.getOrNull(ListHelper.getDefaultResolutionIndex(context, videos))
            ?: return null
        val format = video.format ?: return null
        if (!video.isVideoOnly) {
            return Choice(
                arrayOf(video.content),
                listOf(MissionRecoveryInfo(video)),
                format.suffix,
                format.mimeType,
                null
            )
        }

        // a picture-only stream: download its matching sound as well and merge the two
        val sound = SecondaryStreamHelper.getAudioStreamFor(context, audioStreams, video)
            ?: return null
        val merger = if (format == MediaFormat.MPEG_4) {
            Postprocessing.ALGORITHM_MP4_FROM_DASH_MUXER
        } else {
            Postprocessing.ALGORITHM_WEBM_MUXER
        }
        return Choice(
            arrayOf(video.content, sound.content),
            listOf(MissionRecoveryInfo(video), MissionRecoveryInfo(sound)),
            format.suffix,
            format.mimeType,
            merger
        )
    }
}
