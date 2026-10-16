package com.jarvis.voice.voicestudio
import android.content.Context
import android.view.*
import android.widget.*
import android.text.InputFilter
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.materialswitch.MaterialSwitch

/** Shared native components; every decorative element is non-clickable. */
class StudioUi(val context: Context) {
    fun dp(n: Int)=(n*context.resources.displayMetrics.density).toInt()
    fun color(id: Int)=context.getColor(id)
    fun column()=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(20),dp(20),dp(24)) }
    fun page(column: LinearLayout)=ScrollView(context).apply { isFillViewport=true;addView(column);clipToPadding=false }
    fun text(parent: LinearLayout,value: String,size: Float=16f,muted: Boolean=false)=TextView(context).apply {
        text=value;textSize=size;setTextColor(color(if(muted) R.color.studio_muted else R.color.studio_text));setPadding(0,dp(6),0,dp(12));parent.addView(this)
    }
    fun card(parent: LinearLayout,title: String,subtitle: String=""): LinearLayout {
        val c=MaterialCardView(context).apply { radius=dp(20).toFloat();cardElevation=0f;strokeWidth=dp(1);strokeColor=color(R.color.studio_line);setCardBackgroundColor(color(R.color.studio_surface)) }
        parent.addView(c,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(16) })
        val inner=column().apply { setPadding(dp(18),dp(14),dp(18),dp(14)) };c.addView(inner);text(inner,title,21f);if(subtitle.isNotEmpty()) text(inner,subtitle,14f,true);return inner
    }
    fun button(parent: LinearLayout,title: String,id: Int=View.NO_ID,primary: Boolean=false,action: ()->Unit): MaterialButton {
        val style=if(primary) com.google.android.material.R.attr.materialButtonStyle else com.google.android.material.R.attr.materialButtonOutlinedStyle
        return MaterialButton(context,null,style).apply { this.id=id;text=title;isAllCaps=false;cornerRadius=dp(16);minimumHeight=dp(52);parent.addView(this,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(4);bottomMargin=dp(4) });setOnClickListener { action() } }
    }
    fun input(parent: LinearLayout,title: String,value: String,max: Int=300,id: Int=View.NO_ID,multiline: Boolean=false): TextInputEditText {
        val box=TextInputLayout(context).apply { hint=title;boxBackgroundMode=TextInputLayout.BOX_BACKGROUND_OUTLINE;setBoxCornerRadii(dp(14).toFloat(),dp(14).toFloat(),dp(14).toFloat(),dp(14).toFloat()) }
        val entry=TextInputEditText(box.context).apply { this.id=id;setText(value);filters=arrayOf(InputFilter.LengthFilter(max));isSaveEnabled=false;importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO;imeOptions=android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;minHeight=dp(56)
            if(multiline) { minLines=4;gravity=Gravity.TOP;inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE } else setSingleLine(true)
        };box.addView(entry);parent.addView(box,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(8);bottomMargin=dp(12) });return entry
    }
    fun check(parent: LinearLayout,title: String,checked: Boolean=false,id: Int=View.NO_ID)=com.google.android.material.checkbox.MaterialCheckBox(context).apply { this.id=id;text=title;isChecked=checked;minHeight=dp(48);parent.addView(this) }
    fun toggle(parent: LinearLayout,title: String,checked: Boolean,id: Int=View.NO_ID)=MaterialSwitch(context).apply { this.id=id;text=title;isChecked=checked;minHeight=dp(48);parent.addView(this) }
    fun spinner(parent: LinearLayout,title: String,options: List<String>,selected: Int=0,id: Int=View.NO_ID): Spinner {
        val label=text(parent,title,14f,true)
        return Spinner(context).apply { this.id=if(id==View.NO_ID) View.generateViewId() else id;label.labelFor=this.id;contentDescription=title;minimumHeight=dp(48);adapter=ArrayAdapter(context,android.R.layout.simple_spinner_dropdown_item,options);if(options.isNotEmpty()) setSelection(selected.coerceIn(0,options.lastIndex));parent.addView(this,LinearLayout.LayoutParams(-1,dp(52))) }
    }
}
