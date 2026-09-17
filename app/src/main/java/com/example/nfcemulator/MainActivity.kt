package com.example.nfcemulator

import android.app.Activity
import android.content.Context
import android.nfc.NfcAdapter
import android.os.Bundle
import android.widget.*

class MainActivity : Activity() {

    private var nfcAdapter: NfcAdapter? = null
    private val urlList = ArrayList<String>()
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var tvActiveUrl: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        val etUrlInput = findViewById<EditText>(R.id.etUrlInput)
        val btnAddUrl = findViewById<Button>(R.id.btnAddUrl)
        val lvUrls = findViewById<ListView>(R.id.lvUrls)
        tvActiveUrl = findViewById(R.id.tvActiveUrl)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, urlList)
        lvUrls.adapter = adapter

        loadSavedData()

        btnAddUrl.setOnClickListener {
            var url = etUrlInput.text.toString().trim()
            if (url.isNotEmpty()) {
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "https://$url"
                }
                if (!urlList.contains(url)) {
                    urlList.add(url)
                    adapter.notifyDataSetChanged()
                    saveUrlList()
                    etUrlInput.text.clear()
                    Toast.makeText(this, "Enlace añadido", Toast.LENGTH_SHORT).show()
                }
            }
        }

        lvUrls.setOnItemClickListener { _, _, position, _ ->
            val selectedUrl = urlList[position]
            setActiveUrl(selectedUrl)
        }
    }

    private fun setActiveUrl(url: String) {
        val prefs = getSharedPreferences("NfcPrefs", Context.MODE_PRIVATE)
        prefs.edit().putString("ACTIVE_URL", url).apply()
        tvActiveUrl.text = "Activo: $url"
        Toast.makeText(this, "Enlace activo: $url", Toast.LENGTH_SHORT).show()
    }

    private fun saveUrlList() {
        val prefs = getSharedPreferences("NfcPrefs", Context.MODE_PRIVATE)
        val set = HashSet<String>(urlList)
        prefs.edit().putStringSet("URL_LIST", set).apply()
    }

    private fun loadSavedData() {
        val prefs = getSharedPreferences("NfcPrefs", Context.MODE_PRIVATE)
        val savedSet = prefs.getStringSet("URL_LIST", null)
        val activeUrl = prefs.getString("ACTIVE_URL", null)

        if (savedSet != null) {
            urlList.clear()
            urlList.addAll(savedSet)
            adapter.notifyDataSetChanged()
        }

        if (activeUrl != null) {
            tvActiveUrl.text = "Activo: $activeUrl"
        } else if (urlList.isNotEmpty()) {
            setActiveUrl(urlList[0])
        }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableReaderMode(
            this,
            { },
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or NfcAdapter.FLAG_READER_NO_DISPATCH,
            null
        )
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
    }
}
