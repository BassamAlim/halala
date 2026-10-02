package bassamalim.halala.features.editAsset

import bassamalim.halala.core.Globals
import bassamalim.halala.core.data.dataSources.room.entities.Asset
import bassamalim.halala.core.data.repositories.AssetsRepository
import bassamalim.halala.core.domain.Assets
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.enums.AssetType
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import bassamalim.halala.core.prices.GoldPrice
import bassamalim.halala.core.prices.ListedFund
import bassamalim.halala.core.prices.PriceProtocol
import bassamalim.halala.core.prices.Prices
import javax.inject.Inject

/** An asset as the form holds it: text as typed. */
data class AssetForm(
    val type: AssetType = AssetType.FUND,
    val name: String = "",
    val quantity: String = "",
    val karat: Int = 21,
    val unitPrice: String = "",
    val priceDate: LocalDate? = null,
    val value: String = "",
    val cost: String = "",
    val spread: String = "",
    val depreciation: String = "",
    val currency: String = Globals.PRIMARY_CURRENCY,
    val uid: String = "",
    val createdAt: Instant = Instant.EPOCH,
    /** Where its price is fetched from, when it is: see [PriceProtocol]. */
    val priceSource: String? = null
)

enum class AssetProblem { NameMissing, QuantityInvalid, PriceInvalid, ValueInvalid, CostInvalid, PercentInvalid }

class EditAssetDomain @Inject constructor(
    private val assetsRepository: AssetsRepository,
    private val prices: Prices,
    private val clock: Clock
) {

    /** Every listed fund, to link one; null when the list can't be had (offline). */
    suspend fun funds(): List<ListedFund>? = prices.funds()

    suspend fun gold(): GoldPrice? = prices.gold()

    fun today(): LocalDate = LocalDate.now(clock)

    suspend fun load(id: Long): AssetForm? = assetsRepository.get(id)?.let {
        AssetForm(
            type = it.type,
            name = it.name,
            quantity = if (Money.masked) "" else it.quantity.orEmpty(),
            karat = it.karat ?: 21,
            unitPrice = it.unitPrice.orEmpty(),
            priceDate = it.priceDate,
            value = it.valueMinor?.let { minor -> Money.input(minor, it.currency) }.orEmpty(),
            cost = it.costMinor?.let { minor -> Money.input(minor, it.currency) }.orEmpty(),
            spread = it.spreadPercent.orEmpty(),
            depreciation = it.depreciationPercent.orEmpty(),
            currency = it.currency,
            uid = it.uid,
            createdAt = it.createdAt,
            priceSource = it.priceSource
        )
    }

    /** Checks and writes, then takes today's snapshot so net worth moves with it. */
    suspend fun save(id: Long, form: AssetForm): Set<AssetProblem> {
        val (asset, problems) = validate(id, form, today())
        if (asset != null) {
            assetsRepository.save(asset)
            assetsRepository.snapshot(form.currency)
            if (asset.priceSource != null) prices.refreshSoon()
        }
        return problems
    }

    suspend fun delete(id: Long, currency: String) {
        assetsRepository.delete(id)
        assetsRepository.snapshot(currency)
    }

    companion object {

        fun validate(id: Long, form: AssetForm, today: LocalDate): Pair<Asset?, Set<AssetProblem>> {
            val problems = mutableSetOf<AssetProblem>()
            if (form.name.isBlank()) problems += AssetProblem.NameMissing
            val priced = form.type == AssetType.FUND || form.type == AssetType.GOLD
            val quantity = Assets.decimal(form.quantity)?.takeIf { it.signum() > 0 }
            val price = Assets.decimal(form.unitPrice)?.takeIf { it.signum() > 0 }
            val value = Money.parse(form.value, form.currency)
            if (priced && quantity == null) problems += AssetProblem.QuantityInvalid
            if (priced && price == null) problems += AssetProblem.PriceInvalid
            if (!priced && value == null) problems += AssetProblem.ValueInvalid
            val cost = form.cost.takeIf { it.isNotBlank() }?.let { Money.parse(it, form.currency) ?: -1 }
            if (cost == -1L) problems += AssetProblem.CostInvalid
            val percent = if (form.type == AssetType.GOLD) form.spread else form.depreciation
            val parsedPercent = percent.takeIf { it.isNotBlank() }?.let { Assets.decimal(it) }
            if (percent.isNotBlank() && (parsedPercent == null || parsedPercent > BigDecimal(100))) problems += AssetProblem.PercentInvalid
            if (problems.isNotEmpty()) return null to problems

            return Asset(
                id = id,
                uid = form.uid,
                type = form.type,
                name = form.name.trim(),
                quantity = quantity?.stripTrailingZeros()?.toPlainString().takeIf { priced },
                karat = form.karat.takeIf { form.type == AssetType.GOLD },
                unitPrice = price?.stripTrailingZeros()?.toPlainString().takeIf { priced },
                // A price (or a value) is as of the day you give it, today unless you say.
                priceDate = form.priceDate ?: today,
                valueMinor = value.takeIf { !priced },
                costMinor = cost,
                spreadPercent = parsedPercent?.toPlainString().takeIf { form.type == AssetType.GOLD },
                depreciationPercent = parsedPercent?.toPlainString().takeIf { !priced },
                currency = form.currency,
                createdAt = form.createdAt,
                priceSource = form.priceSource.takeIf {
                    (form.type == AssetType.GOLD && it == PriceProtocol.GOLD_SOURCE) ||
                        (form.type == AssetType.FUND && PriceProtocol.fundIdOf(it) != null)
                }
            ) to emptySet()
        }
    }
}
