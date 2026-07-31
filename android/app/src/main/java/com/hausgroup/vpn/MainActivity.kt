package com.hausgroup.vpn

import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.hausgroup.vpn.ui.ConnectScreen
import com.hausgroup.vpn.ui.theme.HausVpnTheme

class MainActivity : ComponentActivity() {

    private val viewModel: VpnViewModel by viewModels()

    // OS-level VPN consent dialog. On approval we start the tunnel.
    private val vpnPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            viewModel.toggle()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HausVpnTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ConnectScreen(
                        viewModel = viewModel,
                        onConnectRequested = { requestVpnThenToggle() },
                    )
                }
            }
        }
    }

    /** Ask for VPN consent if needed, otherwise toggle straight away. */
    private fun requestVpnThenToggle() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermission.launch(intent)
        } else {
            viewModel.toggle()
        }
    }
}
