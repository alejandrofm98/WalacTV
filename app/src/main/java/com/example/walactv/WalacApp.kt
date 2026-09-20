package com.example.walactv

import android.app.Application
import com.example.walactv.data.preferences.CredentialStore
import com.example.walactv.data.remote.api.AuthInterceptor
import com.example.walactv.di.AppComponent
import com.example.walactv.di.DaggerAppComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WalacApp : Application() {

    lateinit var appComponent: AppComponent
        private set

    val authInterceptor: AuthInterceptor get() = appComponent.authInterceptor

    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appComponent = DaggerAppComponent.factory().create(this)
        CredentialStore.init(this)
        // Precalentar DHT/torrent en fondo: la primera reproduccion Torrentio
        // no debe pagar el bootstrap en frio.
        applicationScope.launch {
            runCatching { appComponent.torrentEngine.warmup() }
        }
    }
}
