package com.cutm.TeamPulse.ui.auth

import com.cutm.TeamPulse.BuildConfig
import com.cutm.TeamPulse.core.auth.GoogleIdentity
import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.cutm.TeamPulse.R
import com.cutm.TeamPulse.core.auth.AuthorizationManager
import com.cutm.TeamPulse.core.auth.AuthorizationOutcome
import com.cutm.TeamPulse.core.auth.SessionRole
import com.cutm.TeamPulse.databinding.FragmentSignInBinding
import com.cutm.TeamPulse.ui.common.BaseFragment
import com.cutm.TeamPulse.ui.common.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SignInFragment :
    BaseFragment<FragmentSignInBinding>(FragmentSignInBinding::inflate) {

    private val viewModel: SignInViewModel by viewModels()

    @Inject
    lateinit var authorizationManager: AuthorizationManager

    private lateinit var authorizationLauncher:
        ActivityResultLauncher<IntentSenderRequest>
    
    // Flag to track if we've already navigated away from this fragment
    // Prevents checking existing session multiple times after navigation
    private var hasNavigatedAway = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("TEAM_PULSE_SIGNIN", "SignInFragment.onCreate() - hasNavigatedAway=$hasNavigatedAway")

        authorizationLauncher =
            registerForActivityResult(
                ActivityResultContracts.StartIntentSenderForResult()
            ) { result ->
                Log.d("TEAM_PULSE_SIGNIN", "Authorization launcher callback: resultCode=${result.resultCode}, data=${result.data}")

                if (result.resultCode != Activity.RESULT_OK) {
                    Log.d("TEAM_PULSE_SIGNIN", "Authorization cancelled or failed: resultCode=${result.resultCode}")
                    viewModel.onAuthorizationError(
                        getString(R.string.sign_in_error_cancelled)
                    )
                    return@registerForActivityResult
                }

                val resultIntent = result.data

                if (resultIntent == null) {
                    Log.d("TEAM_PULSE_SIGNIN", "Authorization result intent is null")
                    viewModel.onAuthorizationError(
                        getString(R.string.sign_in_error_generic)
                    )
                    return@registerForActivityResult
                }

                lifecycleScope.launch {
                    Log.d("TEAM_PULSE_SIGNIN", "Processing authorization result")
                    when (
                        val outcome =
                            authorizationManager.handleAuthorizationResult(resultIntent)
                    ) {
                        is AuthorizationOutcome.Authorized -> {
                            Log.d("TEAM_PULSE_SIGNIN", "Authorization successful: token length=${outcome.accessToken.length}, expiresAt=${outcome.expiresAtMillis}")
                            viewModel.onAuthorizationToken(
                                token = outcome.accessToken,
                                expiresAtMillis = outcome.expiresAtMillis,
                            )
                        }

                        is AuthorizationOutcome.ResolutionRequired -> {
                            Log.d("TEAM_PULSE_SIGNIN", "Authorization resolution required")
                            viewModel.onAuthorizationError(
                                getString(R.string.sign_in_error_generic)
                            )
                        }

                        is AuthorizationOutcome.Error -> {
                            Log.d("TEAM_PULSE_SIGNIN", "Authorization error: ${outcome.message}")
                            viewModel.onAuthorizationError(outcome.message)
                        }
                    }
                }
            }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("TEAM_PULSE_SIGNIN", "SignInFragment.onViewCreated() - hasNavigatedAway=$hasNavigatedAway")

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    Log.d("TEAM_PULSE_SIGNIN", "Back pressed, navigating up to Welcome")
                    findNavController().navigateUp()
                }
            }
        )

        binding.googleSignInButton.setOnClickListener {
            Log.d("TEAM_PULSE_SIGNIN", "Google Sign-In button clicked")
            viewModel.onGoogleSignInClicked(requireActivity())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                Log.d("TEAM_PULSE_SIGNIN", "Lifecycle.STARTED - setting up collectors")

                // Check for existing session ONLY if we haven't already navigated away
                if (!hasNavigatedAway) {
                    launch {
                        Log.d("TEAM_PULSE_SIGNIN", "Starting existingSession collector")
                        viewModel.existingSession.collect { session ->
                            if (session != null) {
                                Log.d("TEAM_PULSE_SIGNIN", "Found existing session: ${session.email}, role=${session.role} - navigating to home")
                                hasNavigatedAway = true
                                navigateToHome(session.role)
                            } else {
                                Log.d("TEAM_PULSE_SIGNIN", "No existing session found")
                            }
                        }
                    }
                } else {
                    Log.d("TEAM_PULSE_SIGNIN", "Skipping existingSession check - already navigated away")
                }

                launch {
                    Log.d("TEAM_PULSE_SIGNIN", "Starting uiState collector")
                    viewModel.uiState.collect { state ->
                        Log.d("TEAM_PULSE_SIGNIN", "UI State changed: $state")
                        render(state)
                    }
                }

                launch {
                    Log.d("TEAM_PULSE_SIGNIN", "Starting requestSheetsAuthorization collector")
                    viewModel.requestSheetsAuthorization.collect {
                        Log.d("TEAM_PULSE_SIGNIN", "Requesting Sheets authorization")
                        requestSheetsAuthorization()
                    }
                }

                launch {
                    Log.d("TEAM_PULSE_SIGNIN", "Starting navigateToHome collector")
                    viewModel.navigateToHome.collect { role ->
                        Log.d("TEAM_PULSE_SIGNIN", "Navigation event received: role=$role")
                        hasNavigatedAway = true
                        navigateToHome(role)
                    }
                }
            }
        }
    }

    private fun requestSheetsAuthorization() {
        Log.d("TEAM_PULSE_SIGNIN", "requestSheetsAuthorization() called")
        viewLifecycleOwner.lifecycleScope.launch {

            when (
                val outcome =
                    authorizationManager.requestSheetsReadAuthorization(
                        requireActivity()
                    )
            ) {
                is AuthorizationOutcome.Authorized -> {
                    Log.d("TEAM_PULSE_SIGNIN", "Sheets authorization successful")
                    viewModel.onAuthorizationToken(
                        token = outcome.accessToken,
                        expiresAtMillis = outcome.expiresAtMillis,
                    )
                }

                is AuthorizationOutcome.ResolutionRequired -> {
                    Log.d("TEAM_PULSE_SIGNIN", "Sheets resolution required, launching intent")
                    authorizationLauncher.launch(
                        IntentSenderRequest.Builder(
                            outcome.pendingIntent.intentSender
                        ).build()
                    )
                }

                is AuthorizationOutcome.Error -> {
                    Log.d("TEAM_PULSE_SIGNIN", "Sheets authorization error: ${outcome.message}")
                    viewModel.onAuthorizationError(outcome.message)
                }
            }
        }
    }

    private fun navigateToHome(role: SessionRole) {
        val navController = findNavController()

        Log.d("TEAM_PULSE_SIGNIN", "NAVIGATION_REQUESTED: role=$role")

        try {
            when (role) {
                SessionRole.TEACHER -> {
                    Log.d("TEAM_PULSE_SIGNIN", "Navigating to teacher_graph")
                    navController.navigate(R.id.action_signIn_to_teacher_graph)
                    Log.d("TEAM_PULSE_SIGNIN", "NAVIGATION_SUCCESS: teacher_graph")
                }
                SessionRole.STUDENT -> {
                    Log.d("TEAM_PULSE_SIGNIN", "Navigating to student_graph")
                    navController.navigate(R.id.action_signIn_to_student_graph)
                    Log.d("TEAM_PULSE_SIGNIN", "NAVIGATION_SUCCESS: student_graph")
                }
            }
        } catch (e: Exception) {
            Log.e("TEAM_PULSE_SIGNIN", "NAVIGATION_FAILED: ${e.message}", e)
        }
    }

    private fun render(state: UiState<GoogleIdentity>) {
        Log.d("TEAM_PULSE_SIGNIN", "render() called with state: $state")

        val buttonEnabled = state !is UiState.Loading
        Log.d("TEAM_PULSE_SIGNIN", "Setting button enabled=$buttonEnabled (state=${state::class.simpleName})")
        binding.googleSignInButton.isEnabled = buttonEnabled

        binding.signInProgress.visibility = if (state is UiState.Loading) {
            Log.d("TEAM_PULSE_SIGNIN", "Showing loading progress")
            View.VISIBLE
        } else {
            Log.d("TEAM_PULSE_SIGNIN", "Hiding loading progress")
            View.GONE
        }

        when (state) {
            is UiState.Error -> {
                Log.d("TEAM_PULSE_SIGNIN", "Showing error: ${state.message}")
                showStatus(
                    message = state.message,
                    isError = true
                )
            }

            is UiState.Success -> {
                Log.d("TEAM_PULSE_SIGNIN", "Showing success message")
                showStatus(
                    message = getString(R.string.sign_in_success),
                    isError = false
                )
            }

            else -> {
                Log.d("TEAM_PULSE_SIGNIN", "Clearing status text")
                binding.statusText.visibility = View.GONE
            }
        }
    }

    private fun showStatus(message: String, isError: Boolean) {
        binding.statusText.visibility = View.VISIBLE
        binding.statusText.text = message

        if (isError) {
            binding.statusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.error))
        } else {
            binding.statusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.success))
        }
    }
}
