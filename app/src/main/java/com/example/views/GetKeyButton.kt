package com.example.views

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatButton

class GetKeyButton : AppCompatButton {

    constructor(context: Context) : super(context) {
        initButton()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initButton()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initButton()
    }

    private fun initButton() {
        setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://x-link.vercel.app/getkey"))
                if (context !is android.app.Activity) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
