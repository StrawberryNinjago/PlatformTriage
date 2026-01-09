# Primary Failure Contract Fixes

## Summary
Fixed three critical contract issues in the Platform Triage backend to prevent confusing UX and ensure proper failure semantics.

---

## ✅ Fix 1: Primary Failure Semantics (CRITICAL)

### Problem
- `primaryFailure` was being set for ANY highest-priority finding, regardless of severity
- Warnings could accidentally become "primary failures"
- UI would show scary red failure boxes even when everything is working fine
- No differentiation between FAIL and UNKNOWN states

### Solution
**Contract Rule (enforced in backend):**
```java
primaryFailure is set ONLY when:
  - overall == FAIL
  - OR overall == UNKNOWN

Otherwise:
  - primaryFailure = null
```

**Implementation:**
- Updated `selectPrimaryFailure(findings, overall)` to accept `overall` parameter
- Added contract enforcement check at the top of the method
- Returns `null` when `overall == WARN` or `overall == PASS`
- Warnings can NEVER populate `primaryFailure`

**Impact:**
- ✅ Prevents WARN findings from triggering red failure UI
- ✅ Clear differentiation: FAIL = broken, UNKNOWN = can't assess, WARN = advisory
- ✅ UI can safely use `primaryFailure` to show critical failure cards

---

## ✅ Fix 2: Top Warning Handling (Documentation)

### Problem
- No explicit "top warning" field
- UI had to infer from findings list ordering
- Coupling between UI and backend logic

### Solution (MVP-safe, no new field)
**UI Contract (documented in DeploymentSummaryResponse):**
```
When overall == WARN:
  - UI derives "Top Warning" as first Severity.WARN finding in findings list
  - Backend ensures findings list has consistent ordering (highest priority first)
```

**Implementation:**
- Added comprehensive contract documentation to `DeploymentSummaryResponse.java`
- Clarified that `primaryFailure` is null when `overall == WARN`
- Documented UI responsibility for deriving top warning

**Future Enhancement (if needed):**
- Can add explicit `topWarning` field if this pattern becomes shared across pages
- For MVP, current approach is sufficient

---

## ✅ Fix 3: UNKNOWN Short-Circuit Logic (CRITICAL)

### Problem
- `NO_MATCHING_OBJECTS` check happened AFTER all expensive detection rules
- Could result in weird states like:
  - PASS with no objects
  - WARN with no objects
  - Empty UIs with no explanation
- Wasted Kubernetes API calls when nothing to assess

### Solution
**Short-circuit rule (enforced at top of getSummary):**
```java
If no pods AND no deployments:
  - overall = UNKNOWN
  - primaryFailure = NO_MATCHING_OBJECTS finding
  - return immediately (skip all other detection)
```

**Implementation:**
- Moved `NO_MATCHING_OBJECTS` check to top of `getSummary()` method (line 79-108)
- Immediately return DeploymentSummaryResponse with:
  - `overall = UNKNOWN`
  - `primaryFailure` set to NO_MATCHING_OBJECTS finding
  - Empty objects lists (no pods, deployments, events, etc.)
  - Zero breakdown counts
- Removed duplicate check from bottom of method

**Impact:**
- ✅ Prevents PASS/WARN with nothing to assess
- ✅ Saves expensive Kubernetes API calls (events, services, endpoints)
- ✅ Clear UX: "Can't assess because nothing found"
- ✅ Fast-fail for common case (wrong selector/namespace)

---

## 🐛 Bonus Fix: WARN Severity Handling

### Problem Found During Implementation
- `computeOverall()` only checked for legacy `Severity.MED` to return `WARN`
- New `Severity.WARN` findings (POD_RESTARTS_DETECTED, etc.) would result in `overall == PASS`
- UI contract expects `overall == WARN` when WARN findings exist

### Solution
Updated `computeOverall()` to check for BOTH:
- `Severity.WARN` (new codes)
- `Severity.MED` (legacy codes)

**Impact:**
- ✅ Risk signals properly trigger `overall == WARN`
- ✅ UI can show "top warning" when advisory findings exist
- ✅ Backward compatible with legacy MED severity

---

## Files Modified

1. **DeploymentDoctorService.java**
   - Line 79-108: Added UNKNOWN short-circuit logic
   - Line 1070-1103: Updated `selectPrimaryFailure()` with contract enforcement
   - Line 1105-1131: Updated `computeOverall()` to check for WARN severity

2. **DeploymentSummaryResponse.java**
   - Added comprehensive contract documentation
   - Clarified `primaryFailure` semantics
   - Documented UI contract for top warning derivation

---

## Testing Checklist

### Test Case 1: FAIL with primaryFailure
```
Given: Pods in CrashLoopBackOff
Expected:
  - overall = FAIL
  - primaryFailure = CRASH_LOOP finding
  - findings includes CRASH_LOOP finding
```

### Test Case 2: WARN without primaryFailure
```
Given: Pods running with 3 restarts
Expected:
  - overall = WARN
  - primaryFailure = null  ⭐ KEY FIX
  - findings includes POD_RESTARTS_DETECTED
  - UI derives top warning from first WARN finding
```

### Test Case 3: UNKNOWN with primaryFailure
```
Given: Wrong selector (no pods, no deployments)
Expected:
  - overall = UNKNOWN
  - primaryFailure = NO_MATCHING_OBJECTS finding
  - findings = [NO_MATCHING_OBJECTS]
  - objects all empty
  - Fast return (no event/service API calls)
```

### Test Case 4: PASS without primaryFailure
```
Given: All pods running and ready, no issues
Expected:
  - overall = PASS
  - primaryFailure = null
  - findings = [] or only INFO findings
```

---

## Migration Guide for UI

### Before
```typescript
// ❌ WRONG: primaryFailure could be a warning
if (response.primaryFailure) {
  showRedFailureCard(response.primaryFailure);
}
```

### After
```typescript
// ✅ CORRECT: primaryFailure is only FAIL or UNKNOWN
if (response.primaryFailure) {
  // Safe to show red critical failure card
  showRedFailureCard(response.primaryFailure);
}

// Handle warnings separately
if (response.health.overall === 'WARN' && !response.primaryFailure) {
  const topWarning = response.findings.find(f => f.severity === 'WARN');
  if (topWarning) {
    showYellowWarningCard(topWarning);
  }
}

// Handle UNKNOWN
if (response.health.overall === 'UNKNOWN') {
  // primaryFailure will be NO_MATCHING_OBJECTS
  showUnknownStateCard(response.primaryFailure);
}
```

---

## Verification

✅ Compiled successfully: `mvn clean compile -pl apps/platformtriage -am`

All three fixes are now enforced at the backend contract level, preventing UI confusion and ensuring consistent behavior.
