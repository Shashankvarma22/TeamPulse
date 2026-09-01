package com.cutm.TeamPulse.ui.common

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import com.cutm.TeamPulse.R
import com.cutm.TeamPulse.databinding.ViewTaskItemBinding
import com.cutm.TeamPulse.domain.model.TaskStatus
import com.google.android.material.card.MaterialCardView

/**
 * Task list item showing title, status chip, and due date.
 * Used in Student Home task list.
 */
class TaskItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : MaterialCardView(context, attrs, defStyleAttr) {

    private val binding: ViewTaskItemBinding

    init {
        // Apply card style
        setCardBackgroundColor(context.getColor(R.color.card_background))
        strokeColor = context.getColor(R.color.card_stroke)
        strokeWidth = resources.getDimensionPixelSize(R.dimen.card_stroke_width)
        radius = resources.getDimension(R.dimen.card_corner_radius)
        cardElevation = 0f
        
        // Make entire card clickable with ripple effect
        isClickable = true
        isFocusable = true
        foreground = context.getDrawable(android.R.drawable.list_selector_background)

        binding = ViewTaskItemBinding.inflate(
            LayoutInflater.from(context),
            this
        )

        // Apply custom attributes
        context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.TaskItemView,
            0,
            0
        ).apply {
            try {
                getString(R.styleable.TaskItemView_taskTitle)?.let { title ->
                    binding.taskTitleText.text = title
                }

                val statusValue = getInt(R.styleable.TaskItemView_taskStatus, 0)
                val status = when (statusValue) {
                    1 -> TaskStatus.IN_PROGRESS
                    2 -> TaskStatus.DONE
                    else -> TaskStatus.TODO
                }
                setStatus(status)

                getString(R.styleable.TaskItemView_taskDueDate)?.let { dueDate ->
                    binding.dueDateText.text = dueDate
                }
            } finally {
                recycle()
            }
        }
    }

    fun setTaskData(title: String, status: TaskStatus, dueDate: String) {
        binding.taskTitleText.text = title
        setStatus(status)
        binding.dueDateText.text = dueDate
    }

    override fun setOnClickListener(listener: OnClickListener?) {
        super.setOnClickListener(listener)
    }

    private fun setStatus(status: TaskStatus) {
        binding.statusChip.apply {
            when (status) {
                TaskStatus.TODO -> {
                    text = context.getString(R.string.status_todo)
                    setChipBackgroundColorResource(R.color.status_todo)
                }
                TaskStatus.IN_PROGRESS -> {
                    text = context.getString(R.string.status_in_progress)
                    setChipBackgroundColorResource(R.color.status_in_progress)
                }
                TaskStatus.DONE -> {
                    text = context.getString(R.string.status_done)
                    setChipBackgroundColorResource(R.color.status_done)
                }
            }
        }
    }
}
