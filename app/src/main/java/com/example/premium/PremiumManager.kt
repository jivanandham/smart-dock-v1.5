package com.example.premium

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Premium entitlement via Google Play Billing.
 *
 * Create these products in Play Console (Monetize › Products) with the same IDs:
 *  - Subscription "premium" with base plans "monthly" and "yearly"
 *  - One-time product "premium_lifetime"
 *
 * The free version keeps the clock, reliable alarms, one companion, calendar, weather and the
 * basic wellness reminders. Premium unlocks the extras listed in [PremiumFeature].
 */
object PremiumManager : PurchasesUpdatedListener {
    private const val TAG = "PremiumManager"
    const val SUB_ID = "premium"
    const val LIFETIME_ID = "premium_lifetime"
    private const val PREFS = "premium_state"
    private const val KEY_CACHED = "cached_entitlement"
    private const val KEY_DEBUG = "debug_unlock"

    enum class Plan { MONTHLY, YEARLY, LIFETIME }

    data class Prices(val monthly: String? = null, val yearly: String? = null, val lifetime: String? = null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var appContext: Context
    private var client: BillingClient? = null
    private var subDetails: ProductDetails? = null
    private var lifetimeDetails: ProductDetails? = null

    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val _prices = MutableStateFlow(Prices())
    val prices: StateFlow<Prices> = _prices.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Cached value so Premium works offline right after launch.
        _isPremium.value = prefs.getBoolean(KEY_CACHED, false) || (BuildConfig.DEBUG && prefs.getBoolean(KEY_DEBUG, false))
        try {
            client = BillingClient.newBuilder(appContext)
                .setListener(this)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .build()
            connect()
        } catch (e: Exception) {
            Log.w(TAG, "Billing unavailable", e)
        }
    }

    private fun connect() {
        client?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    loadProducts()
                    refreshPurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                // Reconnect lazily on the next action.
            }
        })
    }

    private fun loadProducts() {
        val c = client ?: return
        val products = listOf(
            QueryProductDetailsParams.Product.newBuilder().setProductId(SUB_ID).setProductType(BillingClient.ProductType.SUBS).build(),
            QueryProductDetailsParams.Product.newBuilder().setProductId(LIFETIME_ID).setProductType(BillingClient.ProductType.INAPP).build()
        )
        // Query separately: Play requires one product type per request.
        products.forEach { product ->
            c.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()) { _, list ->
                list.forEach { d ->
                    if (d.productId == SUB_ID) subDetails = d else if (d.productId == LIFETIME_ID) lifetimeDetails = d
                }
                updatePrices()
            }
        }
    }

    private fun basePlanOffer(planId: String) =
        subDetails?.subscriptionOfferDetails?.firstOrNull { it.basePlanId == planId && it.offerId == null }
            ?: subDetails?.subscriptionOfferDetails?.firstOrNull { it.basePlanId == planId }

    private fun updatePrices() {
        _prices.value = Prices(
            monthly = basePlanOffer("monthly")?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice,
            yearly = basePlanOffer("yearly")?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice,
            lifetime = lifetimeDetails?.oneTimePurchaseOfferDetails?.formattedPrice
        )
    }

    fun refreshPurchases() {
        val c = client ?: return
        var owned = false
        var pending = 2
        fun done() {
            pending--
            if (pending == 0) setPremium(owned || (BuildConfig.DEBUG && debugUnlocked()))
        }
        listOf(BillingClient.ProductType.SUBS, BillingClient.ProductType.INAPP).forEach { type ->
            c.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(type).build()) { result, purchases ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    purchases.forEach { p ->
                        if (p.purchaseState == Purchase.PurchaseState.PURCHASED) {
                            owned = true
                            acknowledge(p)
                        }
                    }
                } else {
                    owned = owned || _isPremium.value // keep cached value if Play is unreachable
                }
                done()
            }
        }
    }

    fun launchPurchase(activity: Activity, plan: Plan) {
        val c = client
        if (c == null || !c.isReady) {
            _message.value = "Google Play isn't available right now. Try again in a moment."
            connect()
            return
        }
        val params = when (plan) {
            Plan.LIFETIME -> lifetimeDetails?.let {
                BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(it).build()
            }
            Plan.MONTHLY, Plan.YEARLY -> {
                val offer = basePlanOffer(if (plan == Plan.MONTHLY) "monthly" else "yearly")
                val details = subDetails
                if (offer != null && details != null) {
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offer.offerToken)
                        .build()
                } else {
                    null
                }
            }
        }
        if (params == null) {
            _message.value = "This plan isn't available yet. (Create the products in Play Console.)"
            return
        }
        c.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(params)).build())
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { p ->
                    if (p.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        acknowledge(p)
                        setPremium(true)
                        _message.value = "Thank you! Premium is unlocked."
                    } else if (p.purchaseState == Purchase.PurchaseState.PENDING) {
                        _message.value = "Payment pending. Premium unlocks when it completes."
                    }
                }
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                setPremium(true)
                _message.value = "You already own Premium."
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> _message.value = "Purchase didn't complete (${result.debugMessage.ifBlank { "error ${result.responseCode}" }})."
        }
    }

    private fun acknowledge(p: Purchase) {
        if (p.isAcknowledged) return
        client?.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) { r ->
            if (r.responseCode != BillingClient.BillingResponseCode.OK) Log.w(TAG, "Acknowledge failed: ${r.debugMessage}")
        }
    }

    private fun setPremium(value: Boolean) {
        _isPremium.value = value
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_CACHED, value).apply()
    }

    fun clearMessage() {
        _message.value = null
    }

    // --- Debug builds only: unlock without paying, to test Premium features ---
    private fun debugUnlocked() = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DEBUG, false)

    fun setDebugUnlock(enabled: Boolean) {
        if (!BuildConfig.DEBUG) return
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_DEBUG, enabled).apply()
        _isPremium.value = enabled || appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_CACHED, false)
    }

    fun restore() {
        if (client?.isReady == true) {
            refreshPurchases()
            _message.value = "Checking your purchases…"
        } else {
            connect()
        }
    }
}

/** What Premium unlocks (shown on the paywall). */
enum class PremiumFeature(val label: String) {
    COATS("All companion coats"),
    PET_TWIN("Pet twin: turn your own pet into your companion"),
    SCREENSAVER("Companion screensaver while charging"),
    SOUNDS("Sleep and focus sounds"),
    SPOKEN_BRIEFING("Morning briefing read aloud"),
    GUIDED("Guided stretch and breathing sessions")
}
