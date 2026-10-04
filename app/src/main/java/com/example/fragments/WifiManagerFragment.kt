package com.example.fragments

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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

class WifiManagerFragment : Fragment() {

    data class WifiItem(
        val ssid: String,
        val psk: String
    )

    private val wifiList = mutableListOf<WifiItem>()
    private lateinit var adapter: WifiAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_wifi_manager, container, false)
        view.findViewById<ImageView>(R.id.iv_back)?.setOnClickListener {
            (activity as? MainActivity)?.switchFragment(HomeFragment())
        }

        val rv = view.findViewById<RecyclerView>(R.id.rv_wifi)
        rv.layoutManager = LinearLayoutManager(context)
        adapter = WifiAdapter(wifiList) { item ->
            val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Wifi Password", item.psk))
            Toast.makeText(context, "Copied password for ${item.ssid}", Toast.LENGTH_SHORT).show()
        }
        rv.adapter = adapter

        loadWifiPasswords()
        return view
    }

    private fun loadWifiPasswords() {
        Thread {
            wifiList.clear()
            if (RenameUtil.useShizukuOps && RenameUtil.shizukuAvailable()) {
                val out = RenameUtil.executeShizukuCommandWithOutput("cat /data/misc/wifi/WifiConfigStore.xml 2>/dev/null")
                var currentSsid: String? = null
                for (line in out.lines()) {
                    val l = line.trim()
                    if (l.contains("<string name=\"SSID\">")) {
                        currentSsid = l.substringAfter("<string name=\"SSID\">&quot;").substringBefore("&quot;</string>")
                    }
                    if (l.contains("<string name=\"PreSharedKey\">") && currentSsid != null) {
                        val psk = l.substringAfter("<string name=\"PreSharedKey\">&quot;").substringBefore("&quot;</string>")
                        wifiList.add(WifiItem(currentSsid, psk))
                        currentSsid = null
                    }
                }
            }
            activity?.runOnUiThread {
                adapter.notifyDataSetChanged()
            }
        }.start()
    }

    class WifiAdapter(
        private val items: List<WifiItem>,
        private val onClick: (WifiItem) -> Unit
    ) : RecyclerView.Adapter<WifiAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvSsid: TextView = v.findViewById(R.id.tv_wifi_ssid)
            val tvPsk: TextView = v.findViewById(R.id.tv_wifi_password)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_wifi, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.tvSsid.text = item.ssid
            holder.tvPsk.text = "Password: ${item.psk}"
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount(): Int = items.size
    }
}
