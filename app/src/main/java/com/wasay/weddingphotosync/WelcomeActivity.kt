package com.wasay.weddingphotosync

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class WelcomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        // Find views
        val logoText = findViewById<TextView>(R.id.logoImage)
        val titleText = findViewById<TextView>(R.id.appName)
        val subtitleText = findViewById<TextView>(R.id.tagline)
        val joinButton = findViewById<Button>(R.id.continueToRegistrationButton)

        // Animate elements in sequence
        Handler(Looper.getMainLooper()).postDelayed({
            logoText.animate()
                .alpha(1.0f)
                .setDuration(800)
                .start()
        }, 300)

        Handler(Looper.getMainLooper()).postDelayed({
            titleText.animate()
                .alpha(1.0f)
                .setDuration(800)
                .start()
        }, 600)

        Handler(Looper.getMainLooper()).postDelayed({
            subtitleText.animate()
                .alpha(1.0f)
                .setDuration(800)
                .start()
        }, 900)

        Handler(Looper.getMainLooper()).postDelayed({
            joinButton.animate()
                .alpha(1.0f)
                .translationY(0f)
                .setDuration(600)
                .start()
        }, 1200)

        // Button click with animation
        joinButton.setOnClickListener { view ->
            view.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction {
                    view.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(100)
                        .withEndAction {
                            startActivity(Intent(this, MainActivity::class.java))
                            overridePendingTransition(
                                android.R.anim.fade_in,
                                android.R.anim.fade_out
                            )
                            finish()
                        }
                        .start()
                }
                .start()
        }

        // Hidden Admin Access: Long press the logo or title to open Admin Panel
        val adminTrigger = { _: android.view.View ->
            startActivity(Intent(this, AdminActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            true
        }

        logoText.setOnLongClickListener(adminTrigger)
        titleText.setOnLongClickListener(adminTrigger)
        subtitleText.setOnLongClickListener(adminTrigger)
    }
}