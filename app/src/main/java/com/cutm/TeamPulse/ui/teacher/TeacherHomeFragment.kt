package com.cutm.TeamPulse.ui.teacher

import android.animation.ObjectAnimator
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.cutm.TeamPulse.BuildConfig
import com.cutm.TeamPulse.R
import com.cutm.TeamPulse.data.local.dao.ProjectDao
import com.cutm.TeamPulse.data.local.dao.StudentDao
import com.cutm.TeamPulse.data.local.dao.TeamDao
import com.cutm.TeamPulse.data.local.entity.ProjectEntity
import com.cutm.TeamPulse.data.local.entity.StudentEntity
import com.cutm.TeamPulse.data.local.entity.TeamEntity
import com.cutm.TeamPulse.databinding.FragmentTeacherHomeBinding
import com.cutm.TeamPulse.domain.model.ProjectStatus
import com.cutm.TeamPulse.ui.common.BaseFragment
import com.cutm.TeamPulse.ui.common.ProjectProgressCard
import com.google.android.material.card.MaterialCardView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TeacherHomeFragment : BaseFragment<FragmentTeacherHomeBinding>(FragmentTeacherHomeBinding::inflate) {

    private val viewModel: TeacherHomeViewModel by viewModels()
    
    // DEBUG ONLY: Direct DAO injection for seeding with correct teacher email
    @Inject
    lateinit var projectDao: ProjectDao
    @Inject
    lateinit var teamDao: TeamDao
    @Inject
    lateinit var studentDao: StudentDao
    @Inject
    lateinit var taskDao: com.cutm.TeamPulse.data.local.dao.TaskAssignmentDao
    private var hasAnimatedEntrance = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // DEBUG ONLY: Seed test data if missing, or refresh due date if exists
        if (BuildConfig.DEBUG) {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    // Wait for user session to load
                    val session = viewModel.userSession.first { it != null }
                    if (session != null) {
                        val existingProject = projectDao.getById("debug-project-1")
                        if (existingProject == null) {
                            // First-time creation
                            seedTestData(session.email)
                            android.util.Log.d("TeacherHome", "Debug seed data created for ${session.email}")
                        } else {
                            // Refresh due date in place (no delete/insert race)
                            val newDueDate = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000)
                            projectDao.updateDueDate(
                                projectId = existingProject.projectId,
                                dueDate = newDueDate,
                                lastModified = System.currentTimeMillis()
                            )
                            android.util.Log.d("TeacherHome", "Debug project due date refreshed")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("TeacherHome", "Seed failed", e)
                }
            }
        }

        // Light entrance animation for information-dense teacher view
        animateEntrance()

        // Setup FAB for creating new project
        binding.createProjectFab.setOnClickListener {
            CreateProjectBottomSheet.newInstance()
                .show(childFragmentManager, "CreateProjectBottomSheet")
        }

        // TEMPORARY DEBUG: Long-press FAB to dump DB state
        binding.createProjectFab.setOnLongClickListener {
            dumpDatabaseState()
            true
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Collect user session for greeting
                launch {
                    viewModel.userSession.collect { session ->
                        session?.let {
                            binding.greetingText.text = getString(
                                R.string.teacher_home_greeting,
                                it.displayName.split(" ").firstOrNull() ?: it.displayName
                            )
                        }
                    }
                }

                // Collect projects with progress
                launch {
                    viewModel.projectsWithProgress.collect { projects ->
                        renderProjects(projects)
                    }
                }

                // Collect upcoming deadlines
                launch {
                    viewModel.upcomingDeadlines.collect { deadlines ->
                        renderDeadlines(deadlines)
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
            // Exclude data-dependent views (projectsContainer/projectsEmptyState)
            binding.greetingText.alpha = 1f
            binding.attentionEmptyState.alpha = 1f
            binding.projectsSectionHeader.alpha = 1f
            // projectsContainer - EXCLUDED (data-dependent)
            // projectsEmptyState - EXCLUDED (data-dependent)
            binding.deadlinesSectionHeader.alpha = 1f
            binding.deadlinesContainer.alpha = 1f
            binding.deadlinesEmptyState.alpha = 1f
            return
        }

        // Very subtle fade-in for dense content
        // No translation - just alpha for minimal distraction
        // Exclude data-dependent mutually-exclusive views (projectsContainer/projectsEmptyState)
        // whose visibility is managed exclusively by renderProjects()/crossFade()
        val views = listOf(
            binding.greetingText,
            binding.attentionEmptyState,
            binding.projectsSectionHeader,
            // projectsContainer - EXCLUDED (data-dependent)
            // projectsEmptyState - EXCLUDED (data-dependent)
            binding.deadlinesSectionHeader,
            binding.deadlinesContainer,
            binding.deadlinesEmptyState
        )

        views.forEach { it.alpha = 0f }

        val duration = 250L
        val interpolator = DecelerateInterpolator()

        views.forEachIndexed { index, view ->
            ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f).apply {
                this.duration = duration
                this.interpolator = interpolator
                this.startDelay = index * 30L  // Very short stagger
                start()
            }
        }
    }

    private fun renderProjects(projects: List<ProjectWithProgress>) {
        binding.projectsContainer.removeAllViews()

        if (projects.isEmpty()) {
            crossFade(binding.projectsContainer, binding.projectsEmptyState)
        } else {
            crossFade(binding.projectsEmptyState, binding.projectsContainer)

            projects.forEach { projectData ->
                val card = ProjectProgressCard(requireContext()).apply {
                    val progress = if (projectData.totalTasks > 0) {
                        (projectData.completedTasks * 100) / projectData.totalTasks
                    } else 0

                    val deadlineText = when {
                        projectData.daysUntilDeadline < 0 -> getString(R.string.overdue)
                        projectData.daysUntilDeadline == 0 -> getString(R.string.due_today)
                        else -> getString(R.string.due_in_days, projectData.daysUntilDeadline)
                    }

                    setProjectData(
                        name = projectData.project.name,
                        progress = progress,
                        deadline = deadlineText
                    )

                    // Make card clickable to navigate to project detail
                    setOnClickListener {
                        val action = TeacherHomeFragmentDirections
                            .actionTeacherHomeToProjectDetail(
                                projectId = projectData.project.projectId
                            )
                        findNavController().navigate(action)
                    }
                }

                val layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = resources.getDimensionPixelSize(R.dimen.spacing_sm)
                }

                binding.projectsContainer.addView(card, layoutParams)
            }
        }
    }

    private fun renderDeadlines(deadlines: List<UpcomingDeadline>) {
        binding.deadlinesContainer.removeAllViews()

        if (deadlines.isEmpty()) {
            crossFade(binding.deadlinesContainer, binding.deadlinesEmptyState)
        } else {
            crossFade(binding.deadlinesEmptyState, binding.deadlinesContainer)

            deadlines.forEach { deadline ->
                val deadlineCard = createDeadlineCard(deadline)
                val layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = resources.getDimensionPixelSize(R.dimen.spacing_xs)
                }
                binding.deadlinesContainer.addView(deadlineCard, layoutParams)
            }
        }
    }

    private fun createDeadlineCard(deadline: UpcomingDeadline): MaterialCardView {
        val card = MaterialCardView(requireContext()).apply {
            setCardBackgroundColor(requireContext().getColor(R.color.card_background))
            strokeColor = requireContext().getColor(R.color.card_stroke)
            strokeWidth = resources.getDimensionPixelSize(R.dimen.card_stroke_width)
            radius = resources.getDimension(R.dimen.card_corner_radius)
            cardElevation = 0f
        }

        val contentView = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_deadline, card, false)

        val titleText = contentView.findViewById<TextView>(R.id.deadlineTitleText)
        val daysText = contentView.findViewById<TextView>(R.id.deadlineDaysText)

        titleText.text = deadline.title
        daysText.text = when {
            deadline.daysUntil == 0 -> getString(R.string.due_today)
            else -> getString(R.string.due_in_days, deadline.daysUntil)
        }

        // Color-code urgency
        val textColor = when {
            deadline.daysUntil <= 2 -> requireContext().getColor(R.color.error)
            deadline.daysUntil <= 7 -> requireContext().getColor(R.color.warning)
            else -> requireContext().getColor(R.color.text_secondary)
        }
        daysText.setTextColor(textColor)

        card.addView(contentView)
        return card
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

        // Guard 2: Both-GONE is a genuine no-op (nothing to show)
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

    /**
     * DEBUG ONLY: Seeds test data with the correct logged-in teacher's email.
     * Creates: 1 project, 1 team (Alice, Bob), 2 students.
     */
    private suspend fun seedTestData(teacherEmail: String) {
        check(BuildConfig.DEBUG) { "Seed function must not run in release builds" }

        val projectId = "debug-project-1"
        val teamId = "debug-team-1"
        val currentTime = System.currentTimeMillis()

        // Insert project with ACTUAL teacher email
        projectDao.upsert(
            ProjectEntity(
                projectId = projectId,
                name = "Debug Test Project",
                teacherEmail = teacherEmail,  // Use logged-in teacher
                spreadsheetId = "debug-spreadsheet-placeholder-id",
                driveFolderId = "debug-folder-placeholder-id",
                startDate = currentTime,
                dueDate = currentTime + (30L * 24 * 60 * 60 * 1000), // 30 days from now
                status = ProjectStatus.ACTIVE,
                githubRepo = null,
                localDirty = false,
                lastModifiedLocal = currentTime,
                lastSyncedAt = null
            )
        )

        // Insert team with Alice and Bob
        teamDao.upsert(
            TeamEntity(
                teamId = teamId,
                projectId = projectId,
                teamName = "Team Alpha",
                memberEmails = listOf("alice@example.com", "bob@example.com"),
                createdAt = currentTime,
                localDirty = false,
                lastModifiedLocal = currentTime
            )
        )

        // Insert Alice
        studentDao.upsert(
            StudentEntity(
                studentEmail = "alice@example.com",
                displayName = "Alice Johnson",
                teamId = teamId,
                projectId = projectId,
                joinedAt = currentTime,
                localDirty = false
            )
        )

        // Insert Bob
        studentDao.upsert(
            StudentEntity(
                studentEmail = "bob@example.com",
                displayName = "Bob Smith",
                teamId = teamId,
                projectId = projectId,
                joinedAt = currentTime,
                localDirty = false
            )
        )
    }

    /**
     * TEMPORARY DEBUG: Dump actual database state to Logcat
     * Use: Long-press FAB to trigger
     */
    private fun dumpDatabaseState() {
        viewLifecycleOwner.lifecycleScope.launch {
            android.util.Log.d("TeacherHome", "========== DATABASE STATE DUMP ==========")
            
            try {
                val projects = projectDao.debugGetAllProjects()
                android.util.Log.d("TeacherHome", "=== PROJECTS (${projects.size}) ===")
                projects.forEach { proj ->
                    android.util.Log.d("TeacherHome", "  ID: ${proj.projectId}")
                    android.util.Log.d("TeacherHome", "  Name: ${proj.name}")
                    android.util.Log.d("TeacherHome", "  Teacher: ${proj.teacherEmail}")
                    android.util.Log.d("TeacherHome", "  Status: ${proj.status}")
                    android.util.Log.d("TeacherHome", "  ---")
                }
                
                val tasks = taskDao.debugGetAllTasks()
                android.util.Log.d("TeacherHome", "=== TASKS (${tasks.size}) ===")
                tasks.forEach { task ->
                    android.util.Log.d("TeacherHome", "  ID: ${task.taskId}")
                    android.util.Log.d("TeacherHome", "  Title: ${task.title}")
                    android.util.Log.d("TeacherHome", "  TeamId: ${task.teamId}")
                    android.util.Log.d("TeacherHome", "  AssignedTo: ${task.assigneeEmail.ifEmpty { "WHOLE TEAM" }}")
                    android.util.Log.d("TeacherHome", "  Status: ${task.status}")
                    android.util.Log.d("TeacherHome", "  ---")
                }
                
                android.util.Log.d("TeacherHome", "========================================")
                
                android.widget.Toast.makeText(
                    requireContext(),
                    "DB state dumped to Logcat (tag: TeacherHome)",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                
            } catch (e: Exception) {
                android.util.Log.e("TeacherHome", "Failed to dump DB state", e)
                android.widget.Toast.makeText(
                    requireContext(),
                    "DB dump failed: ${e.message}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
