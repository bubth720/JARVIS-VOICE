package com.jarvis.voice.expression

/** Same RFC JSON/duplicate-key rejection on Android's permissive org.json and the JVM. */
internal class StrictContractJson(private val raw: String) {
    private var at=0
    private fun ws() { while(at<raw.length && raw[at] in " \r\n\t") at++ }
    private fun take(c: Char) { ws();require(at<raw.length && raw[at++]==c) }
    private fun string(): String {
        ws();val start=at;take('"')
        while(at<raw.length) {
            val c=raw[at++];require(c.code>=32)
            if(c=='"') return org.json.JSONTokener(raw.substring(start,at)).nextValue() as String
            if(c=='\\') { require(at<raw.length);val e=raw[at++];require(e in "\"\\/bfnrtu");if(e=='u') { require(at+4<=raw.length && raw.substring(at,at+4).all { it in "0123456789abcdefABCDEF" });at+=4 } }
        }
        error("Chaîne JSON incomplète")
    }
    private fun value(depth: Int) {
        ws();require(at<raw.length)
        when(raw[at]) {
            '{' -> obj(depth+1)
            '"' -> string()
            else -> {
                val start=at;while(at<raw.length && raw[at] !in ",} \r\n\t") at++
                val token=raw.substring(start,at)
                require(token in setOf("true","false","null") || token.matches(Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")))
            }
        }
    }
    private fun obj(depth: Int) {
        require(depth<=2);take('{');ws();val keys=mutableSetOf<String>()
        if(at<raw.length && raw[at]=='}') { at++;return }
        while(true) {
            val key=string();require(keys.add(key));take(':');value(depth);ws();require(at<raw.length)
            when(raw[at++]) { '}' -> return;',' -> {};else -> error("Objet JSON invalide") }
        }
    }
    fun validate() { obj(1);ws();require(at==raw.length) }
}
