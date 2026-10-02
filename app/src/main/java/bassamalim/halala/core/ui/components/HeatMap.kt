package bassamalim.halala.core.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import bassamalim.halala.BuildConfig
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.Sizes
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.TilesOverlay
import java.io.File

/** A point of heat: where, and its weight from 0 to 1. */
data class HeatPoint(val latitude: Double, val longitude: Double, val weight: Float)

/**
 * The spending map: OpenStreetMap tiles (inverted, for the dark theme) under a heat layer of
 * [points]. Fits to them when they change, or centres on [focus] when set. Tiles come from
 * OpenStreetMap's servers and are cached on the phone; nothing about the points is sent.
 */
@Composable
fun HeatMap(points: List<HeatPoint>, focus: Pair<Double, Double>?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val radius = with(LocalDensity.current) { Sizes.touchTarget.toPx() }
    val heat = remember { HeatOverlay(HalalaColors.Accent, radius) }
    val map = remember { mapView(context, heat) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            map.onDetach()
        }
    }

    AndroidView(
        factory = { map },
        modifier = modifier,
        update = { view ->
            val changed = heat.points != points
            heat.points = points
            when {
                focus != null -> {
                    view.controller.setZoom(FOCUS_ZOOM)
                    view.controller.animateTo(GeoPoint(focus.first, focus.second))
                }
                changed && points.size == 1 -> {
                    view.controller.setZoom(FOCUS_ZOOM)
                    view.controller.setCenter(GeoPoint(points[0].latitude, points[0].longitude))
                }
                changed && points.size > 1 -> view.post {
                    val box = BoundingBox.fromGeoPointsSafe(points.map { GeoPoint(it.latitude, it.longitude) })
                    view.zoomToBoundingBox(box.increaseByScale(PADDING_SCALE), false)
                }
            }
            view.invalidate()
        }
    )
}

private fun mapView(context: Context, heat: HeatOverlay): MapView {
    Configuration.getInstance().apply {
        userAgentValue = BuildConfig.APPLICATION_ID
        osmdroidBasePath = File(context.cacheDir, "osmdroid")
        osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles")
    }
    return MapView(context).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
        minZoomLevel = MIN_ZOOM
        controller.setZoom(START_ZOOM)
        // Riyadh until there is something to show.
        controller.setCenter(GeoPoint(24.7136, 46.6753))
        overlays.add(heat)
    }
}

/** Soft circles that add up where purchases gather. */
private class HeatOverlay(color: Color, private val radius: Float) : Overlay() {
    var points: List<HeatPoint> = emptyList()
    private val argb = color.toArgb()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val at = Point()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val projection = mapView.projection
        for (p in points) {
            projection.toPixels(GeoPoint(p.latitude, p.longitude), at)
            val alpha = (MIN_ALPHA + (MAX_ALPHA - MIN_ALPHA) * p.weight).toInt()
            val centre = (argb and 0x00FFFFFF) or (alpha shl 24)
            paint.shader = RadialGradient(at.x.toFloat(), at.y.toFloat(), radius, centre, argb and 0x00FFFFFF, Shader.TileMode.CLAMP)
            canvas.drawCircle(at.x.toFloat(), at.y.toFloat(), radius, paint)
        }
    }

    private companion object {
        const val MIN_ALPHA = 70
        const val MAX_ALPHA = 220
    }
}

private const val START_ZOOM = 11.0
private const val FOCUS_ZOOM = 16.0
private const val MIN_ZOOM = 3.0
private const val PADDING_SCALE = 1.4f
