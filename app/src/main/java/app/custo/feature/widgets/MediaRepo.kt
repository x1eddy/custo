package app.custo.feature.widgets

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.core.graphics.scale
import app.custo.feature.overlays.CustoNotificationListener

data class NowPlaying(val title: String, val artist: String, val art: Bitmap?, val playing: Boolean, val pkg: String)

/** Reads active media sessions. Requires Custo's notification listener to be enabled. */
object MediaRepo {

    fun controller(context: Context): MediaController? = try {
        val msm = context.getSystemService(MediaSessionManager::class.java)
        val sessions = msm.getActiveSessions(ComponentName(context, CustoNotificationListener::class.java))
        sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: sessions.firstOrNull()
    } catch (_: SecurityException) {
        null
    }

    fun nowPlaying(context: Context): NowPlaying? {
        val c = controller(context) ?: return null
        val md = c.metadata ?: return null
        val art = (md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART))
            ?.let { if (it.width > 256) it.scale(256, 256 * it.height / it.width) else it }
        return NowPlaying(
            title = md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "",
            artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: "",
            art = art,
            playing = c.playbackState?.state == PlaybackState.STATE_PLAYING,
            pkg = c.packageName,
        )
    }

    fun command(context: Context, cmd: String) {
        val t = controller(context)?.transportControls ?: return
        when (cmd) {
            "prev" -> t.skipToPrevious()
            "next" -> t.skipToNext()
            "toggle" -> if (controller(context)?.playbackState?.state == PlaybackState.STATE_PLAYING) t.pause() else t.play()
        }
    }
}
