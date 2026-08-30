package com.cutm.TeamPulse.ui.teacher

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cutm.TeamPulse.R
import com.cutm.TeamPulse.core.network.ApiResult
import com.cutm.TeamPulse.databinding.BottomSheetCreateTeamBinding
import com.cutm.TeamPulse.domain.repository.ProjectRepository
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class CreateTeamBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetCreateTeamBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TeacherHomeViewModel by viewModels({ requireParentFragment() })

    @Inject
    lateinit var projectRepository: ProjectRepository

    private val projectId: String
        get() = requireArguments().getString(ARG_PROJECT_ID)
            ?: throw IllegalArgumentException("projectId required")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetCreateTeamBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupButtons()
        setupValidation()
    }

    private fun setupButtons() {
        binding.cancelButton.setOnClickListener {
            dismiss()
        }

        binding.createButton.setOnClickListener {
            createTeam()
        }
    }

    private fun setupValidation() {
        binding.teamNameInput.addTextChangedListener {
            clearError()
        }
    }

    private fun createTeam() {
        val teamName = binding.teamNameInput.text?.toString()?.trim()

        // Validation
        when {
            teamName.isNullOrBlank() -> {
                showError(getString(R.string.create_team_error_no_name))
                binding.teamNameInputLayout.error = getString(R.string.create_team_error_no_name)
                return
            }
            teamName.length > 100 -> {
                showError(getString(R.string.create_team_error_name_too_long))
                binding.teamNameInputLayout.error = getString(R.string.create_team_error_name_too_long)
                return
            }
        }

        // Clear validation errors
        binding.teamNameInputLayout.error = null

        // Create team
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Get current user session
                val session = viewModel.userSession.first { it != null }
                if (session == null) {
                    showError(getString(R.string.create_team_error_session_expired))
                    return@repeatOnLifecycle
                }

                // Generate team ID
                val teamId = UUID.randomUUID().toString()

                // Disable button during operation
                binding.createButton.isEnabled = false

                // Call repository
                val result = projectRepository.createTeam(
                    teamId = teamId,
                    projectId = projectId,
                    teamName = teamName
                )

                when (result) {
                    is ApiResult.Success -> {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.create_team_success),
                            Toast.LENGTH_SHORT
                        ).show()
                        dismiss()
                    }
                    is ApiResult.Error -> {
                        binding.createButton.isEnabled = true
                        showError(result.message)
                    }
                }
            }
        }
    }

    private fun showError(message: String) {
        binding.errorText.text = message
        binding.errorText.isVisible = true
    }

    private fun clearError() {
        binding.errorText.isVisible = false
        binding.teamNameInputLayout.error = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_PROJECT_ID = "projectId"

        fun newInstance(projectId: String) = CreateTeamBottomSheet().apply {
            arguments = bundleOf(ARG_PROJECT_ID to projectId)
        }
    }
}
