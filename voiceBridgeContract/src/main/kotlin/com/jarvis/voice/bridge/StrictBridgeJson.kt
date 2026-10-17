package com.jarvis.voice.bridge

/** RFC JSON syntax, duplicate decoded keys, depth and size checks before org.json allocation. */
internal class StrictBridgeJson(private val raw: String) {
    private var at=0
    private fun ws() { while(at<raw.length && raw[at] in " \r\n\t") at++ }
    private fun take(c: Char) { ws();checkBridge(at<raw.length && raw[at++]==c) }
    private fun string(): String {
        ws();val start=at;take('"')
        while(at<raw.length) {
            val c=raw[at++];checkBridge(c.code>=32)
            if(c=='"') return (org.json.JSONTokener(raw.substring(start,at)).nextValue() as String).also(::unicode)
            if(c=='\\') { checkBridge(at<raw.length);val e=raw[at++];checkBridge(e in "\"\\/bfnrtu");if(e=='u') { checkBridge(at+4<=raw.length && raw.substring(at,at+4).all { it in "0123456789abcdefABCDEF" });at+=4 } }
        };throw BridgeFailure(BridgeError.INVALID_INPUT)
    }
    private fun value(depth: Int) {
        ws();checkBridge(at<raw.length && depth<=12)
        when(raw[at]) {
            '{' -> obj(depth+1)
            '[' -> { take('[');ws();if(at<raw.length && raw[at]==']') { at++;return };var n=0
                while(true) { checkBridge(++n<=64);value(depth+1);ws();checkBridge(at<raw.length);when(raw[at++]) { ']'->return;','->{};else->throw BridgeFailure(BridgeError.INVALID_INPUT) } } }
            '"' -> string()
            else -> { val start=at;while(at<raw.length && raw[at] !in ",]} \r\n\t") at++;val t=raw.substring(start,at)
                checkBridge(t in setOf("true","false","null") || t.matches(Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?"))) }
        }
    }
    private fun obj(depth: Int) {
        checkBridge(depth<=12);take('{');ws();val keys=mutableSetOf<String>()
        if(at<raw.length && raw[at]=='}') { at++;return }
        while(true) { checkBridge(keys.size<64 && keys.add(string()));take(':');value(depth);ws();checkBridge(at<raw.length)
            when(raw[at++]) { '}'->return;','->{};else->throw BridgeFailure(BridgeError.INVALID_INPUT) } }
    }
    fun validate() { unicode(raw);checkBridge(raw.toByteArray(Charsets.UTF_8).size in 1..32768);obj(1);ws();checkBridge(at==raw.length) }
}
