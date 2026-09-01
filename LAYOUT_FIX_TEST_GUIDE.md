# Layout Overlap Fix - Testing Guide

## What Was Fixed

**Root Cause:** Missing `Barrier` constraints in `fragment_student_home.xml` causing views to overlap when mutually-exclusive views toggle visibility.

### Problem Explained

ConstraintLayout has two mutually-exclusive views that can be visible:
1. **Project Zone:** `projectFocalCard` OR `projectEmptyState` (never both)
2. **Tasks Zone:** `tasksContainer` OR `tasksEmptyState` (never both)

**Before Fix:**
```xml
<!-- tasksSectionHeader constrained DIRECTLY to projectEmptyState -->
<SectionHeaderView 
    app:layout_constraintTop_toBottomOf="@id/projectEmptyState" />
```

**Issue:** When `projectFocalCard` is visible and `projectEmptyState` is gone:
- `projectEmptyState` has `height = 0` (gone)
- `tasksSectionHeader` positions immediately after it
- **Result:** `tasksSectionHeader` overlaps `projectFocalCard`

Same issue occurred between `tasksContainer` and `progressSectionHeader`.

### Solution

Added **Barriers** that reference both mutually-exclusive views:

```xml
<!-- Barrier after project zone -->
<Barrier
    android:id="@+id/projectSectionBarrier"
    app:barrierDirection="bottom"
    app:constraint_referenced_ids="projectFocalCard,projectEmptyState" />

<!-- Next section constrains to barrier, not specific view -->
<SectionHeaderView
    app:layout_constraintTop_toBottomOf="@id/projectSectionBarrier" />
```

**Result:** Barrier is always positioned after whichever view is visible.

---

## How to Test

### Build and Install

```powershell
# 1. Build APK
.\gradlew assembleDebug

# 2. Install on device (requires adb in PATH)
adb install -r app\build\outputs\apk\debug\app-debug.apk

# OR: Install from Android Studio
# - Open project in Android Studio
# - Click Run (green play button)
# - Select connected device
```

### Test Scenario 1: No Overlap with Project Card

**Steps:**
1. Sign in as student: `231801371093@cutmap.ac.in`
2. Confirm student has assigned project + tasks (from previous test data)
3. Observe student home screen

**Expected Result:**
```
┌─────────────────────────────────┐
│ Welcome, [Student Name]         │  ← Greeting
├─────────────────────────────────┤
│ [PROJECT NAME]                  │  ← projectFocalCard
│ Team Alpha                      │
│ 65% Complete                    │
│ Due in 12 days                  │
└─────────────────────────────────┘
                                      ← CLEAR SPACING (no overlap)
Your Tasks ────────────────────────  ← tasksSectionHeader
┌─────────────────────────────────┐
│ Task: Hi                        │  ← Task card 1
│ [To Do] Due in X days           │
└─────────────────────────────────┘
                                      ← CLEAR SPACING (no overlap)
Your Progress ──────────────────────  ← progressSectionHeader
┌─────────────────────────────────┐
│ Progress Tracking Coming Soon   │
└─────────────────────────────────┘
```

**Verification:**
- ✅ No visual overlap between project card and "Your Tasks" header
- ✅ Clear vertical spacing between sections
- ✅ Task card doesn't overlap "Your Progress" section

### Test Scenario 2: Full-Card Tap Response

**Steps:**
1. On student home screen with task cards visible
2. Tap anywhere on the task card (not just the title)
3. Try tapping near edges, center, status chip area

**Expected Result:**
- ✅ Entire card area is tappable (not just partial regions)
- ✅ Task detail bottom sheet opens on tap
- ✅ No "dead zones" where taps don't register

### Test Scenario 3: Empty State (No Overlap)

**Steps:**
1. Sign in as student with no assigned project
2. Observe student home screen

**Expected Result:**
```
┌─────────────────────────────────┐
│ Welcome, [Student Name]         │
├─────────────────────────────────┤
│ No Active Project               │  ← projectEmptyState
│ [Icon] You'll see your project  │
│ here when assigned by teacher   │
└─────────────────────────────────┘
                                      ← CLEAR SPACING
Your Tasks ────────────────────────
┌─────────────────────────────────┐
│ No Tasks Assigned               │  ← tasksEmptyState
│ [Icon]                          │
└─────────────────────────────────┘
                                      ← CLEAR SPACING
Your Progress ──────────────────────
┌─────────────────────────────────┐
│ Progress Tracking Coming Soon   │
└─────────────────────────────────┘
```

**Verification:**
- ✅ No overlap between "No Active Project" card and "Your Tasks" header
- ✅ No overlap between "No Tasks Assigned" and "Your Progress"

---

## Screenshot Checklist

**Please capture and confirm:**

1. **With Project + Tasks:**
   - [ ] Full screen showing all sections
   - [ ] Clear spacing between project card and tasks header
   - [ ] Clear spacing between task cards and progress section
   - [ ] No visual overlap anywhere

2. **Task Card Tap Test:**
   - [ ] Tap task card → bottom sheet opens
   - [ ] Tap near top edge of card → still works
   - [ ] Tap near bottom edge of card → still works
   - [ ] Tap in overlap region (where it failed before) → now works

3. **Empty States:**
   - [ ] Student with no project (empty states visible)
   - [ ] No visual overlap

---

## Technical Details

### Changes Made

**File:** `app/src/main/res/layout/fragment_student_home.xml`

**Added:**
```xml
<!-- Line ~94: Barrier after project zone -->
<androidx.constraintlayout.widget.Barrier
    android:id="@+id/projectSectionBarrier"
    app:barrierDirection="bottom"
    app:constraint_referenced_ids="projectFocalCard,projectEmptyState" />

<!-- Line ~156: Barrier after tasks zone -->
<androidx.constraintlayout.widget.Barrier
    android:id="@+id/tasksSectionBarrier"
    app:barrierDirection="bottom"
    app:constraint_referenced_ids="tasksContainer,tasksEmptyState" />
```

**Changed:**
```xml
<!-- tasksSectionHeader now constrains to barrier, not direct view -->
<com.cutm.TeamPulse.ui.common.SectionHeaderView
    android:id="@+id/tasksSectionHeader"
    app:layout_constraintTop_toBottomOf="@id/projectSectionBarrier" />

<!-- progressSectionHeader now constrains to barrier, not direct view -->
<com.cutm.TeamPulse.ui.common.SectionHeaderView
    android:id="@+id/progressSectionHeader"
    app:layout_constraintTop_toBottomOf="@id/tasksSectionBarrier" />
```

### Why This Works

**Barrier Behavior:**
- Barrier is positioned at the **bottom** of whichever referenced view is **lowest**
- When `projectFocalCard` visible: Barrier at bottom of focal card
- When `projectEmptyState` visible: Barrier at bottom of empty state
- Next section always has correct spacing

**Same Pattern Used Elsewhere:**
- `TeacherHomeFragment` already uses this pattern (see `projectsSectionBarrier` at line 183)
- This is the recommended ConstraintLayout approach for mutually-exclusive views

---

## Commit Info

**Commit:** `6b6d33f`  
**Message:** "fix: task card overlap with adjacent views in student home"

**Files Changed:**
- `app/src/main/res/layout/fragment_student_home.xml` (added 2 barriers, updated 2 constraints)

---

## If Issue Persists

If overlap still occurs after this fix, check:

1. **Screen size:** Test on different device sizes (small phone, large tablet)
2. **Font scaling:** Test with large system font (Accessibility → Font Size)
3. **Scroll position:** Ensure ScrollView allows full content to scroll
4. **Other overlaps:** Check if overlap is in different zone than fixed

Report back with:
- Screenshot showing remaining overlap
- Device model and screen size
- Which specific views are overlapping
