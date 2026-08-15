package com.cutm.TeamPulse.ui.student

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import com.cutm.TeamPulse.R
import com.cutm.TeamPulse.databinding.BottomSheetTaskDetailBinding
import com.cutm.TeamPulse.domain.model.TaskStatus
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class TaskDetailBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetTaskDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: StudentHomeViewModel by viewModels({ requireParentFragment() })

    private val taskId: String by lazy { arguments?.getString(ARG_TASK_ID) ?: "" }
    private val taskTitle: String by lazy { arguments?.getString(ARG_TITLE) ?: "" }
    private val taskDescription: String by lazy { arguments?.getString(ARG_DESCRIPTION) ?: "" }
    private val taskStatus: TaskStatus by lazy {
        arguments?.getString(ARG_STATUS)?.let { TaskStatus.valueOf(it) } ?: TaskStatus.TODO
    }
    private val taskDueDate: Long by lazy { arguments?.getLong(ARG_DUE_DATE) ?: 0L }
    private val taskWeight: Float by lazy { arguments?.getFloat(ARG_WEIGHT) ?: 1.0f }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetTaskDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.taskTitleText.text = taskTitle
        binding.taskDescriptionText.text = taskDescription
        binding.taskWeightText.text = getString(R.string.task_weight_format, taskWeight)

        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        binding.taskDueDateText.text = dateFormat.format(Date(taskDueDate))

        updateStatusButtons(taskStatus)

        binding.statusTodoButton.setOnClickListener {
            updateStatus(TaskStatus.TODO)
        }

        binding.statusInProgressButton.setOnClickListener {
            updateStatus(TaskStatus.IN_PROGRESS)
        }

        binding.statusDoneButton.setOnClickListener {
            updateStatus(TaskStatus.DONE)
        }
    }

    private fun updateStatusButtons(currentStatus: TaskStatus) {
        binding.statusTodoButton.isChecked = currentStatus == TaskStatus.TODO
        binding.statusInProgressButton.isChecked = currentStatus == TaskStatus.IN_PROGRESS
        binding.statusDoneButton.isChecked = currentStatus == TaskStatus.DONE
    }

    private fun updateStatus(newStatus: TaskStatus) {
        if (newStatus != taskStatus) {
            viewModel.updateTaskStatus(taskId, newStatus)
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_TASK_ID = "task_id"
        private const val ARG_TITLE = "title"
        private const val ARG_DESCRIPTION = "description"
        private const val ARG_STATUS = "status"
        private const val ARG_DUE_DATE = "due_date"
        private const val ARG_WEIGHT = "weight"

        fun newInstance(
            taskId: String,
            title: String,
            description: String,
            status: TaskStatus,
            dueDate: Long,
            weight: Float
        ) = TaskDetailBottomSheet().apply {
            arguments = bundleOf(
                ARG_TASK_ID to taskId,
                ARG_TITLE to title,
                ARG_DESCRIPTION to description,
                ARG_STATUS to status.name,
                ARG_DUE_DATE to dueDate,
                ARG_WEIGHT to weight
            )
        }
    }
}
