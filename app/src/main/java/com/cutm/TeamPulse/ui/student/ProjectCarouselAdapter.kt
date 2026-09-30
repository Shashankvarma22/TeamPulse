package com.cutm.TeamPulse.ui.student

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.cutm.TeamPulse.R

class ProjectCarouselAdapter(
    private val context: Context,
    private var projects: List<CurrentProjectData> = emptyList(),
    private val onPageChange: (String) -> Unit
) : RecyclerView.Adapter<ProjectCarouselAdapter.ProjectViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProjectViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.item_project_carousel, parent, false)
        return ProjectViewHolder(view, context, onPageChange)
    }

    override fun onBindViewHolder(holder: ProjectViewHolder, position: Int) {
        holder.bind(projects[position])
    }

    override fun getItemCount(): Int = projects.size

    fun updateProjects(newProjects: List<CurrentProjectData>) {
        projects = newProjects
        notifyDataSetChanged()
    }

    class ProjectViewHolder(
        itemView: android.view.View,
        private val context: Context,
        private val onPageChange: (String) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val projectName: TextView = itemView.findViewById(R.id.projectName)
        private val teamName: TextView = itemView.findViewById(R.id.teamName)
        private val progressPercentage: TextView = itemView.findViewById(R.id.progressPercentage)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)
        private val taskCount: TextView = itemView.findViewById(R.id.taskCount)
        private val deadlineChip: Chip = itemView.findViewById(R.id.deadlineChip)

        fun bind(projectData: CurrentProjectData) {
            projectName.text = projectData.project.name
            teamName.text = projectData.team.teamName

            // Calculate progress percentage
            val progressPercent = if (projectData.totalTasks > 0) {
                (projectData.completedTasks * 100) / projectData.totalTasks
            } else {
                0
            }

            progressPercentage.text = context.getString(R.string.progress_percentage, progressPercent)
            progressBar.progress = progressPercent
            taskCount.text = context.getString(
                R.string.task_count_format,
                projectData.completedTasks,
                projectData.totalTasks
            )

            // Set deadline chip with urgency styling
            val (deadlineText, chipColor) = getDeadlineInfo(projectData.daysUntilDeadline)
            deadlineChip.text = deadlineText
            deadlineChip.setChipBackgroundColorResource(chipColor)
        }

        private fun getDeadlineInfo(daysUntil: Int): Pair<String, Int> {
            return when {
                daysUntil < 0 -> {
                    val daysOverdue = -daysUntil
                    Pair(
                        context.getString(R.string.overdue_days, daysOverdue),
                        R.color.error
                    )
                }
                daysUntil == 0 -> {
                    Pair(
                        context.getString(R.string.due_today),
                        R.color.warning
                    )
                }
                daysUntil <= 3 -> {
                    Pair(
                        context.getString(R.string.due_soon_days, daysUntil),
                        R.color.warning
                    )
                }
                else -> {
                    Pair(
                        context.getString(R.string.due_in_days, daysUntil),
                        R.color.success
                    )
                }
            }
        }
    }
}
