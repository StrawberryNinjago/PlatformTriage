# Environment Comparison: Concise UX Changes

## Summary
Transformed overwhelming "everything expanded" view into clean 3-level disclosure pattern.

## Backend Changes

### New DTOs
1. **ComparisonKPIs** - Key metrics for executive summary
   - `compatibilityErrors` (critical issues)
   - `performanceWarnings` (perf issues)  
   - `missingMigrations` (flyway gaps)
   - `hasCriticalIssues` (alert flag)

2. **BlastRadiusItem** - Enhanced with grouping
   - Added `driftSubtype` field (explains what changed)
   - Added `isGroupRepresentative` flag
   - Added `groupCount` for grouped items

### BlastRadiusService Enhancements
- **Intelligent grouping**: Generic "Different query execution plans" items are grouped
- **Subtype analysis**: Index mismatches now show what changed:
  - "Method differs: btree → gin"
  - "Uniqueness removed"  
  - "Partial predicate differs"
- **Risk-based sorting**: High → Medium → Low

### Handler Changes
- Calculate KPIs for dashboard
- Pass KPIs in response

## Frontend Changes (3-Level Disclosure)

### Level 1: Executive Summary (Default View)
User sees within 10 seconds without scrolling:

1. **Top Banner**: Comparison mode (Full/Partial)
2. **3 KPI Cards**: 
   - Compatibility (Critical): X errors
   - Missing Migrations: X  
   - Performance: X warnings
3. **Flyway Comparison + Missing Migrations**: Expanded (most actionable)
4. **Diagnostic Conclusions**: Collapsed list (header + badge only)
5. **Primary CTAs**: Show Details | Copy Diagnostics

Everything else collapsed.

### Level 2: Evidence (Click to Expand)
Click a conclusion → expands to show:
- Evidence bullets
- Impact statement
- Recommendation
- Action buttons (Show Details | View Blast Radius)

### Level 3: Deep Dive (Intentional Navigation)
- **Drift Analysis**: Auto-expand logic:
  - Columns: if ERROR exists
  - Indexes: if any missing (not just mismatch)
  - Constraints: if differences exist
  - Otherwise: collapsed
  
- **Blast Radius**: Top 5 highest-risk only
  - "Show all (N)" button if more exist
  - Grouped items show count

## Key UX Improvements

### 1. Identity Chips - Fixed Duplication
**Before**: `Source: localhost:5433/cartdb localhost:5433/cartdb (cart_user)`
**After**: `Source: localhost:5433/cartdb (cart_user)`

### 2. Blast Radius - Concise
- Show top 5 by default
- Group similar generic items
- Add meaningful subtypes

**Before**: 9 cards saying "Different query execution plans..."
**After**: 
```
Index definition mismatches (9 indexes)
└─ Inconsistent performance between environments
   [Show all 9 indexes]
```

### 3. Conclusions - Collapsed
**Before**: All evidence expanded
**After**: Header + severity badge, click to expand

### 4. Buttons - Simplified
**Before**: 4-5 buttons per conclusion
**After**: 
- Primary: "Show Details"
- Secondary: Overflow menu with "More actions"

### 5. Default Viewport
Load shows:
- Flyway (expanded)
- KPIs (visible)
- Conclusions (collapsed)
- Drift tables (collapsed, auto-expand on errors)
- Blast radius (top 5)

## Migration Notes

### Breaking Changes
- `BlastRadiusItem` has new fields (`driftSubtype`, `isGroupRepresentative`, `groupCount`)
- `EnvironmentComparisonResponse` has new `kpis` field
- Frontend expects new response structure

### Backward Compatibility
- Old responses will fail gracefully (KPIs default to zeros)
- Frontend handles missing fields

## Testing Checklist

✅ KPI cards display correct counts
✅ Conclusions collapsed by default
✅ Click conclusion → expands evidence
✅ Flyway section expanded by default
✅ Drift sections auto-expand on errors
✅ Blast radius shows top 5 only
✅ Grouped items show count
✅ Identity chips no duplication
✅ Toggle still works (show differences ON by default)
✅ All actions still accessible

## User Experience Goals

1. **10-second comprehension**: Critical issues visible immediately
2. **Progressive disclosure**: Details on demand
3. **No scrolling required**: For initial assessment
4. **Grouped repetition**: No symptom fatigue
5. **Actionable subtypes**: Know what changed and why it matters

## Results

**Before**: User overwhelmed by 500+ rows of MATCH items + repeated symptoms
**After**: User sees 3 KPI cards, 3 collapsed conclusions, 1 expanded Flyway section

---

**Status**: Ready for implementation

