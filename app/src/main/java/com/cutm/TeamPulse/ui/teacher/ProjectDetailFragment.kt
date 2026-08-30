package com.cutm.TeamPulse.ui.teacher

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.cutm.TeamPulse.R
import com.cutm.TeamPulse.databinding.FragmentProjectDetailBinding
import com.cutm.TeamPulse.domain.model.Project
import com.cutm.TeamPulse.domain.model.ProjectStatus
import com.cutm.TeamPulse.domain.model.Team
import com.cutm.TeamPulse.ui.common.BaseFragment
import com.google.android.material.card.MaterialCardView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class ProjectDetailFragment : BaseFragment<FragmentProjectDetailBinding>(
    FragmentProjectDetailBinding::inflate
) {

    private val viewModel: ProjectDetailViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupCreateTeamButton()
        setupManageTasksButton()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe project
                launch {
                    viewModel.project.collect { project ->
                        renderProject(project)
                    }
                }

                // Observe teams
                launch {
                    viewModel.teams.collect { teams ->
                        renderTeams(teams)
                    }
                }
            }
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupCreateTeamButton() {
        binding.createTeamButton.setOnClickListener {
            val projectId = viewModel.project.value?.projectId ?: return@setOnClickListener
            CreateTeamBottomSheet.newInstance(projectId)
                .show(childFragmentManager, "CreateTeamBottomSheet")
        }
    }

    private fun setupManageTasksButton() {
        binding.manageTasksButton.setOnClickListener {
            val project = viewModel.project.value ?: return@setOnClickListener
            val action = ProjectDetailFragmentDirections
                .actionProjectDetailToTeacherTaskList(
                    projectId = project.projectId,
                    projectName = project.name
                )
            findNavController().navigate(action)
        }
    }

    private fun renderProject(project: Project?) {
        if (project == null) {
            // Project not found or still loading
            return
        }

        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

        binding.projectNameText.text = project.name
        binding.projectDueDateText.text = getString(
            R.string.project_detail_due_date,
            dateFormat.format(Date(project.dueDate))
        )
        binding.projectStatusText.text = getString(
            R.string.project_detail_status,
            formatProjectStatus(project.status)
        )
        binding.projectCreatedText.text = getString(
            R.string.project_detail_created,
            dateFormat.format(Date(project.startDate))
        )
        binding.projectGithubText.text = getString(
            R.string.project_detail_github,
            project.githubRepo ?: "[placeholder]"
        )
    }

    private fun renderTeams(teams: List<Team>) {
        // Update section header count
        binding.teamsSectionHeader.text = getString(
            R.string.project_detail_teams_header,
            teams.size
        )

        if (teams.isEmpty()) {
            // Show empty state
            binding.teamsListContainer.isVisible = false
            binding.teamsEmptyState.isVisible = true
        } else {
            // Show team list
            binding.teamsEmptyState.isVisible = false
            binding.teamsListContainer.isVisible = true

            // Clear existing team cards
            binding.teamsListContainer.removeAllViews()

            // Add team cards
            teams.forEach { team ->
                val teamCard = createTeamCard(team)
                binding.teamsListContainer.addView(teamCard)
            }
        }
    }

    private fun createTeamCard(team: Team): View {
        val cardView = MaterialCardView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = resources.getDimensionPixelSize(R.dimen.spacing_md)
            }
            cardElevation = resources.getDimension(R.dimen.card_elevation)
            radius = resources.getDimension(R.dimen.card_corner_radius)
            val padding = resources.getDimensionPixelSize(R.dimen.spacing_base)
            setContentPadding(padding, padding, padding, padding)
        }

        val cardContent = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Header: Team name + member count
        val headerLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val teamNameText = TextView(requireContext()).apply {
            text = team.teamName
            setTextAppearance(R.style.TextAppearance_TeamPulse_BodyLarge)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val memberCountText = TextView(requireContext()).apply {
            text = if (team.memberEmails.isEmpty()) {
                getString(R.string.project_detail_team_no_members)
            } else {
                getString(R.string.project_detail_team_members, team.memberEmails.size)
            }
            setTextAppearance(R.style.TextAppearance_TeamPulse_BodyMedium)
            setTextColor(resources.getColor(R.color.on_surface_variant, null))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        headerLayout.addView(teamNameText)
        headerLayout.addView(memberCountText)
        cardContent.addView(headerLayout)

        // Member list (hidden if empty)
        if (team.memberEmails.isNotEmpty()) {
            val membersToShow = team.memberEmails.take(3)
            val remainingCount = team.memberEmails.size - 3

            membersToShow.forEach { email ->
                val memberText = TextView(requireContext()).apply {
                    text = "• $email"
                    setTextAppearance(R.style.TextAppearance_TeamPulse_BodyMedium)
                    setTextColor(resources.getColor(R.color.on_surface_variant, null))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = resources.getDimensionPixelSize(R.dimen.spacing_xs)
                    }
                }
                cardContent.addView(memberText)
            }

            if (remainingCount > 0) {
                val moreText = TextView(requireContext()).apply {
                    text = getString(R.string.project_detail_team_more_members, remainingCount)
                    setTextAppearance(R.style.TextAppearance_TeamPulse_BodyMedium)
                    setTextColor(resources.getColor(R.color.on_surface_variant, null))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = resources.getDimensionPixelSize(R.dimen.spacing_xs)
                    }
                }
                cardContent.addView(moreText)
            }
        }

        cardView.addView(cardContent)
        return cardView
    }

    private fun formatProjectStatus(status: ProjectStatus): String {
        return when (status) {
            ProjectStatus.ACTIVE -> "Active"
            ProjectStatus.ARCHIVED -> "Archived"
        }
    }
}
