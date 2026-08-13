package com.cutm.TeamPulse.ui.auth

import com.cutm.TeamPulse.domain.model.UserSession
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cutm.TeamPulse.R
import com.cutm.TeamPulse.core.auth.AuthorizationManager
import com.cutm.TeamPulse.core.auth.AuthorizationOutcome
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

        binding.subtitleText.text =
            getString(R.string.sign_in_subtitle)

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
                    viewModel.sheetsProof.collect { message ->
                        binding.statusText.visibility = View.VISIBLE
                        binding.statusText.text = message
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

    private fun render(state: UiState<UserSession>) {
        binding.signInProgress.visibility =
            if (state is UiState.Loading) {
                View.VISIBLE
            } else {
                View.GONE
            }

        binding.googleSignInButton.isEnabled =
            state !is UiState.Loading

        when (state) {
            is UiState.Error -> {
                binding.statusText.visibility = View.VISIBLE
                binding.statusText.text = state.message
            }

            is UiState.Success -> {
                binding.statusText.visibility = View.VISIBLE
                binding.statusText.text =
                    getString(R.string.sign_in_success_placeholder)
            }

            else -> {
                binding.statusText.visibility = View.GONE
            }
        }
    }
}
