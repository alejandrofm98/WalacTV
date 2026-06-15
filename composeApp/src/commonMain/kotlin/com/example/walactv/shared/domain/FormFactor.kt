package com.example.walactv.shared.domain

enum class FormFactor {
    TV,
    MOBILE,
    DESKTOP,
}

object FormFactorDetector {
    @Volatile
    var current: FormFactor = FormFactor.DESKTOP

    fun isTv(): Boolean = current == FormFactor.TV
    fun isMobile(): Boolean = current == FormFactor.MOBILE
    fun isDesktop(): Boolean = current == FormFactor.DESKTOP
}
