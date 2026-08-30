package com.cutm.TeamPulse.ui.teacher

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cutm.TeamPulse.R
import com.cutm.TeamPulse.core.network.ApiResult
import com.cutm.TeamPulse.databinding.BottomSheetCreateProjectBinding
import com.cutm.TeamPulse.domain.repository.ProjectRepository
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class CreateProjectBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetCreateProjectBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TeacherHomeViewModel by viewModels({ requireParentFragment() })

    @Inject
    lateinit var projectRepository: ProjectRepository

    private var selectedDueDate: Long? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetCreateProjectBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupDueDatePicker()
        setupButtons()
        setupValidation()
    }

    private fun setupDueDatePicker() {
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

        binding.dueDateButton.setOnClickListener {
            val calendar = Calendar.getInstance()
            if (selectedDueDate != null) {
                calendar.timeInMillis = selectedDueDate!!
            }

            DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    calendar.set(year, month, dayOfMonth, 23, 59, 59)
                    selectedDueDate = calendar.timeInMillis

                    // Validate: not more than 1 year out
                    val oneYearFromNow = Calendar.getInstance().apply {
                        add(Calendar.YEAR, 1)
                    }.timeInMillis

                    if (selectedDueDate!! > oneYearFromNow) {
                        showError(getString(R.string.create_project_error_date_too_far))
                        selectedDueDate = null
                        binding.dueDateButton.text = getString(R.string.create_project_select_date)
                    } else {
                        binding.dueDateButton.text = dateFormat.format(Date(selectedDueDate!!))
                        clearError()
                    }
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).apply {
                datePicker.minDate = System.currentTimeMillis()
            }.show()
        }
    }

    private fun setupButtons() {
        binding.cancelButton.setOnClickListener {
            dismiss()
        }

        binding.createButton.setOnClickListener {
            createProject()
        }
    }

    private fun setupValidation() {
        binding.projectNameInput.addTextChangedListener {
            clearError()
        }
    }

    private fun createProject() {
        val name = binding.projectNameInput.text?.toString()?.trim()
        val dueDate = selectedDueDate

        // Validation
        when {
            name.isNullOrBlank() -> {
                showError(getString(R.string.create_project_error_no_name))
                binding.projectNameInputLayout.error = getString(R.string.create_project_error_no_name)
                return
            }
            name.length > 100 -> {
                showError(getString(R.string.create_project_error_name_too_long))
                binding.projectNameInputLayout.error = getString(R.string.create_project_error_name_too_long)
                return
            }
            dueDate == null -> {
                showError(getString(R.string.create_project_error_no_due_date))
                return
            }
        }

        // Clear validation errors
        binding.projectNameInputLayout.error = null

        // Create project
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Get current user session
                val session = viewModel.userSession.first { it != null }
                if (session == null) {
                    showError(getString(R.string.create_project_error_session_expired))
                    return@repeatOnLifecycle
                }

                // Generate project ID
                val projectId = UUID.randomUUID().toString()

                // Call repository
                val result = projectRepository.createProject(
                    projectId = projectId,
                    name = name,
                    teacherEmail = session.email,
                    dueDate = dueDate,
                    spreadsheetId = "placeholder-$projectId",
                    driveFolderId = "placeholder-$projectId"
                )

                when (result) {
                    is ApiResult.Success -> {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.create_project_success),
                            Toast.LENGTH_SHORT
                        ).show()
                        dismiss()
                    }
                    is ApiResult.Error -> {
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
        binding.projectNameInputLayout.error = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = CreateProjectBottomSheet()
    }
}
