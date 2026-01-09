# Backend Contract Quick Reference

## 🎯 The Contract (One-Pager)

### Response Structure
```typescript
interface DeploymentSummaryResponse {
  timestamp: string;
  target: { namespace, selector, release };
  health: {
    overall: "PASS" | "WARN" | "FAIL" | "UNKNOWN";
    deploymentsReady: string;
    breakdown: { ... };
  };
  findings: Finding[];
  primaryFailure: Finding | null;  // Set ONLY when overall == FAIL or UNKNOWN
  topWarning: Finding | null;      // Highest priority WARN finding
  objects: { ... };
}
```

### Decision Table

| overall | primaryFailure | topWarning | UI Shows |
|---------|----------------|------------|----------|
| UNKNOWN | NO_MATCHING_OBJECTS | null | "Cannot assess - no objects found" |
| FAIL | Highest priority ERROR | Set if warnings exist | "Failed - [primaryFailure.title]" |
| WARN | null | Highest priority WARN | "Healthy (with warnings)" |
| PASS | null | null | "All healthy" |

### Priority Order (lower = higher priority)

**TOOLING/QUERY failures (highest priority):**
0. QUERY_INVALID ← NEW (bad selector, invalid input, API 400/422)

**ERROR severity (failures):**
1. EXTERNAL_SECRET_RESOLUTION_FAILED
2. BAD_CONFIG
3. IMAGE_PULL_FAILED
4. INSUFFICIENT_RESOURCES
5. RBAC_DENIED
6. CRASH_LOOP
7. READINESS_CHECK_FAILED
8. SERVICE_SELECTOR_MISMATCH

**WARN severity (risk signals):**
50. POD_RESTARTS_DETECTED
51. POD_SANDBOX_RECYCLE

**Special:**
99. NO_MATCHING_OBJECTS (only when no objects)

### UI Logic (Dead Simple)

```typescript
const { health, primaryFailure, topWarning } = response;

// Determine what to show
let mainFinding: Finding | null;
let statusColor: string;
let statusLabel: string;

switch (health.overall) {
  case 'UNKNOWN':
    mainFinding = primaryFailure;  // NO_MATCHING_OBJECTS
    statusColor = 'gray';
    statusLabel = 'Unknown';
    break;
    
  case 'FAIL':
    mainFinding = primaryFailure;  // Highest priority ERROR
    statusColor = 'red';
    statusLabel = 'Failed';
    break;
    
  case 'WARN':
    mainFinding = topWarning;      // Highest priority WARN
    statusColor = 'yellow';
    statusLabel = 'Healthy (with warnings)';
    break;
    
  case 'PASS':
    mainFinding = null;
    statusColor = 'green';
    statusLabel = 'Healthy';
    break;
}

// Render
<StatusBadge color={statusColor}>{statusLabel}</StatusBadge>
{mainFinding && (
  <FindingCard
    title={mainFinding.title}
    explanation={mainFinding.explanation}
    owner={mainFinding.owner}
    evidence={mainFinding.evidence}
    nextSteps={mainFinding.nextSteps}
  />
)}
```

### What Changed?

**Before:** Frontend computed primaryFailure
```typescript
const primaryFailure = findings
  .filter(f => f.severity === 'ERROR')
  .sort((a, b) => getPriority(a.code) - getPriority(b.code))[0];
```

**After:** Backend provides it
```typescript
const { primaryFailure, topWarning } = response;
```

### Testing Checklist

- [ ] UNKNOWN: Call with nonexistent namespace → primaryFailure set, topWarning null
- [ ] FAIL: Call with broken app → primaryFailure set (highest priority), topWarning may be set
- [ ] WARN: Call with restarting app → primaryFailure null, topWarning set
- [ ] PASS: Call with healthy app → primaryFailure null, topWarning null
- [ ] Multiple ERRORs: Highest priority becomes primaryFailure
- [ ] Multiple WARNs: Highest priority becomes topWarning
- [ ] Mixed: Both primaryFailure and topWarning are set

### Key Guarantees

✅ **Deterministic:** Same inputs always produce same outputs  
✅ **Explainable:** Every status has exactly one root cause  
✅ **UI-friendly:** No business logic in frontend  
✅ **Extensible:** Add new failure codes by assigning priority  
✅ **Backward compatible:** Legacy severities (HIGH, MED, LOW) still work

### Common Scenarios

**Scenario 1: Invalid query (bad selector)**
- Finding: QUERY_INVALID (priority 0)
- overall = FAIL
- primaryFailure = QUERY_INVALID
- Owner: PLATFORM (tooling)
- **Note:** Query failure blocks all other diagnosis (highest priority)

**Scenario 2: External secrets not accessible**
- Finding: EXTERNAL_SECRET_RESOLUTION_FAILED (priority 1)
- overall = FAIL
- primaryFailure = EXTERNAL_SECRET_RESOLUTION_FAILED
- Owner: PLATFORM

**Scenario 3: Pods restarting but working**
- Finding: POD_RESTARTS_DETECTED (priority 50)
- overall = WARN
- primaryFailure = null
- topWarning = POD_RESTARTS_DETECTED
- Owner: APP

**Scenario 4: Secrets + restarts (both)**
- Findings: EXTERNAL_SECRET_RESOLUTION_FAILED (1) + POD_RESTARTS_DETECTED (50)
- overall = FAIL (ERROR takes precedence)
- primaryFailure = EXTERNAL_SECRET_RESOLUTION_FAILED
- topWarning = POD_RESTARTS_DETECTED

**Scenario 5: Wrong namespace**
- Finding: NO_MATCHING_OBJECTS (99)
- overall = UNKNOWN
- primaryFailure = NO_MATCHING_OBJECTS
- topWarning = null

### API Endpoint

```bash
GET /api/deployment/summary?namespace=<ns>&selector=<selector>&limitEvents=50
GET /api/deployment/summary?namespace=<ns>&release=<release>&limitEvents=50
```

**Required:** `namespace` + (`selector` OR `release`)  
**Optional:** `limitEvents` (default: 50)

### Owner Routing

| Owner | Route To | Example Failure |
|-------|----------|-----------------|
| APP | Application team | CRASH_LOOP, BAD_CONFIG, READINESS_CHECK_FAILED |
| PLATFORM | Platform/DevOps team | EXTERNAL_SECRET..., IMAGE_PULL_FAILED, INSUFFICIENT_RESOURCES |
| SECURITY | Security team | RBAC_DENIED |
| UNKNOWN | Triage further | NO_MATCHING_OBJECTS |

### Documentation Files

- `BACKEND_DETERMINISTIC_STATUS.md` - Full implementation details
- `BACKEND_RESPONSE_EXAMPLES.md` - Concrete JSON examples for all scenarios
- `BACKEND_CHANGES_CHECKLIST.md` - Your exact requirements → implementation mapping
- `BACKEND_CONTRACT_QUICK_REF.md` - This file (quick reference)

---

## 🚀 You're Done!

Backend now produces **unambiguous, deterministic status**. UI just needs to:
1. Read `primaryFailure` or `topWarning`
2. Display it
3. No sorting, filtering, or priority logic needed

**Build status:** ✅ Compiles  
**Breaking changes:** Response schema (added `topWarning`)  
**Risk:** LOW (additive only)
