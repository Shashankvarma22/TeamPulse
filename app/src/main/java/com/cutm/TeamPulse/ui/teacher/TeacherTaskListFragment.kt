package com.cutm.TeamPulse.ui.teacher

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.navArgs
import com.cutm.TeamPulse.R
import com.cutm.TeamPulse.databinding.FragmentTeacherTaskListBinding
import com.cutm.TeamPulse.ui.common.BaseFragment
import com.cutm.TeamPulse.ui.common.TaskItemView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TeacherTaskListFragment : BaseFragment<FragmentTeacherTaskListBinding>(
    FragmentTeacherTaskListBinding::inflate
) {

    private val viewModel: TeacherTaskListViewModel by viewModels()
    private val args: TeacherTaskListFragmentArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.projectNameText.text = args.projectName

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.tasks.collect { tasks ->
                        renderTasks(tasks)
                    }
                }

                launch {
                    viewModel.teamName.collect { name ->
                        binding.teamNameText.text = name ?: ""
                        binding.teamNameText.isVisible = name != null
                    }
                }
            }
        }
    }

    private fun renderTasks(tasks: List<TeacherTaskData>) {
        binding.tasksContainer.removeAllViews()

        if (tasks.isEmpty()) {
            binding.tasksContainer.isVisible = false
            binding.tasksEmptyState.isVisible = true
        } else {
            binding.tasksContainer.isVisible = true
            binding.tasksEmptyState.isVisible = false

            tasks.forEach { taskData ->
                val taskView = TaskItemView(requireContext()).apply {
                    val dueDateText = when {
                        taskData.daysUntilDue < 0 -> getString(R.string.overdue)
                        taskData.daysUntilDue == 0 -> getString(R.string.due_today)
                        else -> getString(R.string.due_in_days, taskData.daysUntilDue)
                    }

                    setTaskData(
                        title = taskData.task.title,
                        status = taskData.task.status,
                        dueDate = dueDateText
                    )
                }

                val layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = resources.getDimensionPixelSize(R.dimen.spacing_sm)
                }

                binding.tasksContainer.addView(taskView, layoutParams)
            }
        }
    }
}
