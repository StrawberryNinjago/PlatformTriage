# Complete Failure Taxonomy (3 Categories)

## The Three Pillars of Failures

```
┌─────────────────────────────────────────────────────────────────┐
│                    COMPLETE FAILURE TAXONOMY                     │
└─────────────────────────────────────────────────────────────────┘

                        ┌─────────────────┐
                        │   User Request  │
                        │  "Triage cart"  │
                        └────────┬────────┘
                                 │
                                 ▼
         ┌───────────────────────────────────────────────┐
         │ 1. TOOLING / QUERY FAILURES (Priority 0)      │
         │ Can the tool query the system?                │
         │ ✅ YES → Continue                              │
         │ ❌ NO  → QUERY_INVALID (FAIL, priority 0)     │
         │                                                │
         │ Triggers:                                      │
         │  • Bad selector syntax (app=)                 │
         │  • Invalid namespace                           │
         │  • API 400/422 (Bad Request)                  │
         │  • Missing required parameters                 │
         │                                                │
         │ Owner: Platform (tooling)                     │
         └───────────────────┬───────────────────────────┘
                             │
                             ▼ Query succeeded
         ┌───────────────────────────────────────────────┐
         │ 2. PLATFORM FAILURES (Priority 1-5)           │
         │ Can the platform run the workload?            │
         │ ✅ YES → Continue                              │
         │ ❌ NO  → Platform failure (FAIL)              │
         │                                                │
         │ Failures:                                      │
         │  1. EXTERNAL_SECRET_RESOLUTION_FAILED         │
         │  3. IMAGE_PULL_FAILED                         │
         │  4. INSUFFICIENT_RESOURCES                    │
         │  5. RBAC_DENIED                                │
         │                                                │
         │ Owner: Platform / DevOps / Security           │
         └───────────────────┬───────────────────────────┘
                             │
                             ▼ Platform OK
         ┌───────────────────────────────────────────────┐
         │ 3. APPLICATION FAILURES (Priority 2, 6-8)     │
         │ Is the application working correctly?         │
         │ ✅ YES → PASS or WARN                          │
         │ ❌ NO  → Application failure (FAIL)           │
         │                                                │
         │ Failures:                                      │
         │  2. BAD_CONFIG                                 │
         │  6. CRASH_LOOP                                 │
         │  7. READINESS_CHECK_FAILED                    │
         │  8. SERVICE_SELECTOR_MISMATCH                 │
         │                                                │
         │ Owner: App team                                │
         └───────────────────┬───────────────────────────┘
                             │
                             ▼ All healthy
         ┌───────────────────────────────────────────────┐
         │ 4. RISK SIGNALS (Priority 50+, WARN)          │
         │ Are there warning signs?                      │
         │ ✅ NO  → PASS                                  │
         │ ⚠️ YES → WARN                                 │
         │                                                │
         │ Signals:                                       │
         │  50. POD_RESTARTS_DETECTED                    │
         │  51. POD_SANDBOX_RECYCLE                      │
         │                                                │
         │ Owner: App / Platform                          │
         └───────────────────────────────────────────────┘
```

---

## Why This Matters

### Most Tools Only Model #2 and #3

```
❌ Typical Tool:
   ├─ Application failures ✅ (crash loops, bad config)
   ├─ Platform failures ✅ (resources, secrets)
   └─ Tooling failures ❌ (generic error banner)

✅ Our Tool:
   ├─ Tooling failures ✅ (QUERY_INVALID, first-class)
   ├─ Platform failures ✅ (EXTERNAL_SECRET, IMAGE_PULL)
   └─ Application failures ✅ (CRASH_LOOP, BAD_CONFIG)
```

**Result:** Users trust our tool because it models its own failures correctly.

---

## Decision Tree

```
Start
  │
  ├─ Can we query Kubernetes?
  │    NO → overall=FAIL, primaryFailure=QUERY_INVALID (priority 0)
  │    YES ↓
  │
  ├─ Did we find any objects?
  │    NO → overall=UNKNOWN, primaryFailure=NO_MATCHING_OBJECTS (priority 99)
  │    YES ↓
  │
  ├─ Any ERROR-severity findings?
  │    YES → overall=FAIL, primaryFailure=<highest priority ERROR>
  │    NO ↓
  │
  ├─ Any WARN-severity findings?
  │    YES → overall=WARN, primaryFailure=null, topWarning=<highest priority WARN>
  │    NO ↓
  │
  └─ overall=PASS, primaryFailure=null, topWarning=null
```

---

## Complete Priority List

| Priority | Code | Severity | Owner | Category |
|----------|------|----------|-------|----------|
| **0** | **QUERY_INVALID** | **ERROR** | **Platform (tooling)** | **Tooling/Query** |
| 1 | EXTERNAL_SECRET_RESOLUTION_FAILED | ERROR | Platform | Platform |
| 2 | BAD_CONFIG | ERROR | App | Application |
| 3 | IMAGE_PULL_FAILED | ERROR | Platform | Platform |
| 4 | INSUFFICIENT_RESOURCES | ERROR | Platform | Platform |
| 5 | RBAC_DENIED | ERROR | Security | Platform |
| 6 | CRASH_LOOP | ERROR | App | Application |
| 7 | READINESS_CHECK_FAILED | ERROR | App | Application |
| 8 | SERVICE_SELECTOR_MISMATCH | WARN | App | Application |
| 9 | NO_READY_PODS | ERROR | App | Application (legacy) |
| 10 | ROLLOUT_STUCK | ERROR | App | Application (legacy) |
| 50 | POD_RESTARTS_DETECTED | WARN | App | Risk Signal |
| 51 | POD_SANDBOX_RECYCLE | WARN | Platform | Risk Signal |
| 99 | NO_MATCHING_OBJECTS | WARN | Unknown | Special |

---

## Examples by Category

### 1. Tooling / Query Failures (Priority 0)

**Example: Invalid selector syntax**
```bash
GET /api/deployment/summary?namespace=cart&selector=app=
```

**Response:**
- `overall = FAIL`
- `primaryFailure.code = QUERY_INVALID`
- `primaryFailure.owner = PLATFORM`
- Clear message: "Avoid trailing '=' in selectors"

**Why this matters:** The **tool failed**, not the workload. Users need to know the difference.

---

### 2. Platform Failures (Priority 1-5)

**Example: External secrets not accessible**
```bash
GET /api/deployment/summary?namespace=cart&selector=app=cart-app
# Cart app uses Azure Key Vault via CSI driver
```

**Response:**
- `overall = FAIL`
- `primaryFailure.code = EXTERNAL_SECRET_RESOLUTION_FAILED`
- `primaryFailure.owner = PLATFORM`
- Evidence: "MountVolume.SetUp failed: key vault access denied"

**Why this matters:** Platform team needs to fix Key Vault permissions, not app team.

---

### 3. Application Failures (Priority 2, 6-8)

**Example: App crash loop**
```bash
GET /api/deployment/summary?namespace=cart&selector=app=cart-app
# Cart app has a bug causing crashes
```

**Response:**
- `overall = FAIL`
- `primaryFailure.code = CRASH_LOOP`
- `primaryFailure.owner = APP`
- Evidence: "BackOff events, 15 restarts, exit code 1"

**Why this matters:** App team needs to fix the code, not platform team.

---

### 4. Risk Signals (Priority 50+)

**Example: Pods restarting but working**
```bash
GET /api/deployment/summary?namespace=cart&selector=app=cart-app
# Cart app restarts occasionally but recovers
```

**Response:**
- `overall = WARN`
- `primaryFailure = null`
- `topWarning.code = POD_RESTARTS_DETECTED`
- `topWarning.owner = APP`

**Why this matters:** Not critical, but should be investigated to prevent future failures.

---

## Contract Rules

### Rule 1: Query Failures Are Highest Priority

```
If QUERY_INVALID exists:
  - It is ALWAYS the primaryFailure
  - Priority 0 beats all other failures
  - overall = FAIL (not UNKNOWN)
```

**Rationale:** If you can't query, you can't assess anything else.

### Rule 2: One Root Cause Per Response

```
Each response has exactly ONE of:
  - primaryFailure (when overall == FAIL or UNKNOWN)
  - topWarning (when overall == WARN)
  - Neither (when overall == PASS)
```

**Rationale:** Multiple "primary" causes are confusing.

### Rule 3: No Partial Rendering on Query Failure

```
When primaryFailure.code == QUERY_INVALID:
  - objects.pods = []
  - objects.services = []
  - objects.events = []
  - All arrays empty
```

**Rationale:** Partial/stale data is misleading when query failed.

### Rule 4: Failures Are Routable

```
Every failure has an owner:
  - PLATFORM → Platform/DevOps team
  - APP → Application team
  - SECURITY → Security team
  - UNKNOWN → Needs triage
```

**Rationale:** Clear escalation path.

---

## UI Behavior by Category

### Category 1: Tooling/Query Failures

**UI Shows:**
- 🔴 Status: FAILED
- 🚨 Primary Root Cause: "Invalid query parameters"
- 👤 Owner: Platform (tooling)
- 📝 Clear explanation: "The tool query failed, not the workload"
- ✅ Actionable next steps: "Test with kubectl..."
- ❌ Do NOT show pod/service cards (empty, misleading)

**User reaction:** "I made a mistake in the selector, let me fix it"

---

### Category 2: Platform Failures

**UI Shows:**
- 🔴 Status: FAILED
- 🚨 Primary Root Cause: "External secret mount failed (CSI / Key Vault)"
- 👤 Owner: Platform
- 📝 Clear explanation: "Workload identity/Key Vault permissions issue"
- ✅ Actionable next steps: "Verify Key Vault access..."
- ✅ Show pod/service/event cards (context is helpful)

**User reaction:** "I need to involve the platform team for Key Vault access"

---

### Category 3: Application Failures

**UI Shows:**
- 🔴 Status: FAILED
- 🚨 Primary Root Cause: "Crash loop detected"
- 👤 Owner: App
- 📝 Clear explanation: "Containers repeatedly crashing (exit code 1)"
- ✅ Actionable next steps: "Check logs with kubectl logs..."
- ✅ Show pod/service/event cards (context is helpful)

**User reaction:** "I need to fix a bug in my application code"

---

### Category 4: Risk Signals

**UI Shows:**
- 🟡 Status: HEALTHY (with warnings)
- ⚠️ Top Warning: "Pod restarts detected"
- 👤 Owner: App
- 📝 Explanation: "3 pods have restarted but are currently running"
- ✅ Next steps: "Review logs for crash patterns..."
- ✅ Show pod/service/event cards (context is helpful)

**User reaction:** "Everything works but I should investigate those restarts"

---

## Why Priority 0 for QUERY_INVALID?

### The Dependency Chain

```
Query Success
    ↓
Platform Health
    ↓
Application Health
    ↓
Risk Signals
```

**If step 1 fails, you cannot assess steps 2-4.**

That's why QUERY_INVALID is priority 0 (highest).

---

## Trust & Professional UX

### Unprofessional (Most Tools)

```
┌─────────────────────────────────┐
│ ⚠️ ERROR                         │
│                                  │
│ Failed to fetch deployment data │
│                                  │
│ [Dismiss]                        │
└─────────────────────────────────┘

Running: 0 pods
Pending: 0 pods
Events: (empty)
```

**User thinks:** "This tool is broken"

---

### Professional (Our Tool)

```
┌─────────────────────────────────────────────────────┐
│ Overall: 🔴 FAILED                                   │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│ 🔴 Primary Root Cause                                │
│                                                      │
│ Invalid query parameters                             │
│ Owner: Platform (tooling)                            │
│                                                      │
│ The triage query could not be executed due to       │
│ invalid input parameters or Kubernetes API           │
│ rejection. This indicates a problem with the query  │
│ itself, not the workload.                           │
│                                                      │
│ Evidence:                                            │
│  • Selector: app=                                    │
│  • Error: invalid selector syntax: trailing '='     │
│                                                      │
│ Next Steps:                                          │
│  1. Verify label selector format: key=value         │
│  2. Avoid trailing '=' like 'app='                  │
│  3. Test with: kubectl get pods -l "app=" -n cart   │
│  4. Valid examples: app=my-app, tier=frontend       │
└─────────────────────────────────────────────────────┘
```

**User thinks:** "This tool tells me exactly what's wrong and how to fix it"

---

## Summary

### ✅ Complete Taxonomy

```
Priority 0:  Tooling/Query failures  ← Most tools miss this
Priority 1-5:  Platform failures
Priority 6-8:  Application failures
Priority 50+: Risk signals
```

### ✅ Clear Ownership

```
QUERY_INVALID           → Platform (tooling)
EXTERNAL_SECRET...      → Platform (infrastructure)
CRASH_LOOP              → App team
POD_RESTARTS_DETECTED   → App team (advisory)
```

### ✅ Deterministic Behavior

```
Same input → Same output
No guessing
No partial data when query fails
```

### ✅ Professional UX

```
Consistent failure cards
Actionable guidance
Clear escalation path
Trust in the tool
```

---

## Files

- `QUERY_FAILURE_HANDLING.md` - Detailed implementation guide
- `FAILURE_TAXONOMY_COMPLETE.md` - This file (taxonomy overview)
- `FailureCode.java` - Enum with QUERY_INVALID
- `DeploymentDoctorService.java` - Query failure detection

**Build status:** ✅ Compiles successfully

**The Three Pillars:** Tooling + Platform + Application = Complete
