package com.jarvis.voice.pocket

import org.json.JSONObject
import java.util.Locale

data class PreparedText(val text: String, val tokens: LongArray, val framesAfterEos: Int)

/** Implements the pinned French Unigram/Metaspace tokenizer, including Unicode byte fallback. */
class FrenchFrontend(json: JSONObject) {
    private data class Piece(val text: String, val score: Double, val id: Int)
    private val byFirst = mutableMapOf<Char, MutableList<Piece>>()
    private val byteIds = IntArray(256) { -1 }
    private var unknownScore = 0.0
    init {
        val model = json.getJSONObject("model")
        require(model.getString("type") == "Unigram" && model.getBoolean("byte_fallback"))
        val vocab = model.getJSONArray("vocab")
        require(vocab.length() == 4000)
        var minimum = Double.POSITIVE_INFINITY
        for (i in 0 until vocab.length()) {
            val v = vocab.getJSONArray(i)
            val text = v.getString(0)
            val score = v.getDouble(1)
            minimum = minOf(minimum, score)
            if (text.matches(Regex("<0x[0-9A-F]{2}>"))) byteIds[text.substring(3, 5).toInt(16)] = i
            else if (i >= 4 && text.isNotEmpty()) byFirst.getOrPut(text[0]) { mutableListOf() }.add(Piece(text, score, i))
        }
        require(byteIds.all { it >= 0 })
        unknownScore = minimum - 10.0
    }

    fun encode(text: String): LongArray {
        require(!Regex("<(?:unk|s|/s|pad)>").containsMatchIn(text)) { "Jetons réservés non autorisés" }
        // Metaspace split=true: no vocabulary match may cross a word boundary.
        val words = text.split(Regex("\\s+"))
        return words.filter { it.isNotEmpty() }.flatMap { encodeWord("▁$it") }.map { it.toLong() }.toLongArray()
    }

    private fun encodeWord(word: String): List<Int> {
        val best = DoubleArray(word.length + 1) { Double.NEGATIVE_INFINITY }
        val back = IntArray(word.length + 1) { -1 }
        val ids = IntArray(word.length + 1) { -1 }
        best[0] = 0.0
        for (i in word.indices) {
            if (!best[i].isFinite()) continue
            val endChar = i + Character.charCount(word.codePointAt(i))
            var hasCharacter = false
            for (piece in byFirst[word[i]].orEmpty()) {
                if (!word.startsWith(piece.text, i)) continue
                val end = i + piece.text.length
                if (end == endChar) hasCharacter = true
                val score = best[i] + piece.score
                if (score > best[end]) { best[end] = score; back[end] = i; ids[end] = piece.id }
            }
            if (!hasCharacter && best[i] + unknownScore > best[endChar]) {
                best[endChar] = best[i] + unknownScore; back[endChar] = i; ids[endChar] = -1
            }
        }
        val pieces = mutableListOf<List<Int>>()
        var end = word.length
        while (end > 0) {
            val start = back[end]
            require(start >= 0) { "Texte non tokenisable" }
            pieces.add(if (ids[end] >= 0) listOf(ids[end]) else word.substring(start, end)
                .toByteArray(Charsets.UTF_8).map { byteIds[it.toInt() and 255] })
            end = start
        }
        return pieces.asReversed().flatten()
    }

    fun prepare(raw: String): PreparedText {
        require(raw.length in 1..300) { "Saisir de 1 à 300 caractères" }
        var text = raw
        for (c in "\"“”„«»()[]") text = text.replace(c.toString(), "")
        text = text.replace('’', '\'').replace('‘', '\'').replace(':', ',')
            .trim().replace(Regex("\\s+"), " ").replace(Regex("([.!?…])\\s*[,;:]"), "$1").replace(';', ',')
        require(text.isNotEmpty()) { "Texte vide" }
        text = text.substring(0, 1).uppercase(Locale.FRENCH) + text.substring(1)
        val closers = "\"'”’)]»"
        var last = text.length - 1
        while (last >= 0 && text[last] in closers) last--
        if (last >= 0 && text[last] !in ".!?…") {
            val base = text.substring(0, last + 1).trimEnd(',', ';', ':', '-', '–', '—')
            text = base + "." + text.substring(last + 1)
        }
        val tokens = encode(text)
        require(tokens.size in 1..50) { "Phrase trop longue : maximum 50 jetons pour ce diagnostic" }
        return PreparedText(text, tokens, if (text.split(' ').size <= 4) 5 else 3)
    }
}
