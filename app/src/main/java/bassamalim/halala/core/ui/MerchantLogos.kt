package bassamalim.halala.core.ui

import android.graphics.BitmapFactory
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import bassamalim.halala.core.data.repositories.LogosRepository
import bassamalim.halala.core.di.ApplicationScope
import bassamalim.halala.core.di.DefaultDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every merchant's logo, decoded once for the whole app and handed to avatars through
 * [LocalMerchantLogos], so rows only need to know their merchant.
 */
@Singleton
class MerchantLogos @Inject constructor(
    logos: LogosRepository,
    @ApplicationScope scope: CoroutineScope,
    @DefaultDispatcher decoding: CoroutineDispatcher
) {
    // ponytail: decodes them all again when one arrives; keep the decoded ones if that is ever slow.
    val images: StateFlow<Map<Long, ImageBitmap>> = logos.observeLogos()
        .map { files ->
            files.mapNotNull { (id, bytes) -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { id to it.asImageBitmap() } }.toMap()
        }
        .flowOn(decoding)
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())
}

/** Merchant id → its logo, for [bassamalim.halala.core.ui.components.Avatar]s. */
val LocalMerchantLogos = staticCompositionLocalOf<Map<Long, ImageBitmap>> { emptyMap() }
