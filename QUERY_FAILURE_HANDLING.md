# Query Failure Handling (Tooling/Platform-Level Errors)

## The Problem This Solves

Most internal tools fail to model their own failures correctly. When the tool itself cannot query the system (bad selector, invalid namespace, API rejection), they either:
1. Show a generic error banner: "Failed to fetch deployment data..."
2. Show partial/stale data with no explanation
3. Blame the user without actionable guidance

**This destroys trust in the tool.**

## The Solution: First-Class Query Failures

We now model **query/tooling failures as a first-class failure category**, just like application failures (crash loops) or platform failures (resource exhaustion).

### The Three Failure Categories

```
1. Application failures
   └─ crashloops, bad config, restarts
   └─ Owner: App team

2. Platform failures  
   └─ resources, sandbox recycle, node issues
   └─ Owner: Platform/DevOps team

3. Tooling / Query failures ← THIS IS NEW
   └─ bad selectors, invalid input, API rejection
   └─ Owner: Platform (tooling)
```

**Why #3 matters:** If you can't query the system correctly, you cannot assess application or platform health. Query failures are the **highest priority** because they block all other diagnosis.

---

## Implementation

### 1. New Failure Code: QUERY_INVALID

**Added to FailureCode enum:**
```java
QUERY_INVALID(Owner.PLATFORM, Severity.ERROR)
```

**Priority:** 0 (highest - even higher than EXTERNAL_SECRET_RESOLUTION_FAILED)

**When triggered:**
- Kubernetes API returns 400 (Bad Request) or 422 (Unprocessable Entity)
- Label selector syntax is invalid
- Namespace parameter is invalid
- Neither `selector` nor `release` parameter provided
- API rejects the query for any input-related reason

**Owner:** PLATFORM (tooling team) - this is a tool problem, not an app problem

---

### 2. Short-Circuit on Query Failure

**Rule:** If the query cannot be executed, do not try to partially render anything.

**Before:**
```json
{
  "health": { "overall": "PASS" },  // ❌ Misleading!
  "findings": [],
  "pods": [],  // Empty because query failed, but UI doesn't know why
  "services": []
}
```
Generic error banner: "Failed to fetch deployment data: invalid label selector 'app='"

**After:**
```json
{
  "health": { "overall": "FAIL" },  // ✅ Correct - query failed
  "findings": [
    {
      "code": "QUERY_INVALID",
      "severity": "ERROR",
      "owner": "PLATFORM",
      "title": "Invalid query parameters",
      "explanation": "The triage query could not be executed due to invalid input parameters or Kubernetes API rejection. This indicates a problem with the query itself, not the workload.",
      "evidence": [
        { "type": "Namespace", "name": "cart" },
        { "type": "Selector", "name": "app=" },
        { "type": "Error", "name": "invalid selector syntax: trailing '='" }
      ],
      "nextSteps": [
        "Verify label selector format follows Kubernetes syntax: key=value",
        "Avoid trailing '=' or malformed expressions like 'app=' or '=value'",
        "Test selector with kubectl: kubectl get pods -l \"app=\" -n cart",
        "Common valid examples: app=my-app, tier=frontend, env!=prod",
        "Review error details in evidence section above"
      ]
    }
  ],
  "primaryFailure": { /* QUERY_INVALID */ },
  "topWarning": null,
  "objects": {
    "pods": [],     // ✅ Empty by design (query failed)
    "services": [], // ✅ Empty by design (query failed)
    ...
  }
}
```

**Contract:**
- `overall = FAIL` (not UNKNOWN, because the **tool** failed, not the workload)
- `primaryFailure = QUERY_INVALID` (highest priority)
- `findings = [ QUERY_INVALID ]` (single finding)
- `objects = empty` (do not partially render)

---

### 3. Replace Generic Error Banner with Primary Root Cause

**Before (generic error banner):**
```
┌──────────────────────────────────────────────────────┐
│ ⚠️ Error                                              │
│                                                       │
│ Failed to fetch deployment data: invalid label       │
│ selector 'app='                                       │
│                                                       │
│ [Dismiss]                                             │
└──────────────────────────────────────────────────────┘
```

Problems:
- ❌ Not consistent with other failures
- ❌ No owner/escalation path
- ❌ No actionable next steps
- ❌ Feels like a "broken tool"

**After (consistent with all other failures):**
```
┌──────────────────────────────────────────────────────┐
│ Overall: ⭕ FAILED                                    │
└──────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────┐
│ 🔴 Primary Root Cause                                 │
│                                                       │
│ Invalid query parameters                              │
│ Owner: Platform (tooling)                             │
│                                                       │
│ The triage query could not be executed due to        │
│ invalid input parameters or Kubernetes API            │
│ rejection. This indicates a problem with the query   │
│ itself, not the workload.                            │
│                                                       │
│ Evidence:                                             │
│  • Namespace: cart                                    │
│  • Selector: app=                                     │
│  • Error: invalid selector syntax: trailing '='      │
│                                                       │
│ Next Steps:                                           │
│  1. Verify label selector format follows Kubernetes  │
│     syntax: key=value                                 │
│  2. Avoid trailing '=' or malformed expressions      │
│     like 'app=' or '=value'                           │
│  3. Test selector with kubectl:                      │
│     kubectl get pods -l "app=" -n cart               │
│  4. Common valid examples: app=my-app,               │
│     tier=frontend, env!=prod                         │
│  5. Review error details in evidence section above   │
└──────────────────────────────────────────────────────┘
```

Benefits:
- ✅ Consistent with all other failures
- ✅ Clear owner (Platform/tooling)
- ✅ Actionable next steps
- ✅ Professional, trustworthy feel

---

### 4. UI Rule: Don't Show Partial Data

**When `primaryFailure != null` AND `primaryFailure.code == QUERY_INVALID`:**

**Do NOT show:**
- ❌ Pod cards (empty, misleading)
- ❌ Service cards (empty, misleading)
- ❌ Event lists (empty, misleading)
- ❌ Stale counts ("0/0 deployments ready")
- ❌ Partial object tables

**DO show:**
- ✅ Overall status badge (FAIL)
- ✅ Primary Root Cause card (QUERY_INVALID)
- ✅ Evidence section
- ✅ Next Steps
- ✅ Clear explanation that **the query failed, not the workload**

This prevents confusion and maintains trust in the tool.

---

## Examples

### Example 1: Invalid Selector Syntax

**Request:**
```bash
GET /api/deployment/summary?namespace=cart&selector=app=
```

**Response:**
```json
{
  "timestamp": "2026-01-08T21:09:46Z",
  "target": {
    "namespace": "cart",
    "selector": "app=",
    "release": null
  },
  "health": {
    "overall": "FAIL",
    "deploymentsReady": "0/0",
    "breakdown": { "running": 0, "pending": 0, ... }
  },
  "findings": [
    {
      "code": "QUERY_INVALID",
      "severity": "ERROR",
      "owner": "PLATFORM",
      "title": "Invalid query parameters",
      "explanation": "The triage query could not be executed due to invalid input parameters or Kubernetes API rejection. This indicates a problem with the query itself, not the workload.",
      "evidence": [
        { "type": "Namespace", "name": "cart" },
        { "type": "Selector", "name": "app=" },
        { "type": "Error", "name": "Kubernetes API rejected query: invalid label selector 'app='" }
      ],
      "nextSteps": [
        "Verify label selector format follows Kubernetes syntax: key=value or key in (value1,value2)",
        "Avoid trailing '=' or malformed expressions like 'app=' or '=value'",
        "Test selector with kubectl: kubectl get pods -l \"app=\" -n cart",
        "Common valid examples: app=my-app, tier=frontend, env!=prod",
        "Review error details in evidence section above"
      ]
    }
  ],
  "primaryFailure": { /* QUERY_INVALID finding */ },
  "topWarning": null,
  "objects": {
    "workloads": [],
    "pods": [],
    "events": [],
    "services": [],
    "endpoints": []
  }
}
```

**UI shows:**
- Status: 🔴 FAILED
- Primary Root Cause: "Invalid query parameters"
- Owner: Platform (tooling)
- Clear explanation and next steps
- **No partial pod/service cards** (would be confusing)

---

### Example 2: Missing Required Parameter

**Request:**
```bash
GET /api/deployment/summary?namespace=cart
# Missing both selector and release
```

**Response:**
```json
{
  "findings": [
    {
      "code": "QUERY_INVALID",
      "evidence": [
        { "type": "Namespace", "name": "cart" },
        { "type": "Error", "name": "Either 'selector' or 'release' must be provided." }
      ],
      "nextSteps": [
        "Provide either 'selector' or 'release' parameter",
        "Example: ?namespace=cart&selector=app=cart-app",
        "Example: ?namespace=cart&release=cart-v1",
        "Review error details in evidence section above"
      ]
    }
  ],
  "primaryFailure": { /* QUERY_INVALID */ }
}
```

**UI shows:**
- Clear message: "Either 'selector' or 'release' must be provided"
- Examples of correct usage
- Professional, not blaming the user

---

### Example 3: Malformed Selector Expression

**Request:**
```bash
GET /api/deployment/summary?namespace=cart&selector=app in (foo,bar
# Missing closing parenthesis
```

**Response:**
```json
{
  "findings": [
    {
      "code": "QUERY_INVALID",
      "evidence": [
        { "type": "Selector", "name": "app in (foo,bar" },
        { "type": "Error", "name": "Kubernetes API rejected query: unable to parse requirement: expected ')'" }
      ],
      "nextSteps": [
        "Verify label selector format follows Kubernetes syntax: key=value or key in (value1,value2)",
        "Avoid trailing '=' or malformed expressions like 'app=' or '=value'",
        "Test selector with kubectl: kubectl get pods -l \"app in (foo,bar\" -n cart",
        "Common valid examples: app=my-app, tier=frontend, env!=prod",
        "Review error details in evidence section above"
      ]
    }
  ]
}
```

---

## Priority Order (Updated)

```
Priority 0:  QUERY_INVALID                         ← NEW (highest)
Priority 1:  EXTERNAL_SECRET_RESOLUTION_FAILED
Priority 2:  BAD_CONFIG
Priority 3:  IMAGE_PULL_FAILED
Priority 4:  INSUFFICIENT_RESOURCES
Priority 5:  RBAC_DENIED
Priority 6:  CRASH_LOOP
Priority 7:  READINESS_CHECK_FAILED
Priority 8:  SERVICE_SELECTOR_MISMATCH
...
Priority 50: POD_RESTARTS_DETECTED (warning)
Priority 51: POD_SANDBOX_RECYCLE (warning)
Priority 99: NO_MATCHING_OBJECTS (UNKNOWN)
```

**Why priority 0?** Because if you cannot query the system, you cannot assess any other failures. Query failures block all diagnosis.

---

## Implementation Details

### Error Detection

**In `DeploymentDoctorService.getSummary()`:**
```java
try {
    return executeQuery(namespace, selector, release, limitEvents);
} catch (IllegalArgumentException e) {
    // Selector/release validation failed
    return buildQueryInvalidResponse(...);
} catch (ApiException e) {
    // Kubernetes API returned error
    if (e.getCode() == 400 || e.getCode() == 422) {
        return buildQueryInvalidResponse(...);
    }
    // Other errors (403, 500) handled elsewhere
    throw new IllegalStateException(...);
}
```

**Trigger conditions:**
1. `IllegalArgumentException` from `buildEffectiveSelector()` (missing selector/release)
2. `ApiException` with code 400 (Bad Request)
3. `ApiException` with code 422 (Unprocessable Entity)
4. Invalid label selector syntax
5. Invalid namespace

**Non-trigger conditions:**
- 403 Forbidden (RBAC) → Not a query failure, handled separately as RBAC_DENIED
- 404 Not Found → Not a query failure, may just be empty namespace
- 500 Internal Server Error → Platform issue, not query issue

### Short-Circuit Response

**`buildQueryInvalidResponse()` creates:**
```java
new DeploymentSummaryResponse(
    timestamp,
    target,
    new Health(OverallStatus.FAIL, "0/0", emptyBreakdown),
    List.of(queryInvalidFinding),    // Only one finding
    queryInvalidFinding,              // primaryFailure
    null,                             // topWarning
    new Objects(empty, empty, ...)   // All objects empty
)
```

**Key points:**
- `overall = FAIL` (tool failed)
- Only one finding (QUERY_INVALID)
- No partial data (all objects empty)
- Actionable next steps based on error type

---

## Testing

### Test Case 1: Invalid Selector Syntax

```bash
# Missing value
curl "http://localhost:8080/api/deployment/summary?namespace=cart&selector=app="

# Expected:
# - overall: FAIL
# - primaryFailure.code: QUERY_INVALID
# - evidence includes "app="
# - nextSteps include "Avoid trailing '='"
```

### Test Case 2: Missing Required Parameters

```bash
# No selector or release
curl "http://localhost:8080/api/deployment/summary?namespace=cart"

# Expected:
# - overall: FAIL
# - primaryFailure.code: QUERY_INVALID
# - nextSteps include "Provide either 'selector' or 'release' parameter"
```

### Test Case 3: Malformed Expression

```bash
# Invalid 'in' expression
curl "http://localhost:8080/api/deployment/summary?namespace=cart&selector=app%20in%20(foo"

# Expected:
# - overall: FAIL
# - primaryFailure.code: QUERY_INVALID
# - evidence includes selector syntax error
```

### Test Case 4: Valid Query (Should NOT Trigger)

```bash
# Valid selector
curl "http://localhost:8080/api/deployment/summary?namespace=cart&selector=app=cart-app"

# Expected:
# - NO QUERY_INVALID finding
# - Normal workflow (PASS/WARN/FAIL based on workload health)
```

---

## Benefits

### 1. Maintains Trust

**Before:** "This tool is broken, it always shows errors"  
**After:** "This tool tells me when I made a mistake, and how to fix it"

### 2. Consistent Mental Model

**All failures follow the same pattern:**
- App failures → CRASH_LOOP, BAD_CONFIG
- Platform failures → EXTERNAL_SECRET_RESOLUTION_FAILED, INSUFFICIENT_RESOURCES
- **Query failures → QUERY_INVALID** ← NEW

### 3. Actionable Guidance

**Before:** "Failed to fetch data"  
**After:** "Test selector with kubectl: kubectl get pods -l 'app=' -n cart"

### 4. Professional UX

**Before:** Red error banner (feels broken)  
**After:** Primary Root Cause card (feels intentional)

### 5. Prevents Confusion

**Before:** Shows empty cards, users wonder if workload is broken  
**After:** Clear message that query failed, not workload

---

## Frontend Changes Required

### 1. Handle QUERY_INVALID Like Other Failures

**No special case needed!** QUERY_INVALID is just another failure code.

```typescript
// Existing logic already handles it
const { health, primaryFailure, topWarning } = response;

if (health.overall === 'FAIL' || health.overall === 'UNKNOWN') {
  // Show primaryFailure (could be QUERY_INVALID, CRASH_LOOP, etc.)
  return <FindingCard finding={primaryFailure} />;
}
```

### 2. Optional: Don't Render Empty Objects

```typescript
// Only show object cards when primaryFailure is NOT QUERY_INVALID
const shouldShowObjects = 
  primaryFailure?.code !== 'QUERY_INVALID';

return (
  <>
    <StatusBadge>{health.overall}</StatusBadge>
    <FindingCard finding={primaryFailure} />
    
    {shouldShowObjects && (
      <>
        <PodCards pods={objects.pods} />
        <ServiceCards services={objects.services} />
      </>
    )}
  </>
);
```

### 3. Update TypeScript Types

```typescript
type FailureCode = 
  | 'QUERY_INVALID'                        // 👈 NEW
  | 'EXTERNAL_SECRET_RESOLUTION_FAILED'
  | 'BAD_CONFIG'
  | 'IMAGE_PULL_FAILED'
  | 'CRASH_LOOP'
  | 'READINESS_CHECK_FAILED'
  | 'SERVICE_SELECTOR_MISMATCH'
  | 'INSUFFICIENT_RESOURCES'
  | 'RBAC_DENIED'
  | 'POD_RESTARTS_DETECTED'
  | 'POD_SANDBOX_RECYCLE'
  | 'NO_MATCHING_OBJECTS'
  | 'ROLLOUT_STUCK'
  | 'NO_READY_PODS';
```

---

## Summary

✅ **Query failures are now first-class failures**  
✅ **Highest priority (0) - blocks all other diagnosis**  
✅ **Consistent with application and platform failures**  
✅ **Actionable guidance for users**  
✅ **Professional, trustworthy UX**  
✅ **Prevents confusion from partial data**

**Most internal tools never model #3 (query failures) correctly.**  
**That's why people don't trust them.**  
**We fixed that.**

---

## Files Modified

1. **`FailureCode.java`** - Added QUERY_INVALID with priority 0
2. **`DeploymentDoctorService.java`** - Added query failure detection and short-circuit logic

**Build status:** ✅ Compiles successfully

**Risk level:** 🟢 LOW (only additive changes)
