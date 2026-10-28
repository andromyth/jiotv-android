package com.andromyth.jiotv.android.ui

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.andromyth.jiotv.android.data.JioApi
import com.andromyth.jiotv.android.data.TvChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var api: JioApi
    private lateinit var root: LinearLayout
    private lateinit var title: TextView
    private lateinit var status: TextView
    private lateinit var channelList: ListView
    private lateinit var playerView: PlayerView
    private var player: ExoPlayer? = null
    private var channels: List<TvChannel> = emptyList()
    private var activeLanguage = "All"
    private var activeCategory = "All"
    private var activeChannel: TvChannel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        api = JioApi(applicationContext)
        buildUi()
        refreshChannels()
        if (api.session == null) showSignIn()
    }

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 24, 36, 24)
            setBackgroundColor(0xFF080B10.toInt())
            isFocusableInTouchMode = true
        }
        title = TextView(this).apply {
            text = "JTV   LIVE TV"
            textSize = 28f; setTextColor(0xFFFF805D.toInt())
        }
        status = TextView(this).apply {
            text = "Connecting to Jio…"; textSize = 15f; setTextColor(0xFFB8C0C8.toInt())
        }
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(button("All channels") { activeLanguage = "All"; activeCategory = "All"; renderChannels() })
        actions.addView(button("Bengali") { activeLanguage = "Bengali"; renderChannels() })
        actions.addView(button("Hindi") { activeLanguage = "Hindi"; renderChannels() })
        actions.addView(button("English") { activeLanguage = "English"; renderChannels() })
        actions.addView(button("Refresh") { refreshChannels() })
        actions.addView(button("Settings") { showSignIn() })
        channelList = ListView(this).apply {
            divider = null
            selector = android.graphics.drawable.ColorDrawable(0xFFFF805D.toInt())
            isFocusable = true
            onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
                val filtered = filteredChannels()
                filtered.getOrNull(position)?.let(::playChannel)
            }
        }
        playerView = PlayerView(this).apply {
            useController = true
            visibility = View.GONE
        }
        root.addView(title)
        root.addView(status)
        root.addView(actions)
        root.addView(channelList, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(playerView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isFocusable = true
        setOnClickListener { action() }
    }

    private fun refreshChannels() {
        status.text = "Loading channels…"
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { api.getChannels() }
                channels = result
                status.text = "${result.size} channels loaded"
                renderChannels()
            } catch (e: Exception) {
                status.text = e.message ?: "Could not load Jio channels"
            }
        }
    }

    private fun filteredChannels(): List<TvChannel> {
        return channels.filter {
            (activeLanguage == "All" || it.language.equals(activeLanguage, ignoreCase = true)) &&
            (activeCategory == "All" || it.group.equals(activeCategory, ignoreCase = true))
        }
    }

    private fun renderChannels() {
        val filtered = filteredChannels()
        channelList.adapter = object : ArrayAdapter<TvChannel>(
            this, android.R.layout.simple_list_item_2, android.R.id.text1, filtered
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = (convertView ?: layoutInflater.inflate(android.R.layout.simple_list_item_2, parent, false))
                val channel = getItem(position)!!
                view.findViewById<TextView>(android.R.id.text1).apply {
                    text = channel.name
                    textSize = 19f
                    setTextColor(0xFFF5F6F7.toInt())
                }
                view.findViewById<TextView>(android.R.id.text2).apply {
                    text = "ID ${channel.id}   ·   ${channel.language}   ·   ${channel.group}"
                    textSize = 13f
                    setTextColor(0xFF9DA8B4.toInt())
                }
                view.setBackgroundColor(0xFF151B22.toInt())
                return view
            }
        }
    }

    private fun showSignIn() {
        val mobile = EditText(this).apply {
            hint = "10-digit Jio mobile number"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setSingleLine()
        }
        val otp = EditText(this).apply {
            hint = "OTP"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setSingleLine()
        }
        val authStatus = TextView(this).apply {
            text = "Enter your Jio mobile number, then choose Send OTP."
            textSize = 14f
            setTextColor(0xFFB8C0C8.toInt())
            setPadding(0, 16, 0, 8)
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 0)
            addView(mobile)
            addView(otp)
            addView(authStatus)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (api.session == null) "Sign in to Jio" else "Jio account")
            .setMessage("Sign in using an OTP delivered by Jio. Your session is stored privately on this device.")
            .setView(layout)
            .setPositiveButton("Send OTP", null)
            .setNeutralButton("Verify OTP", null)
            .setNegativeButton(if (api.session == null) "Close" else "Sign out", null)
            .create()

        fun showAuthMessage(message: String, isError: Boolean = false) {
            authStatus.text = message
            authStatus.setTextColor(if (isError) 0xFFFF8B8B.toInt() else 0xFF91E0AF.toInt())
            status.text = message
        }

        dialog.setOnShowListener {
            val sendButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val verifyButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            val closeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

            sendButton.setOnClickListener {
                val mobileNumber = mobile.text.toString()
                sendButton.isEnabled = false
                verifyButton.isEnabled = false
                showAuthMessage("Contacting Jio to request the OTP…")
                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) { api.sendOtp(mobileNumber) }
                        showAuthMessage("Jio accepted the OTP request. Check your SMS, then enter the code above.")
                        otp.requestFocus()
                    } catch (e: Exception) {
                        showAuthMessage(e.message ?: "Could not request OTP from Jio.", isError = true)
                    } finally {
                        sendButton.isEnabled = true
                        verifyButton.isEnabled = true
                    }
                }
            }

            verifyButton.setOnClickListener {
                val mobileNumber = mobile.text.toString()
                val code = otp.text.toString()
                sendButton.isEnabled = false
                verifyButton.isEnabled = false
                showAuthMessage("Verifying OTP with Jio…")
                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) { api.verifyOtp(mobileNumber, code) }
                        showAuthMessage("Signed in to Jio.")
                        dialog.dismiss()
                        refreshChannels()
                    } catch (e: Exception) {
                        showAuthMessage(e.message ?: "OTP verification failed.", isError = true)
                    } finally {
                        sendButton.isEnabled = true
                        verifyButton.isEnabled = true
                    }
                }
            }

            closeButton.setOnClickListener {
                if (api.session != null) {
                    api.signOut()
                    status.text = "Signed out"
                }
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun playChannel(channel: TvChannel) {
        activeChannel = channel
        lifecycleScope.launch {
            try {
                val info = withContext(Dispatchers.IO) { api.resolvePlayback(channel) }
                root.findViewWithTag<View>("category-bar")?.visibility = View.GONE
                channelList.visibility = View.GONE
                playerView.visibility = View.VISIBLE
                player?.release()
                player = ExoPlayer.Builder(this@MainActivity).build().also { exo ->
                    playerView.player = exo
                    val item = MediaItem.Builder().setUri(Uri.parse(info.url))
                        .setMimeType(if (info.mimeType == "dash") MimeTypes.APPLICATION_MPD else MimeTypes.APPLICATION_M3U8)
                        .build()
                    exo.setMediaItem(item)
                    exo.prepare()
                    exo.playWhenReady = true
                }
            } catch (e: Exception) {
                status.text = "${channel.name}: ${e.message ?: "Playback failed"}"
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (playerView.visibility == View.VISIBLE && (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_DPAD_LEFT)) {
            player?.stop()
            playerView.player = null
            player?.release()
            player = null
            playerView.visibility = View.GONE
            channelList.visibility = View.VISIBLE
            channelList.requestFocus()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            showSignIn(); return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        playerView.player = null
        player?.release()
        player = null
        super.onDestroy()
    }
}
