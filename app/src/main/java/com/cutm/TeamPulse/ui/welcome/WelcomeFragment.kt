package com.cutm.TeamPulse.ui.welcome

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.cutm.TeamPulse.R
import com.cutm.TeamPulse.databinding.FragmentWelcomeBinding

class WelcomeFragment : Fragment() {

    private var _binding: FragmentWelcomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWelcomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("TEAM_PULSE_RUNTIME", "WelcomeFragment onViewCreated START")

        // Set up click listener for Get Started button
        binding.ctaGetStarted.setOnClickListener {
            Log.d("TEAM_PULSE_RUNTIME", "Get Started button clicked")
            navigateToSignIn()
        }

        Log.d("TEAM_PULSE_RUNTIME", "WelcomeFragment onViewCreated COMPLETE")
    }

    private fun navigateToSignIn() {
        try {
            findNavController().navigate(R.id.action_welcome_to_signIn)
        } catch (e: Exception) {
            Log.e("TEAM_PULSE_RUNTIME", "Navigation failed: ${e.message}", e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
