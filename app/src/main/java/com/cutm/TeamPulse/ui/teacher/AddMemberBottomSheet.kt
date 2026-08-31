package com.cutm.TeamPulse.ui.teacher

import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cutm.TeamPulse.R
import com.cutm.TeamPulse.databinding.BottomSheetAddMemberBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AddMemberBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAddMemberBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProjectDetailViewModel by viewModels({ requireParentFragment() })

    private val teamId: String by lazy { arguments?.getString(ARG_TEAM_ID) ?: "" }
    private val teamName: String by lazy { arguments?.getString(ARG_TEAM_NAME) ?: "" }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetAddMemberBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.sheetTitleText.text = getString(R.string.add_member_title, teamName)

        setupButtons()
        setupValidation()
        observeResults()
    }

    private fun observeResults() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch {
                    viewModel.addMemberSuccess.collect { message ->
                        Toast.makeText(requireContext(), R.string.add_member_success, Toast.LENGTH_SHORT).show()
                        dismiss()
                    }
                }
                launch {
                    viewModel.memberOperationError.collect { errorMessage ->
                        showError(errorMessage)
                    }
                }
            }
        }
    }

    private fun setupButtons() {
        binding.cancelButton.setOnClickListener {
            dismiss()
        }

        binding.addButton.setOnClickListener {
            validateAndAddMember()
        }
    }

    private fun setupValidation() {
        binding.emailInput.addTextChangedListener {
            clearError()
        }
        binding.nameInput.addTextChangedListener {
            clearError()
        }
    }

    private fun validateAndAddMember() {
        val email = binding.emailInput.text?.toString()?.trim() ?: ""
        val displayName = binding.nameInput.text?.toString()?.trim() ?: ""

        // Clear previous errors
        binding.emailInputLayout.error = null
        binding.nameInputLayout.error = null

        when {
            email.isEmpty() -> {
                showError(getString(R.string.error_email_required))
                binding.emailInputLayout.error = getString(R.string.error_email_required)
                return
            }
            email != email.lowercase() -> {
                showError(getString(R.string.error_email_must_be_lowercase))
                binding.emailInputLayout.error = getString(R.string.error_email_must_be_lowercase)
                return
            }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                showError(getString(R.string.error_email_invalid))
                binding.emailInputLayout.error = getString(R.string.error_email_invalid)
                return
            }
            displayName.isEmpty() -> {
                showError(getString(R.string.error_name_required))
                binding.nameInputLayout.error = getString(R.string.error_name_required)
                return
            }
        }

        // Call ViewModel to add member (result will be delivered via Flow)
        viewModel.addMemberToTeam(teamId, email, displayName)
    }

    private fun showError(message: String) {
        binding.errorText.text = message
        binding.errorText.isVisible = true
    }

    private fun clearError() {
        binding.errorText.isVisible = false
        binding.emailInputLayout.error = null
        binding.nameInputLayout.error = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_TEAM_ID = "team_id"
        private const val ARG_TEAM_NAME = "team_name"

        fun newInstance(teamId: String, teamName: String) = AddMemberBottomSheet().apply {
            arguments = bundleOf(
                ARG_TEAM_ID to teamId,
                ARG_TEAM_NAME to teamName
            )
        }
    }
}
