package bassamalim.halala.core.places

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import bassamalim.halala.core.data.dataSources.room.entities.TransactionPlace
import bassamalim.halala.core.data.repositories.PlacesRepository
import bassamalim.halala.core.data.repositories.TransactionsRepository
import bassamalim.halala.core.domain.Places
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** What location Halala has: all of it, only while open, none, or none because location is off. */
enum class LocationAccess { GRANTED, FOREGROUND_ONLY, DENIED, SERVICES_OFF }

/**
 * Where you spend: when a purchase's SMS arrives, where the phone is, whenever location is
 * allowed all the time. Android's own location (no Play services); kept only in the encrypted
 * ledger, never sent anywhere.
 */
@Singleton
class PlaceCapture @Inject constructor(
    private val application: Application,
    private val places: PlacesRepository,
    private val transactions: TransactionsRepository,
    private val clock: Clock
) {

    /** Needs location in the background too: the SMS arrives while the app is closed. */
    fun access(): LocationAccess {
        fun granted(permission: String) = ContextCompat.checkSelfPermission(application, permission) == PackageManager.PERMISSION_GRANTED
        val location = granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)
        val manager = application.getSystemService(LocationManager::class.java)
        return when {
            !location -> LocationAccess.DENIED
            !granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION) -> LocationAccess.FOREGROUND_ONLY
            manager == null || !manager.isLocationEnabled -> LocationAccess.SERVICES_OFF
            else -> LocationAccess.GRANTED
        }
    }

    /** Gives a place to the purchases this SMS run recorded since [since], if any just happened. */
    suspend fun captureFor(since: Instant) {
        if (access() != LocationAccess.GRANTED) return
        val placed = places.getAll().map { it.transactionId }.toSet()
        val fresh = Places.needingPlace(transactions.getAll(), placed, since, clock.instant())
        if (fresh.isEmpty()) return
        val here = runCatching { current() }.getOrNull()?.takeIf { it.accuracy <= Places.MAX_ACCURACY_METERS } ?: return
        places.add(fresh.map { TransactionPlace.of(it.id, here.latitude, here.longitude, here.accuracy) })
    }

    @SuppressLint("MissingPermission") // Checked in captureFor.
    private suspend fun current(): Location? {
        val manager = application.getSystemService(LocationManager::class.java) ?: return null
        val provider = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && manager.hasProvider(LocationManager.FUSED_PROVIDER) -> LocationManager.FUSED_PROVIDER
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> return null
        }
        val fresh = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) withTimeoutOrNull(TIMEOUT_MILLIS) {
            suspendCancellableCoroutine<Location?> { continuation ->
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                manager.getCurrentLocation(provider, signal, application.mainExecutor) { continuation.resume(it) }
            }
        } else null
        // Android 10 has no one-off request: a fix from the last few minutes will do.
        return fresh ?: manager.getLastKnownLocation(provider)?.takeIf {
            clock.millis() - it.time <= RECENT_MILLIS
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 20_000L
        const val RECENT_MILLIS = 5 * 60_000L
    }
}
