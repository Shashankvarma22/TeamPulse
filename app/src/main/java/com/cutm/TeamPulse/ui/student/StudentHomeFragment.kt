package com.cutm.TeamPulse.ui.student

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.animation.DecelerateInterpolator
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
    private var hasAnimatedEntrance = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Animate entrance once
        animateEntrance()

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

    private fun animateEntrance() {
        if (hasAnimatedEntrance) return
        hasAnimatedEntrance = true

        // Check if animations are disabled
        val animationScale = Settings.Global.getFloat(
            requireContext().contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )

        if (animationScale == 0f) {
            // Immediately show all content
            binding.greetingText.alpha = 1f
            binding.greetingText.translationY = 0f
            binding.projectFocalCard.alpha = 1f
            binding.projectFocalCard.translationY = 0f
            binding.projectEmptyState.alpha = 1f
            binding.projectEmptyState.translationY = 0f
            binding.tasksSectionHeader.alpha = 1f
            binding.tasksSectionHeader.translationY = 0f
            binding.tasksContainer.alpha = 1f
            binding.tasksContainer.translationY = 0f
            binding.tasksEmptyState.alpha = 1f
            binding.tasksEmptyState.translationY = 0f
            return
        }

        // Prepare views for animation
        val translationDistance = 32f * resources.displayMetrics.density
        listOf(
            binding.greetingText,
            binding.projectFocalCard,
            binding.projectEmptyState,
            binding.tasksSectionHeader,
            binding.tasksContainer,
            binding.tasksEmptyState
        ).forEach { view ->
            view.alpha = 0f
            view.translationY = translationDistance
        }

        // Staggered entrance animation
        val interpolator = DecelerateInterpolator()
        val duration = 300L
        val staggerDelay = 80L

        val animators = mutableListOf<ObjectAnimator>()

        // Greeting (immediate)
        animators.add(ObjectAnimator.ofFloat(binding.greetingText, View.ALPHA, 0f, 1f).apply {
            this.duration = duration
            this.interpolator = interpolator
            startDelay = 0L
        })
        animators.add(ObjectAnimator.ofFloat(binding.greetingText, View.TRANSLATION_Y, translationDistance, 0f).apply {
            this.duration = duration
            this.interpolator = interpolator
            startDelay = 0L
        })

        // Project zone (stagger 1)
        listOf(binding.projectFocalCard, binding.projectEmptyState).forEach { view ->
            if (view.visibility == View.VISIBLE) {
                animators.add(ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f).apply {
                    this.duration = duration
                    this.interpolator = interpolator
                    startDelay = staggerDelay
                })
                animators.add(ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, translationDistance, 0f).apply {
                    this.duration = duration
                    this.interpolator = interpolator
                    startDelay = staggerDelay
                })
            }
        }

        // Tasks zone (stagger 2)
        listOf(binding.tasksSectionHeader, binding.tasksContainer, binding.tasksEmptyState).forEach { view ->
            if (view.visibility == View.VISIBLE) {
                animators.add(ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f).apply {
                    this.duration = duration
                    this.interpolator = interpolator
                    startDelay = staggerDelay * 2
                })
                animators.add(ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, translationDistance, 0f).apply {
                    this.duration = duration
                    this.interpolator = interpolator
                    startDelay = staggerDelay * 2
                })
            }
        }

        if (animators.isNotEmpty()) {
            AnimatorSet().apply {
                playTogether(animators as Collection<android.animation.Animator>)
                start()
            }
        }
    }

    private fun renderProject(projectData: CurrentProjectData?) {
        val wasVisible = binding.projectFocalCard.isVisible
        val newVisible = projectData != null

        if (projectData == null) {
            crossFade(binding.projectFocalCard, binding.projectEmptyState)
        } else {
            crossFade(binding.projectEmptyState, binding.projectFocalCard)

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
        val wasVisible = binding.tasksContainer.isVisible
        val newVisible = tasks.isNotEmpty()

        binding.tasksContainer.removeAllViews()

        if (tasks.isEmpty()) {
            crossFade(binding.tasksContainer, binding.tasksEmptyState)
        } else {
            crossFade(binding.tasksEmptyState, binding.tasksContainer)

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

    private fun crossFade(fromView: View, toView: View) {
        // Check if animations are disabled
        val animationScale = Settings.Global.getFloat(
            requireContext().contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )

        if (animationScale == 0f) {
            fromView.isVisible = false
            toView.isVisible = true
            return
        }

        // Don't animate if already in correct state
        if (fromView.isVisible && toView.isVisible) return
        if (!fromView.isVisible && !toView.isVisible) return

        val duration = 200L

        if (fromView.isVisible) {
            ObjectAnimator.ofFloat(fromView, View.ALPHA, 1f, 0f).apply {
                this.duration = duration
                start()
                doOnEnd { fromView.isVisible = false }
            }
        }

        if (!toView.isVisible) {
            toView.alpha = 0f
            toView.isVisible = true
            ObjectAnimator.ofFloat(toView, View.ALPHA, 0f, 1f).apply {
                this.duration = duration
                start()
            }
        }
    }

    private fun ObjectAnimator.doOnEnd(action: () -> Unit) {
        addListener(object : android.animation.Animator.AnimatorListener {
            override fun onAnimationStart(animation: android.animation.Animator) {}
            override fun onAnimationEnd(animation: android.animation.Animator) {
                action()
            }
            override fun onAnimationCancel(animation: android.animation.Animator) {}
            override fun onAnimationRepeat(animation: android.animation.Animator) {}
        })
    }
}
