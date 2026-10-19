package com.cutm.TeamPulse.ui.student

import android.content.res.ColorStateList
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import com.cutm.TeamPulse.R
import com.cutm.TeamPulse.databinding.FragmentStudentHomeBinding
import com.cutm.TeamPulse.domain.model.TaskStatus
import com.cutm.TeamPulse.ui.student.TaskAdapter.TaskStatusFilter
import com.google.android.material.button.MaterialButtonToggleGroup
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar

@AndroidEntryPoint
class StudentHomeFragment : Fragment() {

    private var _binding: FragmentStudentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: StudentHomeViewModel by viewModels()
    
    private var taskAdapter: TaskAdapter? = null
    private var currentTaskFilter: TaskStatusFilter = TaskStatusFilter.ALL
    private var carouselAdapter: ProjectCarouselAdapter? = null
    private var carouselPageChangeCallback: androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback? = null
    
    // Track if session was ever received (to distinguish between init delay and actual sign-out)
    private var sessionWasReceived = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d("StudentHomeFragment", "onCreateView()")
        _binding = FragmentStudentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("StudentHomeFragment", "onViewCreated()")

        setupGreeting()
        setupAccountButton()
        
        // Setup task adapter and filtering
        taskAdapter = TaskAdapter(
            onTaskClicked = { task -> onTaskClicked(task) },
            onStatusAction = { taskId, newStatus -> onStatusAction(taskId, newStatus) }
        )
        binding.tasksRecyclerView.adapter = taskAdapter
        setupTaskFiltering()
        
        observeViewModel()
    }

    private fun setupGreeting() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        
        val greetingRes = when (hour) {
            in 5..11 -> R.string.student_home_greeting_morning
            in 12..17 -> R.string.student_home_greeting_afternoon
            else -> R.string.student_home_greeting_evening
        }
        
        binding.greetingLabel.setText(greetingRes)
    }

    private fun setupAccountButton() {
        binding.accountMenuButton.setOnClickListener {
            showSignOutConfirmation()
        }
    }

    private fun showSignOutConfirmation() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Sign Out")
            .setMessage("Are you sure you want to sign out?")
            .setPositiveButton("Sign Out") { _, _ ->
                lifecycleScope.launch {
                    viewModel.signOut()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupTaskFiltering() {
        val filterGroup = binding.root.findViewById<MaterialButtonToggleGroup>(R.id.taskFilterGroup) ?: return
        
        filterGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            
            currentTaskFilter = when (checkedId) {
                R.id.filterAll -> TaskStatusFilter.ALL
                R.id.filterTodo -> TaskStatusFilter.TODO
                R.id.filterInProgress -> TaskStatusFilter.IN_PROGRESS
                R.id.filterDone -> TaskStatusFilter.DONE
                else -> return@addOnButtonCheckedListener
            }
            
            viewModel.myTasks.value?.let { tasks ->
                renderTasks(tasks)
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.userSession.collectLatest { session ->
                    Log.d("StudentHomeFragment", "userSession emission: ${if (session != null) "session for ${session.email}" else "null"}, sessionWasReceived=$sessionWasReceived")
                    
                    if (session != null) {
                        // Session exists - update UI
                        sessionWasReceived = true
                        binding.greetingText.text = session.displayName ?: "Student"
                        Log.d("StudentHomeFragment", "Session received: ${session.email}")
                    } else if (sessionWasReceived) {
                        // Session became null AFTER we already received a valid session
                        // This means user explicitly signed out
                        Log.d("StudentHomeFragment", "Session cleared - user signed out, navigating to SignIn")
                        findNavController().navigate(R.id.action_studentHome_to_signIn)
                    } else {
                        // Session is null but we haven't received a valid session yet
                        // This is likely initial load delay - do NOT navigate
                        Log.d("StudentHomeFragment", "Session null during initial load - waiting for valid session")
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentProjects.collectLatest { projects ->
                    renderProjectCarousel(projects)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.myTasks.collectLatest { tasks ->
                    renderTasks(tasks)
                }
            }
        }
    }

    private fun renderProjectCarousel(projects: List<CurrentProjectData>) {
        if (projects.isEmpty()) {
            crossFade(binding.projectCarousel, binding.projectEmptyState)
            binding.projectIndicator.isVisible = false
        } else {
            crossFade(binding.projectEmptyState, binding.projectCarousel)
            binding.projectIndicator.isVisible = projects.size > 1

            // Set fixed height for carousel
            val targetHeight = resources.getDimensionPixelSize(R.dimen.project_carousel_height)
            val layoutParams = binding.projectCarousel.layoutParams
            layoutParams.height = targetHeight
            binding.projectCarousel.layoutParams = layoutParams

            // Setup or update carousel adapter
            if (carouselAdapter == null) {
                carouselAdapter = ProjectCarouselAdapter(requireContext(), projects) { projectId ->
                    viewModel.setFocusedProject(projectId)
                }
                binding.projectCarousel.adapter = carouselAdapter

                // Create indicator dots
                if (projects.size > 1) {
                    setupCustomCarouselIndicator(projects.size)
                }

                // Register callback ONCE
                carouselPageChangeCallback = object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        if (position < projects.size) {
                            viewModel.setFocusedProject(projects[position].project.projectId)
                        }
                        updateCarouselIndicator(position)
                    }
                }
                binding.projectCarousel.registerOnPageChangeCallback(carouselPageChangeCallback!!)
            } else {
                carouselAdapter?.updateProjects(projects)
                if (projects.size > 1) {
                    setupCustomCarouselIndicator(projects.size)
                } else {
                    binding.projectIndicator.isVisible = false
                }
            }
        }
    }

    private fun renderTasks(tasks: List<StudentTaskData>) {
        updateTaskCountBadge(tasks)
        
        if (tasks.isEmpty()) {
            showEmptyStateForFilter(TaskStatusFilter.ALL)
            return
        }
        
        // Update adapter with current filter
        taskAdapter?.updateTasks(tasks, currentTaskFilter)
        
        // Show/hide appropriate empty state
        val filteredCount = taskAdapter?.getFilteredCount(tasks, currentTaskFilter) ?: 0
        if (filteredCount == 0) {
            showEmptyStateForFilter(currentTaskFilter)
        } else {
            showTaskList()
        }
    }

    private fun updateTaskCountBadge(tasks: List<StudentTaskData>) {
        val totalCount = tasks.size
        binding.taskCountBadge.isVisible = totalCount > 0
        if (totalCount > 0) {
            binding.taskCountBadge.text = totalCount.toString()
        }
    }

    private fun showEmptyStateForFilter(filter: TaskStatusFilter) {
        binding.tasksEmptyStateAll.isVisible = false
        binding.tasksEmptyStateTodo.isVisible = false
        binding.tasksEmptyStateInProgress.isVisible = false
        binding.tasksEmptyStateDone.isVisible = false
        binding.tasksRecyclerView.isVisible = false
        
        when (filter) {
            TaskStatusFilter.ALL -> binding.tasksEmptyStateAll.isVisible = true
            TaskStatusFilter.TODO -> binding.tasksEmptyStateTodo.isVisible = true
            TaskStatusFilter.IN_PROGRESS -> binding.tasksEmptyStateInProgress.isVisible = true
            TaskStatusFilter.DONE -> binding.tasksEmptyStateDone.isVisible = true
        }
    }

    private fun showTaskList() {
        binding.tasksEmptyStateAll.isVisible = false
        binding.tasksEmptyStateTodo.isVisible = false
        binding.tasksEmptyStateInProgress.isVisible = false
        binding.tasksEmptyStateDone.isVisible = false
        binding.tasksRecyclerView.isVisible = true
    }

    private fun onTaskClicked(task: com.cutm.TeamPulse.domain.model.TaskAssignment) {
        val clickedView = binding.tasksRecyclerView.focusedChild
        clickedView?.let { animateTaskTap(it) }

        val bottomSheet = TaskDetailBottomSheet.newInstance(
            taskId = task.taskId,
            title = task.title,
            description = task.description,
            status = task.status,
            dueDate = task.dueDate,
            weight = task.weight
        )
        bottomSheet.show(childFragmentManager, "TaskDetailBottomSheet")
    }

    private fun onStatusAction(taskId: String, newStatus: TaskStatus) {
        viewModel.updateTaskStatus(taskId, newStatus)
    }

    private fun animateTaskTap(view: View) {
        view.animate()
            .scaleX(0.97f)
            .scaleY(0.97f)
            .setDuration(100)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }

    private fun crossFade(fromView: View, toView: View) {
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

        // Determine current states
        val fromCurrentlyVisible = fromView.isVisible
        val toCurrentlyVisible = toView.isVisible

        // If both same state, no crossfade needed
        if (fromCurrentlyVisible == toCurrentlyVisible && fromCurrentlyVisible == toView.isVisible) {
            return
        }

        val duration = 200L
        toView.alpha = 0f
        toView.isVisible = true

        toView.animate()
            .alpha(1f)
            .setDuration(duration)
            .setInterpolator(DecelerateInterpolator())
            .start()

        fromView.animate()
            .alpha(0f)
            .setDuration(duration)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                fromView.isVisible = false
                fromView.alpha = 1f
            }
            .start()
    }

    private fun setupCustomCarouselIndicator(count: Int) {
        binding.projectIndicator.removeAllViews()
        
        val indicatorSize = resources.getDimensionPixelSize(R.dimen.indicator_size)
        val indicatorMargin = resources.getDimensionPixelSize(R.dimen.indicator_margin)
        
        for (i in 0 until count) {
            val indicator = View(requireContext()).apply {
                layoutParams = ViewGroup.MarginLayoutParams(indicatorSize, indicatorSize).apply {
                    setMargins(indicatorMargin, 0, indicatorMargin, 0)
                }
                background = androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.bg_indicator_inactive)
            }
            binding.projectIndicator.addView(indicator)
        }
        
        updateCarouselIndicator(0)
    }

    private fun updateCarouselIndicator(position: Int) {
        for (i in 0 until binding.projectIndicator.childCount) {
            val indicator = binding.projectIndicator.getChildAt(i)
            val drawableRes = if (i == position) R.drawable.bg_indicator_active else R.drawable.bg_indicator_inactive
            indicator.background = androidx.core.content.ContextCompat.getDrawable(requireContext(), drawableRes)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d("StudentHomeFragment", "onDestroyView()")
        
        // Unregister carousel callback to prevent leaks
        if (carouselPageChangeCallback != null) {
            binding.projectCarousel.unregisterOnPageChangeCallback(carouselPageChangeCallback!!)
            carouselPageChangeCallback = null
        }
        
        _binding = null
    }
}
