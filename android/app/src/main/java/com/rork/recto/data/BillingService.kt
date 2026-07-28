package com.rork.recto.data

import android.app.Activity
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.getCustomerInfoWith
import com.revenuecat.purchases.getOfferingsWith
import com.revenuecat.purchases.logInWith
import com.revenuecat.purchases.logOutWith
import com.revenuecat.purchases.purchaseWith
import com.revenuecat.purchases.restorePurchasesWith
import com.rork.recto.BuildConfig

class BillingService {
    val isConfigured: Boolean get() = BuildConfig.REVENUECAT_API_KEY.isNotBlank()

    fun refresh(onResult: (Boolean, String?) -> Unit) {
        if (!isConfigured) {
            onResult(false, "Subscriptions are still being configured")
            return
        }
        Purchases.sharedInstance.getCustomerInfoWith(
            onError = { onResult(false, it.message) },
            onSuccess = { onResult(it.hasProAccess(), null) }
        )
    }

    fun loadPackages(onResult: (List<Package>, String?) -> Unit) {
        if (!isConfigured) {
            onResult(emptyList(), "Subscriptions are still being configured")
            return
        }
        Purchases.sharedInstance.getOfferingsWith(
            onError = { onResult(emptyList(), it.message) },
            onSuccess = { offerings -> onResult(offerings.current?.availablePackages.orEmpty(), null) }
        )
    }

    fun purchase(activity: Activity, selectedPackage: Package, onResult: (Boolean, String?) -> Unit) {
        Purchases.sharedInstance.purchaseWith(
            purchaseParams = PurchaseParams.Builder(activity, selectedPackage).build(),
            onError = { error, userCancelled ->
                onResult(false, if (userCancelled) null else error.message)
            },
            onSuccess = { _, customerInfo -> onResult(customerInfo.hasProAccess(), null) }
        )
    }

    fun restore(onResult: (Boolean, String?) -> Unit) {
        if (!isConfigured) {
            onResult(false, "Subscriptions are still being configured")
            return
        }
        Purchases.sharedInstance.restorePurchasesWith(
            onError = { onResult(false, it.message) },
            onSuccess = { onResult(it.hasProAccess(), if (it.hasProAccess()) null else "No active subscription was found") }
        )
    }

    fun identify(userId: String) {
        if (isConfigured) Purchases.sharedInstance.logInWith(userId, onError = {}, onSuccess = { _, _ -> })
    }

    fun logOut() {
        if (isConfigured) Purchases.sharedInstance.logOutWith(onError = {}, onSuccess = {})
    }

    private fun CustomerInfo.hasProAccess(): Boolean = entitlements["pro"]?.isActive == true
}
