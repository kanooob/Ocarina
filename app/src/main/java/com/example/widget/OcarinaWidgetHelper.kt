package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.RadioApp
import com.example.service.ArtworkRepository
import com.example.service.PlaybackStatus
import com.example.service.PlayerState
import com.example.service.RadioPlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object OcarinaWidgetHelper {

    private const val TAG = "OcarinaWidgetHelper"

    const val ACTION_TOGGLE = "fr.galaxie.ocarina.widget.ACTION_TOGGLE"
    const val ACTION_NEXT = "fr.galaxie.ocarina.widget.ACTION_NEXT"

    private const val REQUEST_CODE_TOGGLE_CIRCLE = 2001
    private const val REQUEST_CODE_OPEN_CIRCLE = 2002
    private const val REQUEST_CODE_TOGGLE_RECT = 2003
    private const val REQUEST_CODE_NEXT_RECT = 2004
    private const val REQUEST_CODE_OPEN_RECT = 2005

    private val widgetScope = CoroutineScope(Dispatchers.IO + Job())

    @Volatile
    private var lastKnownArtwork: Bitmap? = null

    @Volatile
    private var lastArtworkUrl: String? = null

    /**
     * Met à jour l'ensemble des widgets installés (Rond et Rectangle)
     */
    fun updateWidgets(
        context: Context,
        playerState: PlayerState? = null,
        artworkBitmap: Bitmap? = null
    ) {
        val appContext = context.applicationContext
        widgetScope.launch {
            try {
                val state = playerState ?: RadioPlaybackService.playerState.value
                val isPlaying = state.status is PlaybackStatus.Playing || state.status is PlaybackStatus.Buffering

                // Résolution de la station de secours si le service n'est pas encore démarré
                val fallbackStation = if (state.currentStation == null) {
                    try {
                        (appContext as? RadioApp)?.repository?.getSelectedStationSync()
                    } catch (e: Exception) {
                        null
                    }
                } else null

                // Résolution du Bitmap d'artwork
                val rawBitmap = when {
                    artworkBitmap != null -> {
                        lastKnownArtwork = artworkBitmap
                        lastArtworkUrl = state.artworkUrl
                        artworkBitmap
                    }
                    state.artworkUrl != null && state.artworkUrl == lastArtworkUrl && lastKnownArtwork != null -> {
                        lastKnownArtwork!!
                    }
                    !state.artworkUrl.isNullOrBlank() -> {
                        val downloaded = ArtworkRepository.fetchArtworkBitmap(state.artworkUrl)
                        if (downloaded != null) {
                            lastKnownArtwork = downloaded
                            lastArtworkUrl = state.artworkUrl
                            downloaded
                        } else {
                            getDefaultLogo(appContext)
                        }
                    }
                    else -> {
                        lastKnownArtwork = null
                        lastArtworkUrl = null
                        getDefaultLogo(appContext)
                    }
                }

                // Résolution propre des métadonnées (morceau ou station choisie)
                val rawTrackTitle = state.currentTrackTitle.trim()
                val stationName = state.currentStation?.name?.trim()
                    ?: fallbackStation?.name?.trim()
                    ?: appContext.getString(R.string.app_name)

                val streamUrl = state.currentStation?.streamUrl ?: fallbackStation?.streamUrl
                val hasMetadata = rawTrackTitle.isNotBlank() && 
                                  !rawTrackTitle.equals(stationName, ignoreCase = true) &&
                                  !com.example.service.IcyMetadataExtractor.isStreamPathOrFilename(rawTrackTitle, streamUrl)

                val appWidgetManager = AppWidgetManager.getInstance(appContext) ?: return@launch

                // 1. Mise à jour du widget Circulaire :
                // Quand pas de metadata -> nom choisi de la station (jamais la fin de l'url)
                val circleSingleLineText = if (hasMetadata) rawTrackTitle else stationName
                updateCircleWidgetsInternal(
                    appContext,
                    appWidgetManager,
                    rawBitmap,
                    circleSingleLineText
                )

                // 2. Mise à jour du widget Rectangle :
                // Quand pas de metadata -> titre : nom de station choisi, sous-titre : nom de l'application
                val rectTitle: String
                val rectSubtitle: String
                if (hasMetadata) {
                    rectTitle = rawTrackTitle
                    rectSubtitle = when {
                        state.currentTrackArtist.isNotBlank() -> state.currentTrackArtist.trim()
                        else -> stationName
                    }
                } else {
                    rectTitle = stationName
                    rectSubtitle = appContext.getString(R.string.app_name)
                }

                updateRectangleWidgetsInternal(
                    appContext,
                    appWidgetManager,
                    rawBitmap,
                    rectTitle,
                    rectSubtitle,
                    isPlaying
                )

            } catch (e: Exception) {
                Log.e(TAG, "Erreur lors de la mise à jour des widgets: ${e.message}", e)
            }
        }
    }

private fun updateCircleWidgetsInternal(
    context: Context,
    appWidgetManager: AppWidgetManager,
    artwork: Bitmap,
    singleLineText: String
) {
    val circleComponent = ComponentName(context, CircleRadioWidgetProvider::class.java)
    val appWidgetIds = appWidgetManager.getAppWidgetIds(circleComponent)
    if (appWidgetIds.isEmpty()) return

    val circularArtwork = createCircularBitmap(artwork)
    val views = RemoteViews(context.packageName, R.layout.widget_circle_layout).apply {
        setImageViewBitmap(R.id.widget_circle_artwork, circularArtwork)
        setTextViewText(R.id.widget_circle_title, singleLineText)

        // Clic sur le cercle central et l'artwork -> Toggle Play / Pause
        val toggleIntent = Intent(context, CircleRadioWidgetProvider::class.java).apply {
            action = ACTION_TOGGLE
        }
        val togglePendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_TOGGLE_CIRCLE,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setOnClickPendingIntent(R.id.widget_circle_button, togglePendingIntent)
        setOnClickPendingIntent(R.id.widget_circle_artwork, togglePendingIntent)

        // Clic sur le texte en dessous -> Ouvre MainActivity
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_OPEN_CIRCLE,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setOnClickPendingIntent(R.id.widget_circle_text_container, openPendingIntent)
        setOnClickPendingIntent(R.id.widget_circle_title, openPendingIntent)
    }

    for (id in appWidgetIds) {
        appWidgetManager.updateAppWidget(id, views)
    }
}

    private fun updateRectangleWidgetsInternal(
        context: Context,
        appWidgetManager: AppWidgetManager,
        artwork: Bitmap,
        title: String,
        subtitle: String,
        isPlaying: Boolean
    ) {
        val rectComponent = ComponentName(context, RectangleRadioWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(rectComponent)
        if (appWidgetIds.isEmpty()) return

        val dominantColor = extractDominantColor(artwork)
        val roundedArtwork = createRoundedBitmap(artwork, 24f)

        val views = RemoteViews(context.packageName, R.layout.widget_rectangle_layout).apply {
            // Teinte dynamique du fond rectangulaire
            setInt(R.id.widget_rect_background, "setColorFilter", dominantColor)

            // Pochette d'album arrondie
            setImageViewBitmap(R.id.widget_rect_artwork, roundedArtwork)

            // Textes
            setTextViewText(R.id.widget_rect_title, title)
            setTextViewText(R.id.widget_rect_artist, subtitle)

            // Bouton Play / Pause
            setImageViewResource(
                R.id.widget_rect_btn_play_pause,
                if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
            )

            // Actions de contrôle
            val toggleIntent = Intent(context, RectangleRadioWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE
            }
            val togglePendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_TOGGLE_RECT,
                toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setOnClickPendingIntent(R.id.widget_rect_btn_play_pause, togglePendingIntent)

            val nextIntent = Intent(context, RectangleRadioWidgetProvider::class.java).apply {
                action = ACTION_NEXT
            }
            val nextPendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_NEXT_RECT,
                nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setOnClickPendingIntent(R.id.widget_rect_btn_next, nextPendingIntent)

            // Clic sur l'en-tête (pochette + texte) -> Ouvre l'app
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPendingIntent = PendingIntent.getActivity(
                context,
                REQUEST_CODE_OPEN_RECT,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setOnClickPendingIntent(R.id.widget_rect_info_container, openPendingIntent)
        }

        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    /**
     * Extrait la couleur dominante d'un bitmap et ajuste sa luminosité
     * pour garantir un contraste optimal avec le texte clair.
     */
    fun extractDominantColor(bitmap: Bitmap): Int {
        return try {
            val sampleSize = 24
            val scaled = Bitmap.createScaledBitmap(bitmap, sampleSize, sampleSize, false)
            val pixels = IntArray(sampleSize * sampleSize)
            scaled.getPixels(pixels, 0, sampleSize, 0, 0, sampleSize, sampleSize)
            if (scaled != bitmap) scaled.recycle()

            var totalR = 0L
            var totalG = 0L
            var totalB = 0L
            var count = 0

            for (pixel in pixels) {
                val a = Color.alpha(pixel)
                if (a < 128) continue
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                val max = maxOf(r, maxOf(g, b))
                val min = minOf(r, minOf(g, b))
                val lum = (max + min) / 2

                // Élimine les noirs complets et blancs purs
                if (lum in 28..228) {
                    totalR += r
                    totalG += g
                    totalB += b
                    count++
                }
            }

            if (count == 0) return Color.parseColor("#1E2419") // Teinte Ocarina sombre par défaut

            val avgR = (totalR / count).toInt()
            val avgG = (totalG / count).toInt()
            val avgB = (totalB / count).toInt()

            val hsv = FloatArray(3)
            Color.RGBToHSV(avgR, avgG, avgB, hsv)
            // Harmonisation Material : saturation douce et fond sombre pour lisibilité texte blanc
            hsv[1] = (hsv[1] * 0.85f).coerceIn(0.25f, 0.65f)
            hsv[2] = hsv[2].coerceIn(0.12f, 0.24f)

            Color.HSVToColor(hsv)
        } catch (e: Exception) {
            Color.parseColor("#1E2419")
        }
    }

    /**
     * Découpe un bitmap en cercle parfait avec anti-aliasing
     */
    fun createCircularBitmap(src: Bitmap): Bitmap {
        return try {
            val size = minOf(src.width, src.height)
            val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            val left = (src.width - size) / 2
            val top = (src.height - size) / 2
            val cropped = if (src.width != size || src.height != size) {
                Bitmap.createBitmap(src, left, top, size, size)
            } else src

            paint.shader = BitmapShader(cropped, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val radius = size / 2f
            canvas.drawCircle(radius, radius, radius, paint)
            if (cropped != src && cropped != output) cropped.recycle()
            output
        } catch (e: Exception) {
            src
        }
    }

    /**
     * Découpe un bitmap avec coins arrondis
     */
    fun createRoundedBitmap(src: Bitmap, cornerRadiusPx: Float): Bitmap {
        return try {
            val size = minOf(src.width, src.height)
            val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            val left = (src.width - size) / 2
            val top = (src.height - size) / 2
            val cropped = if (src.width != size || src.height != size) {
                Bitmap.createBitmap(src, left, top, size, size)
            } else src

            paint.shader = BitmapShader(cropped, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val rectF = RectF(0f, 0f, size.toFloat(), size.toFloat())
            canvas.drawRoundRect(rectF, cornerRadiusPx, cornerRadiusPx, paint)
            if (cropped != src && cropped != output) cropped.recycle()
            output
        } catch (e: Exception) {
            src
        }
    }

    private fun getDefaultLogo(context: Context): Bitmap {
        return BitmapFactory.decodeResource(context.resources, R.drawable.ic_ocarina_logo)
    }
}
