package com.example.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.R
import com.google.firebase.firestore.FirebaseFirestore

class MessageFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_message, container, false)
        val prefs = requireActivity().getSharedPreferences("xcel_prefs", Context.MODE_PRIVATE)
        val savedKey = prefs.getString("saved_key", "general") ?: "general"

        val tvMessages = view.findViewById<TextView>(R.id.tv_messages)
        val etMessage = view.findViewById<EditText>(R.id.et_message)
        val btnSend = view.findViewById<Button>(R.id.btn_send)

        FirebaseFirestore.getInstance().collection("messages").document(savedKey)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    val msg = snapshot.getString("adminMessage") ?: "No new messages."
                    tvMessages.text = msg
                } else {
                    tvMessages.text = "Welcome to X-CELESTIALS. System running smoothly."
                }
            }

        btnSend.setOnClickListener {
            val text = etMessage.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener
            FirebaseFirestore.getInstance().collection("messages").document(savedKey)
                .set(mapOf("userReply" to text, "timestamp" to System.currentTimeMillis()))
                .addOnSuccessListener {
                    Toast.makeText(context, "Message sent to admin! ✓", Toast.LENGTH_SHORT).show()
                    etMessage.setText("")
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        return view
    }
}
