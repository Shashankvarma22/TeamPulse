package com.cutm.TeamPulse.ui.common

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import com.cutm.TeamPulse.R
import com.cutm.TeamPulse.databinding.ViewProjectProgressCardBinding
import com.google.android.material.card.MaterialCardView

/**
 * Compact project progress card for Teacher Home overview.
 * Shows project name, completion percentage, and deadline proximity.
 */
class ProjectProgressCard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : MaterialCardView(context, attrs, defStyleAttr) {

    private val binding: ViewProjectProgressCardBinding

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
                binding.progressText.text = context.getString(R.string.progress_percentage, progress)

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
        binding.progressText.text = context.getString(R.string.progress_percentage, progress)
        binding.deadlineText.text = deadline
    }
}
