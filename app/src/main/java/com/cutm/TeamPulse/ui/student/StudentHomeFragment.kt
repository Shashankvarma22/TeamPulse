package com.cutm.TeamPulse.ui.student

import android.os.Bundle
import android.view.View
import com.cutm.TeamPulse.databinding.FragmentStudentHomeBinding
import com.cutm.TeamPulse.ui.common.BaseFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class StudentHomeFragment : BaseFragment<FragmentStudentHomeBinding>(FragmentStudentHomeBinding::inflate) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.titleText.text = getString(com.cutm.TeamPulse.R.string.student_home_title)
    }
}
