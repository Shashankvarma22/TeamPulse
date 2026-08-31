package com.cutm.TeamPulse.ui.teacher

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cutm.TeamPulse.R
import com.cutm.TeamPulse.databinding.BottomSheetEditTaskBinding
import com.cutm.TeamPulse.domain.model.Student
import com.cutm.TeamPulse.domain.model.TaskStatus
import com.cutm.TeamPulse.domain.model.Team
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class EditTaskBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetEditTaskBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TeacherTaskListViewModel by viewModels({ requireParentFragment() })

    private val taskId: String by lazy { arguments?.getString(ARG_TASK_ID) ?: "" }
    private val taskTitle: String by lazy { arguments?.getString(ARG_TITLE) ?: "" }
    private val taskDescription: String by lazy { arguments?.getString(ARG_DESCRIPTION) ?: "" }
    private val taskStatus: TaskStatus by lazy {
        arguments?.getString(ARG_STATUS)?.let { TaskStatus.valueOf(it) } ?: TaskStatus.TODO
    }
    private val taskDueDate: Long by lazy { arguments?.getLong(ARG_DUE_DATE) ?: 0L }
    private val taskWeight: Float by lazy { arguments?.getFloat(ARG_WEIGHT) ?: 1.0f }
    private val originalTeamId: String by lazy { arguments?.getString(ARG_TEAM_ID) ?: "" }
    private val projectId: String by lazy { arguments?.getString(ARG_PROJECT_ID) ?: "" }
    private val assigneeEmail: String by lazy { arguments?.getString(ARG_ASSIGNEE_EMAIL) ?: "" }
    private val remoteRowIndex: Int? by lazy {
        if (arguments?.containsKey(ARG_REMOTE_ROW_INDEX) == true) {
            arguments?.getInt(ARG_REMOTE_ROW_INDEX)
        } else null
    }

    private var selectedDueDate: Long = 0L
    private var selectedStatus: TaskStatus = TaskStatus.TODO
    private var selectedTeamId: String = ""
    private var selectedAssigneeEmail: String = ""
    private var availableTeams: List<Team> = emptyList()
    private var teamMembers: List<Student> = emptyList()
    private var isAssigneeStale: Boolean = false
    private var teamMembersLoaded: Boolean = false
    private var memberObserverJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetEditTaskBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        selectedDueDate = taskDueDate
        selectedStatus = taskStatus
        selectedTeamId = originalTeamId
        selectedAssigneeEmail = assigneeEmail

        populateFields()
        setupTeamDropdown()
        setupDueDatePicker()
        setupStatusButtons()
        setupAssigneeDropdown()
        setupButtons()
        setupValidation()
    }

    private fun populateFields() {
        binding.taskTitleInput.setText(taskTitle)
        binding.taskDescriptionInput.setText(taskDescription)

        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        binding.dueDateButton.text = dateFormat.format(Date(taskDueDate))

        updateStatusButtons(taskStatus)
    }

    private fun setupTeamDropdown() {
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
        if (teams.isEmpty()) {
            binding.teamDropdownLayout.isEnabled = false
            binding.teamDropdown.setText(getString(R.string.task_team_not_found))
            return
        }

        val teamNames = teams.map { it.teamName }
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            teamNames
        )
        binding.teamDropdown.setAdapter(adapter)
        binding.teamDropdownLayout.isEnabled = true

        // Default to current team if exists
        val currentTeam = teams.find { it.teamId == selectedTeamId }
        if (currentTeam != null) {
            binding.teamDropdown.setText(currentTeam.teamName, false)
            // Load members for current team
            loadTeamMembers(selectedTeamId)
        } else {
            // Current team deleted - no selection
            binding.teamDropdown.setText("", false)
            selectedTeamId = ""
            updateAssigneeDropdown()  // Show "No members" when no team selected
        }

        binding.teamDropdown.setOnItemClickListener { _, _, position, _ ->
            val newTeamId = teams[position].teamId
            onTeamChangeRequested(newTeamId)
        }
    }

    private fun onTeamChangeRequested(newTeamId: String) {
        if (newTeamId == selectedTeamId) {
            return // No change
        }

        // If current assignee is not empty, show confirmation
        if (selectedAssigneeEmail.isNotEmpty()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.change_team_warning_title)
                .setMessage(R.string.change_team_warning_message)
                .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                    // Revert team selection in UI
                    val currentTeam = availableTeams.find { it.teamId == selectedTeamId }
                    if (currentTeam != null) {
                        binding.teamDropdown.setText(currentTeam.teamName, false)
                    }
                    dialog.dismiss()
                }
                .setPositiveButton(R.string.change_team_confirm) { dialog, _ ->
                    applyTeamChange(newTeamId)
                    dialog.dismiss()
                }
                .show()
        } else {
            // No assignee, change directly
            applyTeamChange(newTeamId)
        }
    }

    private fun applyTeamChange(newTeamId: String) {
        selectedTeamId = newTeamId
        selectedAssigneeEmail = "" // Clear assignee
        isAssigneeStale = false
        teamMembersLoaded = false
        loadTeamMembers(newTeamId)
        clearError()
    }

    private fun loadTeamMembers(teamId: String) {
        // Cancel previous member observer
        memberObserverJob?.cancel()
        
        memberObserverJob = viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.getTeamMembers(teamId).collectLatest { students ->
                    teamMembers = students
                    teamMembersLoaded = true
                    updateAssigneeDropdown()
                }
            }
        }
    }

    private fun setupDueDatePicker() {
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

        binding.dueDateButton.setOnClickListener {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = selectedDueDate

            DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    calendar.set(year, month, dayOfMonth, 23, 59, 59)
                    selectedDueDate = calendar.timeInMillis
                    binding.dueDateButton.text = dateFormat.format(Date(selectedDueDate))
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

    private fun setupStatusButtons() {
        binding.statusTodoButton.setOnClickListener {
            selectedStatus = TaskStatus.TODO
            updateStatusButtons(TaskStatus.TODO)
        }

        binding.statusInProgressButton.setOnClickListener {
            selectedStatus = TaskStatus.IN_PROGRESS
            updateStatusButtons(TaskStatus.IN_PROGRESS)
        }

        binding.statusDoneButton.setOnClickListener {
            selectedStatus = TaskStatus.DONE
            updateStatusButtons(TaskStatus.DONE)
        }
    }

    private fun updateStatusButtons(status: TaskStatus) {
        binding.statusTodoButton.isChecked = status == TaskStatus.TODO
        binding.statusInProgressButton.isChecked = status == TaskStatus.IN_PROGRESS
        binding.statusDoneButton.isChecked = status == TaskStatus.DONE
    }

    private fun setupAssigneeDropdown() {
        binding.assigneeDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedAssigneeEmail = if (position == 0) {
                "" // Unassigned
            } else {
                teamMembers[position - 1].studentEmail
            }
            // Explicit user action resolved stale state
            isAssigneeStale = false
            clearError()
        }
    }

    private fun updateAssigneeDropdown() {
        val items = mutableListOf(getString(R.string.task_assign_unassigned))

        if (selectedTeamId.isEmpty()) {
            // No team selected yet
            binding.assigneeDropdownLayout.isEnabled = false
            binding.assigneeDropdown.setText("No members")
            isAssigneeStale = false
        } else if (!teamMembersLoaded) {
            // Team members not loaded yet - show loading state
            binding.assigneeDropdownLayout.isEnabled = false
            binding.assigneeDropdown.setText("Loading team members...")
            isAssigneeStale = false
        } else if (teamMembers.isEmpty()) {
            // Team loaded but has zero members - treat as stale if assigned
            binding.assigneeDropdownLayout.isEnabled = true
            if (selectedAssigneeEmail.isEmpty()) {
                isAssigneeStale = false
                binding.assigneeDropdown.setText(getString(R.string.task_assign_unassigned))
            } else {
                // Task is assigned but team has no members - stale
                isAssigneeStale = true
                binding.assigneeDropdown.setText("$selectedAssigneeEmail — team has no members")
            }
        } else {
            // Team members loaded and present
            binding.assigneeDropdownLayout.isEnabled = true
            items.addAll(teamMembers.map { it.displayName })

            // Set current selection - DO NOT mutate selectedAssigneeEmail
            val currentSelection = if (selectedAssigneeEmail.isEmpty()) {
                isAssigneeStale = false
                getString(R.string.task_assign_unassigned)
            } else {
                val matchingStudent = teamMembers.find { it.studentEmail == selectedAssigneeEmail }
                if (matchingStudent != null) {
                    isAssigneeStale = false
                    matchingStudent.displayName
                } else {
                    // Assignee no longer in team - mark as stale but preserve email
                    isAssigneeStale = true
                    "$selectedAssigneeEmail — no longer on team"
                }
            }
            binding.assigneeDropdown.setText(currentSelection, false)
        }

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, items)
        binding.assigneeDropdown.setAdapter(adapter)
    }

    private fun setupButtons() {
        binding.cancelButton.setOnClickListener {
            dismiss()
        }

        binding.saveButton.setOnClickListener {
            saveTask()
        }

        binding.deleteButton.setOnClickListener {
            showDeleteConfirmation()
        }
    }

    private fun setupValidation() {
        binding.taskTitleInput.addTextChangedListener {
            clearError()
        }
    }

    private fun saveTask() {
        val title = binding.taskTitleInput.text?.toString()?.trim()
        val description = binding.taskDescriptionInput.text?.toString()?.trim() ?: ""

        // Validation
        if (title.isNullOrBlank()) {
            showError(getString(R.string.create_task_error_no_title))
            binding.taskTitleInputLayout.error = getString(R.string.create_task_error_no_title)
            return
        }

        // Validate team selected
        if (selectedTeamId.isEmpty()) {
            showError("Please select a team")
            return
        }

        // Validate team members have loaded before allowing save
        if (!teamMembersLoaded) {
            showError("Please wait for team members to load before saving.")
            return
        }

        // Validate assignee is not stale
        if (isAssigneeStale) {
            showError("This task is assigned to someone no longer on the team. Please select 'Unassigned' or assign to a current team member.")
            return
        }

        // Clear validation errors
        binding.taskTitleInputLayout.error = null

        // Update task through ViewModel
        viewModel.updateTask(
            taskId = taskId,
            title = title,
            description = description,
            dueDate = selectedDueDate,
            status = selectedStatus,
            teamId = selectedTeamId,
            projectId = projectId,
            assigneeEmail = selectedAssigneeEmail,
            weight = taskWeight,
            remoteRowIndex = remoteRowIndex
        )

        Toast.makeText(requireContext(), R.string.edit_task_success, Toast.LENGTH_SHORT).show()
        dismiss()
    }

    private fun showDeleteConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_task_confirm_title)
            .setMessage(R.string.delete_task_confirm_message)
            .setNegativeButton(R.string.create_task_button_cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton(R.string.delete_task_confirm_delete) { dialog, _ ->
                deleteTask()
                dialog.dismiss()
            }
            .show()
    }

    private fun deleteTask() {
        viewModel.deleteTask(taskId)
        Toast.makeText(requireContext(), R.string.delete_task_success, Toast.LENGTH_SHORT).show()
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
        memberObserverJob?.cancel()
        _binding = null
    }

    companion object {
        private const val ARG_TASK_ID = "task_id"
        private const val ARG_TITLE = "title"
        private const val ARG_DESCRIPTION = "description"
        private const val ARG_STATUS = "status"
        private const val ARG_DUE_DATE = "due_date"
        private const val ARG_WEIGHT = "weight"
        private const val ARG_TEAM_ID = "team_id"
        private const val ARG_PROJECT_ID = "project_id"
        private const val ARG_ASSIGNEE_EMAIL = "assignee_email"
        private const val ARG_REMOTE_ROW_INDEX = "remote_row_index"

        fun newInstance(
            taskId: String,
            title: String,
            description: String,
            status: TaskStatus,
            dueDate: Long,
            weight: Float,
            teamId: String,
            projectId: String,
            assigneeEmail: String,
            remoteRowIndex: Int?
        ) = EditTaskBottomSheet().apply {
            arguments = bundleOf(
                ARG_TASK_ID to taskId,
                ARG_TITLE to title,
                ARG_DESCRIPTION to description,
                ARG_STATUS to status.name,
                ARG_DUE_DATE to dueDate,
                ARG_WEIGHT to weight,
                ARG_TEAM_ID to teamId,
                ARG_PROJECT_ID to projectId,
                ARG_ASSIGNEE_EMAIL to assigneeEmail
            ).apply {
                remoteRowIndex?.let { putInt(ARG_REMOTE_ROW_INDEX, it) }
            }
        }
    }
}
