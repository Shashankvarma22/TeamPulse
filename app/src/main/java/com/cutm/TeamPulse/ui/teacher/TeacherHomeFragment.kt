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
import android.widget.Toast
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

    // Track all active animators so we can cancel them on destroy
    private val activeAnimators = mutableListOf<ObjectAnimator>()
    
    // Track whether this is the first render after fragment creation (post-rotation or initial load)
    // Used to skip crossFade and force correct state directly on first render, avoiding animation
    // race conditions where rapid rotation fires onDestroyView/onViewCreated while crossFade is mid-transition
    private var isFirstProjectRender = true
    private var isFirstDeadlineRender = true

    // Bug B Fix: Track previous isSyncing state to detect true → false transition (completion)
    private var wasSyncing = false

    private fun setupMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_home, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_sync -> {
                        triggerSync()
                        true
                    }
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

    private fun triggerSync() {
        // Bug A Fix: Defense-in-depth check (ViewModel also guards)
        if (viewModel.isSyncing.value) {
            android.util.Log.d("TeacherHomeFragment", "triggerSync: Already syncing, ignoring tap")
            return
        }

        // Delegate to ViewModel which runs in viewModelScope (survives rotation)
        viewModel.triggerSync()
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
        
        // CRITICAL: Initialize ALL views to clean state BEFORE any animations
        // This prevents views from being in partial states from animation interruptions
        binding.projectsContainer.alpha = 1f
        binding.projectsContainer.isVisible = false  // Match layout default (gone)
        binding.projectsEmptyState.alpha = 1f
        binding.projectsEmptyState.isVisible = true  // Match layout default (visible)
        binding.deadlinesContainer.alpha = 1f
        binding.deadlinesContainer.isVisible = true
        binding.deadlinesEmptyState.alpha = 1f
        binding.deadlinesEmptyState.isVisible = true
        
        // VERIFY: Read back immediately to confirm values were set
        android.util.Log.d("TeacherHomeFragment", "onViewCreated INIT_CHECK: projectsContainer alpha=${String.format("%.10f", binding.projectsContainer.alpha)} visible=${binding.projectsContainer.isVisible}")
        android.util.Log.d("TeacherHomeFragment", "onViewCreated INIT_CHECK: projectsEmptyState alpha=${String.format("%.10f", binding.projectsEmptyState.alpha)} visible=${binding.projectsEmptyState.isVisible}")
        android.util.Log.d("TeacherHomeFragment", "onViewCreated INIT_CHECK: deadlinesContainer alpha=${String.format("%.10f", binding.deadlinesContainer.alpha)} visible=${binding.deadlinesContainer.isVisible}")
        android.util.Log.d("TeacherHomeFragment", "onViewCreated INIT_CHECK: deadlinesEmptyState alpha=${String.format("%.10f", binding.deadlinesEmptyState.alpha)} visible=${binding.deadlinesEmptyState.isVisible}")
        
        // Reset first-render flags so render methods know this is the first time after fragment creation
        isFirstProjectRender = true
        isFirstDeadlineRender = true
        
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
        android.util.Log.d("TeacherHomeFragment", "animateEntrance took ${animEnd - animStart}ms, ${activeAnimators.size} animators started")

        // Setup FAB for creating new project
        val fabStart = System.currentTimeMillis()
        binding.createProjectFab.setOnClickListener {
            CreateProjectBottomSheet.newInstance()
                .show(childFragmentManager, "CreateProjectBottomSheet")
        }
        val fabEnd = System.currentTimeMillis()
        android.util.Log.d("TeacherHomeFragment", "FAB setup took ${fabEnd - fabStart}ms")

        val collectorsStart = System.currentTimeMillis()
        viewLifecycleOwner.lifecycleScope.launch {
            android.util.Log.d("TeacherHomeFragment", "Flow collectors coroutine launched")
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                android.util.Log.d("TeacherHomeFragment", "repeatOnLifecycle(STARTED) block entered")
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
                    android.util.Log.d("TeacherHomeFragment", "projectsWithProgress collector launched, active animators = ${activeAnimators.size}")
                    viewModel.projectsWithProgress.collect { projects ->
                        android.util.Log.d("TeacherHomeFragment", "Collected projectsWithProgress: ${projects.size} projects, active animators = ${activeAnimators.size}")
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

                // Bug B Fix: Collect isSyncing state once per view lifecycle (not per tap)
                // Track previous state to only show completion Toast on true → false transition
                launch {
                    android.util.Log.d("TeacherHomeFragment", "isSyncing collector launched")
                    viewModel.isSyncing.collect { isSyncing ->
                        android.util.Log.d("TeacherHomeFragment", "isSyncing state: $isSyncing (was: $wasSyncing)")
                        
                        // Show "Sync in progress" Toast on false → true transition
                        if (!wasSyncing && isSyncing) {
                            Toast.makeText(
                                requireContext(),
                                "Sync in progress...",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        
                        wasSyncing = isSyncing
                    }
                }

                // NEW: Collect sync outcome (success or error with stage)
                launch {
                    android.util.Log.d("TeacherHomeFragment", "syncOutcome collector launched")
                    viewModel.syncOutcome.collect { outcome ->
                        if (outcome != null) {
                            android.util.Log.d("TeacherHomeFragment", "Sync outcome: $outcome")
                            
                            when (outcome) {
                                is TeacherHomeViewModel.SyncOutcome.Success -> {
                                    Toast.makeText(
                                        requireContext(),
                                        "Sync completed successfully",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    android.util.Log.d("TeacherHomeFragment", "Sync succeeded")
                                }
                                is TeacherHomeViewModel.SyncOutcome.Error -> {
                                    val stageLabel = when (outcome.stage) {
                                        "push" -> "Sending changes"
                                        "pull" -> "Loading latest data"
                                        else -> "Sync"
                                    }
                                    val errorMessage = "Sync failed ($stageLabel): ${outcome.message}"
                                    
                                    Toast.makeText(
                                        requireContext(),
                                        errorMessage,
                                        Toast.LENGTH_LONG
                                    ).show()
                                    
                                    android.util.Log.e("TeacherHomeFragment", errorMessage)
                                }
                            }
                            
                            // Clear the outcome after displaying so rotation doesn't re-show it
                            viewModel.clearSyncOutcome()
                        }
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

    override fun onDestroyView() {
        // CRITICAL: Cancel all active animators before view is destroyed
        // Otherwise they keep running on orphaned views after rotation
        android.util.Log.d("TeacherHomeFragment", "onDestroyView: Canceling ${activeAnimators.size} active animators")
        activeAnimators.forEach { animator ->
            animator.cancel()
        }
        activeAnimators.clear()
        
        // Reset view states to clean baseline for ALL data views
        // Ensures new fragment created after rotation starts with clean state, not partial animation frames
        binding.projectsContainer.alpha = 1f
        binding.projectsContainer.isVisible = false
        binding.projectsEmptyState.alpha = 1f
        binding.projectsEmptyState.isVisible = true
        binding.deadlinesContainer.alpha = 1f
        binding.deadlinesContainer.isVisible = true
        binding.deadlinesEmptyState.alpha = 1f
        binding.deadlinesEmptyState.isVisible = true
        
        super.onDestroyView()
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
            binding.deadlinesSectionHeader.alpha = 1f
            binding.deadlinesContainer.alpha = 1f
            binding.deadlinesEmptyState.alpha = 1f
            return
        }

        // Very subtle fade-in for dense content
        val views = listOf(
            binding.greetingText,
            binding.attentionEmptyState,
            binding.projectsSectionHeader,
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
                this.startDelay = index * 30L
                activeAnimators.add(this)
                start()
            }
        }
    }

    private fun renderProjects(projects: List<ProjectWithProgress>) {
        android.util.Log.d("TeacherHomeFragment", "renderProjects: ${projects.size} projects, isFirstRender=$isFirstProjectRender")
        
        binding.projectsContainer.removeAllViews()

        if (projects.isEmpty()) {
            // Empty state: show projectsEmptyState, hide projectsContainer
            if (isFirstProjectRender) {
                // FIRST RENDER: Force state directly without animation
                // This prevents fractional alpha from interrupted prior rotations
                android.util.Log.d("TeacherHomeFragment", "renderProjects: FIRST RENDER - forcing empty state (no crossfade)")
                binding.projectsEmptyState.alpha = 1f
                binding.projectsEmptyState.isVisible = true
                binding.projectsContainer.alpha = 1f
                binding.projectsContainer.isVisible = false
                isFirstProjectRender = false
            } else {
                // SUBSEQUENT RENDER: Animate the data-driven transition
                android.util.Log.d("TeacherHomeFragment", "renderProjects: DATA CHANGE - animating to empty state (crossfade)")
                crossFade(binding.projectsContainer, binding.projectsEmptyState)
            }
        } else {
            // Data state: show projectsContainer, hide projectsEmptyState
            if (isFirstProjectRender) {
                // FIRST RENDER: Force state directly without animation
                android.util.Log.d("TeacherHomeFragment", "renderProjects: FIRST RENDER - forcing data state (no crossfade)")
                binding.projectsContainer.alpha = 1f
                binding.projectsContainer.isVisible = true
                binding.projectsEmptyState.alpha = 1f
                binding.projectsEmptyState.isVisible = false
                isFirstProjectRender = false
            } else {
                // SUBSEQUENT RENDER: Animate the data-driven transition
                android.util.Log.d("TeacherHomeFragment", "renderProjects: DATA CHANGE - animating to data state (crossfade)")
                crossFade(binding.projectsEmptyState, binding.projectsContainer)
            }

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
        android.util.Log.d("TeacherHomeFragment", "renderDeadlines: ${deadlines.size} deadlines, isFirstRender=$isFirstDeadlineRender")
        binding.deadlinesContainer.removeAllViews()

        if (deadlines.isEmpty()) {
            // Empty state
            if (isFirstDeadlineRender) {
                // FIRST RENDER: Force state directly without animation
                android.util.Log.d("TeacherHomeFragment", "renderDeadlines: FIRST RENDER - forcing empty state (no crossfade)")
                binding.deadlinesEmptyState.alpha = 1f
                binding.deadlinesEmptyState.isVisible = true
                binding.deadlinesContainer.alpha = 1f
                binding.deadlinesContainer.isVisible = false
                isFirstDeadlineRender = false
            } else {
                // SUBSEQUENT RENDER: Animate
                android.util.Log.d("TeacherHomeFragment", "renderDeadlines: DATA CHANGE - animating to empty state (crossfade)")
                crossFade(binding.deadlinesContainer, binding.deadlinesEmptyState)
            }
        } else {
            // Data state
            if (isFirstDeadlineRender) {
                // FIRST RENDER: Force state directly without animation
                android.util.Log.d("TeacherHomeFragment", "renderDeadlines: FIRST RENDER - forcing data state (no crossfade)")
                binding.deadlinesContainer.alpha = 1f
                binding.deadlinesContainer.isVisible = true
                binding.deadlinesEmptyState.alpha = 1f
                binding.deadlinesEmptyState.isVisible = false
                isFirstDeadlineRender = false
            } else {
                // SUBSEQUENT RENDER: Animate
                android.util.Log.d("TeacherHomeFragment", "renderDeadlines: DATA CHANGE - animating to data state (crossfade)")
                crossFade(binding.deadlinesEmptyState, binding.deadlinesContainer)
            }

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
        android.util.Log.d("TeacherHomeFragment", "crossFade START: from=${viewName(fromView)} (visible=${fromView.isVisible}, alpha=${String.format("%.10f", fromView.alpha)}), to=${viewName(toView)} (visible=${toView.isVisible}, alpha=${String.format("%.10f", toView.alpha)}) - ACTIVE_ANIMATORS=${activeAnimators.size}")
        
        val animationScale = Settings.Global.getFloat(
            requireContext().contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )

        if (animationScale == 0f) {
            android.util.Log.d("TeacherHomeFragment", "crossFade: Animations disabled, immediate transition")
            fromView.isVisible = false
            fromView.alpha = 1f
            toView.isVisible = true
            toView.alpha = 1f
            return
        }

        if (!fromView.isVisible && !toView.isVisible) {
            android.util.Log.d("TeacherHomeFragment", "crossFade: Both views GONE, no-op")
            return
        }

        val duration = 200L

        if (fromView.isVisible && fromView.alpha > 0f) {
            android.util.Log.d("TeacherHomeFragment", "crossFade: Fading out ${viewName(fromView)} from alpha=${fromView.alpha}")
            ObjectAnimator.ofFloat(fromView, View.ALPHA, fromView.alpha, 0f).apply {
                this.duration = duration
                activeAnimators.add(this)
                start()
                doOnEnd { 
                    fromView.isVisible = false
                    fromView.alpha = 1f
                    android.util.Log.d("TeacherHomeFragment", "crossFade: ${viewName(fromView)} hidden after fade-out, alpha reset to 1f")
                }
            }
        } else {
            android.util.Log.d("TeacherHomeFragment", "crossFade: ${viewName(fromView)} already hidden or transparent, skipping fade-out")
            fromView.isVisible = false
            fromView.alpha = 1f
        }

        if (!toView.isVisible || toView.alpha < 1f) {
            android.util.Log.d("TeacherHomeFragment", "crossFade: Fading in ${viewName(toView)} from alpha=${toView.alpha} to 1f")
            toView.alpha = if (!toView.isVisible) 0f else toView.alpha
            toView.isVisible = true
            ObjectAnimator.ofFloat(toView, View.ALPHA, toView.alpha, 1f).apply {
                this.duration = duration
                activeAnimators.add(this)
                start()
                doOnEnd {
                    toView.alpha = 1f
                    android.util.Log.d("TeacherHomeFragment", "crossFade: ${viewName(toView)} visible after fade-in, alpha=${toView.alpha}")
                }
            }
        } else {
            android.util.Log.d("TeacherHomeFragment", "crossFade: ${viewName(toView)} already visible with alpha=${toView.alpha}")
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
