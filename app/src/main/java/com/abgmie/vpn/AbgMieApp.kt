package com.abgmie.vpn

import android.app.Application
import com.abgmie.vpn.vpn.VpnManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AbgMieApp : Application() {
    // Created at process start so Android "Always-on VPN" can bring the tunnel up without UI.
    @Inject lateinit var vpnManager: VpnManager
}
