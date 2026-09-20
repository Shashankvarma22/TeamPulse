package com.cutm.TeamPulse.ui.debug

import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.cutm.TeamPulse.core.security.KeystoreRecoveryManager
import android.util.Log

/**
 * Debug implementation: provides functional debug menu for testing.
 */
class DebugMenuProviderImpl : DebugMenuProvider {
    
    override fun attach(
        fragment: Fragment,
        keystoreRecoveryManager: KeystoreRecoveryManager,
        greetingView: View
    ) {
        greetingView.setOnLongClickListener {
            val options = arrayOf("Force Keystore Null (Testing)")
            val currentState = keystoreRecoveryManager.getDebugForceNullKey()
            
            AlertDialog.Builder(fragment.requireContext())
                .setTitle("Debug Menu")
                .setMultiChoiceItems(
                    options,
                    booleanArrayOf(currentState)
                ) { _, which, isChecked ->
                    if (which == 0) {
                        keystoreRecoveryManager.setDebugForceNullKey(isChecked)
                        Toast.makeText(
                            fragment.requireContext(),
                            "DEBUG: Keystore forced to null - fallback will trigger on next database access",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .setNegativeButton("Close", null)
                .show()
            
            true
        }
    }
}
