package com.rork.recto

import android.app.Application
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.rork.recto.data.AuthRepository
import com.rork.recto.data.CloudSyncService
import com.rork.recto.data.EncryptionKeyRepository
import com.rork.recto.ui.screens.AppViewModel
import com.rork.recto.ui.screens.RectoViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.core.context.startKoin
import org.koin.dsl.module

val appModule = module {
    single {
        HttpClient(Android) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }
    single { AuthRepository(get(), get()) }
    single { CloudSyncService(get(), get()) }
    single { EncryptionKeyRepository(get(), get()) }
    viewModelOf(::AppViewModel)
    viewModelOf(::RectoViewModel)
}

class RectoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@RectoApplication)
            modules(appModule)
        }
        if (BuildConfig.REVENUECAT_API_KEY.isNotBlank()) {
            Purchases.configure(
                PurchasesConfiguration.Builder(this, BuildConfig.REVENUECAT_API_KEY).build()
            )
        }
    }
}
