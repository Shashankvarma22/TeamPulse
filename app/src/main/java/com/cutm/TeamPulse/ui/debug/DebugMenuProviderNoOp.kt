package com.cutm.TeamPulse.ui.debug

import android.view.View
import androidx.fragment.app.Fragment
import com.cutm.TeamPulse.core.security.KeystoreRecoveryManager

/**
 * No-op implementation for release builds.
 * Does nothing when attached — effectively disables debug menu.
 */
class DebugMenuProviderNoOp : DebugMenuProvider {
    override fun attach(
        fragment: Fragment,
        keystoreRecoveryManager: KeystoreRecoveryManager,
        greetingView: View
    ) {
        // No-op: debug menu is disabled in release builds
    }
}
