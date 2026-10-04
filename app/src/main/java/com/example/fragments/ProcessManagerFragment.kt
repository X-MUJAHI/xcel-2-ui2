package com.example.fragments

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.MainActivity
import com.example.R
import com.example.utils.RenameUtil

class ProcessManagerFragment : Fragment() {

    data class ProcessItem(
        val pid: String,
        val user: String,
        val name: String,
        val label: String,
        val icon: Drawable?
    )

    private val processList = mutableListOf<ProcessItem>()
    private lateinit var adapter: ProcessAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_process_manager, container, false)
        view.findViewById<ImageView>(R.id.iv_back)?.setOnClickListener {
            (activity as? MainActivity)?.switchFragment(HomeFragment())
        }

        val rv = view.findViewById<RecyclerView>(R.id.rv_processes)
        rv.layoutManager = LinearLayoutManager(context)
        adapter = ProcessAdapter(processList) { item ->
            killProcess(item)
        }
        rv.adapter = adapter

        loadProcesses()
        return view
    }

    private fun loadProcesses() {
        Thread {
            processList.clear()
            val pm = requireContext().packageManager
            if (RenameUtil.useShizukuOps && RenameUtil.shizukuAvailable()) {
                val out = RenameUtil.executeShizukuCommandWithOutput("ps -A -o PID,USER,NAME")
                val lines = out.lines().drop(1)
                for (l in lines) {
                    val parts = l.trim().split(Regex("\\s+"))
                    if (parts.size >= 3) {
                        val pid = parts[0]
                        val user = parts[1]
                        val name = parts[2]
                        var label = name
                        var icon: Drawable? = null
                        try {
                            val appInfo = pm.getApplicationInfo(name, 0)
                            label = pm.getApplicationLabel(appInfo).toString()
                            icon = pm.getApplicationIcon(appInfo)
                        } catch (_: Exception) {}
                        processList.add(ProcessItem(pid, user, name, label, icon))
                    }
                }
            }
            activity?.runOnUiThread {
                adapter.notifyDataSetChanged()
            }
        }.start()
    }

    private fun killProcess(item: ProcessItem) {
        Thread {
            if (RenameUtil.useShizukuOps && RenameUtil.shizukuAvailable()) {
                RenameUtil.executeShizukuCommand("kill -9 ${item.pid} || am force-stop ${item.name}")
            }
            activity?.runOnUiThread {
                processList.remove(item)
                adapter.notifyDataSetChanged()
                Toast.makeText(context, "Stopped ${item.label}", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    class ProcessAdapter(
        private val items: List<ProcessItem>,
        private val onKill: (ProcessItem) -> Unit
    ) : RecyclerView.Adapter<ProcessAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val ivIcon: ImageView = v.findViewById(R.id.iv_process_icon)
            val tvName: TextView = v.findViewById(R.id.tv_process_name)
            val tvPid: TextView = v.findViewById(R.id.tv_process_pid)
            val btnFreeze: View = v.findViewById(R.id.btn_freeze)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_process, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.tvName.text = item.label
            holder.tvPid.text = "PID: ${item.pid} | ${item.name}"
            if (item.icon != null) {
                holder.ivIcon.setImageDrawable(item.icon)
            } else {
                holder.ivIcon.setImageResource(R.mipmap.ic_launcher)
            }
            holder.btnFreeze.setOnClickListener { onKill(item) }
        }

        override fun getItemCount(): Int = items.size
    }
}
