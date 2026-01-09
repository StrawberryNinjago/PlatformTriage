# Backend Deterministic Status Implementation

## Summary

The backend now produces **deterministic, unambiguous "overall + primary + topWarning" semantics**, ensuring the UI never has to guess or re-implement business logic.

## Changes Implemented

### 1. ✅ UNKNOWN Status for NO_MATCHING_OBJECTS

**Status:** Already implemented correctly

- `OverallStatus.UNKNOWN` enum exists
- When `no pods AND no deployments`, the service short-circuits and returns:
  - `overall = UNKNOWN`
  - `primaryFailure = NO_MATCHING_OBJECTS finding`
  - Clear error message and next steps

**Code location:** `DeploymentDoctorService.java` lines 81-108

### 2. ✅ Added `topWarning` to Response Contract

**Status:** Newly implemented

**Changes:**
- Added `Finding topWarning` field to `DeploymentSummaryResponse`
- Added `selectTopWarning()` method to service
- `topWarning` is computed as highest priority finding with `severity == WARN` or `MED`

**Contract rules:**
- `topWarning` is set when there are WARN-severity findings
- `topWarning` can be present even when `overall == PASS` (warnings without errors)
- `topWarning` is independent of `primaryFailure`
- Returns `null` when no warnings exist

**Code locations:**
- `DeploymentSummaryResponse.java` - added field
- `DeploymentDoctorService.java` - added `selectTopWarning()` method

### 3. ✅ Priority Model Already Exists

**Status:** Already implemented correctly

**Priority order** (in `FailureCode.getPriority()`):
```
ERROR severity (failures that block operation):
  1. EXTERNAL_SECRET_RESOLUTION_FAILED
  2. BAD_CONFIG
  3. IMAGE_PULL_FAILED
  4. INSUFFICIENT_RESOURCES
  5. RBAC_DENIED
  6. CRASH_LOOP
  7. READINESS_CHECK_FAILED
  8. SERVICE_SELECTOR_MISMATCH
  9. NO_READY_PODS
 10. ROLLOUT_STUCK

WARN severity (risk signals):
 50. POD_RESTARTS_DETECTED
 51. POD_SANDBOX_RECYCLE

Special case:
 99. NO_MATCHING_OBJECTS (only used when overall=UNKNOWN)
```

### 4. ✅ "PASS with Warnings" via OverallStatus.WARN

**Status:** Already implemented correctly

**Implementation:** Option A (cleaner approach)
- Backend returns `overall = WARN`
- Frontend can render as "PASS (with warnings)" if desired
- No need for extra `hasWarnings` boolean

### 5. ✅ Deterministic Overall Computation

**Status:** Already implemented correctly

**Logic in `computeOverall()`:**
```java
if (NO_MATCHING_OBJECTS finding exists) {
    return UNKNOWN;
}
else if (any finding.severity == ERROR or HIGH) {
    return FAIL;
}
else if (any finding.severity == WARN or MED) {
    return WARN;
}
else {
    return PASS;
}
```

**Code location:** `DeploymentDoctorService.java` lines 1106-1133

### 6. ✅ Enhanced Primary Failure Selection

**Status:** Improved for clarity

**Changes:**
- Updated `selectPrimaryFailure()` to explicitly filter for ERROR/HIGH severity
- Added comprehensive documentation
- Ensures WARN findings never become primaryFailure

**Contract rules:**
- `primaryFailure` is set ONLY when `overall == FAIL` or `overall == UNKNOWN`
- `primaryFailure` = highest priority ERROR-severity finding
- Returns `null` when `overall == WARN` or `overall == PASS`

**Code location:** `DeploymentDoctorService.java` - `selectPrimaryFailure()` method

## Final API Contract

### Response Structure

```json
{
  "timestamp": "2026-01-08T20:53:58Z",
  "target": {
    "namespace": "cart",
    "selector": "app=cart-app",
    "release": "cart-v1"
  },
  "health": {
    "overall": "PASS | WARN | FAIL | UNKNOWN",
    "deploymentsReady": "3/3",
    "breakdown": { ... }
  },
  "findings": [ ... ],
  "primaryFailure": { ... } | null,
  "topWarning": { ... } | null,
  "objects": { ... }
}
```

### Contract Guarantees

| overall  | primaryFailure | topWarning | Meaning |
|----------|----------------|------------|---------|
| UNKNOWN  | Set (NO_MATCHING_OBJECTS) | null | No objects found, cannot assess |
| FAIL     | Set (highest priority ERROR) | Set if warnings exist | Critical failure blocking operation |
| WARN     | null | Set (highest priority WARN) | No errors, but warnings present |
| PASS     | null | null | All checks passed, healthy |

### Special Case: PASS with Warnings

If there are WARN-severity findings but no ERROR-severity findings:
- `overall = WARN` (not PASS)
- `primaryFailure = null`
- `topWarning = highest priority WARN finding`

Frontend can render this as "PASS (with warnings)" or "Healthy with advisories" as desired.

## Benefits

✅ **UI is fully decoupled from detection logic**
- No need to implement priority sorting in UI
- No need to filter findings by severity in UI
- No need to guess which finding to show

✅ **Consistent behavior across all pages**
- Same logic for Database Triage, Platform Triage, etc.
- No special-case handling needed

✅ **Explainable and debuggable**
- Every status has exactly one root cause (primaryFailure) or top warning
- Priority order is clear and documented
- Evidence trails are complete

✅ **Extensible**
- Add new failure codes by assigning priority
- Add new severities without breaking contract
- Detection rules are isolated and composable

## Testing the Changes

### Test Case 1: No Objects Found
```bash
curl "http://localhost:8080/api/deployment-doctor?namespace=nonexistent&release=test"
```

**Expected:**
- `overall = UNKNOWN`
- `primaryFailure.code = NO_MATCHING_OBJECTS`
- `topWarning = null`

### Test Case 2: Critical Failure
```bash
curl "http://localhost:8080/api/deployment-doctor?namespace=cart&selector=app=broken-app"
```

**Expected (if secrets missing):**
- `overall = FAIL`
- `primaryFailure.code = EXTERNAL_SECRET_RESOLUTION_FAILED` (priority 1)
- `topWarning = null` (or set if warnings also exist)

### Test Case 3: Warnings Only
```bash
curl "http://localhost:8080/api/deployment-doctor?namespace=cart&selector=app=stable-app"
```

**Expected (if pods have restarts):**
- `overall = WARN`
- `primaryFailure = null`
- `topWarning.code = POD_RESTARTS_DETECTED`

### Test Case 4: Healthy
```bash
curl "http://localhost:8080/api/deployment-doctor?namespace=cart&selector=app=healthy-app"
```

**Expected:**
- `overall = PASS`
- `primaryFailure = null`
- `topWarning = null`

## Migration Notes

### Frontend Changes Required

**Before:**
```typescript
// UI had to implement this logic
const primaryFailure = findings
  .filter(f => f.severity === 'ERROR')
  .sort((a, b) => getPriority(a.code) - getPriority(b.code))[0];
```

**After:**
```typescript
// Backend provides it directly
const { primaryFailure, topWarning } = response;
```

### Breaking Changes

⚠️ **Response schema changed**
- Added `topWarning` field to `DeploymentSummaryResponse`
- Clients consuming the API must update their types

### Backward Compatibility

✅ **Legacy severities still work**
- `HIGH` maps to `ERROR`
- `MED` maps to `WARN`
- `LOW` maps to `INFO`

✅ **Legacy failure codes still work**
- `NO_READY_PODS`, `ROLLOUT_STUCK` are still detected
- They have defined priorities and severities

## Next Steps

1. **Update Frontend**
   - Use `primaryFailure` instead of computing it
   - Use `topWarning` instead of filtering findings
   - Update TypeScript types to include `topWarning`

2. **Update Tests**
   - Verify `topWarning` is correctly set in test cases
   - Add test for "PASS with warnings" scenario

3. **Update Documentation**
   - API documentation should reflect new contract
   - Add examples showing `topWarning` usage

4. **Database Triage**
   - Apply same pattern to `dbtriage` service
   - Ensure consistent behavior across all triage endpoints

## Code Changes Summary

**Files modified:**
1. `DeploymentSummaryResponse.java` - Added `topWarning` field
2. `DeploymentDoctorService.java` - Added `selectTopWarning()` method
3. `DeploymentDoctorService.java` - Enhanced `selectPrimaryFailure()` documentation

**No breaking changes to existing logic:**
- All existing detection rules unchanged
- All priorities unchanged
- All severity mappings unchanged

**Build status:** ✅ Compiles successfully
