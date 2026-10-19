package com.cutm.TeamPulse.ui.student

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cutm.TeamPulse.R
import com.cutm.TeamPulse.domain.model.TaskStatus
import com.cutm.TeamPulse.ui.common.TaskItemViewPremium

/**
 * Adapter for displaying task cards in RecyclerView with premium design.
 * Supports filtering by task status.
 */
class TaskAdapter(
    private val onTaskClicked: (com.cutm.TeamPulse.domain.model.TaskAssignment) -> Unit,
    private val onStatusAction: (String, TaskStatus) -> Unit
) : ListAdapter<StudentTaskData, TaskAdapter.TaskViewHolder>(TaskDiffCallback()) {

    private var currentFilter: TaskStatusFilter = TaskStatusFilter.ALL

    /**
     * Update tasks with filtering applied
     */
    fun updateTasks(tasks: List<StudentTaskData>, filter: TaskStatusFilter = currentFilter) {
        currentFilter = filter
        val filteredTasks = when (filter) {
            TaskStatusFilter.ALL -> tasks
            TaskStatusFilter.TODO -> tasks.filter { it.task.status == TaskStatus.TODO }
            TaskStatusFilter.IN_PROGRESS -> tasks.filter { it.task.status == TaskStatus.IN_PROGRESS }
            TaskStatusFilter.DONE -> tasks.filter { it.task.status == TaskStatus.DONE }
        }
        submitList(filteredTasks)
    }

    /**
     * Get count of tasks for a specific filter
     */
    fun getFilteredCount(tasks: List<StudentTaskData>, filter: TaskStatusFilter): Int {
        return when (filter) {
            TaskStatusFilter.ALL -> tasks.size
            TaskStatusFilter.TODO -> tasks.count { it.task.status == TaskStatus.TODO }
            TaskStatusFilter.IN_PROGRESS -> tasks.count { it.task.status == TaskStatus.IN_PROGRESS }
            TaskStatusFilter.DONE -> tasks.count { it.task.status == TaskStatus.DONE }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val taskView = TaskItemViewPremium(parent.context)
        taskView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        return TaskViewHolder(taskView, onTaskClicked, onStatusAction)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class TaskViewHolder(
        private val taskView: TaskItemViewPremium,
        private val onTaskClicked: (com.cutm.TeamPulse.domain.model.TaskAssignment) -> Unit,
        private val onStatusAction: (String, TaskStatus) -> Unit
    ) : RecyclerView.ViewHolder(taskView) {

        fun bind(taskData: StudentTaskData) {
            val task = taskData.task
            
            // Format due date text
            val dueDateText = when {
                taskData.daysUntilDue < 0 -> taskView.context.getString(R.string.overdue)
                taskData.daysUntilDue == 0 -> taskView.context.getString(R.string.due_today)
                else -> taskView.context.getString(R.string.due_in_days, taskData.daysUntilDue)
            }

            // Bind data to premium task view
            taskView.setTaskData(
                taskId = task.taskId,
                title = task.title,
                description = task.description,
                status = task.status,
                dueDate = dueDateText,
                weight = task.weight,
                onStatusAction = onStatusAction
            )

            // Set click listener for task detail
            taskView.setOnClickListener {
                onTaskClicked(task)
            }
        }
    }

    class TaskDiffCallback : DiffUtil.ItemCallback<StudentTaskData>() {
        override fun areItemsTheSame(oldItem: StudentTaskData, newItem: StudentTaskData): Boolean {
            return oldItem.task.taskId == newItem.task.taskId
        }

        override fun areContentsTheSame(oldItem: StudentTaskData, newItem: StudentTaskData): Boolean {
            return oldItem == newItem
        }
    }

    /**
     * Task status filter types
     */
    enum class TaskStatusFilter {
        ALL, TODO, IN_PROGRESS, DONE
    }
}
