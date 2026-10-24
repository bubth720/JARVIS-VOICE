package com.jarvis.voice.bridgeclient

/** Reference client library only; transport injection does not establish caller identity or a lease. */
class CoreSpeechClientPort(private val transport: PublicSpeechTransport) {
    private val lock=Any();private var pending: SpeechCorrelation?=null
    private val used=linkedSetOf<String>()
    @Volatile var mode=SpeechConnectionMode.TEXT_ONLY;private set
    fun submit(request: PublicSpeechJobRequestV2): SpeechProgressEnvelope {
        synchronized(lock) {
            check(pending==null && request.key.requestId !in used && used.size<128) { "BUSY_OR_REPLAY" }
            used.add(request.key.requestId);pending=request.key
        }
        try {
            val reply=transport.submit(request)
            synchronized(lock) {
                check(pending==request.key && reply.key==request.key) { "STALE_RESPONSE" }
                mode=reply.mode;return reply
            }
        } finally { synchronized(lock) { if(pending==request.key) pending=null } }
    }
    fun disconnect()=synchronized(lock) { pending=null;mode=SpeechConnectionMode.TEXT_ONLY }
}
