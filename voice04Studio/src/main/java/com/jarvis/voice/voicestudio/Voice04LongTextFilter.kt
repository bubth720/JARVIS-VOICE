package com.jarvis.voice.voicestudio
import android.text.InputFilter
import android.text.Spanned

/** Reject the WHOLE edit, never silently accept a truncated prefix of a long pasted answer. */
class Voice04LongTextFilter(private val rejected: ()->Unit): InputFilter {
    override fun filter(source: CharSequence,start: Int,end: Int,dest: Spanned,dstart: Int,dend: Int): CharSequence? {
        if(dest.length-(dend-dstart)+(end-start)<=1800) return null
        rejected()
        // Returning the original replacement range cancels insertion AND replacement without deleting selection.
        return dest.subSequence(dstart,dend)
    }
}
