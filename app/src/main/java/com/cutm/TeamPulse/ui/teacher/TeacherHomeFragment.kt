package com.cutm.TeamPulse.ui.teacher

import android.animation.ObjectAnimator
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.cutm.TeamPulse.R
import com.cutm.TeamPulse.databinding.FragmentTeacherHomeBinding
import com.cutm.TeamPulse.ui.common.BaseFragment
import com.cutm.TeamPulse.ui.common.ProjectProgressCard
import com.cutm.TeamPulse.core.security.KeystoreRecoveryManager
import com.cutm.TeamPulse.ui.debug.DebugMenuProvider
import com.google.android.material.card.MaterialCardView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TeacherHomeFragment : BaseFragment<FragmentTeacherHomeBinding>(FragmentTeacherHomeBinding::inflate) {

    private val viewModel: TeacherHomeViewModel by viewModels()
    
    @Inject
    lateinit var keystoreRecoveryManager: KeystoreRecoveryManager
    
    @Inject
    lateinit var debugMenuProvider: DebugMenuProvider
    
    private var hasAnimatedEntrance = false

    private fun setupMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_home, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_sign_out -> {
                        signOut()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner)
    }

    private fun signOut() {
        viewLifecycleOwner.lifecycleScope.launch {
            // Call repository sign-out (clears Room + CredentialManager)
            viewModel.signOut()
            
            // Navigate back to sign-in, clearing backstack
            findNavController().navigate(R.id.action_teacherHome_to_signIn)
        }
    }

    private fun setupSecurityWarningBanner(parent: ViewGroup) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                keystoreRecoveryManager.isUsingFallbackPassphrase.collect { isUsingFallback ->
                    // Check if banner already exists
                    val existingBanner = parent.findViewWithTag<TextView>("security_banner")
                    
                    if (isUsingFallback) {
                        // Show banner if not already present
                        if (existingBanner == null) {
                            val banner = TextView(requireContext()).apply {
                                tag = "security_banner"
                                                                text = "This device's secure storage failed and can't be repaired automatically — local data on this device is no longer hardware-encrypted"
                                setTextColor(android.graphics.Color.WHITE)
                                setBackgroundColor(requireContext().getColor(R.color.warning))
                                setPadding(32, 32, 32, 32)
                                textSize = 14f
                            }
                            parent.addView(banner, 0)
                        }
                    } else {
                        // Hide banner if present
                        if (existingBanner != null) {
                            parent.removeView(existingBanner)
                        }
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val fragmentStart = System.currentTimeMillis()
        android.util.Log.d("TeacherHomeFragment", "onViewCreated START at $fragmentStart")
        
        val superStart = System.currentTimeMillis()
        super.onViewCreated(view, savedInstanceState)
        val superEnd = System.currentTimeMillis()
        android.util.Log.d("TeacherHomeFragment", "super.onViewCreated() took ${superEnd - superStart}ms")

        // Setup security warning banner
        setupSecurityWarningBanner(binding.root as ViewGroup)

        // Attach debug menu (no-op in release builds)
        debugMenuProvider.attach(this, keystoreRecoveryManager, binding.greetingText)

        // Set toolbar as activity's action bar so MenuProvider can attach
        val toolbarStart = System.currentTimeMillis()
        (requireActivity() as AppCompatActivity).setSupportActionBar(binding.toolbar)
        val toolbarEnd = System.currentTimeMillis()
        android.util.Log.d("TeacherHomeFragment", "setSupportActionBar took ${toolbarEnd - toolbarStart}ms")

        // Setup menu (MenuProvider now has a toolbar to attach to)
        val menuStart = System.currentTimeMillis()
        setupMenu()
        val menuEnd = System.currentTimeMillis()
        android.util.Log.d("TeacherHomeFragment", "setupMenu took ${menuEnd - menuStart}ms")

        // Light entrance animation for information-dense teacher view
        val animStart = System.currentTimeMillis()
        animateEntrance()
        val animEnd = System.currentTimeMillis()
        android.util.Log.d("TeacherHomeFragment", "animateEntrance took ${animEnd - animStart}ms")

        // Setup FAB for creating new project
        val fabStart = System.currentTimeMillis()
        binding.createProjectFab.setOnClickListener {
            CreateProjectBottomSheet.newInstance()
                .show(childFragmentManager, "CreateProjectBottomSheet")
        }
        val fabEnd = System.currentTimeMillis()
        android.util.Log.d("TeacherHomeFragment", "FAB setup took ${fabEnd - fabStart}ms")

        // PHASE 1 TESTING: Temporary sync button (long-press greeting to trigger)
        // TODO: Move to proper UI location (project detail menu) after Phase 1 verification
        binding.greetingText.setOnLongClickListener {
            lifecycleScope.launch {
                // For Phase 1, sync the first project's spreadsheet as a test
                // In production, this would be per-project from detail screen
                val firstProject = viewModel.projectsWithProgress.value.firstOrNull()
                if (firstProject != null) {
                    android.util.Log.d("TeacherHome", "PHASE 1 SYNC: Starting for ${firstProject.project.spreadsheetId}")
                    val result = viewModel.syncProjectFromSheets(firstProject.project.spreadsheetId)
                    android.widget.Toast.makeText(
                        requireContext(),
                        when (result) {
                            is com.cutm.TeamPulse.core.network.ApiResult.Success -> "Sync completed successfully"
                            is com.cutm.TeamPulse.core.network.ApiResult.Error -> "Sync failed: ${result.message}"
                        },
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                } else {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "No projects to sync",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
            true
        }

        val collectorsStart = System.currentTimeMillis()
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
                        android.util.Log.d("TeacherHomeFragment", "Collected projectsWithProgress: ${projects.size} projects")
                        renderProjects(projects)
                    }
                }

                // Collect upcoming deadlines
                launch {
                    viewModel.upcomingDeadlines.collect { deadlines ->
                        android.util.Log.d("TeacherHomeFragment", "Collected upcomingDeadlines: ${deadlines.size} deadlines")
                        renderDeadlines(deadlines)
                    }
                }
            }
        }
        val collectorsEnd = System.currentTimeMillis()
        android.util.Log.d("TeacherHomeFragment", "Collectors setup took ${collectorsEnd - collectorsStart}ms")
        
        val fragmentEnd = System.currentTimeMillis()
        android.util.Log.d("TeacherHomeFragment", "onViewCreated COMPLETE (TOTAL: ${fragmentEnd - fragmentStart}ms)")
    }

    override fun onStart() {
        val startTime = System.currentTimeMillis()
        android.util.Log.d("TeacherHomeFragment", "onStart() called at $startTime")
        super.onStart()
        val endTime = System.currentTimeMillis()
        android.util.Log.d("TeacherHomeFragment", "onStart() complete (${endTime - startTime}ms)")
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
        android.util.Log.d("TeacherHomeFragment", "renderProjects: ${projects.size} projects, projectsContainer visible=${binding.projectsContainer.isVisible} alpha=${binding.projectsContainer.alpha}, projectsEmptyState visible=${binding.projectsEmptyState.isVisible} alpha=${binding.projectsEmptyState.alpha}")
        
        binding.projectsContainer.removeAllViews()

        if (projects.isEmpty()) {
            android.util.Log.d("TeacherHomeFragment", "renderProjects: Calling crossFade(projectsContainer â†’ projectsEmptyState)")
            crossFade(binding.projectsContainer, binding.projectsEmptyState)
        } else {
            android.util.Log.d("TeacherHomeFragment", "renderProjects: Calling crossFade(projectsEmptyState â†’ projectsContainer)")
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
        android.util.Log.d("TeacherHomeFragment", "crossFade START: from=${viewName(fromView)} (visible=${fromView.isVisible}, alpha=${fromView.alpha}), to=${viewName(toView)} (visible=${toView.isVisible}, alpha=${toView.alpha})")
        
        // Check if animations are disabled
        val animationScale = Settings.Global.getFloat(
            requireContext().contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )

        if (animationScale == 0f) {
            android.util.Log.d("TeacherHomeFragment", "crossFade: Animations disabled, immediate transition")
            fromView.isVisible = false
            fromView.alpha = 1f  // Reset alpha to clean state
            toView.isVisible = true
            toView.alpha = 1f    // Ensure fully opaque
            return
        }

        // Guard: Both-GONE is a genuine no-op (nothing to show)
        if (!fromView.isVisible && !toView.isVisible) {
            android.util.Log.d("TeacherHomeFragment", "crossFade: Both views GONE, no-op")
            return
        }

        val duration = 200L

        // Fade out fromView if visible AND has non-zero alpha
        // (Animation could have been interrupted, leaving it partially transparent)
        if (fromView.isVisible && fromView.alpha > 0f) {
            android.util.Log.d("TeacherHomeFragment", "crossFade: Fading out ${viewName(fromView)} from alpha=${fromView.alpha}")
            ObjectAnimator.ofFloat(fromView, View.ALPHA, fromView.alpha, 0f).apply {
                this.duration = duration
                start()
                doOnEnd { 
                    fromView.isVisible = false
                    fromView.alpha = 1f  // Reset to clean state for next time
                    android.util.Log.d("TeacherHomeFragment", "crossFade: ${viewName(fromView)} hidden after fade-out, alpha reset to 1f")
                }
            }
        } else {
            android.util.Log.d("TeacherHomeFragment", "crossFade: ${viewName(fromView)} already hidden or transparent, skipping fade-out")
            // Ensure clean state even if skipping animation
            fromView.isVisible = false
            fromView.alpha = 1f
        }

        // Fade in toView if not visible OR has non-1f alpha
        // (Animation could have been interrupted, leaving it partially transparent)
        if (!toView.isVisible || toView.alpha < 1f) {
            android.util.Log.d("TeacherHomeFragment", "crossFade: Fading in ${viewName(toView)} from alpha=${toView.alpha} to 1f")
            toView.alpha = if (!toView.isVisible) 0f else toView.alpha  // Start from current alpha if partially visible
            toView.isVisible = true
            ObjectAnimator.ofFloat(toView, View.ALPHA, toView.alpha, 1f).apply {
                this.duration = duration
                start()
                doOnEnd {
                    toView.alpha = 1f  // Ensure exactly 1f, not 0.9999...
                    android.util.Log.d("TeacherHomeFragment", "crossFade: ${viewName(toView)} visible after fade-in, alpha=${toView.alpha}")
                }
            }
        } else {
            android.util.Log.d("TeacherHomeFragment", "crossFade: ${viewName(toView)} already visible with alpha=${toView.alpha}")
            // FIX: Force alpha = 1f explicitly, don't assume it's already fully opaque
            toView.alpha = 1f
            android.util.Log.d("TeacherHomeFragment", "crossFade: ${viewName(toView)} alpha forced to 1f")
        }
        
        android.util.Log.d("TeacherHomeFragment", "crossFade END")
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
    
    private fun viewName(view: View): String = when (view.id) {
        R.id.projectsContainer -> "projectsContainer"
        R.id.projectsEmptyState -> "projectsEmptyState"
        R.id.deadlinesContainer -> "deadlinesContainer"
        R.id.deadlinesEmptyState -> "deadlinesEmptyState"
        else -> "unknown(${view.id})"
    }
}




