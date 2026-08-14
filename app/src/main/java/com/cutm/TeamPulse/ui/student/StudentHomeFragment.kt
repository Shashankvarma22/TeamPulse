package com.cutm.TeamPulse.ui.student

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cutm.TeamPulse.R
import com.cutm.TeamPulse.databinding.FragmentStudentHomeBinding
import com.cutm.TeamPulse.ui.common.BaseFragment
import com.cutm.TeamPulse.ui.common.TaskItemView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class StudentHomeFragment : BaseFragment<FragmentStudentHomeBinding>(FragmentStudentHomeBinding::inflate) {

    private val viewModel: StudentHomeViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Collect user session for greeting
                launch {
                    viewModel.userSession.collect { session ->
                        session?.let {
                            binding.greetingText.text = getString(
                                R.string.student_home_greeting,
                                it.displayName.split(" ").firstOrNull() ?: it.displayName
                            )
                        }
                    }
                }

                // Collect current project data
                launch {
                    viewModel.currentProject.collect { projectData ->
                        renderProject(projectData)
                    }
                }

                // Collect student's tasks
                launch {
                    viewModel.myTasks.collect { tasks ->
                        renderTasks(tasks)
                    }
                }
            }
        }
    }

    private fun renderProject(projectData: CurrentProjectData?) {
        if (projectData == null) {
            binding.projectFocalCard.isVisible = false
            binding.projectEmptyState.isVisible = true
        } else {
            binding.projectFocalCard.isVisible = true
            binding.projectEmptyState.isVisible = false

            binding.projectNameText.text = projectData.project.name
            binding.teamNameText.text = projectData.team.teamName

            val progress = if (projectData.totalTasks > 0) {
                (projectData.completedTasks * 100) / projectData.totalTasks
            } else 0
            binding.projectProgressText.text = getString(R.string.progress_percentage, progress)

            val deadlineText = when {
                projectData.daysUntilDeadline < 0 -> getString(R.string.overdue)
                projectData.daysUntilDeadline == 0 -> getString(R.string.due_today)
                else -> getString(R.string.due_in_days, projectData.daysUntilDeadline)
            }
            binding.projectDeadlineText.text = deadlineText
        }
    }

    private fun renderTasks(tasks: List<StudentTaskData>) {
        binding.tasksContainer.removeAllViews()

        if (tasks.isEmpty()) {
            binding.tasksContainer.isVisible = false
            binding.tasksEmptyState.isVisible = true
        } else {
            binding.tasksContainer.isVisible = true
            binding.tasksEmptyState.isVisible = false

            // Show up to 5 tasks
            tasks.take(5).forEach { taskData ->
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
