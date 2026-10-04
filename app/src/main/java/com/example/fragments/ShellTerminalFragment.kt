package com.example.fragments

import android.content.Context
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.MainActivity
import com.example.R
import com.example.utils.RenameUtil

class ShellTerminalFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_shell_terminal, container, false)
        view.findViewById<ImageView>(R.id.iv_back)?.setOnClickListener {
            (activity as? MainActivity)?.switchFragment(HomeFragment())
        }

        val etCommands = view.findViewById<EditText>(R.id.et_commands)
        val tvOutput = view.findViewById<TextView>(R.id.tv_output)
        val btnRun = view.findViewById<ImageButton>(R.id.btn_run)
        val btnSave = view.findViewById<View>(R.id.btn_save)
        val btnSaved = view.findViewById<View>(R.id.btn_saved)
        val btnSchedule = view.findViewById<View>(R.id.btn_schedule)

        tvOutput.movementMethod = ScrollingMovementMethod()
        val prefs = requireActivity().getSharedPreferences("saved_scripts", Context.MODE_PRIVATE)

        btnRun.setOnClickListener {
            val cmd = etCommands.text.toString().trim()
            if (cmd.isEmpty()) return@setOnClickListener
            tvOutput.text = "Executing...\n"
            Thread {
                val out = RenameUtil.executeShizukuCommandWithOutput(cmd)
                activity?.runOnUiThread {
                    tvOutput.text = if (out.isNotEmpty()) out else "[Execution completed with no output]"
                }
            }.start()
        }

        btnSave.setOnClickListener {
            val cmd = etCommands.text.toString().trim()
            if (cmd.isEmpty()) return@setOnClickListener
            val etName = EditText(requireContext()).apply { hint = "Script Name" }
            AlertDialog.Builder(requireContext(), androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog)
                .setTitle("Save Script")
                .setView(etName)
                .setPositiveButton("Save") { _, _ ->
                    val name = etName.text.toString().trim()
                    if (name.isNotEmpty()) {
                        prefs.edit().putString(name, cmd).apply()
                        Toast.makeText(context, "Saved!", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        btnSaved.setOnClickListener {
            val all = prefs.all
            val keys = all.keys.toTypedArray()
            if (keys.isEmpty()) {
                Toast.makeText(context, "No saved scripts.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AlertDialog.Builder(requireContext(), androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog)
                .setTitle("Saved Scripts")
                .setItems(keys) { _, which ->
                    val key = keys[which]
                    etCommands.setText(prefs.getString(key, ""))
                }
                .show()
        }

        btnSchedule.setOnClickListener {
            Toast.makeText(context, "Script scheduler active in background", Toast.LENGTH_SHORT).show()
        }

        return view
    }
}
