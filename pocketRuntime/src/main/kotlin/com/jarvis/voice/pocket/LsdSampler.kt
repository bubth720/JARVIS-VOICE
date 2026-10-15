package com.jarvis.voice.pocket

data class QualitySampling(val steps: Int=1, val temperature: Double=0.3,
    val eosThreshold: Double=-4.0, val framesAfterEos: Int?=null) {
    init {
        require(steps in setOf(1,2,3,5))
        require(temperature.isFinite() && temperature in 0.1..0.6)
        require(eosThreshold in setOf(-5.0,-4.0,-3.0))
        require(framesAfterEos==null || framesAfterEos in setOf(3,5))
    }
    fun label()="LSD $steps étapes, température $temperature, EOS $eosThreshold, queue ${framesAfterEos ?: "officielle selon texte"}"
}

/** Exact schedule/update of Kyutai pocket-tts 3.3.0 lsd_decode, no new noise per substep. */
object LsdSampler {
    fun decode(noise: FloatArray, steps: Int, cancel: CancellationToken,
        field: (s: Float,t: Float,current: FloatArray) -> FloatArray): FloatArray {
        require(steps in setOf(1,2,3,5) && noise.size==32 && noise.all { it.isFinite() })
        var current=noise.copyOf()
        for(i in 0 until steps) {
            cancel.check()
            val direction=field(i.toFloat()/steps,(i+1).toFloat()/steps,current)
            require(direction.size==32 && direction.all { it.isFinite() }) { "Direction Flow non finie ou incompatible" }
            current=FloatArray(32) { j -> current[j]+direction[j]/steps.toFloat() }
            require(current.all { it.isFinite() }) { "Latent LSD non fini" }
        }
        cancel.check(); return current
    }
}
