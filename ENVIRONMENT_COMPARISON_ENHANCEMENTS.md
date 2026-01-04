# Environment Comparison Enhancements

## Overview
Comprehensive enhancements to the Environment Comparison feature to provide evidence-based diagnostics, actionable insights, and better handling of limited access scenarios.

## What's New

### 1. Evidence-Based Diagnostic Conclusions ✅

**Problem Solved:** Conclusions were too generic and not defendable.

**Solution:** Each conclusion now follows a structured format:
- **Finding** (one sentence, specific)
- **Evidence** (bullet list of concrete facts)
- **Impact** (short description of consequences)
- **Recommended next actions** (1-3 actionable buttons/links)

**Example:**
```
Finding: Flyway mismatch detected - target missing migrations

Evidence:
• Source latest version: 3
• Target latest version: 2
• Target failed migrations: 0
• Missing migrations: 1

Impact: Target likely missing migration(s) that introduced schema objects used by the app

Next Actions:
[Show Missing Migrations] [Open Flyway Health (Target)] [Copy Diagnostics]
```

### 2. Missing Migrations View ✅

**Problem Solved:** Users couldn't see what specific migrations were missing.

**Solution:** 
- New `FlywayMigrationGap` analysis compares Flyway history between environments
- If source has higher version than target, lists all missing migrations
- Shows: rank, version, description, script name, installed_by, installed_on
- Graceful degradation if Flyway history not accessible

**When It Shows:**
- Only when Flyway is available in both environments
- Only when source version > target version
- Shows clear explanation if detection fails

### 3. Blast Radius Analysis ✅

**Problem Solved:** Users couldn't understand the runtime impact of schema drift.

**Solution:** 
- New `BlastRadiusItem` translates DB drift → runtime symptoms
- Automatically generated from drift analysis

**Examples:**

**Missing Column:**
- Likely symptoms:
  - INSERT/UPDATE fails: column "promo_code" does not exist
  - SELECT fails if application queries this column
  - Application errors: NullPointerException or field mapping failures

**Missing Index:**
- Likely symptoms:
  - Slow queries / table scans on cart_items
  - Query timeouts under load
  - CPU spikes during peak usage
  - CRITICAL: This may be a unique or primary key index - data integrity at risk

### 4. Improved Severity Rules & Risk Categorization ✅

**Enhanced DriftItem:**
- Added `riskLevel` field: "High", "Medium", "Low"
- Changed `category` from technical (TABLE, COLUMN) to functional (Compatibility, Performance)

**Risk Assignment:**

**Compatibility Issues (High):**
- Missing tables
- Missing columns
- Column type mismatches
- NOT NULL constraint mismatches
- Primary key / unique constraint drift

**Compatibility Issues (Medium):**
- Column default mismatches
- Foreign key constraint drift

**Performance Issues (High):**
- Missing unique/primary key indexes

**Performance Issues (Medium):**
- Missing composite indexes
- Missing standard b-tree indexes
- Index definition differences

**Performance Issues (Low):**
- Extra indexes in target
- Extra tables in target

### 5. Comparison Scope Filtering ✅

**Problem Solved:** As drift grows, results become noisy.

**Solution:** Three-level filtering system:

**"Only show differences" toggle:**
- Default: ON (only show DIFFER items)
- OFF: show all including MATCH items

**"Show by severity" filter:**
- All Severities
- Errors Only
- Warnings Only

**"Search objects" input:**
- Real-time filter by object name or message
- Example: type "cart_item" to see only cart_item drift

### 6. Handle Limited PROD Access Gracefully ✅

**Problem Solved:** Limited PROD access was hard to diagnose and fix.

**Solution:**

**A) Enhanced Capability Matrix:**
- Tooltips show **why** capability is unavailable
- Shows specific missing privilege
- Example: "Missing privilege: SELECT on pg_catalog.pg_indexes"

**B) Partial Results Banner:**
- Clear banner: "⚠️ Partial Comparison"
- Lists what was compared vs. what couldn't be
- Shows reason for each limitation

**C) Privilege Request Snippet:**
- Ready-to-send SQL grants for DBA
- Minimal required grants documented
- Optional Flyway table grant
- "Copy Privilege Request" button
- Full SQL with explanations:
  ```sql
  -- Grant read access to pg_catalog (PostgreSQL system catalog)
  GRANT SELECT ON pg_catalog.pg_indexes TO <your_user>;
  GRANT SELECT ON pg_catalog.pg_constraint TO <your_user>;
  -- etc.
  ```

**D) Missing Privileges Section:**
- Lists each missing capability
- Shows reason and required grant
- Appears in Access Requirements panel

### 7. Source/Target Identity Chips ✅

**Problem Solved:** Screenshots get shared without context about which environments were compared.

**Solution:**
- Identity chips at top of results:
  - `Source: DEV localhost:5433/cartdb (cart_user)`
  - `Target: PROD localhost:5434/cartdb (cart_user)`
- Reduces misreads in screenshots/reports

### 8. Diagnostic Export: One-Click "Copy All Diagnostics" ✅

**Problem Solved:** Users needed to share full comparison results.

**Solution:**
- "Copy All Diagnostics" button at top of results
- Exports complete JSON bundle:
  - Source/target identity
  - Capability matrices
  - Flyway comparison
  - Missing migrations
  - All drift sections
  - Blast radius analysis
  - Conclusions
  - Missing privileges
- Snackbar confirmation: "Diagnostics copied to clipboard"

## Technical Implementation

### Backend Changes

**New DTOs:**
1. `DiagnosticConclusion` - Enhanced with evidence, category, nextActions
2. `FlywayMigrationGap` - Missing migration analysis
3. `BlastRadiusItem` - Runtime symptom predictions
4. `PrivilegeRequirement` - Detailed privilege needs
5. Enhanced `DriftItem` - Added riskLevel field
6. Enhanced `FlywayComparisonDto` - Added rank fields
7. Enhanced `CapabilityStatus` - Added missingPrivilege, permissionDenied fields

**New Services:**
1. `BlastRadiusService` - Generates symptoms from drift
2. `PrivilegeAnalysisService` - Analyzes privileges and generates snippets

**Updated Services:**
1. `SchemaDriftService` - Risk levels for all drift items
2. `EnvironmentComparisonHandler` - Orchestrates new features

**Updated Response:**
- `EnvironmentComparisonResponse` - Added all new fields

### Frontend Changes

**Enhanced EnvironmentComparisonPanel:**
1. Identity chips display
2. Comparison scope filtering (toggle, severity, search)
3. Missing migrations table
4. Blast radius cards with symptoms
5. Evidence-based conclusion cards with next action buttons
6. Enhanced capability matrix with tooltips
7. Privilege request snippet with copy button
8. Export diagnostics button
9. Missing privileges alert
10. Risk level chips in drift tables
11. Category chips (Compatibility/Performance)
12. Smooth scroll to sections via next actions

**New UI Elements:**
- ToggleButtonGroup for show differences
- Search input with icon
- Severity filter dropdown
- Blast radius cards with risk indicators
- Evidence bullet lists in conclusions
- Action buttons in conclusions
- Snackbar for copy confirmations
- Monospace code block for SQL snippets

## User Benefits

### For Developers
- **Immediate Impact Understanding:** Blast radius shows what will break
- **Evidence-Based Decisions:** Conclusions cite specific findings
- **Quick Navigation:** Next action buttons jump to relevant sections
- **Export for Tickets:** Copy diagnostics to paste in JIRA/GitHub

### For DBAs
- **Copy-Paste Grants:** Privilege snippet is ready to run
- **Risk Prioritization:** High/Medium/Low risk levels guide fixes
- **Missing Migrations:** Exact list of what to apply

### For DevOps/SRE
- **Performance Impact:** Index drift shows query risk
- **Limited Access Handling:** Works with read-only PROD access
- **Screenshot-Ready:** Identity chips prevent confusion

### For Stakeholders
- **Clear Conclusions:** Evidence-based findings are defendable
- **Risk Categories:** Compatibility vs. Performance helps prioritize
- **Impact Statements:** Business impact is explicit

## Example Scenarios

### Scenario 1: Missing Column in PROD
**Before:**
- "Column drift detected"

**After:**
- **Finding:** Critical schema drift detected - application failures likely
- **Evidence:**
  - Total critical differences: 1
  - cart_items.promo_code: Column exists in source but missing in target
- **Impact:** INSERT/UPDATE/SELECT operations will fail
- **Blast Radius:**
  - INSERT/UPDATE fails: column "promo_code" does not exist
  - SELECT fails if app queries it
  - Application errors: NullPointerException
- **Next Actions:** [Show Drift Details] [Show Blast Radius]

### Scenario 2: Flyway Mismatch
**Before:**
- "Flyway version mismatch: 3 vs 2"

**After:**
- **Finding:** Flyway mismatch detected - target missing migrations
- **Evidence:**
  - Source latest version: 3
  - Target latest version: 2
  - Missing migrations: 1
- **Impact:** Target likely missing migration(s) that introduced schema objects
- **Missing Migrations Table:** Shows V3__add_promo_code.sql
- **Next Actions:** [Show Missing Migrations] [Copy Diagnostics]

### Scenario 3: Limited PROD Access
**Before:**
- "Indexes unavailable"

**After:**
- **Capability Matrix:** 
  - Indexes: 🔒 (tooltip: "Missing privilege: SELECT on pg_catalog.pg_indexes")
- **Partial Comparison Banner:** 
  - "Compared: tables + columns"
  - "Could not compare: indexes + constraints"
- **Privilege Request Snippet:**
  - Copy-paste ready SQL
  - GRANT SELECT ON pg_catalog.pg_indexes TO prod_readonly;
- **Next Actions:** [Copy Privilege Request]

## Migration Notes

### Backward Compatibility
- ⚠️ **BREAKING:** `DriftItem` now requires `riskLevel` parameter
- ⚠️ **BREAKING:** `DiagnosticConclusion` structure changed completely
- ⚠️ **BREAKING:** `FlywayComparisonDto` added rank fields
- ⚠️ **BREAKING:** `EnvironmentComparisonResponse` added many new fields

### Compilation
All existing code that creates `DriftItem` must be updated to include `riskLevel`.

### Testing
Test these scenarios:
1. Full access comparison (all green)
2. Partial access comparison (some locked)
3. Flyway version mismatch with accessible history
4. Flyway version mismatch with inaccessible target
5. Missing columns/indexes/constraints
6. Filter by severity/search
7. Copy diagnostics
8. Copy privilege snippet
9. Next action button navigation

## Future Enhancements

### Could Add:
1. **Drift History:** Track comparisons over time
2. **Auto-Fix Scripts:** Generate DDL to align schemas
3. **Scheduled Comparisons:** Nightly drift detection
4. **Slack/Email Alerts:** Notify on new drift
5. **Row Count Stats:** Enhance risk levels with table sizes
6. **Execution Plan Analysis:** Show actual query cost differences

## Files Changed

### Backend
- ✅ `DiagnosticConclusion.java` - Enhanced with evidence
- ✅ `FlywayMigrationGap.java` - NEW
- ✅ `BlastRadiusItem.java` - NEW
- ✅ `PrivilegeRequirement.java` - NEW
- ✅ `CapabilityStatus.java` - Enhanced
- ✅ `DriftItem.java` - Added riskLevel
- ✅ `FlywayComparisonDto.java` - Added ranks
- ✅ `EnvironmentComparisonResponse.java` - Added new fields
- ✅ `SchemaDriftService.java` - Risk levels
- ✅ `BlastRadiusService.java` - NEW
- ✅ `PrivilegeAnalysisService.java` - NEW
- ✅ `EnvironmentComparisonHandler.java` - Orchestration

### Frontend
- ✅ `EnvironmentComparisonPanel.jsx` - Comprehensive rewrite

## Summary

This enhancement transforms Environment Comparison from a **basic schema diff** to a **comprehensive DB Doctor diagnostic tool** with:

✅ Evidence-based, defendable conclusions
✅ Runtime impact predictions (blast radius)
✅ Missing migrations detection
✅ Risk-based categorization
✅ Filtering for large schemas
✅ Graceful limited-access handling
✅ Copy-paste privilege requests
✅ One-click diagnostic export

**Result:** Users can confidently diagnose production issues, defend decisions to stakeholders, and get unblocked even with read-only PROD access.

