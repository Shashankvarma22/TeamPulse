package com.cutm.TeamPulse.ui.debug

import android.view.View
import androidx.fragment.app.Fragment
import com.cutm.TeamPulse.core.security.KeystoreRecoveryManager

/**
 * Provides debug menu functionality.
 * Implemented differently in debug vs release builds.
 */
interface DebugMenuProvider {
    /**
     * Attach debug menu to a fragment's view.
     * @param fragment The fragment to attach to
     * @param keystoreRecoveryManager For testing Keystore fallback
     * @param greetingView The view to attach long-press listener to
     */
    fun attach(
        fragment: Fragment,
        keystoreRecoveryManager: KeystoreRecoveryManager,
        greetingView: View
    )
}
