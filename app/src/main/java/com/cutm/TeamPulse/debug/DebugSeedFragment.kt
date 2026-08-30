package com.cutm.TeamPulse.debug

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.cutm.TeamPulse.BuildConfig
import com.cutm.TeamPulse.databinding.FragmentDebugSeedBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * DEBUG ONLY: Fragment for testing task assignment stale-state scenarios.
 * 
 * DO NOT WIRE INTO PRODUCTION NAVIGATION.
 * Access via direct fragment transaction or for testing only.
 * 
 * MUST BE REMOVED before production release.
 */
@AndroidEntryPoint
class DebugSeedFragment : Fragment() {

    @Inject
    lateinit var debugSeedUtil: DebugSeedUtil

    private var _binding: FragmentDebugSeedBinding? = null
    private val binding get() = _binding!!

    private var currentProjectId: String? = null
    private var currentTeamId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        if (!BuildConfig.DEBUG) {
            throw IllegalStateException("DebugSeedFragment must not be used in release builds")
        }

        _binding = FragmentDebugSeedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonSeedData.setOnClickListener {
            seedTestData()
        }

        binding.buttonRemoveAlice.setOnClickListener {
            removeAliceFromTeam()
        }

        binding.buttonClearAllMembers.setOnClickListener {
            clearAllMembers()
        }

        updateStatus("Ready. Tap 'Seed Test Data' to begin.")
    }

    private fun seedTestData() {
        lifecycleScope.launch {
            try {
                val (projectId, teamId) = debugSeedUtil.seedTestProjectWithTeam()
                currentProjectId = projectId
                currentTeamId = teamId
                
                updateStatus(
                    "✓ Seeded:\n" +
                    "Project: $projectId\n" +
                    "Team: $teamId\n" +
                    "Students: Alice, Bob\n\n" +
                    "Now you can:\n" +
                    "1. Create task assigned to Alice\n" +
                    "2. Tap 'Remove Alice' to simulate stale\n" +
                    "3. Reopen task edit → test Case 2"
                )
                
                Toast.makeText(requireContext(), "Test data seeded", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                updateStatus("ERROR: ${e.message}")
                Toast.makeText(requireContext(), "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun removeAliceFromTeam() {
        val teamId = currentTeamId
        if (teamId == null) {
            Toast.makeText(requireContext(), "Seed data first", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                debugSeedUtil.removeStudentFromTeamRoster(teamId, "alice@example.com")
                
                updateStatus(
                    "✓ Alice removed from team roster\n\n" +
                    "Team now has: Bob only\n" +
                    "Alice's StudentEntity still exists\n\n" +
                    "Test Case 2:\n" +
                    "- Open task assigned to Alice\n" +
                    "- Edit title only\n" +
                    "- Verify Save is blocked"
                )
                
                Toast.makeText(requireContext(), "Alice removed from team", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                updateStatus("ERROR: ${e.message}")
                Toast.makeText(requireContext(), "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun clearAllMembers() {
        val teamId = currentTeamId
        if (teamId == null) {
            Toast.makeText(requireContext(), "Seed data first", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                debugSeedUtil.clearAllTeamMembers(teamId)
                
                updateStatus(
                    "✓ All members removed from team roster\n\n" +
                    "Team.memberEmails = []\n" +
                    "Student entities still exist\n\n" +
                    "Test Case 6:\n" +
                    "- Open task assigned to Alice\n" +
                    "- Verify empty roster stale state\n" +
                    "- Select 'Unassigned' to resolve"
                )
                
                Toast.makeText(requireContext(), "All members cleared", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                updateStatus("ERROR: ${e.message}")
                Toast.makeText(requireContext(), "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateStatus(message: String) {
        binding.statusText.text = message
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
