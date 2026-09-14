package com.yad.guitar

import android.app.Application

/**
 * YadGuitarApp — Application class.
 * Init Logger saat app start.
 */
class YadGuitarApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Init Logger — HARUS PALING AWAL
        Logger.init(this)

        Logger.i("YadGuitarApp", "Application started")
        Logger.i("YadGuitarApp", "Package: $packageName")
    }
}
