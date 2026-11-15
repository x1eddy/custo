package app.custo.ui.components

import android.os.Process
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Where software preview renders (set previews, wallpaper thumbnails) run: two background-priority
 * threads. On Dispatchers.Default a gallery's worth of renders ran on every core at normal priority
 * and starved the UI and render threads, so scrolling stuttered until they finished.
 */
val PreviewRender: CoroutineDispatcher by lazy {
    val n = AtomicInteger()
    Executors.newFixedThreadPool(2) { r ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            r.run()
        }, "custo-preview-${n.incrementAndGet()}").apply { isDaemon = true }
    }.asCoroutineDispatcher()
}
