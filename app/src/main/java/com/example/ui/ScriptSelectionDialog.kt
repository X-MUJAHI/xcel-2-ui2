package com.example.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import com.example.utils.PanelFileManager
import com.example.utils.ScriptImageHelper

class ScriptSelectionDialog(
    private val context: Context,
    private val mode: Mode,
    private val onScriptSelected: (PanelFileManager.ScriptItem, PanelFileManager.ScriptsConfig) -> Unit
) {

    enum class Mode {
        ACTIVATE_FIRST_TIME,
        TURN_ON,
        INSTALL_NEW
    }

    private var dialog: AlertDialog? = null

    fun show() {
        val inflater = LayoutInflater.from(context)
        val dialogView = inflater.inflate(R.layout.dialog_select_script, null)

        val tvTitle = dialogView.findViewById<TextView>(R.id.tv_dialog_title)
        val tvSubtitle = dialogView.findViewById<TextView>(R.id.tv_dialog_subtitle)
        val tvCount = dialogView.findViewById<TextView>(R.id.tv_script_count)
        val layoutLoading = dialogView.findViewById<LinearLayout>(R.id.layout_loading)
        val tvLoadingMsg = dialogView.findViewById<TextView>(R.id.tv_loading_msg)
        val tvEmptyError = dialogView.findViewById<TextView>(R.id.tv_empty_error)
        val rvScripts = dialogView.findViewById<RecyclerView>(R.id.rv_scripts)
        val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel_dialog)

        when (mode) {
            Mode.ACTIVATE_FIRST_TIME -> {
                tvTitle.text = "ACTIVATE FIRST TIME"
                tvSubtitle.text = "Select a script profile to download and initialize"
            }
            Mode.TURN_ON -> {
                tvTitle.text = "SELECT SCRIPT TO TURN ON"
                tvSubtitle.text = "Choose which script to activate and load"
            }
            Mode.INSTALL_NEW -> {
                tvTitle.text = "INSTALL NEW SCRIPT"
                tvSubtitle.text = "Browse and switch to a cloud script profile"
            }
        }

        rvScripts.layoutManager = LinearLayoutManager(context)

        dialog = AlertDialog.Builder(context, android.R.style.Theme_Translucent_NoTitleBar)
            .setView(dialogView)
            .create()

        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        btnCancel.setOnClickListener { dialog?.dismiss() }

        layoutLoading.visibility = View.VISIBLE
        tvLoadingMsg.text = "Checking Firebase scripts..."
        rvScripts.visibility = View.GONE
        tvEmptyError.visibility = View.GONE

        dialog?.show()

        // Fetch config from Firestore
        PanelFileManager.fetchScriptsConfigFromFirestore(
            context = context,
            onSuccess = { config ->
                (context as? android.app.Activity)?.runOnUiThread {
                    if (dialog?.isShowing != true) return@runOnUiThread
                    layoutLoading.visibility = View.GONE

                    if (config.scripts.isEmpty()) {
                        tvEmptyError.visibility = View.VISIBLE
                        tvEmptyError.text = "No scripts found in Firebase."
                        tvCount.text = "0 Scripts"
                    } else {
                        tvCount.text = "${config.scripts.size} Available"
                        rvScripts.visibility = View.VISIBLE
                        val adapter = ScriptAdapter(context, config, mode) { selectedScript ->
                            dialog?.dismiss()
                            onScriptSelected(selectedScript, config)
                        }
                        rvScripts.adapter = adapter
                    }
                }
            },
            onError = { errMsg ->
                (context as? android.app.Activity)?.runOnUiThread {
                    if (dialog?.isShowing != true) return@runOnUiThread
                    layoutLoading.visibility = View.GONE
                    tvEmptyError.visibility = View.VISIBLE
                    tvEmptyError.text = "Error loading scripts:\n$errMsg"
                    tvCount.text = "Error"
                }
            }
        )
    }

    private class ScriptAdapter(
        private val context: Context,
        private val config: PanelFileManager.ScriptsConfig,
        private val mode: Mode,
        private val onSelect: (PanelFileManager.ScriptItem) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val activeScriptId: Int
            get() = PanelFileManager.getActiveScriptId(context)

        override fun getItemViewType(position: Int): Int {
            return if (mode == Mode.TURN_ON) 0 else 1
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == 0) {
                // TURN ON mode: simple item without image
                val view = inflater.inflate(R.layout.item_script_simple, parent, false)
                SimpleViewHolder(view)
            } else {
                // ACTIVATE FIRST TIME and +INSTALL NEW SCRIPT: with image
                val view = inflater.inflate(R.layout.item_script_with_image, parent, false)
                WithImageViewHolder(view)
            }
        }

        override fun getItemCount(): Int = config.scripts.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val script = config.scripts[position]
            val isCurrentActive = (script.id == activeScriptId)
            val isDownloaded = PanelFileManager.isScriptStaged(holder.itemView.context, script)

            if (holder is SimpleViewHolder) {
                holder.tvTitle.text = script.name
                holder.tvDesc.text = if (isDownloaded) "Staged locally • 2 Files" else "Cloud script • 2 Files"

                if (isCurrentActive) {
                    holder.tvActiveBadge.visibility = View.VISIBLE
                    holder.tvActiveBadge.text = "ACTIVE"
                    holder.container.setBackgroundResource(R.drawable.card_cyber_active_bg)
                } else {
                    holder.tvActiveBadge.visibility = View.GONE
                    holder.container.setBackgroundResource(R.drawable.card_cyber_bg)
                }

                holder.btnAction.text = "TURN ON"
                holder.btnAction.setOnClickListener { onSelect(script) }
                holder.container.setOnClickListener { onSelect(script) }
            } else if (holder is WithImageViewHolder) {
                // Bind Script Image safely from res/raw/script<id>.jpg
                ScriptImageHelper.bindScriptImage(holder.ivImage, script.id)

                holder.tvBadge.text = "SCRIPT ${script.id}"
                holder.tvTitle.text = script.name

                if (isCurrentActive) {
                    holder.tvActiveIndicator.visibility = View.VISIBLE
                    holder.tvActiveIndicator.text = "ACTIVE 🟢"
                    holder.container.setBackgroundResource(R.drawable.card_cyber_active_bg)
                } else {
                    holder.tvActiveIndicator.visibility = View.GONE
                    holder.container.setBackgroundResource(R.drawable.card_cyber_bg)
                }

                when (mode) {
                    Mode.ACTIVATE_FIRST_TIME -> {
                        holder.tvDesc.text = "2 Cloud Files • Ready to download"
                        holder.btnAction.text = "ACTIVATE"
                    }
                    Mode.INSTALL_NEW -> {
                        if (isCurrentActive) {
                            holder.tvDesc.text = "Currently Active Script"
                            holder.btnAction.text = "ACTIVE"
                        } else if (isDownloaded) {
                            holder.tvDesc.text = "Downloaded • Ready to use"
                            holder.btnAction.text = "SWITCH"
                        } else {
                            holder.tvDesc.text = "2 Files • Install from cloud"
                            holder.btnAction.text = "INSTALL"
                        }
                    }
                    else -> {
                        holder.btnAction.text = "SELECT"
                    }
                }

                holder.btnAction.setOnClickListener { onSelect(script) }
                holder.container.setOnClickListener { onSelect(script) }
            }
        }

        class SimpleViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val container: View = v.findViewById(R.id.card_script_container)
            val tvTitle: TextView = v.findViewById(R.id.tv_script_title)
            val tvDesc: TextView = v.findViewById(R.id.tv_script_desc)
            val tvActiveBadge: TextView = v.findViewById(R.id.tv_active_badge)
            val btnAction: Button = v.findViewById(R.id.btn_action)
        }

        class WithImageViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val container: View = v.findViewById(R.id.card_script_container)
            val ivImage: ImageView = v.findViewById(R.id.iv_script_image)
            val tvBadge: TextView = v.findViewById(R.id.tv_script_badge)
            val tvActiveIndicator: TextView = v.findViewById(R.id.tv_active_indicator)
            val tvTitle: TextView = v.findViewById(R.id.tv_script_title)
            val tvDesc: TextView = v.findViewById(R.id.tv_script_desc)
            val btnAction: Button = v.findViewById(R.id.btn_action)
        }
    }
}
