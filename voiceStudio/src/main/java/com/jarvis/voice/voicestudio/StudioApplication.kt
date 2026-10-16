package com.jarvis.voice.voicestudio
import android.app.Application
open class StudioApplication: Application() {
    protected open fun createController()=StudioController(this)
    val controller: StudioController by lazy { createController() }
}
