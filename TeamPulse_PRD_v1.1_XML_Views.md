# Product Requirements Document

## PS 31 --- Team Dynamics & Gamified Project Management with AI Conflict Detection

**Document Version:** 1.1 **Status:** Revised for Android Views / Empty
Views Activity **Owner:** Product/Engineering **Platform:** Android
(Kotlin, XML Views, ViewBinding)

**UI Decision:** The Android client will use XML Views with ViewBinding
and Navigation Component. The project will be created from Android
Studio's **Empty Views Activity** template. Jetpack Compose is
explicitly out of scope for v1 so that the implementation matches the
selected project template and keeps the UI technology consistent
throughout the application.

------------------------------------------------------------------------

## 1. Executive Summary

Student team projects routinely suffer from three chronic failures:
free-riding (uneven contribution), invisible conflict (breakdowns
surface only at the deadline), and subjective grading (teachers grade
the loudest student, not the most productive one). This product turns a
classroom team project into an instrumented, gamified workspace that:

-   Tracks *who actually did what* (tasks, commits, wiki edits, reviews)
    automatically instead of relying on self-report.
-   Surfaces collaboration risk *before* it becomes a crisis, via a
    daily AI-driven conflict-detection pass.
-   Rewards good collaboration behavior with XP, badges, and
    leaderboards --- not just "task done" checkmarks.
-   Produces a defensible, formula-driven final grade that a student can
    audit against the underlying activity log.

Google Sheets is used as the system of record (not just an export
target) so that teachers who already live in Google Workspace can
inspect, override, and audit every number the app produces, and
Drive/Sheets sharing permissions double as the authorization model for
the classroom.

------------------------------------------------------------------------

## 2. Goals & Non-Goals

### 2.1 Goals

  -----------------------------------------------------------------------
  \#       Goal                Success Signal
  -------- ------------------- ------------------------------------------
  G1       Eliminate manual    Teacher grading time per project ↓ 70%
           grade tabulation    
           for team projects   

  G2       Detect              ≥80% of flagged teams surfaced ≥5 days
           collaboration       before deadline
           breakdowns before   
           final week          

  G3       Increase even       Gini coefficient of per-student
           distribution of     contribution ↓ across a term
           contribution        

  G4       Make grading        \<5% of grades disputed after rollout;
           auditable and       disputes resolved via activity log, not
           dispute-resistant   negotiation

  G5       Increase student    ≥60% weekly active usage of
           engagement via      badges/leaderboard screens
           gamification        
  -----------------------------------------------------------------------

### 2.2 Non-Goals (v1)

-   Not a full LMS (no assignment authoring, no course content
    delivery).
-   Not a generic Git hosting/code-review tool --- GitHub is a data
    source, not a destination.
-   No iOS client in v1 (Android only, per tech stack).
-   No support for institutional SSO beyond Google Workspace/Gmail
    OAuth.

------------------------------------------------------------------------

## 3. Personas

**Teacher (Project Owner)** Google Workspace account. Creates projects,
forms teams, assigns tasks, reviews AI conflict flags, writes
intervention notes, has final override authority on grades.

**Student (Contributor)** Personal or institutional Gmail account. Joins
a team the teacher adds them to, completes tasks, logs contributions,
gives/receives peer feedback, tracks their own XP/badges, sees their
(locked) grade breakdown.

**System (Background Actor)** GAS hourly aggregation job, WorkManager
weekly evaluation job, GitHub webhook receiver --- none are human but
all write to the same Sheets-backed data model and must be treated as
first-class actors in the audit trail.

------------------------------------------------------------------------

## 4. User Roles & Access Control

  -----------------------------------------------------------------------
  Capability              Teacher                 Student
  ----------------------- ----------------------- -----------------------
  Create project / Drive  ✅                      ❌
  folder                                          

  Form teams, assign      ✅                      ❌
  tasks                                           

  Append activity rows    ✅                      ✅
  (task complete, commit                          
  note, wiki edit)                                

  Submit peer feedback    ✅ (optional)           ✅

  View own contribution   ✅                      ✅
  metrics                                         

  View team-wide          ✅                      View-only, aggregate
  contribution metrics                            

  Modify                  ✅ (override only)      ❌ read-only
  `AutomatedGrades`                               

  View `ConflictFlags`    ✅                      ❌

  Write `TeacherNotes`    ✅                      ❌

  Configure GitHub        ✅                      ❌
  webhook / repo linking                          

  Receive FCM conflict    ✅                      ❌
  alerts                                          
  -----------------------------------------------------------------------

Enforcement happens at two layers: 1. **Drive/Sheets Permissions API**
--- students get Commenter/restricted-Editor access to specific tabs
only (append-only ranges where feasible); teachers get full Editor
access. 2. **App-layer role gate** --- Hilt-injected `SessionRole` gates
Compose navigation and ViewModel actions regardless of what the Sheets
ACL technically allows, so the app never renders grade-edit UI to a
student even if a permission is misconfigured.

------------------------------------------------------------------------

## 5. Authentication & Authorization Flow

1.  **Sign-In**: Google Sign-In (Credential Manager API) requests
    scopes: `drive.file`, `spreadsheets`, `userinfo.email`,
    `userinfo.profile`. GitHub read scope is requested as an
    *incremental* OAuth grant only if a teacher enables GitHub
    integration for a project (avoids over-scoping students who never
    touch GitHub).
2.  **Role Determination**: On first sign-in, app checks a central
    `Users` registry (a dedicated Sheet or lightweight Firestore doc) to
    determine whether the account is registered as Teacher or Student.
    Teachers self-register via a Workspace-domain allowlist; students
    are added by a teacher (no self-provisioning of teacher role).
3.  **Project Creation (Teacher)**: Drive API v3 creates/locates a
    "Class Projects" folder, creates a new Sheets file from an internal
    template (12 tabs, see §6), and stores the file ID in the app's
    project registry.
4.  **Sharing**: Drive Permissions API grants each enrolled student's
    Gmail account `commenter`/restricted-writer access; teacher retains
    `writer`/`owner`. Where Sheets doesn't support true column-level
    ACLs, the app enforces write-eligible ranges via Apps Script bound
    to the sheet (a script-triggered `onEdit` guard rejects/reverts
    edits from non-owners to protected tabs like `AutomatedGrades`).
5.  **Token Storage**: OAuth tokens stored via
    `EncryptedSharedPreferences`/Android Keystore; refresh handled by
    `AuthorizationClient` with silent retry before falling back to
    interactive sign-in.
6.  **GitHub Linking (optional)**: Teacher supplies a repo URL; app (or
    a lightweight backend relay, since GitHub webhooks need a public
    HTTPS endpoint) registers a webhook for `push`, `pull_request`, and
    `pull_request_review` events, scoped to that repo only.

------------------------------------------------------------------------

## 6. Data Model --- Google Sheets as System of Record

Each project = one spreadsheet with the following tabs. Row 1 is
header/schema; all writes go through a thin repository layer (never raw
range writes from arbitrary screens) so schema stays consistent.

  -------------------------------------------------------------------------------------
  Tab                       Key Columns              Written By     Notes
  ------------------------- ------------------------ -------------- -------------------
  **Projects**              project_id, name,        Teacher (app)  One row per project
                            teacher_email,                          
                            start_date, due_date,                   
                            drive_folder_id,                        
                            github_repo (opt),                      
                            status                                  

  **Teams**                 team_id, project_id,     Teacher (app)  
                            team_name,                              
                            member_emails\[\],                      
                            created_at                              

  **Students**              student_email,           Teacher (app), 
                            display_name, team_id,   self-updated   
                            joined_at, role          display name   
                                                     only           

  **TaskAssignments**       task_id, team_id,        Teacher        
                            assignee_email, title,   (create),      
                            description, weight,     Student        
                            due_date, status         (status        
                                                     update)        

  **CompletionLog**         log_id, task_id,         Student (app), Append-only
                            student_email, action,   System         
                            timestamp, evidence_link                

  **Commits**               commit_sha, repo,        GitHub webhook message stored as
                            author_email (mapped via relay          hash/summary, not
                            GitHub login↔email                      full text, to
                            table), timestamp,                      respect student
                            additions, deletions,                   privacy and repo
                            message_hash                            size

  **PeerFeedback**          feedback_id, team_id,    Student (app)  `sentiment_label`
                            from_email, to_email,                   from ML Kit runs on
                            collab_score,                           free-text comment
                            comms_score,                            client-side or via
                            reliability_score,                      relay
                            sentiment_label,                        
                            timestamp                               

  **ContributionMetrics**   student_email, team_id,  GAS hourly job Auto-computed,
                            week_id, task_pct,                      read-only to app
                            commit_count,                           
                            wiki_edits,                             
                            review_actions,                         
                            computed_at                             

  **ConflictFlags**         team_id, week_id,        WorkManager    Drives FCM alerts
                            risk_score,              daily job →    
                            risk_reasons\[\],        Sheets         
                            flagged_at,                             
                            teacher_acknowledged                    

  **AutomatedGrades**       student_email,           WorkManager    Locked/read-only to
                            project_id,              weekly job;    students
                            task_completion_score,   Teacher        
                            contribution_score,      (override      
                            peer_feedback_score,     column only)   
                            collaboration_score,                    
                            final_grade,                            
                            computed_at,                            
                            teacher_override                        

  **Achievements**          student_email, badge_id, System (rules  Feeds leaderboard
                            badge_name, awarded_at,  engine)        
                            xp_awarded                              

  **TeacherNotes**          team_id, note,           Teacher        Surfaced in app as
                            author_email, timestamp,                intervention
                            related_flag_id                         suggestions
  -------------------------------------------------------------------------------------

**Why Sheets, not just a backend DB:** transparency (teacher/institution
can open the raw sheet), zero extra hosting cost for schools, and native
fit with Workspace sharing/permissions as an access-control mechanism.
Room is the *offline* mirror, not a replacement.

------------------------------------------------------------------------

## 7. Offline Storage --- Room (SQLCipher)

-   All tables above are mirrored locally in an encrypted Room database
    (SQLCipher, key derived from Android Keystore, never hardcoded).
-   Local tables: `task_assignments`, `completion_log`, `peer_feedback`,
    `contribution_cache`, `conflict_cache`, `sync_queue`.
-   **Offline-first writes**: task status updates, contribution logs,
    and peer feedback are written to Room immediately and enqueued in
    `sync_queue`; a WorkManager-backed sync worker flushes the queue to
    Sheets via Sheets API v4 `batchUpdate`/`append` when connectivity
    returns, with exponential backoff and conflict resolution
    (last-write-wins per row + a `local_dirty` flag to avoid clobbering
    server-computed columns like `ContributionMetrics`).
-   Conflict-detection cache stores the last-computed risk score locally
    so the dashboard renders instantly on cold start before a background
    refresh.
-   Full DB encryption is mandatory since Room may cache peer-feedback
    text and identifiers subject to FERPA/classroom-privacy
    expectations.

------------------------------------------------------------------------

## 8.1 Implementation Guardrails

-   Do not introduce Jetpack Compose in v1.
-   Use XML layout resources and ViewBinding for UI screens.
-   Use Navigation Component for screen navigation and deep links.
-   Keep UI logic in Activities/Fragments and presentation state in
    ViewModels; business/data logic remains in repositories and use
    cases as defined by the architecture.
-   Prefer reusable XML layouts, styles, dimensions, colors, and
    drawables rather than hardcoded UI values.
-   Cursor must implement the project incrementally from approved
    architecture/issues rather than generating the entire application
    from the PRD in one pass.

## 8. Functional Requirements

### FR1 --- Authentication & RBAC

Google Sign-In for both roles; role-gated navigation graph (Navigation
Component); incremental OAuth for GitHub scope only when needed.

### FR2 --- Project & Team Setup

Teacher creates project → Drive folder + Sheets file generated from
template → teacher forms teams and assigns tasks with weights.

### FR3 --- Bidirectional Sheets Sync

Retrofit-based `SheetsApiService` wrapping Sheets API v4
(`values.append`, `values.batchGet`, `values.batchUpdate`); repository
layer maps Sheet rows ↔ Room entities ↔ UI models. Sync runs on: app
foreground, pull-to-refresh, and a periodic WorkManager job (every
15--30 min while active, hourly otherwise, respecting Sheets API quota).

### FR4 --- Gamification Engine

-   XP awarded for: task completion (weighted), commit activity, peer
    feedback given (not just received, to reward reciprocity), wiki
    edits.
-   Badges: **Collaborator** (peer-feedback quality + frequency),
    **Communicator** (wiki edits + review comments), **TaskMaster**
    (task completion streak/volume), **MVP** (top composite score in
    team for the week).
-   Leaderboard: per-team and per-class, computed from `Achievements` +
    `ContributionMetrics`, rendered with XML Views + ViewBinding and a
    lightweight ranking animation.
-   Badge/XP rules live in a single versioned rules table so thresholds
    can be tuned without an app release.

### FR5 --- Real-Time Contribution Tracker

Aggregates: task completions (`CompletionLog`), commits (`Commits`, via
GitHub webhook → relay → Sheets append), wiki edits (if wiki source
connected, e.g., GitHub wiki or a Sheet-based wiki log), peer review
actions (`PeerFeedback` + PR review events). Displayed per-student in
near real time, refreshed hourly by the GAS aggregation job and
on-demand pull.

### FR6 --- Peer Feedback Module

Students rate teammates on Collaboration, Communication, Reliability
(1--5) plus optional free-text comment. ML Kit Text Classification runs
sentiment analysis on the comment (positive/neutral/negative) --- used
only as a *signal into conflict detection*, never shown verbatim to the
teacher without the student's own attribution intact for accountability
(no anonymous feedback in v1, to reduce weaponization risk --- flagged
as an open design decision, see §14).

### FR7 --- AI Conflict-Detection Algorithm

Runs daily via WorkManager (device-side trigger, computation can be
device-side or delegated to GAS/cloud function for consistency across
all teachers' devices --- recommended: **server/GAS-side** so the risk
score isn't dependent on any one device being online). See §9 for
algorithm detail.

### FR8 --- Automated Evaluation Engine

Weekly (and on-demand pre-deadline) grade computation per formula in
§10, written to `AutomatedGrades`, locked from student edits, visible
read-only for transparency.

### FR9 --- Artefact Storage

Firebase Storage buckets per project/team; uploads (docs, code zips,
decks) tagged with contributor UID and version number; version history
shown as a simple linear list per artefact, not full diffing (out of
scope for v1).

### FR10 --- Team Health Dashboard

MPAndroidChart-based: collaboration heat-map (student × week activity
intensity), activity timeline (stacked bar per contribution type),
conflict-risk score trend line, individual contribution pie chart. All
driven by `ContributionMetrics` + `ConflictFlags`.

### FR11 --- WorkManager Jobs

-   **Sync worker** (periodic, \~15--60 min): Sheets ↔ Room
    reconciliation.
-   **Weekly evaluation worker** (cron-like, e.g., Sunday 23:00 local):
    triggers grade computation + conflict-risk pass + teacher digest.
-   **Digest notification worker**: compiles a per-teacher FCM summary
    with actionable recommendations.

### FR12 --- Notifications & Deep Links

FCM push to teacher on new/escalated conflict flag; notification carries
a deep link (`app://project/{id}/team/{id}/conflict`) that routes
directly to the flagged team's health dashboard via Navigation Component
deep-link handling, bypassing the project list.

------------------------------------------------------------------------

## 9. AI Conflict-Detection Algorithm (v1 Design)

**Inputs (per student, rolling 7-day window):** - `activity_gap_days`
--- days since last logged action (task/commit/wiki/review) -
`peer_feedback_avg` --- mean of collab+comms+reliability scores
received - `sentiment_negative_ratio` --- share of received feedback
comments classified negative by ML Kit - `contribution_share` --- this
student's share of team's total weighted contribution - `expected_share`
--- 1 / team_size (baseline for equal distribution)

**Per-student risk sub-scores (0--100, higher = riskier):**

    inactivity_score   = min(100, activity_gap_days * 14)
    feedback_score     = (5 - peer_feedback_avg) * 20            // scale 1–5 → 0–100
    sentiment_score     = sentiment_negative_ratio * 100
    imbalance_score     = abs(contribution_share - expected_share) / expected_share * 100, capped at 100

**Composite per-student risk:**

    student_risk = 0.35*inactivity_score + 0.25*feedback_score
                 + 0.15*sentiment_score  + 0.25*imbalance_score

**Team-level risk** = weighted combination of max(student_risk) and
team-level variance of `contribution_share` (a team can look "fine on
average" while masking one free-rider or one burnt-out over-contributor
--- both should trip the flag):

    team_risk = 0.6 * max(student_risk_i) + 0.4 * variance_penalty(contribution_share across team)

**Thresholds:** `team_risk ≥ 70` → high (immediate FCM), `40–69` →
medium (included in weekly digest only), `<40` → low (dashboard only, no
push). Thresholds are stored as tunable config, not hardcoded, since a
school will want to calibrate false-positive rate.

**Output:** row appended to `ConflictFlags` with `risk_reasons[]` --- a
human-readable list (e.g., "Priya: 6 days inactive", "Team contribution
variance high --- 2 students carrying 78% of load") so the teacher's
intervention isn't a black-box number.

**Explicitly excluded from v1:** free-text content of commit
messages/wiki edits is *not* semantically analyzed beyond sentiment on
peer-feedback comments --- avoids over-reach into surveillance of
student communication and keeps the ML Kit footprint small and on-device
where possible.

------------------------------------------------------------------------

## 10. Automated Evaluation Engine

    final_grade = 0.40 * task_completion_rate
                + 0.30 * normalized_contribution_volume
                + 0.20 * peer_feedback_average_normalized
                + 0.10 * collaboration_metric_normalized

-   `task_completion_rate` = weighted tasks completed / weighted tasks
    assigned (per student).
-   `normalized_contribution_volume` = student's (commits + wiki edits +
    review actions), min-max normalized within the team to avoid
    penalizing teams whose repo naturally has fewer commits (e.g.,
    design-heavy projects).
-   `peer_feedback_average_normalized` = mean received score, scaled
    0--100; a minimum-N-raters floor (e.g., ≥2 raters) prevents a single
    biased rating from dominating.
-   `collaboration_metric_normalized` = derived from
    `ContributionMetrics` collaboration signals (feedback given, review
    participation) --- deliberately separate from peer_feedback (which
    measures being *rated well*) vs. collaboration (measures *actively
    collaborating*).

All four components and the final number are stored per student in
`AutomatedGrades`, always visible to that student with a breakdown (not
just the final number) --- this is the "eliminates subjective bias"
requirement made auditable rather than asserted. Teacher override is a
separate column (`teacher_override`, nullable) with a mandatory
justification note written to `TeacherNotes`, so overrides are also
auditable, not silent.

------------------------------------------------------------------------

## 11. System Architecture

    ┌─────────────────────────────┐
    │   Android App (XML Views)      │
    │ ViewBinding · Navigation · Flow│
    └───────────┬──────────────────┘
                │
       ┌────────┼─────────────┬───────────────┐
       ▼        ▼             ▼               ▼
     Room(SQLCipher)   Retrofit(Sheets v4)  Firebase(FCM+Storage)  ML Kit (on-device)
       │                     │                     │
       │ sync via            │                     │
       │ WorkManager          ▼                     
       │              Google Sheets (system of record)
       │                     ▲
       │                     │ appends
       │              GAS hourly aggregation
       │                     ▲
       │                     │ webhook relay (public HTTPS endpoint)
       └──────────────── GitHub Webhooks (push / PR / review)

**Note on GitHub webhooks:** GitHub cannot deliver webhooks directly to
an Android device (no stable public endpoint). A minimal serverless
relay (e.g., Cloud Function) is required to receive the webhook, map
GitHub committer email → student, and append to the `Commits` tab /
notify the app via FCM. This is a necessary architectural addition
beyond "pure Android app" and should be scoped explicitly in engineering
estimates.

------------------------------------------------------------------------

## 12. Non-Functional Requirements

  ---------------------------------------------------------------------
  Category                           Requirement
  ---------------------------------- ----------------------------------
  Security                           SQLCipher-encrypted Room DB;
                                     Keystore-backed token storage;
                                     least-privilege OAuth scopes;
                                     incremental auth for GitHub

  Privacy                            Peer-feedback free text not
                                     exposed beyond sentiment label +
                                     attributed author; commit message
                                     content not stored verbatim;
                                     FERPA-aware handling of grade data

  Performance                        Sheets sync batched (no per-row
                                     API call); dashboard cold-start
                                     renders from Room cache \<1s
                                     before background refresh

  Reliability                        Offline-first for all
                                     student-facing writes; sync queue
                                     survives app kill; idempotent
                                     Sheets append (dedupe by `log_id`)

  Quota management                   Sheets API v4 has per-minute
                                     quotas; batch reads/writes, cache
                                     aggressively, backoff on 429

  Accessibility                      XML/View-based UI meets standard
                                     TalkBack, content-description,
                                     focus-order, touch-target, and
                                     contrast guidelines

  Auditability                       Every grade-affecting computation
                                     traceable to source rows;
                                     overrides require justification
  ---------------------------------------------------------------------

------------------------------------------------------------------------

## 13. Tech Stack Mapping

  -----------------------------------------------------------------------
  Layer                   Choice                  Purpose
  ----------------------- ----------------------- -----------------------
  UI                      XML Views +             Screens, navigation,
                          ViewBinding +           and deep links
                          Navigation Component    

  DI                      Hilt                    Testable, scoped
                                                  dependencies

  Async                   Coroutines + Flow       Reactive data from
                                                  Room/Retrofit

  Local DB                Room + SQLCipher        Encrypted offline
                                                  cache/queue

  Networking              Retrofit 2              Sheets API v4, GitHub
                                                  relay, FCM registration

  Background              WorkManager             Sync, weekly
                                                  evaluation, digest jobs

  Backend-of-record       Google Sheets API v4 +  Data store, hourly
                          Apps Script             aggregation, ACL guard

  Auth                    Google Sign-In          Teacher/student
                          (Credential Manager)    identity

  Storage                 Firebase Storage        Artefact uploads +
                                                  version history

  Messaging               Firebase Cloud          Conflict alerts,
                          Messaging               digests, deep links

  ML                      ML Kit Text             Peer-feedback sentiment
                          Classification          signal

  Charts                  MPAndroidChart          Heat-maps, timelines,
                                                  pie charts, trend lines

  VCS integration         GitHub API + Webhooks   Commit-based
                          (via relay)             contribution signal
  -----------------------------------------------------------------------

------------------------------------------------------------------------

## 14. Open Design Decisions / Risks

1.  **Peer feedback anonymity** --- fully anonymous feedback increases
    honesty but enables weaponization ("pile-on"); fully attributed
    feedback is safer but may suppress honest criticism.
    *Recommendation: attributed to teacher, anonymized to peers.*
2.  **GitHub webhook relay** --- requires a small serverless component
    outside the Android app; must be scoped in project timeline, not
    assumed to be "just an API call from the app."
3.  **Sheets API quota at scale** --- a school with many concurrent
    projects may hit per-minute quotas; consider a project-level quota
    budget and staggered sync windows.
4.  **Where conflict-detection runs** --- device-side (WorkManager)
    vs. server-side (GAS/Cloud Function). Recommended: server-side for
    consistency (device may be offline/uninstalled); WorkManager
    on-device is used for the *digest delivery* and *cache refresh*, not
    the source computation.
5.  **Grading fairness across project types** --- commit-count-based
    contribution volume disadvantages non-coding roles (design, docs,
    PM); the normalization approach in §10 partially mitigates this but
    should be piloted before high-stakes grading use.

------------------------------------------------------------------------

## 15. Milestones (Indicative)

  ------------------------------------------------------------------------
  Phase                  Scope                      Duration
  ---------------------- -------------------------- ----------------------
  M1                     Auth, project/team setup,  2--3 wks
                         Sheets schema + template   
                         generation                 

  M2                     Room offline cache,        2--3 wks
                         bidirectional sync, task   
                         tracking                   

  M3                     Gamification               2 wks
                         (XP/badges/leaderboard),   
                         Firebase Storage           

  M4                     GitHub webhook relay,      2--3 wks
                         contribution tracker, GAS  
                         aggregation                

  M5                     Peer feedback + ML Kit     2 wks
                         sentiment, evaluation      
                         engine                     

  M6                     Conflict-detection         2--3 wks
                         algorithm, ConflictFlags,  
                         FCM + deep links           

  M7                     Team health dashboard      2 wks
                         (MPAndroidChart), digest   
                         reports                    

  M8                     Hardening: encryption      2 wks
                         audit, quota handling,     
                         pilot testing              
  ------------------------------------------------------------------------

------------------------------------------------------------------------

## 16. Success Metrics (Post-Launch)

-   \% of projects with at least one conflict flag resolved before final
    deadline
-   Reduction in grade disputes vs. prior manual-grading baseline
-   Weekly active rate on gamification screens (leaderboard/badges)
-   Sync failure rate (\<1% of queued writes failing after retries)
-   Teacher-reported trust in automated grade (survey, target ≥4/5)

------------------------------------------------------------------------

## 17. Appendix --- Full Sheets Tab Reference

`Projects · Teams · Students · TaskAssignments · CompletionLog · Commits · PeerFeedback · ContributionMetrics · ConflictFlags · AutomatedGrades · Achievements · TeacherNotes`

All 12 tabs generated automatically from an internal template when a
teacher creates a project, ensuring schema consistency across every
project spreadsheet in the system.
