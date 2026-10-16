package com.jarvis.voice.voicestudio
import android.app.Application
class StudioApplication: Application() { val controller: StudioController by lazy { StudioController(this) } }
