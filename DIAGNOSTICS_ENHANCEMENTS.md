# Table Diagnostics Enhancements

## Overview
This document describes the comprehensive diagnostic enhancements made to the DB Doctor table details page, transforming it from a schema inspection tool into a full diagnostic and debugging platform.

## What Was Added

### 1. ✅ Diagnostics Summary Strip
**Location:** Immediately below the table header

**Features:**
- Color-coded clickable pills showing diagnostic status at a glance
- Pills include:
  - 🟢 Ownership OK / 🔴 Ownership Mismatch
  - 🟢 SELECT Access / 🔴 SELECT Missing
  - 🟡 Write Access Limited
  - 🟢 FK Integrity OK
  - 🟡 Cascade FK warnings
- Each pill is clickable and scrolls to the relevant section
- Visual hierarchy mirrors the successful Flyway Health panel

**Why it matters:** Engineers can answer "Can I read/write this table?" in 5 seconds without scrolling.

---

### 2. ✅ Ownership & Grants: Persistent Diagnostic Card
**Location:** Dedicated section with ref for scroll-to

**Features:**
- **Pre-check state:** Shows "Check Ownership & Grants" button
- **Post-check state:** Persistent diagnostic table showing:
  - Table Owner with match/mismatch indicator
  - SELECT, INSERT, UPDATE, DELETE permissions with ✅/❌ status
  - Details column explaining each result
- **Interpretation section:** Plain English explanation of what the permissions mean
  - "This table is owned by X"
  - "The connected user Y can read data but cannot modify it"
  - "This is acceptable for read-only services but will fail for write paths"

**Why it matters:** Transforms a tool into a diagnosis. Shows impact, not just facts.

---

### 3. ✅ "Why This Matters" Collapsible Explanations
**Location:** Below each major section (Indexes, Foreign Keys, Ownership)

**Features:**
- Small collapsible button with info icon
- Expands to show context-specific guidance
- Examples:
  - **Indexes:** "Missing or misaligned indexes can cause severe performance degradation under load, especially for cart and LOS queries."
  - **Foreign Keys:** "Foreign key cascades can amplify delete operations and cause unexpected data loss or latency spikes."
  - **Ownership:** "Ownership and access control determine what operations your application can perform. Mismatched ownership is a common cause of production failures."

**Why it matters:** Educates junior engineers and PMs without cluttering the default view.

---

### 4. ✅ Cross-Check Signals (Flyway ↔ Ownership Drift)
**Location:** Alert banner below diagnostics summary (when detected)

**Features:**
- Automatic detection when:
  - Flyway `installed_by` ≠ current user
  - Table `owner` ≠ current user
  - These values differ from each other
- Shows warning banner:
  - "⚠️ Potential Credential Drift"
  - "This table was likely created by a different role than the one currently connected"
  - Shows all three values: Flyway installer, table owner, connected user
  - Explains why this matters: "This connects Flyway diagnostics with ownership issues and may explain permission problems"

**Why it matters:** Correlates multiple diagnostic signals to identify root cause of common production failures.

---

### 5. ✅ Diagnostic Timeline
**Location:** Dedicated section showing chronological story

**Features:**
- Visual timeline with left border indicator
- Shows sequence of events:
  - ✔️ Flyway migration applied (X days ago by Y)
  - ✔️ Table created (Owner: Z)
  - ⚠️ Ownership differs from current user (if applicable)
  - ❌ Write privileges missing (if applicable)
- Color-coded status indicators (green/yellow/red)

**Why it matters:** Gives a story, not just a snapshot. Shows how the current state came to be.

---

### 6. ✅ Enhanced Constraints Section with Icons and Risk Indicators
**Location:** Constraints collapsible section

**Features:**
- **Icons for each constraint type:**
  - 🔑 Primary Keys
  - 🔗 Foreign Keys
  - Unique Constraints
  - 🧪 Check Constraints
- **Risk indicators for Foreign Keys:**
  - Detects `ON DELETE CASCADE` and `ON UPDATE CASCADE`
  - Shows 🟡 "High impact delete" chip
  - Highlights cascading FKs with warning background color
  - Tooltip: "Deletes/updates will cascade to related records"
- **Collapsible subsections** for each constraint type
- Summary chip showing count of cascading FKs

**Why it matters:** Makes dangerous configurations immediately visible. Prevents accidental data loss.

---

### 7. ✅ Copy Diagnostics Button
**Location:** Top of page, next to table header

**Features:**
- Generates comprehensive text report including:
  - Connection ID
  - Schema.table
  - Table owner
  - Connected user
  - Granted/missing privileges
  - Flyway migration info
  - Index summary
  - Constraint summary with cascade warnings
  - Timestamp
- Copies to clipboard with success feedback
- Formatted for easy pasting into Slack/tickets

**Why it matters:** Turns DB Doctor into a debugging collaboration tool. Enables async troubleshooting.

---

### 8. ✅ Backend Enhancements
**Files Modified:**
- `DbTableIntrospectResponse.java` - Added owner, currentUser, and FlywayMigrationInfo
- `DbIntrospectService.java` - Added queries for owner, current user, and Flyway info

**New Data Returned:**
```java
{
  "schema": "public",
  "table": "cart_item",
  "owner": "cart_admin",
  "currentUser": "cart_user",
  "indexes": [...],
  "constraints": [...],
  "flywayInfo": {
    "version": "1.0",
    "description": "Create cart schema",
    "installedBy": "flyway",
    "installedOn": "2025-12-22T10:30:00"
  }
}
```

**Why it matters:** Provides the data needed for all diagnostic features without additional API calls.

---

## Visual Hierarchy Improvements

### Before
- Flat list of indexes and constraints
- No context or interpretation
- No connection between different data points

### After
- **Top:** Diagnostics Summary (fast answer)
- **Alerts:** Drift warnings (if detected)
- **Timeline:** Story of how we got here
- **Sections:** Collapsible deep-dive areas
- **Explanations:** Context for each section

---

## What Was Intentionally NOT Added (Yet)

Based on your guidance, these were avoided:
- ❌ Row counts (dangerous on large tables)
- ❌ EXPLAIN ANALYZE (too heavy)
- ❌ Write actions (breaks trust in read-only tool)

---

## How to Use

### For Quick Diagnosis (30 seconds)
1. Look at Diagnostics Summary pills
2. Check for drift warnings
3. Click "Check Ownership & Grants" if needed
4. Read interpretation

### For Deep Investigation
1. Expand Indexes section - check for missing indexes
2. Expand Constraints section - review FK cascades
3. Check Timeline for historical context
4. Copy Diagnostics for sharing

### For Collaboration
1. Click "Copy Diagnostics"
2. Paste into Slack/ticket
3. Share context with team

---

## Testing Checklist

- [ ] Connect to database
- [ ] Run "Show Table Details" on a table
- [ ] Verify Diagnostics Summary shows correct status
- [ ] Click "Check Ownership & Grants"
- [ ] Verify ownership diagnostic card appears
- [ ] Check if drift warning appears (if applicable)
- [ ] Verify Timeline shows Flyway info (if available)
- [ ] Expand Indexes section
- [ ] Expand Constraints section
- [ ] Verify FK cascade warnings appear (if applicable)
- [ ] Click "Copy Diagnostics" and verify clipboard content
- [ ] Click diagnostic pills and verify scroll behavior

---

## Future Enhancements (Not Implemented)

Potential additions based on user feedback:
- Performance risk indicators (missing indexes on FK columns)
- Column-level diagnostics
- Historical ownership changes
- Privilege recommendations
- Integration with monitoring/alerting

---

## Files Changed

### Backend
- `apps/dbtriage/src/main/java/com/example/Triage/model/response/DbTableIntrospectResponse.java`
- `apps/dbtriage/src/main/java/com/example/Triage/service/db/DbIntrospectService.java`

### Frontend
- `frontend/src/components/TableDiagnosticsPanel.jsx` (NEW)
- `frontend/src/components/ResultsPanel.jsx` (modified to use new component)

---

## Impact

This transforms DB Doctor from:
- **Schema inspector** → **Diagnostic platform**
- **Shows facts** → **Explains impact**
- **Single-mode tool** → **Guided debugging experience**

The page now answers the critical questions:
1. ✅ Can this service read this table?
2. ✅ Can it write?
3. ✅ Is ownership correct?
4. ✅ Is schema structure sane?
5. ✅ Is there a known Flyway/credential drift risk?

In 30 seconds or less.

