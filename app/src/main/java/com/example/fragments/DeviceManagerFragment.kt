package com.example.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.MainActivity
import com.example.R
import com.example.utils.RenameUtil

class DeviceManagerFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_device_manager, container, false)
        view.findViewById<View?>(R.id.btn_back)?.setOnClickListener {
            (activity as? MainActivity)?.switchFragment(HomeFragment())
        }

        val tvRes = view.findViewById<TextView?>(R.id.tv_resolution)
        val tvDpi = view.findViewById<TextView?>(R.id.tv_dpi)
        val btnEditDisplay = view.findViewById<Button?>(R.id.btn_edit_display)
        val seekPointer = view.findViewById<SeekBar?>(R.id.seek_pointer_speed)
        val btnAnimOff = view.findViewById<Button?>(R.id.btn_anim_off)
        val btnAnimHalf = view.findViewById<Button?>(R.id.btn_anim_half)
        val btnRefreshRate = view.findViewById<Button?>(R.id.btn_refresh_rate)
        val btnBattOpt = view.findViewById<Button?>(R.id.btn_batt_opt)
        val btnTouchOpt = view.findViewById<Button?>(R.id.btn_touch_opt)

        val dm = resources.displayMetrics
        tvRes?.text = "${dm.widthPixels}x${dm.heightPixels}"
        tvDpi?.text = "${dm.densityDpi} DPI"

        btnEditDisplay?.setOnClickListener {
            val dView = layoutInflater.inflate(R.layout.dialog_display_config, null)
            val etW = dView.findViewById<EditText>(R.id.et_width)
            val etH = dView.findViewById<EditText>(R.id.et_height)
            val etD = dView.findViewById<EditText>(R.id.et_density)
            val btnReset = dView.findViewById<Button>(R.id.btn_reset_default)

            val dialog = AlertDialog.Builder(requireContext(), androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog)
                .setTitle("Display Configuration")
                .setView(dView)
                .setPositiveButton("Apply") { _, _ ->
                    val w = etW.text.toString().trim()
                    val h = etH.text.toString().trim()
                    val d = etD.text.toString().trim()
                    Thread {
                        if (w.isNotEmpty() && h.isNotEmpty()) {
                            RenameUtil.executeShizukuCommand("wm size ${w}x$h")
                        }
                        if (d.isNotEmpty()) {
                            RenameUtil.executeShizukuCommand("wm density $d")
                        }
                        activity?.runOnUiThread {
                            Toast.makeText(context, "Display settings updated! ✓", Toast.LENGTH_SHORT).show()
                        }
                    }.start()
                }
                .setNegativeButton("Cancel", null)
                .create()

            btnReset.setOnClickListener {
                Thread {
                    RenameUtil.executeShizukuCommand("wm size reset && wm density reset")
                    activity?.runOnUiThread {
                        dialog.dismiss()
                        Toast.makeText(context, "Display settings reset to default! ✓", Toast.LENGTH_SHORT).show()
                    }
                }.start()
            }

            dialog.show()
        }

        btnAnimOff?.setOnClickListener {
            Thread {
                RenameUtil.executeShizukuCommand("settings put global window_animation_scale 0 && settings put global transition_animation_scale 0 && settings put global animator_duration_scale 0")
                activity?.runOnUiThread {
                    Toast.makeText(context, "Animations disabled for maximum FPS! ⚡", Toast.LENGTH_SHORT).show()
                }
            }.start()
        }

        btnAnimHalf?.setOnClickListener {
            Thread {
                RenameUtil.executeShizukuCommand("settings put global window_animation_scale 0.5 && settings put global transition_animation_scale 0.5 && settings put global animator_duration_scale 0.5")
                activity?.runOnUiThread {
                    Toast.makeText(context, "Animations set to 0.5x! ⚡", Toast.LENGTH_SHORT).show()
                }
            }.start()
        }

        btnRefreshRate?.setOnClickListener {
            Thread {
                RenameUtil.executeShizukuCommand("settings put system peak_refresh_rate 120 && settings put system min_refresh_rate 120")
                activity?.runOnUiThread {
                    Toast.makeText(context, "Maximized display refresh rate! ⚡", Toast.LENGTH_SHORT).show()
                }
            }.start()
        }

        btnBattOpt?.setOnClickListener {
            Thread {
                RenameUtil.executeShizukuCommand("cmd deviceidle whitelist +com.dts.freefiremax && cmd deviceidle whitelist +${requireActivity().packageName}")
                activity?.runOnUiThread {
                    Toast.makeText(context, "Battery whitelist optimized for gaming! 🔋", Toast.LENGTH_SHORT).show()
                }
            }.start()
        }

        btnTouchOpt?.setOnClickListener {
            Thread {
                RenameUtil.executeShizukuCommand("settings put system touch_response 1 || settings put system pointer_speed 7")
                activity?.runOnUiThread {
                    Toast.makeText(context, "Touch sensitivity optimized! 🎯", Toast.LENGTH_SHORT).show()
                }
            }.start()
        }

        return view
    }
}
