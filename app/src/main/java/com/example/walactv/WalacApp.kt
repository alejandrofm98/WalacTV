package com.example.walactv

import android.app.Application
import com.example.walactv.data.preferences.CredentialStore
import com.example.walactv.data.remote.api.AuthInterceptor
import com.example.walactv.di.AppComponent
import com.example.walactv.di.DaggerAppComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class WalacApp : Application() {

    lateinit var appComponent: AppComponent
        private set

    val authInterceptor: AuthInterceptor get() = appComponent.authInterceptor

    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appComponent = DaggerAppComponent.factory().create(this)
        CredentialStore.init(this)
    }
}
