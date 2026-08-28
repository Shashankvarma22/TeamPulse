package com.cutm.TeamPulse.ui.teacher

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cutm.TeamPulse.R
import com.cutm.TeamPulse.databinding.BottomSheetCreateTaskBinding
import com.cutm.TeamPulse.domain.model.Team
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class CreateTaskBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetCreateTaskBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TeacherTaskListViewModel by viewModels({ requireParentFragment() })

    private var availableTeams: List<Team> = emptyList()
    private var selectedTeamId: String? = null
    private var selectedDueDate: Long? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetCreateTaskBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTeamSelection()
        setupDueDatePicker()
        setupButtons()
        setupValidation()
    }

    private fun setupTeamSelection() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.availableTeams.collect { teams ->
                    availableTeams = teams
                    handleTeamSelection(teams)
                }
            }
        }
    }

    private fun handleTeamSelection(teams: List<Team>) {
        when (teams.size) {
            0 -> {
                // No teams available
                binding.teamSpinner.isEnabled = false
                binding.teamSpinner.setText(getString(R.string.create_task_error_no_teams))
                binding.saveButton.isEnabled = false
                showError(getString(R.string.create_task_error_no_teams))
            }
            1 -> {
                // Single team: auto-select
                val team = teams.first()
                selectedTeamId = team.teamId
                binding.teamSpinner.setText(team.teamName)
                binding.teamSpinner.isEnabled = false
            }
            else -> {
                // Multiple teams: user must select
                binding.teamSpinner.isEnabled = true
                val teamNames = teams.map { it.teamName }
                val adapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_dropdown_item_1line,
                    teamNames
                )
                binding.teamSpinner.setAdapter(adapter)
                
                binding.teamSpinner.setOnItemClickListener { _, _, position, _ ->
                    selectedTeamId = teams[position].teamId
                    clearError()
                }
            }
        }
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
                    binding.dueDateButton.text = dateFormat.format(Date(selectedDueDate!!))
                    clearError()
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

        binding.saveButton.setOnClickListener {
            createTask()
        }
    }

    private fun setupValidation() {
        binding.taskTitleInput.addTextChangedListener {
            clearError()
        }
    }

    private fun createTask() {
        val title = binding.taskTitleInput.text?.toString()?.trim()
        val description = binding.taskDescriptionInput.text?.toString()?.trim() ?: ""
        val teamId = selectedTeamId
        val dueDate = selectedDueDate

        // Validation
        when {
            title.isNullOrBlank() -> {
                showError(getString(R.string.create_task_error_no_title))
                binding.taskTitleInputLayout.error = getString(R.string.create_task_error_no_title)
                return
            }
            dueDate == null -> {
                showError(getString(R.string.create_task_error_no_due_date))
                return
            }
            teamId == null -> {
                showError(getString(R.string.create_task_error_select_team))
                return
            }
        }

        // Clear validation errors
        binding.taskTitleInputLayout.error = null

        // Create task through ViewModel
        viewModel.createTask(
            title = title,
            description = description,
            teamId = teamId,
            dueDate = dueDate
        )

        Toast.makeText(requireContext(), R.string.create_task_success, Toast.LENGTH_SHORT).show()
        dismiss()
    }

    private fun showError(message: String) {
        binding.errorText.text = message
        binding.errorText.isVisible = true
    }

    private fun clearError() {
        binding.errorText.isVisible = false
        binding.taskTitleInputLayout.error = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = CreateTaskBottomSheet()
    }
}
