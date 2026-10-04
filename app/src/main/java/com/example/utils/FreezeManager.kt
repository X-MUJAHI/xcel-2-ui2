package com.example.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.concurrent.CopyOnWriteArrayList

object FreezeManager {
    private const val TAG = "FreezeManager"
    private const val PREFS_NAME = "xcel_prefs"
    private const val PREF_IS_FROZEN = "is_frozen"
    private const val PREF_FREEZE_REASON = "freeze_reason"
    private const val PREF_LAST_KNOWN_FROZEN = "last_known_frozen_state"
    private const val PREF_WAS_ON_BEFORE_FREEZE = "was_on_before_freeze"

    private var listenerRegistration: ListenerRegistration? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<FreezeStateListener>()

    interface FreezeStateListener {
        fun onFreezeStateChanged(isFrozen: Boolean, reason: String)
    }

    fun isCurrentlyFrozen(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_IS_FROZEN, false)
    }

    fun getFreezeReason(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_FREEZE_REASON, "Server maintenance in progress")
            ?: "Server maintenance in progress"
    }

    fun registerListener(listener: FreezeStateListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun unregisterListener(listener: FreezeStateListener) {
        listeners.remove(listener)
    }

    fun startListening(context: Context) {
        if (listenerRegistration != null) return
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        try {
            val docRef: DocumentReference = FirebaseFirestore.getInstance()
                .collection("app_settings")
                .document("global")

            listenerRegistration = docRef.addSnapshotListener { snapshot: DocumentSnapshot?, error ->
                if (error != null) {
                    Log.e(TAG, "Snapshot error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val isFrozen = snapshot.getBoolean("isFrozen") ?: false
                    val reason = snapshot.getString("freezeReason") ?: "Server maintenance in progress"
                    handleFreezeState(appContext, prefs, isFrozen, reason)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error attaching Firestore listener: ${e.message}")
        }
    }

    fun stopListening() {
        try {
            listenerRegistration?.remove()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        listenerRegistration = null
    }

    @Synchronized
    fun handleFreezeState(context: Context, prefs: SharedPreferences, isFrozen: Boolean, reason: String) {
        val lastKnown = prefs.getBoolean(PREF_LAST_KNOWN_FROZEN, false)
        prefs.edit()
            .putBoolean(PREF_IS_FROZEN, isFrozen)
            .putString(PREF_FREEZE_REASON, reason)
            .apply()

        if (isFrozen) {
            if (!lastKnown) {
                prefs.edit().putBoolean(PREF_LAST_KNOWN_FROZEN, true).apply()
                Thread {
                    if (RenameUtil.isModOn()) {
                        Log.d(TAG, "Transition to FROZEN: Device is ON. Saving state as true and turning OFF...")
                        prefs.edit().putBoolean(PREF_WAS_ON_BEFORE_FREEZE, true).apply()
                        RenameUtil.turnOff(context)
                    } else {
                        Log.d(TAG, "Transition to FROZEN: Device is OFF. Saving state as false.")
                        prefs.edit().putBoolean(PREF_WAS_ON_BEFORE_FREEZE, false).apply()
                    }
                    notifyListeners(true, reason)
                }.start()
            } else {
                notifyListeners(true, reason)
            }
        } else {
            if (lastKnown) {
                prefs.edit().putBoolean(PREF_LAST_KNOWN_FROZEN, false).apply()
                Thread {
                    val wasOn = prefs.getBoolean(PREF_WAS_ON_BEFORE_FREEZE, false)
                    prefs.edit().putBoolean(PREF_WAS_ON_BEFORE_FREEZE, false).apply()
                    if (wasOn) {
                        Log.d(TAG, "Transition to UNFROZEN: Device was ON before freeze. Restoring to ON...")
                        RenameUtil.turnOn(context)
                    } else {
                        Log.d(TAG, "Transition to UNFROZEN: Device was OFF before freeze. Leaving OFF.")
                    }
                    notifyListeners(false, reason)
                }.start()
            } else {
                prefs.edit().putBoolean(PREF_LAST_KNOWN_FROZEN, false).apply()
                notifyListeners(false, reason)
            }
        }
    }

    private fun notifyListeners(isFrozen: Boolean, reason: String) {
        mainHandler.post {
            for (listener in listeners) {
                try {
                    listener.onFreezeStateChanged(isFrozen, reason)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
