# Multi-Project Support - Design Options

## Current Behavior (Limitation)

**Problem:** `StudentHomeViewModel` uses `firstOrNull` to select a single team when a student belongs to multiple teams across different projects. This silently picks one project without indicating to the student that other projects exist.

**Code location:** `StudentHomeViewModel.kt` line ~70:
```kotlin
val studentTeam = teams.firstOrNull { team ->
    team.memberEmails.contains(session.email)
}
```

**When this becomes a real issue:**
- A student is added to multiple teams across different projects (e.g., enrolled in multiple courses)
- `firstOrNull` picks whichever team happens to be returned first by the database query
- Student sees only ONE project card, with no indication that other active projects exist
- No way to switch between projects
- Task list aggregates across ALL projects (by email), but project card shows only one

## Design Options

### Option 1: Most-Recently-Active Project (Simple)

**Behavior:**
- Show the project the student interacted with most recently (based on last task update, or explicit "last viewed" timestamp)
- Add small indicator: "You have 2 other active projects" with a button to switch
- Clicking indicator opens a bottom sheet listing all projects, student picks one
- Selection persists in SharedPreferences

**Pros:**
- Minimal UI change (single project card remains focal)
- Easy to implement (add `lastViewedAt` timestamp to student-project association)
- Works well if students primarily focus on one project at a time

**Cons:**
- Still hides other projects by default
- Requires user to discover the "other projects" indicator
- No at-a-glance view of all project statuses

**Implementation complexity:** Low
- Add `selectedProjectId` to SharedPreferences
- Add project picker bottom sheet
- Update `firstOrNull` logic to prioritize selected project

---

### Option 2: Horizontal Carousel (Visual)

**Behavior:**
- Replace single project card with a horizontal paging carousel (ViewPager2)
- Student swipes left/right to view different projects
- Page indicator dots show "2 of 3" projects
- Task list below updates to show only tasks from currently-viewed project

**Pros:**
- All projects visible via swipe (discoverable affordance)
- Focal card design preserved (one project at a time)
- Natural gesture for switching context

**Cons:**
- Requires re-architecting the currentProject flow (ViewPager integration)
- Task list needs to filter by selected project, not all tasks by email
- Carousel may feel heavyweight if student only has 1 project (most common case)

**Implementation complexity:** Medium
- Add ViewPager2 to layout
- Create project card adapter
- Sync selected page with task list filtering
- Handle empty state when no projects exist

---

### Option 3: Stacked Summary Cards (Compact)

**Behavior:**
- Show all projects as compact cards (smaller than current focal card)
- Each card shows: project name, team name, progress %, deadline
- Tapping a card expands it to full focal view + filters task list
- Only one card expanded at a time (accordion pattern)

**Pros:**
- All projects visible at once (no hidden state)
- Works well for 2-4 projects (realistic course load)
- Tap to focus preserves hierarchy (one primary project)

**Cons:**
- Cluttered if student has many projects (edge case, but possible)
- Requires more vertical scroll space
- Loses "focal" design emphasis if all cards are equal

**Implementation complexity:** Medium
- Add RecyclerView for project list
- Add expand/collapse animation
- Update task list to filter by expanded project
- Handle empty state and single-project case

---

### Option 4: Tabs (Navigation)

**Behavior:**
- Add tab bar below greeting: "Project A | Project B | Project C"
- Selected tab determines which project card and task list are shown
- Tabs scroll horizontally if many projects
- Badge indicator on tab if project has overdue tasks

**Pros:**
- Clear, standard navigation pattern (familiar to users)
- Easy to see all projects at a glance (tab labels)
- Works for any number of projects (scrollable tabs)

**Cons:**
- Adds permanent UI chrome (tab bar always visible)
- Takes vertical space from content
- May feel like overkill if student only has 1 project

**Implementation complexity:** Medium-High
- Add TabLayout to layout
- Sync tab selection with ViewModel state
- Update project card and task list based on selected tab
- Handle dynamic tab creation as projects are added/removed

---

### Option 5: Dropdown/Spinner (Minimal)

**Behavior:**
- Project name becomes a dropdown/spinner at top of card
- Student taps project name → sees list of all projects → selects one
- Card and task list update to show selected project
- Default selection: most recently viewed or first by date

**Pros:**
- Minimal UI impact (single interactive element)
- Works for any number of projects
- Preserves focal card design

**Cons:**
- Less discoverable (user must realize project name is tappable)
- Requires careful visual design (spinner affordance without clutter)
- No at-a-glance view of other project statuses

**Implementation complexity:** Low
- Add dropdown icon to project name
- Create project selection dialog
- Persist selection in SharedPreferences

---

### Option 6: Multi-Project Summary + Drill-Down (Information Architecture)

**Behavior:**
- Home screen shows ALL projects as compact summary cards (read-only)
- Each card shows: project name, team, progress %, deadline, task count
- Tapping a card navigates to dedicated "Project Detail" screen
- Project Detail screen shows focal card + task list for THAT project only
- Back button returns to multi-project home

**Pros:**
- Clear information hierarchy (overview → detail)
- Scales to any number of projects
- Task list never mixes projects (always scoped to one)
- Home screen gives student full context of all commitments

**Cons:**
- Requires new screen + navigation flow
- More taps to reach tasks (home → project → task)
- Deviates from current "everything on one screen" design

**Implementation complexity:** High
- Create new ProjectDetailFragment
- Add navigation action
- Redesign home screen as project list
- Update task queries to scope by project

---

## Recommendation for User Decision

**Ask the user:**

1. **Expected usage pattern:**
   - Will students typically be enrolled in 1 project at a time, or multiple simultaneously?
   - If multiple: do they work on one at a time (context-switching), or need at-a-glance view of all?

2. **Priority:**
   - Discoverability (make it obvious other projects exist) vs. Simplicity (keep UI minimal)
   - Single-screen convenience vs. Proper information architecture

3. **Design preference:**
   - Keep "focal card" concept (one primary project at a time)
   - OR embrace "dashboard" concept (all projects equal weight)

**My recommendation (pending user input):**
- **Option 1 (Most-Recently-Active)** if students typically focus on one project at a time
- **Option 3 (Stacked Summary Cards)** if students need at-a-glance view of 2-4 projects
- **Option 6 (Multi-Project Summary + Drill-Down)** if this is a real multi-course system

**Do NOT implement yet** - flag this for user to decide intended behavior before any code changes.

---

## Known Issues to Address

Regardless of which option is chosen, fix these underlying issues:

1. **Task list scope mismatch:**
   - Current: Home screen task list queries by `assigneeEmail` (all projects)
   - Should: Task list should match the project(s) currently being displayed

2. **Database design:**
   - Student can belong to multiple teams via `students` table
   - No explicit "active project" or "last viewed project" tracking
   - Consider adding `StudentProjectPreference` table with `lastViewedAt` timestamp

3. **firstOrNull selection order:**
   - Currently unpredictable (depends on DB query order)
   - Should be deterministic (e.g., sort by project due date, or team join date)

---

## Next Steps

1. User decides which option (or hybrid) matches intended product behavior
2. Create implementation plan for chosen option
3. Update known-issues.md with decision + rationale
4. Implement chosen solution in separate feature branch
