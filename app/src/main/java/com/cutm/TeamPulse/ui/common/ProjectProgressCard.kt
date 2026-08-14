package com.cutm.TeamPulse.ui.common

import android.animation.ValueAnimator
import android.content.Context
import android.provider.Settings
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.animation.DecelerateInterpolator
import com.cutm.TeamPulse.R
import com.cutm.TeamPulse.databinding.ViewProjectProgressCardBinding
import com.google.android.material.card.MaterialCardView

/**
 * Compact project progress card for Teacher Home overview.
 * Shows project name, completion percentage, and deadline proximity.
 * Animates progress changes smoothly.
 */
class ProjectProgressCard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : MaterialCardView(context, attrs, defStyleAttr) {

    private val binding: ViewProjectProgressCardBinding
    private var currentProgress: Int = 0
    private var progressAnimator: ValueAnimator? = null

    init {
        // Apply card style
        setCardBackgroundColor(context.getColor(R.color.card_background))
        cardElevation = resources.getDimension(R.dimen.card_elevation)
        radius = resources.getDimension(R.dimen.card_corner_radius)

        binding = ViewProjectProgressCardBinding.inflate(
            LayoutInflater.from(context),
            this
        )

        // Apply custom attributes
        context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.ProjectProgressCard,
            0,
            0
        ).apply {
            try {
                getString(R.styleable.ProjectProgressCard_projectName)?.let { name ->
                    binding.projectNameText.text = name
                }

                val progress = getInteger(R.styleable.ProjectProgressCard_projectProgress, 0)
                setProgressImmediately(progress)

                getString(R.styleable.ProjectProgressCard_projectDeadline)?.let { deadline ->
                    binding.deadlineText.text = deadline
                }
            } finally {
                recycle()
            }
        }
    }

    fun setProjectData(name: String, progress: Int, deadline: String) {
        binding.projectNameText.text = name
        animateProgress(progress)
        binding.deadlineText.text = deadline
    }

    private fun animateProgress(targetProgress: Int) {
        // Don't animate if value hasn't changed
        if (targetProgress == currentProgress) return

        // Check if animations are disabled
        val animationScale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )

        if (animationScale == 0f) {
            setProgressImmediately(targetProgress)
            return
        }

        // Animate from current to target
        progressAnimator?.cancel()
        progressAnimator = ValueAnimator.ofInt(currentProgress, targetProgress).apply {
            duration = 400L
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val value = animator.animatedValue as Int
                binding.progressText.text = context.getString(R.string.progress_percentage, value)
            }
            start()
        }

        currentProgress = targetProgress
    }

    private fun setProgressImmediately(progress: Int) {
        currentProgress = progress
        binding.progressText.text = context.getString(R.string.progress_percentage, progress)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        progressAnimator?.cancel()
        progressAnimator = null
    }
}
