package com.wetune

/**
 * Lyrics-style romanization for Hindi (Devanagari) and Tamil: the way people type lyrics ("dil", "zindagi",
 * "kaadhal", "anbu"), not letter-for-letter scholarly forms ("dila", "katal"). Other scripts are left untouched
 * for the caller's general transliterator.
 */
object Roman {
    // Unicode block start of each script handled here. Telugu, Kannada and Malayalam share Devanagari's layout
    // (same letter at the same offset), so they're shifted onto it and read by the same engine.
    private const val DEVA = 0x0900; private const val TAMIL = 0x0B80; private const val TELUGU = 0x0C00
    private const val KANNADA = 0x0C80; private const val MALAYALAM = 0x0D00

    private fun blockOf(c: Char): Int = when (c.code) {
        in 0x0900..0x097F -> DEVA; in 0x0B80..0x0BFF -> TAMIL; in 0x0C00..0x0C7F -> TELUGU
        in 0x0C80..0x0CFF -> KANNADA; in 0x0D00..0x0D7F -> MALAYALAM; else -> 0
    }

    fun convert(text: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            val block = blockOf(text[i])
            if (block == 0) { out.append(text[i]); i++; continue }
            var j = i
            while (j < text.length && blockOf(text[j]) == block) j++
            val word = text.substring(i, j)
            out.append(
                when (block) {
                    DEVA -> hiWords[word] ?: hindi(word)
                    TAMIL -> tamil(word)
                    else -> hindi(shiftToDevanagari(word, block), dravidian = true, malayalam = block == MALAYALAM)
                }
            )
            i = j
        }
        return out.toString()
    }

    /** Words the rules can't get right, written the way lyrics sites spell them. */
    private val hiWords = mapOf(
        "हम" to "hum", "हमें" to "humein", "तुम्हें" to "tumhein", "में" to "mein", "मैंने" to "maine", "नहीं" to "nahin",
        "यह" to "yeh", "वह" to "woh", "वो" to "wo", "कहीं" to "kahin", "यहाँ" to "yahan", "वहाँ" to "wahan", "कहाँ" to "kahan",
        "मुझको" to "mujhko", "तुमको" to "tumko", "दुनिया" to "duniya", "ख़ुदा" to "khuda", "ख़्वाब" to "khwaab", "वक़्त" to "waqt",
    )

    // a Malayalam chillu (a consonant that never takes a vowel); marked so it isn't read as a spoken final "u"
    private const val CHILLU = ''
    private val chillus = mapOf('ൺ' to 'ण', 'ൻ' to 'न', 'ർ' to 'र', 'ൽ' to 'ल', 'ൾ' to 'ळ', 'ൿ' to 'क')

    private fun shiftToDevanagari(word: String, block: Int): String = buildString {
        for (c in word) {
            val chillu = chillus[c]
            if (chillu != null) { append(chillu); append(CHILLU) }
            else append((c.code - block + DEVA).toChar())
        }
    }

    // ---------------- Hindi ----------------

    private val hiCons = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "n", 'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "n",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n", 'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m", 'य' to "y", 'र' to "r", 'ल' to "l", 'ळ' to "l", 'व' to "v",
        'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h",
        // used by Telugu / Kannada / Malayalam (shifted onto these code points): ṟ ("R", read in context) and ḻ
        'ऱ' to "R", 'ऴ' to "zh",
        // precomposed nukta letters (the decomposed letter + ़ form is handled below)
        'क़' to "q", 'ख़' to "kh", 'ग़' to "g", 'ज़' to "z", 'ड़' to "r", 'ढ़' to "rh", 'फ़' to "f", 'य़' to "y",
    )
    private val hiNukta = mapOf("k" to "q", "j" to "z", "d" to "r", "dh" to "rh", "ph" to "f")
    private val hiVowels = mapOf(
        'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ee", 'उ' to "u", 'ऊ' to "oo", 'ऋ' to "ri", 'ए' to "e", 'ऐ' to "ai",
        'ओ' to "o", 'औ' to "au", 'ऍ' to "e", 'ऑ' to "o", 'ऎ' to "e", 'ऒ' to "o",
    )
    private val hiSigns = mapOf(
        'ा' to "aa", 'ि' to "i", 'ी' to "ee", 'ु' to "u", 'ू' to "oo", 'ृ' to "ri", 'े' to "e", 'ै' to "ai",
        'ो' to "o", 'ौ' to "au", 'ॅ' to "e", 'ॉ' to "o", 'ॆ' to "e", 'ॊ' to "o",
        'ॗ' to "au", // Telugu/Kannada/Malayalam length marks after o/e
    )

    /** One syllable: consonant cluster, its vowel ("a" when inherent), and a trailing nasal (N) or h. */
    private class Syl(val cons: MutableList<String>, var vowel: String, var inherent: Boolean, var coda: String = "", var open: Boolean = false)

    /** Devanagari engine; [dravidian] (Telugu, Kannada, Malayalam shifted onto Devanagari) keeps every written vowel. */
    private fun hindi(word: String, dravidian: Boolean = false, malayalam: Boolean = false): String {
        val s = mutableListOf<Syl>()
        for (ch in word) {
            val cur = s.lastOrNull()
            when {
                ch in hiCons -> if (cur != null && cur.open) { cur.cons += hiCons[ch]!!; cur.vowel = "a"; cur.inherent = true; cur.open = false }
                    else s += Syl(mutableListOf(hiCons[ch]!!), "a", true)
                ch == '़' -> cur?.let { c -> c.cons.lastOrNull()?.let { l -> c.cons[c.cons.size - 1] = hiNukta[l] ?: l } }
                ch in hiSigns -> cur?.let { it.vowel = hiSigns[ch]!!; it.inherent = false }
                ch == '्' -> cur?.let { it.vowel = ""; it.inherent = false; it.open = true }
                ch == CHILLU -> cur?.let { it.vowel = ""; it.inherent = false } // ends the consonant, no vowel follows
                ch in hiVowels -> s += Syl(mutableListOf(), hiVowels[ch]!!, false)
                ch == 'ं' || ch == 'ँ' -> cur?.coda = "N"
                ch == 'ः' -> cur?.coda = "h"
                else -> {} // digits, danda etc. are dropped from the word
            }
        }
        if (s.isEmpty()) return word
        // the inherent "a" is silent at the end of a word and in the middle of V C a C V ("dilbar", "samajhna")
        val dead = BooleanArray(s.size)
        if (!dravidian && s.size > 1 && s.last().inherent && s.last().cons.isNotEmpty()) dead[s.lastIndex] = true
        for (i in s.size - 2 downTo 1) {
            if (dravidian) break
            val x = s[i]
            if (!x.inherent || x.cons.size != 1 || x.coda.isNotEmpty()) continue
            val next = s[i + 1]; val prev = s[i - 1]
            val nextVoiced = next.cons.isNotEmpty() && next.vowel.isNotEmpty() && !dead[i + 1]
            val prevVoiced = prev.vowel.isNotEmpty() && !dead[i - 1] && prev.coda.isEmpty() && !prev.open
            if (nextVoiced && prevVoiced) dead[i] = true
        }
        val b = StringBuilder()
        s.forEachIndexed { i, x ->
            // Malayalam ṟ: ന്റ "nt", റ്റ "tt", otherwise "r"
            b.append(x.cons.joinToString("").replace("nR", "nt").replace("RR", "tt").replace("R", "r"))
            var v = if (dead[i]) "" else x.vowel
            if (dravidian && v == "ri") v = "ru" // ృ / ೃ / ൃ: "hrudaya", "amrutham"
            if (malayalam && i == s.lastIndex && x.open && x.cons.isNotEmpty()) v = "u" // a final ് is spoken "u": ആണ് "aanu"
            if (!dravidian && i == s.lastIndex && s.size > 1 && x.coda.isEmpty()) v = when (v) { "aa" -> "a"; "ee" -> "i"; "oo" -> "u"; else -> v }
            if (!dravidian && i == s.lastIndex && s.size == 1 && x.coda.isEmpty() && v == "ee") v = "i" // "ji", "hi"
            b.append(v)
            if (x.coda == "N") b.append(if (s.getOrNull(i + 1)?.cons?.firstOrNull()?.first() in setOf('p', 'b', 'm') || (dravidian && i == s.lastIndex)) "m" else "n")
            else b.append(x.coda)
        }
        return b.toString()
    }

    // ---------------- Tamil ----------------

    private val taVowels = mapOf(
        'அ' to "a", 'ஆ' to "aa", 'இ' to "i", 'ஈ' to "ee", 'உ' to "u", 'ஊ' to "oo", 'எ' to "e", 'ஏ' to "e", 'ஐ' to "ai",
        'ஒ' to "o", 'ஓ' to "o", 'ஔ' to "au",
    )
    private val taSigns = mapOf(
        'ா' to "aa", 'ி' to "i", 'ீ' to "ee", 'ு' to "u", 'ூ' to "oo", 'ெ' to "e", 'ே' to "e", 'ை' to "ai",
        'ொ' to "o", 'ோ' to "o", 'ௌ' to "au",
    )
    private const val TA_CONS = "கஙசஞடணதநபமயரலவழளறனஜஷஸஹ"
    private val nasals = setOf('ங', 'ஞ', 'ண', 'ந', 'ம', 'ன')
    private val sonorants = setOf('ய', 'ர', 'ல', 'வ', 'ழ', 'ள')

    private class TSyl(val c: Char?, var vowel: String)

    private fun tamil(word: String): String {
        val s = mutableListOf<TSyl>()
        for (ch in word) {
            val cur = s.lastOrNull()
            when {
                ch in TA_CONS -> s += TSyl(ch, "a")
                ch in taSigns -> cur?.vowel = taSigns[ch]!!
                ch == '்' -> cur?.vowel = ""
                ch in taVowels -> s += TSyl(null, taVowels[ch]!!)
                else -> {}
            }
        }
        val b = StringBuilder()
        s.forEachIndexed { i, x ->
            val c = x.c
            if (c != null) {
                val prev = s.getOrNull(i - 1)
                // how the previous sound shapes this one: Tamil softens stops between vowels and after nasals
                val gem = prev != null && prev.vowel.isEmpty() && prev.c == c
                val soft = prev != null && (prev.vowel.isNotEmpty() || (prev.c in nasals) || (prev.c in sonorants))
                val afterN = prev != null && prev.vowel.isEmpty() && prev.c == 'ன'
                // first half of a doubled stop stays hard: பட்டு "pattu", முத்தம் "mutham" → "muttham"
                val gemFirst = x.vowel.isEmpty() && s.getOrNull(i + 1)?.c == c
                b.append(
                    if (gemFirst && c in "கசடதப") when (c) { 'க' -> "k"; 'ச' -> "c"; 'ப' -> "p"; else -> "t" } else when (c) {
                        'க' -> if (gem) "k" else if (prev?.c == 'ங' && prev.vowel.isEmpty()) "g" else if (soft) "g" else "k"
                        'ச' -> if (gem) "ch" else if (prev?.c == 'ஞ' && prev.vowel.isEmpty()) "j" else if (prev == null || soft) "s" else "ch"
                        'ட' -> if (gem) "t" else if (soft) "d" else "t"
                        'த' -> if (gem) "th" else if (prev == null) "th" else if (soft) "dh" else "th"
                        'ப' -> if (gem) "p" else if (soft) "b" else "p"
                        'ற' -> if (gem) "r" else if (afterN) "dr" else "r"
                        'ங' -> "n"; 'ஞ' -> if (prev == null) "gn" else "n"; 'ண' -> "n"; 'ந' -> "n"; 'ம' -> "m"; 'ன' -> "n"
                        'ய' -> "y"; 'ர' -> "r"; 'ல' -> "l"; 'வ' -> "v"; 'ழ' -> "zh"; 'ள' -> "l"
                        'ஜ' -> "j"; 'ஷ' -> "sh"; 'ஸ' -> "s"; else -> "h"
                    }
                )
                // ற்ற reads "tr" ("kaatru"): the first ற is the t
                if (c == 'ற' && x.vowel.isEmpty() && s.getOrNull(i + 1)?.c == 'ற') { b.setLength(b.length - 1); b.append("t") }
            }
            b.append(x.vowel)
        }
        return b.toString()
    }
}
