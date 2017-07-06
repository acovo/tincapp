package org.pacien.tincapp.activities

import android.content.Intent
import android.os.Bundle
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.app.AlertDialog
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import kotlinx.android.synthetic.main.base.*
import kotlinx.android.synthetic.main.dialog_text_monopsace.view.*
import kotlinx.android.synthetic.main.fragment_network_status_header.*
import kotlinx.android.synthetic.main.page_status.*
import org.pacien.tincapp.R
import org.pacien.tincapp.commands.Tinc
import org.pacien.tincapp.service.TincVpnService
import org.pacien.tincapp.service.VpnInterfaceConfiguration
import org.pacien.tincapp.utils.setElements
import org.pacien.tincapp.utils.setText
import java.util.*
import kotlin.concurrent.timerTask

/**
 * @author pacien
 */
class StatusActivity : BaseActivity(), AdapterView.OnItemClickListener, SwipeRefreshLayout.OnRefreshListener {

    private var nodeListAdapter: ArrayAdapter<String>? = null
    private var refreshTimer: Timer? = null
    private var updateView: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        nodeListAdapter = ArrayAdapter<String>(this, R.layout.fragment_list_item)
        refreshTimer = Timer(true)

        layoutInflater.inflate(R.layout.page_status, main_content)
        node_list_wrapper.setOnRefreshListener(this)
        node_list.addHeaderView(layoutInflater.inflate(R.layout.fragment_network_status_header, node_list, false), null, false)
        node_list.addFooterView(View(this), null, false)
        node_list.emptyView = node_list_empty
        node_list.onItemClickListener = this
        node_list.adapter = nodeListAdapter
    }

    override fun onCreateOptionsMenu(m: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_status, m)
        return super.onCreateOptionsMenu(m)
    }

    override fun onDestroy() {
        super.onDestroy()
        refreshTimer?.cancel()
        nodeListAdapter = null
        refreshTimer = null
    }

    override fun onStart() {
        super.onStart()
        writeNetworkInfo(TincVpnService.getCurrentInterfaceCfg() ?: VpnInterfaceConfiguration())
        updateView = true
        onRefresh()
        updateNodeList()
    }

    override fun onStop() {
        super.onStop()
        updateView = false
    }

    override fun onResume() {
        super.onResume()
        if (!TincVpnService.isConnected()) openStartActivity()
    }

    override fun onRefresh() {
        val nodes = getNodeNames()
        runOnUiThread {
            nodeListAdapter?.setElements(nodes)
            node_list_wrapper.isRefreshing = false
            if (!TincVpnService.isConnected()) openStartActivity()
        }
    }

    override fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        val nodeName = (view as TextView).text.toString()
        val dialogTextView = layoutInflater.inflate(R.layout.dialog_text_monopsace, main_content, false)
        dialogTextView.dialog_text_monospace.text = Tinc.info(TincVpnService.getCurrentNetName()!!, nodeName)

        AlertDialog.Builder(this)
                .setTitle(R.string.title_node_info)
                .setView(dialogTextView)
                .setPositiveButton(R.string.action_close) { _, _ -> /* nop */ }
                .show()
    }

    fun writeNetworkInfo(cfg: VpnInterfaceConfiguration) {
        text_network_name.text = TincVpnService.getCurrentNetName() ?: getString(R.string.value_none)
        text_network_ip_addresses.setText(cfg.addresses.map { it.toString() })
        text_network_routes.setText(cfg.routes.map { it.toString() })
        text_network_dns_servers.setText(cfg.dnsServers)
        text_network_search_domains.setText(cfg.searchDomains)
        text_network_allow_bypass.text = getString(if (cfg.allowBypass) R.string.value_yes else R.string.value_no)
        block_network_allowed_applications.visibility = if (cfg.allowedApplications.isNotEmpty()) View.VISIBLE else View.GONE
        text_network_allowed_applications.setText(cfg.allowedApplications)
        block_network_disallowed_applications.visibility = if (cfg.disallowedApplications.isNotEmpty()) View.VISIBLE else View.GONE
        text_network_disallowed_applications.setText(cfg.disallowedApplications)
    }

    fun updateNodeList() {
        refreshTimer?.schedule(timerTask {
            onRefresh()
            if (updateView) updateNodeList()
        }, REFRESH_RATE)
    }

    fun stopVpn(@Suppress("UNUSED_PARAMETER") i: MenuItem) {
        TincVpnService.stopVpn()
        openStartActivity()
        finish()
    }

    fun openStartActivity() = startActivity(Intent(this, StartActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))

    companion object {
        private val REFRESH_RATE = 5000L

        fun getNodeNames() =
                if (TincVpnService.isConnected()) Tinc.dumpNodes(TincVpnService.getCurrentNetName()!!).map { it.substringBefore(" ") }
                else emptyList()
    }

}
