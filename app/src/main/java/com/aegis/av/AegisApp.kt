package com.aegis.av

import android.app.Application
import com.aegis.av.data.Prefs
import com.aegis.av.data.SignatureRepository
import com.aegis.av.util.Notify

class AegisApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Notify.ensureChannels(this)
        Prefs.init(this)
        SignatureRepository.init(this)
    }
}
