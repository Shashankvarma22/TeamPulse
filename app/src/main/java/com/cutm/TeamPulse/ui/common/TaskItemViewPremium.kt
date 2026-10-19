package com.cutm.TeamPulse.ui.common

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.cutm.TeamPulse.R
import com.cutm.TeamPulse.domain.model.TaskStatus
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip

/**
 * Premium Task Card - Enhanced with dark navy surface, task description,
 * weight display, and contextual status action buttons.
 * Match reference screenshot design.
 */
class TaskItemViewPremium @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : MaterialCardView(context, attrs, defStyleAttr) {

    private lateinit var statusChip: Chip
    private lateinit var taskTitleText: TextView
    private lateinit var taskDescriptionText: TextView
    private lateinit var weightText: TextView
    private lateinit var dueDateText: TextView
    private lateinit var actionButton1: MaterialButton
    private lateinit var actionButton2: MaterialButton
    private lateinit var actionButtonsRow: LinearLayout
    private lateinit var metadataSeparator: TextView
    
    private var currentTaskId: String = ""
    private var currentStatus: TaskStatus = TaskStatus.TODO
    private var onStatusAction: ((String, TaskStatus) -> Unit)? = null

    init {
        // Apply premium card style
        setCardBackgroundColor(context.getColor(R.color.surface_navy))
        strokeColor = context.getColor(R.color.border_primary)
        strokeWidth = resources.getDimensionPixelSize(R.dimen.card_stroke_width)
        radius = resources.getDimension(R.dimen.card_corner_radius_premium)
        cardElevation = resources.getDimension(R.dimen.elevation_sm)
        
        // Inflate layout
        LayoutInflater.from(context).inflate(R.layout.view_task_item_premium, this, true)
        
        // Find views
        statusChip = findViewById(R.id.statusChip)
        taskTitleText = findViewById(R.id.taskTitleText)
        taskDescriptionText = findViewById(R.id.taskDescriptionText)
        weightText = findViewById(R.id.weightText)
        dueDateText = findViewById(R.id.dueDateText)
        actionButton1 = findViewById(R.id.actionButton1)
        actionButton2 = findViewById(R.id.actionButton2)
        actionButtonsRow = findViewById(R.id.actionButtonsRow)
        metadataSeparator = findViewById(R.id.metadataSeparator)
        
        // Setup action buttons
        setupActionButtons()
    }

    /**
     * Set complete task data including optional description and weight
     */
    fun setTaskData(
        taskId: String,
        title: String,
        description: String?,
        status: TaskStatus,
        dueDate: String,
        weight: Float? = null,
        onStatusAction: ((String, TaskStatus) -> Unit)? = null
    ) {
        this.currentTaskId = taskId
        this.currentStatus = status
        this.onStatusAction = onStatusAction
        
        taskTitleText.text = title
        
        // Show description if available
        if (!description.isNullOrBlank()) {
            taskDescriptionText.text = description
            taskDescriptionText.isVisible = true
        } else {
            taskDescriptionText.isVisible = false
        }
        
        // Show weight if supported and > 0
        if (weight != null && weight > 0) {
            weightText.text = context.getString(R.string.weight_format, weight.toInt())
            weightText.isVisible = true
            metadataSeparator.isVisible = true
        } else {
            weightText.isVisible = false
            metadataSeparator.isVisible = false
        }
        
        dueDateText.text = dueDate
        setStatus(status)
    }

    /**
     * Set status and update UI including action buttons
     */
    private fun setStatus(status: TaskStatus) {
        currentStatus = status
        
        statusChip.apply {
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
        
        updateActionButtons()
    }

    /**
     * Update action buttons based on current task status
     */
    private fun updateActionButtons() {
        actionButtonsRow.isVisible = onStatusAction != null
        
        when (currentStatus) {
            TaskStatus.TODO -> {
                actionButton1.text = context.getString(R.string.action_in_progress)
                actionButton2.text = context.getString(R.string.action_complete)
                
                actionButton1.setOnClickListener {
                    onStatusAction?.invoke(currentTaskId, TaskStatus.IN_PROGRESS)
                }
                actionButton2.setOnClickListener {
                    onStatusAction?.invoke(currentTaskId, TaskStatus.DONE)
                }
            }
            TaskStatus.IN_PROGRESS -> {
                actionButton1.text = context.getString(R.string.action_todo)
                actionButton2.text = context.getString(R.string.action_complete)
                
                actionButton1.setOnClickListener {
                    onStatusAction?.invoke(currentTaskId, TaskStatus.TODO)
                }
                actionButton2.setOnClickListener {
                    onStatusAction?.invoke(currentTaskId, TaskStatus.DONE)
                }
            }
            TaskStatus.DONE -> {
                actionButton1.text = context.getString(R.string.action_todo)
                actionButton2.text = context.getString(R.string.action_in_progress)
                
                actionButton1.setOnClickListener {
                    onStatusAction?.invoke(currentTaskId, TaskStatus.TODO)
                }
                actionButton2.setOnClickListener {
                    onStatusAction?.invoke(currentTaskId, TaskStatus.IN_PROGRESS)
                }
            }
        }
    }

    /**
     * Setup action button styling and behavior
     */
    private fun setupActionButtons() {
        // Prevent double taps with click debouncing
        var lastClickTime = 0L
        val clickDebounce = 500L
        
        actionButton1.setOnClickListener { view ->
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime > clickDebounce) {
                lastClickTime = currentTime
                view.performClick()
            }
        }
        
        actionButton2.setOnClickListener { view ->
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime > clickDebounce) {
                lastClickTime = currentTime
                view.performClick()
            }
        }
    }
}
