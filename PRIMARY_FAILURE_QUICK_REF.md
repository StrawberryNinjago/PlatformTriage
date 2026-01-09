# Primary Failure Contract - Quick Reference

## 🎯 Core Contract Rules

### Rule 1: primaryFailure Semantics
```
primaryFailure is set ONLY when:
  ✅ overall == FAIL
  ✅ overall == UNKNOWN

primaryFailure is null when:
  ❌ overall == WARN
  ❌ overall == PASS
```

### Rule 2: Top Warning (UI-derived)
```
When overall == WARN:
  - primaryFailure = null
  - UI finds: first finding with severity == WARN
```

### Rule 3: UNKNOWN Short-Circuit
```
If no pods AND no deployments:
  - overall = UNKNOWN
  - primaryFailure = NO_MATCHING_OBJECTS
  - return immediately (skip detection)
```

---

## 📊 State Matrix

| Overall | Primary Failure | Meaning | UI Color |
|---------|-----------------|---------|----------|
| PASS | null | Everything healthy | Green |
| WARN | null | Advisory/risk signal | Yellow |
| FAIL | Set (ERROR) | Critical failure | Red |
| UNKNOWN | Set (WARN) | Can't assess | Gray |

---

## 🔍 Backend Implementation

### Detection Flow (DeploymentDoctorService)

```java
public DeploymentSummaryResponse getSummary(...) {
  // 1. Fetch objects
  List<V1Pod> pods = listPods(...);
  Map<String, V1Deployment> deployments = listDeploymentsBySelector(...);
  
  // 2. SHORT-CIRCUIT: If nothing found, return UNKNOWN immediately
  if (pods.isEmpty() && deployments.isEmpty()) {
    return new DeploymentSummaryResponse(
      overall: UNKNOWN,
      primaryFailure: NO_MATCHING_OBJECTS finding,
      findings: [NO_MATCHING_OBJECTS],
      objects: empty
    );
  }
  
  // 3. Fetch events, services, endpoints (expensive)
  // 4. Run all detection rules
  // 5. Normalize findings
  // 6. Compute overall status
  // 7. Select primary failure BASED ON OVERALL
  
  OverallStatus overall = computeOverall(findings);
  Finding primaryFailure = selectPrimaryFailure(findings, overall);
  
  return new DeploymentSummaryResponse(...);
}
```

### Primary Failure Selection

```java
private Finding selectPrimaryFailure(List<Finding> findings, OverallStatus overall) {
  // CONTRACT ENFORCEMENT
  if (overall != FAIL && overall != UNKNOWN) {
    return null;  // ⭐ KEY FIX
  }
  
  // Return highest priority finding
  return findings.stream()
    .min(Comparator.comparingInt(Finding::getPriority))
    .orElse(null);
}
```

### Overall Status Computation

```java
private OverallStatus computeOverall(List<Finding> findings) {
  // 1. NO_MATCHING_OBJECTS → UNKNOWN
  if (findings has NO_MATCHING_OBJECTS) {
    return UNKNOWN;
  }
  
  // 2. ERROR/HIGH severity → FAIL
  if (findings has ERROR or HIGH) {
    return FAIL;
  }
  
  // 3. WARN/MED severity → WARN
  if (findings has WARN or MED) {  // ⭐ Fixed to include WARN
    return WARN;
  }
  
  // 4. Otherwise → PASS
  return PASS;
}
```

---

## 🎨 UI Implementation

### React/TypeScript Example

```typescript
interface DeploymentSummaryResponse {
  health: {
    overall: 'PASS' | 'WARN' | 'FAIL' | 'UNKNOWN';
    deploymentsReady: string;
  };
  findings: Finding[];
  primaryFailure: Finding | null;  // ⭐ Can be null!
}

function renderHealthStatus(response: DeploymentSummaryResponse) {
  const { health, findings, primaryFailure } = response;
  
  // Handle FAIL state
  if (health.overall === 'FAIL' && primaryFailure) {
    return (
      <FailureCard
        color="red"
        title={primaryFailure.title}
        explanation={primaryFailure.explanation}
        evidence={primaryFailure.evidence}
        nextSteps={primaryFailure.nextSteps}
      />
    );
  }
  
  // Handle UNKNOWN state
  if (health.overall === 'UNKNOWN' && primaryFailure) {
    return (
      <UnknownCard
        color="gray"
        title={primaryFailure.title}
        explanation={primaryFailure.explanation}
        nextSteps={primaryFailure.nextSteps}
      />
    );
  }
  
  // Handle WARN state
  if (health.overall === 'WARN') {
    const topWarning = findings.find(f => f.severity === 'WARN');
    return (
      <WarningCard
        color="yellow"
        title={topWarning?.title || 'Warnings detected'}
        explanation={topWarning?.explanation}
        findings={findings.filter(f => f.severity === 'WARN')}
      />
    );
  }
  
  // Handle PASS state
  return <HealthyCard color="green" />;
}
```

---

## 🧪 Testing Scenarios

### 1. Test WARN State (primaryFailure = null)

**Setup:**
- Deploy app with pods that have restarted but are now healthy
- Pods: Running + Ready, restarts > 0

**Expected Response:**
```json
{
  "health": { "overall": "WARN" },
  "findings": [
    { "code": "POD_RESTARTS_DETECTED", "severity": "WARN" }
  ],
  "primaryFailure": null
}
```

**Test Command:**
```bash
curl "localhost:8080/api/deployment-doctor/summary?namespace=test&release=healthy-app"
```

---

### 2. Test UNKNOWN State (short-circuit)

**Setup:**
- Use wrong selector or non-existent release
- No pods or deployments match

**Expected Response:**
```json
{
  "health": { "overall": "UNKNOWN" },
  "findings": [
    { "code": "NO_MATCHING_OBJECTS", "severity": "WARN" }
  ],
  "primaryFailure": {
    "code": "NO_MATCHING_OBJECTS",
    "severity": "WARN"
  }
}
```

**Test Command:**
```bash
curl "localhost:8080/api/deployment-doctor/summary?namespace=test&selector=wrong-label"
```

---

### 3. Test FAIL State (primaryFailure set)

**Setup:**
- Deploy broken app (e.g., CrashLoopBackOff)
- Pods: CrashLoopBackOff or ImagePullBackOff

**Expected Response:**
```json
{
  "health": { "overall": "FAIL" },
  "findings": [
    { "code": "CRASH_LOOP", "severity": "ERROR" }
  ],
  "primaryFailure": {
    "code": "CRASH_LOOP",
    "severity": "ERROR"
  }
}
```

**Test Command:**
```bash
curl "localhost:8080/api/deployment-doctor/summary?namespace=test&release=broken-app"
```

---

## 🚨 Common Mistakes to Avoid

### ❌ Mistake 1: Checking only primaryFailure
```typescript
// WRONG: Misses WARN and PASS states
if (response.primaryFailure) {
  showRedCard();
} else {
  showGreenCard();  // ❌ What about WARN?
}
```

### ✅ Correct: Check overall first
```typescript
// RIGHT: Handle all four states
switch (response.health.overall) {
  case 'FAIL':
    showRedCard(response.primaryFailure);
    break;
  case 'UNKNOWN':
    showGrayCard(response.primaryFailure);
    break;
  case 'WARN':
    const topWarning = response.findings.find(f => f.severity === 'WARN');
    showYellowCard(topWarning);
    break;
  case 'PASS':
    showGreenCard();
    break;
}
```

---

### ❌ Mistake 2: Assuming primaryFailure is always ERROR
```typescript
// WRONG: primaryFailure can be WARN (for UNKNOWN state)
if (response.primaryFailure.severity === 'ERROR') {
  // ❌ Misses NO_MATCHING_OBJECTS (WARN severity)
}
```

### ✅ Correct: Check overall state
```typescript
// RIGHT: Use overall to determine failure type
if (response.health.overall === 'FAIL') {
  // primaryFailure is ERROR severity
} else if (response.health.overall === 'UNKNOWN') {
  // primaryFailure is WARN severity (NO_MATCHING_OBJECTS)
}
```

---

### ❌ Mistake 3: Not handling null primaryFailure
```typescript
// WRONG: Crashes when primaryFailure is null
<FailureCard title={response.primaryFailure.title} />
```

### ✅ Correct: Null-safe handling
```typescript
// RIGHT: Check for null
{response.primaryFailure && (
  <FailureCard title={response.primaryFailure.title} />
)}
```

---

## 📚 Related Documents

- **PRIMARY_FAILURE_CONTRACT_FIX.md** - Detailed explanation of all three fixes
- **PRIMARY_FAILURE_BEFORE_AFTER.md** - Visual before/after comparison
- **PLATFORM_FAILURE_TAXONOMY.md** - Complete taxonomy reference

---

## ⚡ TL;DR

**Backend:** `primaryFailure` is null unless `overall == FAIL` or `overall == UNKNOWN`

**UI:** Check `overall` first, then handle primaryFailure or derive top warning accordingly

**Key Fix:** Warnings no longer trigger scary red failure cards! 🎉
