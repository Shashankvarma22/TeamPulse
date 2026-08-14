package com.cutm.TeamPulse.ui.common

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import com.cutm.TeamPulse.R
import com.cutm.TeamPulse.databinding.ViewSectionHeaderBinding

/**
 * Reusable section header component with optional subtitle and action button.
 *
 * Usage:
 * ```xml
 * <SectionHeaderView
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     app:sectionTitle="My Team"
 *     app:sectionSubtitle="4 members"
 *     app:sectionActionText="View All"
 *     app:sectionActionVisible="true" />
 * ```
 */
class SectionHeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding: ViewSectionHeaderBinding

    var onActionClick: (() -> Unit)? = null

    init {
        binding = ViewSectionHeaderBinding.inflate(
            LayoutInflater.from(context),
            this
        )

        // Apply custom attributes
        context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.SectionHeaderView,
            0,
            0
        ).apply {
            try {
                // Title
                getString(R.styleable.SectionHeaderView_sectionTitle)?.let { title ->
                    binding.sectionTitle.text = title
                }

                // Subtitle
                val subtitle = getString(R.styleable.SectionHeaderView_sectionSubtitle)
                binding.sectionSubtitle.apply {
                    text = subtitle
                    isVisible = !subtitle.isNullOrEmpty()
                }

                // Action button
                val actionText = getString(R.styleable.SectionHeaderView_sectionActionText)
                val actionVisible = getBoolean(R.styleable.SectionHeaderView_sectionActionVisible, false)

                binding.sectionAction.apply {
                    text = actionText
                    isVisible = actionVisible && !actionText.isNullOrEmpty()
                    setOnClickListener { onActionClick?.invoke() }
                }
            } finally {
                recycle()
            }
        }
    }

    fun setTitle(title: String) {
        binding.sectionTitle.text = title
    }

    fun setSubtitle(subtitle: String?) {
        binding.sectionSubtitle.apply {
            text = subtitle
            isVisible = !subtitle.isNullOrEmpty()
        }
    }

    fun setActionText(text: String?, visible: Boolean = true) {
        binding.sectionAction.apply {
            this.text = text
            isVisible = visible && !text.isNullOrEmpty()
        }
    }
}
