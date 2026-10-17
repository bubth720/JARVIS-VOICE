package com.jarvis.voice.bridgepilot

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.*

class PilotActivity: Activity() {
    private lateinit var status: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        status=TextView(this).apply { setText(R.string.pilot_intro) }
        setContentView(LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL;setPadding(24,24,24,24);addView(status)
            addView(Button(this@PilotActivity).apply { filterTouchesWhenObscured=true;setText(R.string.pilot_arm);setOnClickListener { confirm() } })
            addView(Button(this@PilotActivity).apply { filterTouchesWhenObscured=true;setText(R.string.pilot_revoke);setOnClickListener { PilotRuntime.authorization.revoke();status.setText(R.string.pilot_intro) } })
        })
    }
    private fun confirm() {
        try {
            val uid=packageManager.getApplicationInfo(PilotWire.CLIENT,0).uid
            val principal=PilotIdentity(this).candidate(uid)
            val dialog=AlertDialog.Builder(this).setTitle(R.string.pilot_confirm)
                .setMessage("${principal.packageName}\nSHA-256 : ${principal.certificate}")
                .setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.pilot_allow) { _,_ ->
                    try {
                        val current=PilotIdentity(this).candidate(uid);need(current==principal,"IDENTITY_CHANGED")
                        PilotRuntime.authorization.arm(current);status.setText(R.string.pilot_armed)
                    } catch(_: Exception) { status.setText(R.string.pilot_denied) }
                }.create()
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            dialog.show()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).filterTouchesWhenObscured=true
        } catch(_: Exception) { status.setText(R.string.pilot_denied) }
    }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if(event.flags and (MotionEvent.FLAG_WINDOW_IS_OBSCURED or MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED)!=0) return false
        return super.dispatchTouchEvent(event)
    }
    override fun onResume() { super.onResume();PilotRuntime.authorization.visibility(true) }
    override fun onPause() { PilotRuntime.authorization.visibility(false);super.onPause() }
}
