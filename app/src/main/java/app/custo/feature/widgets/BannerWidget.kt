package app.custo.feature.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxWidth

/** A short greeting in big letters ("Good luck", "Merry Christmas"). */
class BannerWidget : CustoWidget() {
    @Composable
    override fun Content(context: Context, id: GlanceId) {
        val style = currentStyle()
        Frame(style, tapStart(editIntent(context, id))) {
            Column(GlanceModifier.fillMaxWidth(), horizontalAlignment = style.hAlign.glance()) {
                WText(style.bannerText, style = style.text(28f, bold = true), modifier = GlanceModifier.fillMaxWidth(), maxLines = 2)
                if (style.bannerSub.isNotBlank()) {
                    WText(style.bannerSub, style = style.text(13f, alpha = 0.8f), modifier = GlanceModifier.fillMaxWidth(), maxLines = 1)
                }
            }
        }
    }
}

class BannerReceiver : CustoReceiver() { override val glanceAppWidget: GlanceAppWidget = BannerWidget() }
