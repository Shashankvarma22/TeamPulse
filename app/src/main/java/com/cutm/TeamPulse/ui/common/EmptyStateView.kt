package com.cutm.TeamPulse.ui.common

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.view.isVisible
import com.cutm.TeamPulse.R
import com.cutm.TeamPulse.databinding.ViewEmptyStateBinding

/**
 * Reusable empty state component with consistent layout and styling.
 *
 * Usage:
 * ```xml
 * <EmptyStateView
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     app:emptyIcon="@drawable/ic_empty_team"
 *     app:emptyTitle="@string/empty_state_no_teams_title"
 *     app:emptyMessage="@string/empty_state_no_teams_message"
 *     app:emptyActionText="@string/action_refresh"
 *     app:emptyActionVisible="false" />
 * ```
 */
class EmptyStateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding: ViewEmptyStateBinding

    var onActionClick: (() -> Unit)? = null

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER

        binding = ViewEmptyStateBinding.inflate(
            LayoutInflater.from(context),
            this
        )

        // Apply custom attributes
        context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.EmptyStateView,
            0,
            0
        ).apply {
            try {
                // Icon
                val iconResId = getResourceId(R.styleable.EmptyStateView_emptyIcon, 0)
                if (iconResId != 0) {
                    binding.emptyIcon.setImageResource(iconResId)
                }

                // Title
                getString(R.styleable.EmptyStateView_emptyTitle)?.let { title ->
                    binding.emptyTitle.text = title
                }

                // Message
                getString(R.styleable.EmptyStateView_emptyMessage)?.let { message ->
                    binding.emptyMessage.text = message
                }

                // Action button
                val actionText = getString(R.styleable.EmptyStateView_emptyActionText)
                val actionVisible = getBoolean(R.styleable.EmptyStateView_emptyActionVisible, false)

                binding.emptyActionButton.apply {
                    text = actionText
                    isVisible = actionVisible && !actionText.isNullOrEmpty()
                    setOnClickListener { onActionClick?.invoke() }
                }
            } finally {
                recycle()
            }
        }
    }

    fun setIcon(iconResId: Int) {
        binding.emptyIcon.setImageResource(iconResId)
    }

    fun setTitle(title: String) {
        binding.emptyTitle.text = title
    }

    fun setMessage(message: String) {
        binding.emptyMessage.text = message
    }

    fun setActionText(text: String?, visible: Boolean = true) {
        binding.emptyActionButton.apply {
            this.text = text
            isVisible = visible && !text.isNullOrEmpty()
        }
    }
}
