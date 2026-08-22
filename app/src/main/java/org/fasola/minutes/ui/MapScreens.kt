package org.fasola.minutes.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RadialGradient
import android.graphics.Shader
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import org.fasola.minutes.R
import org.fasola.minutes.data.MapLocation
import org.fasola.minutes.data.MinutesRepository
import org.fasola.minutes.data.SingingSummary
import org.fasola.minutes.data.SongHeatPoint
import org.fasola.minutes.ui.theme.BookColor
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import kotlin.math.abs

@Composable
internal fun LocationsMapScreen(
    repository: MinutesRepository,
    title: String,
    back: () -> Unit,
    openSinging: (Int) -> Unit,
    singingId: Int? = null,
    leaderId: Int? = null,
) {
    val locations = load(singingId to leaderId) { repository.locations(singingId, leaderId) }
    var selected by remember { mutableStateOf<MapLocation?>(null) }
    val singings = load(selected?.id to leaderId) {
        selected?.let { repository.singingsAtLocation(it.id, leaderId) } ?: emptyList()
    }
    DetailScaffold(title, back) {
        val points = locations.value
        if (points == null) Loading()
        else if (points.isEmpty()) EmptyMapMessage("No mapped locations found")
        else Box(Modifier.fillMaxSize()) {
            OsmMap(points, onSelect = { selected = it })
            selected?.let { location ->
                LocationCard(
                    location = location,
                    singings = singings.value.orEmpty(),
                    openSinging = openSinging,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
internal fun SongHeatmapScreen(
    repository: MinutesRepository,
    songId: Int,
    title: String,
    back: () -> Unit,
) {
    val points = load(songId) { repository.songHeatPoints(songId) }
    DetailScaffold(title, back) {
        val data = points.value
        if (data == null) Loading()
        else if (data.isEmpty()) EmptyMapMessage("No mapped lessons found for this song")
        else Box(Modifier.fillMaxSize()) {
            HeatMap(data)
            Surface(
                Modifier.align(Alignment.BottomCenter).padding(12.dp),
                shadowElevation = 6.dp,
                color = MaterialTheme.colorScheme.surface.copy(alpha = .92f),
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text("Relative popularity", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("● Less", color = androidx.compose.ui.graphics.Color(0xff2878c8))
                        Text("● Average", color = androidx.compose.ui.graphics.Color(0xff7b6699))
                        Text("● More", color = androidx.compose.ui.graphics.Color(0xffd43d3d))
                    }
                }
            }
        }
    }
}

@Composable
private fun OsmMap(locations: List<MapLocation>, onSelect: (MapLocation) -> Unit) {
    ManagedMapView { map ->
        locations.forEach { location ->
            map.overlays.add(Marker(map).apply {
                position = GeoPoint(location.latitude, location.longitude)
                title = location.name
                icon = ContextCompat.getDrawable(map.context, R.drawable.map_pin)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                setOnMarkerClickListener { marker, _ ->
                    onSelect(location)
                    marker.showInfoWindow()
                    map.controller.animateTo(marker.position)
                    true
                }
            })
        }
        zoomToLocations(map, locations)
    }
}

@Composable
private fun HeatMap(points: List<SongHeatPoint>) {
    ManagedMapView { map ->
        map.overlays.add(RelativeHeatmapOverlay(points))
        map.controller.setZoom(5.25)
        map.controller.setCenter(GeoPoint(37.554926, -82.605648))
    }
}

/** Draws overlapping radial gradients instead of discrete geographic circles. */
private class RelativeHeatmapOverlay(points: List<SongHeatPoint>) : Overlay() {
    private val scale = points.maxOf { abs(it.relativePopularity) }.coerceAtLeast(.01)
    private val points = points.sortedBy { abs(it.relativePopularity) }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val screenPoint = Point()

    override fun draw(canvas: Canvas, map: MapView, shadow: Boolean) {
        if (shadow) return
        val radius = (map.context.resources.displayMetrics.density * 44f).coerceAtMost(canvas.width * .13f)
        val bounds = map.boundingBox
        points.forEach { point ->
            val location = point.location
            if (!bounds.contains(location.latitude, location.longitude)) return@forEach
            map.projection.toPixels(GeoPoint(location.latitude, location.longitude), screenPoint)
            val strength = (abs(point.relativePopularity) / scale).coerceIn(.08, 1.0).toFloat()
            val base = if (point.relativePopularity >= 0) RED else BLUE
            val centerAlpha = (12 + 38 * strength).toInt()
            paint.shader = RadialGradient(
                screenPoint.x.toFloat(),
                screenPoint.y.toFloat(),
                radius * (.72f + .28f * strength),
                intArrayOf(
                    withAlpha(base, centerAlpha),
                    withAlpha(base, centerAlpha / 2),
                    Color.TRANSPARENT,
                ),
                floatArrayOf(0f, .38f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(screenPoint.x.toFloat(), screenPoint.y.toFloat(), radius, paint)
        }
        paint.shader = null
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private companion object {
        val RED: Int = Color.rgb(212, 61, 61)
        val BLUE: Int = Color.rgb(40, 120, 200)
    }
}

@Composable
private fun ManagedMapView(configure: (MapView) -> Unit) {
    val context = LocalContext.current
    val map = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isVerticalMapRepetitionEnabled = false
            minZoomLevel = 2.0
            maxZoomLevel = 19.0
            configure(this)
        }
    }
    DisposableEffect(map) {
        map.onResume()
        onDispose {
            map.onPause()
            map.onDetach()
        }
    }
    AndroidView(factory = { map }, modifier = Modifier.fillMaxSize())
}

private fun zoomToLocations(map: MapView, locations: List<MapLocation>) {
    if (locations.size == 1) {
        map.controller.setZoom(8.0)
        map.controller.setCenter(GeoPoint(locations.first().latitude, locations.first().longitude))
        return
    }
    val box = BoundingBox.fromGeoPoints(locations.map { GeoPoint(it.latitude, it.longitude) })
    map.post { map.zoomToBoundingBox(box, false, 72) }
}

@Composable
private fun LocationCard(
    location: MapLocation,
    singings: List<SingingSummary>,
    openSinging: (Int) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    Surface(modifier.fillMaxWidth().padding(10.dp), shadowElevation = 10.dp) {
        Column(Modifier.heightIn(max = 310.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 6.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(location.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = {
                    val googleNavigation = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("google.navigation:q=${location.latitude},${location.longitude}"),
                    )
                    try {
                        context.startActivity(googleNavigation)
                    } catch (_: ActivityNotFoundException) {
                        val geo = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(
                                "geo:${location.latitude},${location.longitude}" +
                                    "?q=${location.latitude},${location.longitude}(${Uri.encode(location.name)})",
                            ),
                        )
                        try {
                            context.startActivity(geo)
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(context, "No maps application found", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Icon(Icons.Default.Directions, "Directions") }
            }
            HorizontalDivider()
            LazyColumn {
                items(singings, key = { it.id }) { singing ->
                    Row(
                        Modifier.fillMaxWidth().clickable { openSinging(singing.id) }.padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(singing.name, style = MaterialTheme.typography.bodyMedium)
                            Text("${singing.date}\n${singing.location}", style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun EmptyMapMessage(message: String) = Box(
    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    contentAlignment = Alignment.Center,
) { Text(message, color = BookColor) }
