package com.hamishnotes.app

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Google Play Billing: منتج اشتراك واحد (سنوي) مع عرض تجربة مجانية.
 * التطبيق لا يقرر وحده أن الشراء صحيح: يرسل رمز الشراء إلى الخادم الذي يسأل Google ويصدر القسيمة.
 */
internal object PlayBilling {
    private var client: BillingClient? = null
    private var details: ProductDetails? = null

    /** السعر كما تعرضه Google Play بعملة المستخدم (مثل "US$4.00")؛ null قبل التحميل. */
    var price by mutableStateOf<String?>(null)
        private set
    /** هل التجربة المجانية متاحة لهذا الحساب (لا تُعرض لمن جرّبها من قبل). */
    var trialAvailable by mutableStateOf(true)
        private set
    var busy by mutableStateOf(false)
        private set

    fun init(context: Context) {
        if (client != null) return
        client = BillingClient.newBuilder(context.applicationContext)
            .setListener { result, purchases -> onPurchases(result, purchases) }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
    }

    private suspend fun ready(): BillingClient? {
        val c = client ?: return null
        if (c.isReady) return c
        val ok = suspendCancellableCoroutine { k ->
            c.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (k.isActive) k.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                }

                override fun onBillingServiceDisconnected() {
                    if (k.isActive) k.resume(false)
                }
            })
        }
        return if (ok) c else null
    }

    private suspend fun product(): ProductDetails? {
        details?.let { return it }
        val c = ready() ?: return null
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(BuildConfig.PREMIUM_PRODUCT)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            )
        ).build()
        val d = suspendCancellableCoroutine { k ->
            c.queryProductDetailsAsync(params) { _, result -> if (k.isActive) k.resume(result.productDetailsList.firstOrNull()) }
        }
        if (d != null) {
            details = d
            val offers = d.subscriptionOfferDetails.orEmpty()
            trialAvailable = offers.any { o -> o.pricingPhases.pricingPhaseList.any { it.priceAmountMicros == 0L } }
            price = offers.flatMap { it.pricingPhases.pricingPhaseList }.lastOrNull { it.priceAmountMicros > 0 }?.formattedPrice
        }
        return d
    }

    /** يحمّل السعر لعرضه في شاشة الاشتراك. */
    suspend fun load() {
        product()
    }

    /** يفتح نافذة الدفع من Google. الحساب مربوط بمعرّف المستخدم في Supabase. */
    suspend fun buy(activity: Activity, userId: String): Boolean {
        val c = ready() ?: return false
        val d = product() ?: return false
        val offers = d.subscriptionOfferDetails.orEmpty()
        // Google تعيد فقط العروض التي يحق لهذا الحساب استخدامها: نختار التجربة إن وُجدت، وإلا الخطة الأساسية
        val offer = offers.firstOrNull { o -> o.pricingPhases.pricingPhaseList.any { it.priceAmountMicros == 0L } }
            ?: offers.firstOrNull { it.offerId == null }
            ?: offers.firstOrNull()
            ?: return false
        val flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).setOfferToken(offer.offerToken).build())
            )
            .setObfuscatedAccountId(userId)
            .build()
        return c.launchBillingFlow(activity, flow).responseCode == BillingClient.BillingResponseCode.OK
    }

    /** الاشتراكات الفعّالة في حساب Google على هذا الجهاز (للاستعادة أو بعد إعادة التثبيت). */
    suspend fun owned(): List<Purchase> {
        val c = ready() ?: return emptyList()
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        return suspendCancellableCoroutine { k ->
            c.queryPurchasesAsync(params) { result, list ->
                if (k.isActive) k.resume(
                    if (result.responseCode == BillingClient.BillingResponseCode.OK)
                        list.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && BuildConfig.PREMIUM_PRODUCT in it.products }
                    else emptyList()
                )
            }
        }
    }

    private fun onPurchases(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases.orEmpty()
                .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                .forEach { p ->
                    busy = true
                    Premium.submitPurchase(p.purchaseToken) { busy = false }
                }
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> Premium.restore(quiet = false)
            else -> AppController.say(MsgKind.ERROR, L("تعذّر إتمام الشراء، حاول مرة أخرى", "Purchase couldn’t be completed, please try again"))
        }
    }
}
