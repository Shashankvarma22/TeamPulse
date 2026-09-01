package com.cutm.TeamPulse.ui.auth

import com.cutm.TeamPulse.core.auth.GoogleIdentity
import android.app.Activity
import android.os.Bundle
import android.view.View
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
 
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        authorizationLauncher =
            registerForActivityResult(
                ActivityResultContracts.StartIntentSenderForResult()
            ) { result ->

                if (result.resultCode != Activity.RESULT_OK) {
                    viewModel.onAuthorizationError(
                        getString(R.string.sign_in_error_cancelled)
                    )
                    return@registerForActivityResult
                }

                val resultIntent = result.data

                if (resultIntent == null) {
                    viewModel.onAuthorizationError(
                        getString(R.string.sign_in_error_generic)
                    )
                    return@registerForActivityResult
                }

                lifecycleScope.launch {
                    when (
                        val outcome =
                            authorizationManager.handleAuthorizationResult(resultIntent)
                    ) {
                        is AuthorizationOutcome.Authorized -> {
                            viewModel.onAuthorizationToken(
                                token = outcome.accessToken,
                                expiresAtMillis = outcome.expiresAtMillis,
                            )
                        }

                        is AuthorizationOutcome.ResolutionRequired -> {
                            viewModel.onAuthorizationError(
                                getString(R.string.sign_in_error_generic)
                            )
                        }

                        is AuthorizationOutcome.Error -> {
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

        binding.googleSignInButton.setOnClickListener {
            viewModel.onGoogleSignInClicked(requireActivity())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.uiState.collect { state ->
                        render(state)
                    }
                }

                launch {
                    viewModel.requestSheetsAuthorization.collect {
                        requestSheetsAuthorization()
                    }
                }

                launch {
                    viewModel.navigateToHome.collect { role ->
                        navigateToHome(role)
                    }
                }
            }
        }
    }

    private fun requestSheetsAuthorization() {
        viewLifecycleOwner.lifecycleScope.launch {

            when (
                val outcome =
                    authorizationManager.requestSheetsReadAuthorization(
                        requireActivity()
                    )
            ) {
                is AuthorizationOutcome.Authorized -> {
                    viewModel.onAuthorizationToken(
                        token = outcome.accessToken,
                        expiresAtMillis = outcome.expiresAtMillis,
                    )
                }

                is AuthorizationOutcome.ResolutionRequired -> {
                    authorizationLauncher.launch(
                        IntentSenderRequest.Builder(
                            outcome.pendingIntent.intentSender
                        ).build()
                    )
                }

                is AuthorizationOutcome.Error -> {
                    viewModel.onAuthorizationError(outcome.message)
                }
            }
        }
    }

    private fun navigateToHome(role: SessionRole) {
        val navController = findNavController()
        
        // Navigate to appropriate home based on role, clearing auth back stack
        when (role) {
            SessionRole.TEACHER -> {
                navController.navigate(R.id.action_signIn_to_teacher_graph)
            }
            SessionRole.STUDENT -> {
                navController.navigate(R.id.action_signIn_to_student_graph)
            }
        }
    }

    private fun render(state: UiState<GoogleIdentity>) {
        // Update button state
        binding.googleSignInButton.isEnabled = state !is UiState.Loading

        // Update loading visibility
        binding.loadingLayout.visibility = if (state is UiState.Loading) {
            View.VISIBLE
        } else {
            View.GONE
        }

        // Update status card
        when (state) {
            is UiState.Error -> {
                showStatus(
                    message = state.message,
                    isError = true
                )
            }

            is UiState.Success -> {
                showStatus(
                    message = getString(R.string.sign_in_success),
                    isError = false
                )
            }

            else -> {
                binding.statusCard.visibility = View.GONE
            }
        }
    }

    private fun showStatus(message: String, isError: Boolean) {
        binding.statusCard.visibility = View.VISIBLE
        binding.statusText.text = message

        if (isError) {
            binding.statusIcon.setImageResource(R.drawable.ic_alert)
            binding.statusIcon.setColorFilter(
                ContextCompat.getColor(requireContext(), R.color.error)
            )
            binding.statusCard.strokeColor = ContextCompat.getColor(requireContext(), R.color.error)
        } else {
            binding.statusIcon.setImageResource(R.drawable.ic_progress)
            binding.statusIcon.setColorFilter(
                ContextCompat.getColor(requireContext(), R.color.success)
            )
            binding.statusCard.strokeColor = ContextCompat.getColor(requireContext(), R.color.success)
        }
    }
}
