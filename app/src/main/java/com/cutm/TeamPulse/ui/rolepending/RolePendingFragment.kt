package com.cutm.TeamPulse.ui.rolepending

import android.os.Bundle
import android.view.View
import com.cutm.TeamPulse.databinding.FragmentRolePendingBinding
import com.cutm.TeamPulse.ui.common.BaseFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RolePendingFragment : BaseFragment<FragmentRolePendingBinding>(FragmentRolePendingBinding::inflate) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.messageText.text = getString(com.cutm.TeamPulse.R.string.role_pending_message)
    }
}
