package com.example.walactv

import android.app.Application
import com.example.walactv.network.AuthInterceptor

class WalacApp : Application() {

    lateinit var appComponent: AppComponent
        private set

    val authInterceptor: AuthInterceptor get() = appComponent.authInterceptor

    override fun onCreate() {
        super.onCreate()
        appComponent = DaggerAppComponent.factory().create(this)
        CredentialStore.init(this)
    }
}
