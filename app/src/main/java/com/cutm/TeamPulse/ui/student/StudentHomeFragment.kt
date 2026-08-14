package com.cutm.TeamPulse.ui.student

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cutm.TeamPulse.R
import com.cutm.TeamPulse.databinding.FragmentStudentHomeBinding
import com.cutm.TeamPulse.ui.common.BaseFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class StudentHomeFragment : BaseFragment<FragmentStudentHomeBinding>(FragmentStudentHomeBinding::inflate) {

    private val viewModel: StudentHomeViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.userSession.collect { session ->
                        session?.let {
                            binding.greetingText.text = getString(
                                R.string.student_home_title,
                                it.displayName.split(" ").firstOrNull() ?: it.displayName
                            )
                        }
                    }
                }
            }
        }

        // Show empty states by default (data loading not yet implemented)
        showEmptyStates()
    }

    private fun showEmptyStates() {
        // Show team empty state
        binding.teamCard.visibility = View.GONE
        binding.teamEmptyState.visibility = View.VISIBLE

        // Show tasks empty state
        binding.tasksContainer.visibility = View.GONE
        binding.tasksEmptyState.visibility = View.VISIBLE
    }
}
