package com.example.fragments

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.MainActivity
import com.example.R
import com.example.utils.RenameUtil

class AppManagerFragment : Fragment() {

    data class AppItem(
        val packageName: String,
        val label: String,
        val icon: Drawable?,
        val isSystem: Boolean
    )

    private val appList = mutableListOf<AppItem>()
    private lateinit var adapter: AppAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_app_manager, container, false)
        view.findViewById<ImageView>(R.id.iv_back)?.setOnClickListener {
            (activity as? MainActivity)?.switchFragment(HomeFragment())
        }

        val rv = view.findViewById<RecyclerView>(R.id.rv_apps)
        rv.layoutManager = LinearLayoutManager(context)
        adapter = AppAdapter(appList) { item ->
            handleAppAction(item)
        }
        rv.adapter = adapter

        view.findViewById<Button?>(R.id.btn_sort_date)?.setOnClickListener {
            appList.sortBy { it.label.lowercase() }
            adapter.notifyDataSetChanged()
        }

        view.findViewById<Button?>(R.id.btn_sort_size)?.setOnClickListener {
            appList.sortBy { it.packageName }
            adapter.notifyDataSetChanged()
        }

        view.findViewById<Button?>(R.id.btn_sort_usage)?.setOnClickListener {
            appList.sortByDescending { it.isSystem }
            adapter.notifyDataSetChanged()
        }

        loadApps()
        return view
    }

    private fun loadApps() {
        Thread {
            appList.clear()
            val pm = requireContext().packageManager
            val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (app in installed) {
                val label = pm.getApplicationLabel(app).toString()
                val icon = pm.getApplicationIcon(app)
                val isSys = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                appList.add(AppItem(app.packageName, label, icon, isSys))
            }
            appList.sortBy { it.label.lowercase() }
            activity?.runOnUiThread {
                adapter.notifyDataSetChanged()
            }
        }.start()
    }

    private fun handleAppAction(item: AppItem) {
        Thread {
            RenameUtil.executeShizukuCommand("am force-stop ${item.packageName}")
            activity?.runOnUiThread {
                Toast.makeText(context, "Force stopped ${item.label}", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    class AppAdapter(
        private val items: List<AppItem>,
        private val onAction: (AppItem) -> Unit
    ) : RecyclerView.Adapter<AppAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val ivIcon: ImageView = v.findViewById(R.id.iv_app_icon)
            val tvName: TextView = v.findViewById(R.id.tv_app_name)
            val tvPackage: TextView = v.findViewById(R.id.tv_app_package)
            val tvInfo: TextView = v.findViewById(R.id.tv_app_info)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.tvName.text = item.label
            holder.tvPackage.text = item.packageName
            holder.tvInfo.text = if (item.isSystem) "System App" else "User App"
            if (item.icon != null) {
                holder.ivIcon.setImageDrawable(item.icon)
            } else {
                holder.ivIcon.setImageResource(R.mipmap.ic_launcher)
            }
            holder.itemView.setOnClickListener { onAction(item) }
        }

        override fun getItemCount(): Int = items.size
    }
}
