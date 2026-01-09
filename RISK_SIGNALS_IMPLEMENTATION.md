# Risk Signals vs Failures - Implementation

## The Missing Concept

**Problem**: Platform Triage only detected failures (ERROR → FAIL). This misses a critical layer: **risk signals** that deserve attention but don't block deployment.

**Solution**: Added **advisory findings** (WARN severity) that keep Overall = PASS but flag concerns.

This is exactly what makes DBTriage mature.

---

## The Two Classes

### 1. Failures (ERROR severity)
- **Outcome**: Overall = FAIL
- **Meaning**: Something is broken, deployment blocked
- **Examples**: 
  - BAD_CONFIG
  - IMAGE_PULL_FAILED
  - CRASH_LOOP

### 2. Risk Signals (WARN severity)
- **Outcome**: Overall = PASS (with warnings)
- **Meaning**: Something deserves attention, but deployment works
- **Examples**:
  - POD_RESTARTS_DETECTED
  - POD_SANDBOX_RECYCLE

---

## What Platform Triage Can Judge

### ✅ New Risk Signals (WARN)

#### 1. POD_RESTARTS_DETECTED

**What it is**: Pod is Running + Ready, but has restarts > 0

**Why it matters**: Indicates transient crashes, config reloads, or unstable startup

**Detection Rule**:
```
IF pod.status.phase == Running
AND pod.ready == true
AND restartCount > 0
→ WARN: POD_RESTARTS_DETECTED
```

**Example Evidence**:
```
Pod: my-app-xyz
Restarts: 3 (currently Ready)
```

**Next Steps**:
1. Review pod logs for crash patterns: `kubectl logs <pod> --previous`
2. Check if restarts correlate with deployments or config changes
3. Verify readiness/liveness probe settings are appropriate
4. Look for OOM events: `kubectl describe pod <pod>`
5. Consider if restarts are expected (e.g., app restarts on config reload)

---

#### 2. POD_SANDBOX_RECYCLE

**What it is**: SandboxChanged events (pod sandbox changed, will be killed and re-created)

**Why it matters**: Strong early smell of instability - node issues, runtime problems, or network changes

**Detection Rule**:
```
IF Event.reason == "SandboxChanged"
→ WARN: POD_SANDBOX_RECYCLE
```

**Example Evidence**:
```
Event: SandboxChanged
Pod sandbox changed, it will be killed and re-created.
```

**Next Steps**:
1. Check node health: `kubectl describe node <node>`
2. Review container runtime logs on the node
3. Check for network policy or CNI changes
4. Look for node resource pressure or eviction events
5. Verify pod security policies or admission webhooks

---

### 🔮 Future Risk Signals (MVP+)

#### 3. DEPLOYMENT_STILL_PROGRESSING
**Trigger**: Progressing=True for > X minutes  
**Why**: Rollout taking too long may indicate issues

#### 4. HIGH_MEMORY_USAGE
**Trigger**: Container memory usage > 80% of limit  
**Why**: May lead to OOM soon

#### 5. EVICTED_PODS
**Trigger**: Recent eviction events  
**Why**: Resource pressure or node issues

---

## Severity Rules

| Severity | Overall Impact | Meaning | Use When |
|----------|---------------|---------|----------|
| **ERROR** | FAIL | Blocking failure | Something is broken |
| **WARN** | PASS (with warnings) | Advisory signal | Something needs attention |
| **INFO** | PASS | Informational | Just FYI |
| **PASS** | PASS | Healthy | All good |

### Critical Design Decision

**WARN findings do NOT fail overall**. This is intentional:
- Preserves trust (deployment actually works)
- Flags concerns (but doesn't block)
- Matches mature platform behavior

---

## Backend Implementation

### FailureCode Enum (Updated)

```java
public enum FailureCode {
    // ... 8 failure codes (ERROR) ...
    
    /**
     * Risk signals / Advisory findings (WARN severity - do not fail overall)
     */
    POD_RESTARTS_DETECTED(Owner.APP, Severity.WARN),
    POD_SANDBOX_RECYCLE(Owner.PLATFORM, Severity.WARN),
    
    // ...
}
```

### Detection Methods

#### detectPodRestarts()
```java
private List<Finding> detectPodRestarts(List<PodInfo> pods) {
    List<Evidence> evidence = new ArrayList<>();
    
    // Pattern: Pod is Running AND Ready, but has restarted
    pods.stream()
        .filter(p -> "Running".equalsIgnoreCase(p.phase()))
        .filter(PodInfo::ready)
        .filter(p -> p.restarts() > 0)
        .forEach(p -> evidence.add(new Evidence("Pod", p.name(), 
            "Restarts: " + p.restarts() + " (currently Ready)")));
    
    if (evidence.isEmpty()) {
        return List.of();
    }
    
    return List.of(new Finding(
        FailureCode.POD_RESTARTS_DETECTED,
        "Pod restarts detected",
        "Pod has restarted but is currently running. " +
        "This may indicate transient crashes, config reloads, or unstable startup.",
        evidence,
        List.of(/* next steps */)
    ));
}
```

#### detectPodSandboxRecycle()
```java
private List<Finding> detectPodSandboxRecycle(List<EventInfo> events) {
    List<Evidence> evidence = new ArrayList<>();
    
    // Pattern: SandboxChanged events
    events.stream()
        .filter(e -> "SandboxChanged".equalsIgnoreCase(e.reason()))
        .forEach(e -> evidence.add(new Evidence("Event", e.involvedObjectName(), e.message())));
    
    if (evidence.isEmpty()) {
        return List.of();
    }
    
    return List.of(new Finding(
        FailureCode.POD_SANDBOX_RECYCLE,
        "Pod sandbox recycled",
        "Pod sandbox changed and pod will be killed and re-created.",
        evidence,
        List.of(/* next steps */)
    ));
}
```

### Overall Status Computation (Updated)

**Key Change**: WARN findings do NOT make overall = FAIL

```java
private OverallStatus computeOverall(List<Finding> findings) {
    // Check for NO_MATCHING_OBJECTS first
    if (findings.stream().anyMatch(f -> f.code() == FailureCode.NO_MATCHING_OBJECTS)) {
        return OverallStatus.UNKNOWN;
    }
    
    // Check for ERROR or legacy HIGH severity
    // These are actual failures that should fail overall
    boolean hasError = findings.stream().anyMatch(f ->
        f.severity() == Severity.ERROR || f.severity() == Severity.HIGH);
    if (hasError) {
        return OverallStatus.FAIL;
    }

    // IMPORTANT: WARN findings do NOT fail overall
    // They are risk signals / advisory findings
    // Overall stays PASS (or becomes WARN for legacy MED)
    boolean hasLegacyMed = findings.stream().anyMatch(f -> f.severity() == Severity.MED);
    if (hasLegacyMed) {
        return OverallStatus.WARN;
    }

    return OverallStatus.PASS;
}
```

### Priority (Risk Signals Don't Become Primary)

Risk signals have low priority (50+) so they never become the primary failure:

```java
public int getPriority() {
    return switch (this) {
        case EXTERNAL_SECRET_RESOLUTION_FAILED -> 1;
        case BAD_CONFIG -> 2;
        // ... 8 failure codes (1-10) ...
        
        // Risk signals - low priority
        case POD_RESTARTS_DETECTED -> 50;
        case POD_SANDBOX_RECYCLE -> 51;
        case NO_MATCHING_OBJECTS -> 99;
    };
}
```

---

## Frontend Implementation

### UI Behavior: "PASS (with warnings)"

**Key Change**: Show warnings even when Overall = PASS

```jsx
const getHealthFromSummary = () => {
  // Check if there are WARN severity findings
  const hasWarnings = summary.findings?.some(f => 
    f.severity === 'WARN' || f.severity === 'MED'
  ) || false;
  
  return {
    overall: summary.health.overall,
    hasWarnings: hasWarnings
  };
};
```

### Overall Status Display

```jsx
<Chip
  label={
    getHealthFromSummary().overall === 'PASS' && getHealthFromSummary().hasWarnings
      ? 'PASS (with warnings)'  // ← Subtle but powerful
      : getHealthFromSummary().overall
  }
  color={
    getHealthFromSummary().overall === 'FAIL' ? 'error' : 
    getHealthFromSummary().overall === 'WARN' ? 'warning' :
    getHealthFromSummary().hasWarnings ? 'warning' :  // PASS with warnings → yellow
    'success'
  }
  icon={
    getHealthFromSummary().hasWarnings ? <WarningIcon /> :
    <CheckCircleIcon />
  }
/>
```

### Findings Section

```jsx
<Typography variant="h6" sx={{ mb: 2, display: 'flex', alignItems: 'center', gap: 1 }}>
  Findings
  {getHealthFromSummary().overall === 'PASS' && getHealthFromSummary().hasWarnings && (
    <Chip 
      label="Advisory" 
      size="small" 
      color="warning" 
      variant="outlined"
    />
  )}
</Typography>
```

---

## Example Scenarios

### Scenario 1: kv-misconfig-app (Failure)

**State**:
- Pod: Pending, CreateContainerConfigError
- Event: FailedMount, secret not found

**Detection**:
- BAD_CONFIG (ERROR)
- EXTERNAL_SECRET_RESOLUTION_FAILED (ERROR)

**Result**:
```
Overall: FAIL ❌
Primary Root Cause: EXTERNAL_SECRET_RESOLUTION_FAILED
Additional Findings: BAD_CONFIG
```

---

### Scenario 2: cart-app (Healthy)

**State**:
- Deployment: 1/1 ready
- Pods: Running, Ready, 0 restarts

**Detection**:
- No findings

**Result**:
```
Overall: PASS ✅
Findings: (none)
```

---

### Scenario 3: bad-app (PASS with Warnings) ⭐ NEW

**State**:
- Deployment: 1/1 ready
- Pods: Running, Ready, **3 restarts**
- Event: SandboxChanged

**Detection**:
- POD_RESTARTS_DETECTED (WARN)
- POD_SANDBOX_RECYCLE (WARN)

**Result**:
```
Overall: PASS (with warnings) ⚠️
Findings: [Advisory]
  ⚠️ POD_RESTARTS_DETECTED [Owner: Application]
     Pod has restarted 3 times but is currently running.
     
     📋 Evidence:
       • Pod: bad-app-xyz (Restarts: 3, currently Ready)
     
     💡 Next Steps:
       1. Review pod logs for crash patterns
       2. Check if restarts correlate with deployments
       3. Verify probe settings
  
  ⚠️ POD_SANDBOX_RECYCLE [Owner: Platform]
     Pod sandbox changed, will be killed and re-created.
     
     📋 Evidence:
       • Event: SandboxChanged
     
     💡 Next Steps:
       1. Check node health
       2. Review container runtime logs
```

**User behavior**: Deployment succeeds, but engineer investigates warnings.

---

## Visual Comparison

### Before (No Risk Detection)
```
╔════════════════════════════════╗
║ Overall: PASS ✅               ║
║ Deployments: 1/1               ║
╚════════════════════════════════╝

Pods:
• bad-app-xyz (Running, Ready)
  Restarts: 3                    ← Hidden problem!

Events:
• Normal / SandboxChanged        ← Hidden problem!
  Pod sandbox changed...

Findings: (none)                 ← Missed signals!
```

### After (Risk Detection)
```
╔════════════════════════════════╗
║ Overall: PASS (with warnings)⚠️║
║ Deployments: 1/1               ║
╚════════════════════════════════╝

Findings: [Advisory]
⚠️ POD_RESTARTS_DETECTED [Owner: Application]
   Pod has restarted 3 times but is currently running.
   
   📋 Evidence:
     • Pod: bad-app-xyz (Restarts: 3)
   
   💡 Next Steps:
     1. Review pod logs for crash patterns
     2. Check if restarts correlate with deployments

⚠️ POD_SANDBOX_RECYCLE [Owner: Platform]
   Pod sandbox changed, will be killed and re-created.
   
   📋 Evidence:
     • Event: SandboxChanged
   
   💡 Next Steps:
     1. Check node health
     2. Review container runtime logs
```

---

## Why This Design is Right

### 1. Preserves Trust
- **Before**: Saying FAIL for restarts would be dishonest (pod IS running)
- **After**: PASS (with warnings) is accurate

### 2. Matches Mature Platforms
- DBTriage does this
- GitHub Actions does this (warnings don't fail builds)
- CI/CD systems do this (lint warnings vs errors)

### 3. Enables Proactive Monitoring
- Engineers can investigate warnings before they become failures
- Platform teams can spot trends (many pods restarting?)
- Leadership sees both "it works" AND "here are concerns"

### 4. Doesn't Block Operations
- Deployments succeed
- Pipelines continue
- But concerns are visible

---

## Decision Matrix

| Finding | Severity | Overall | Primary Failure? | Blocks Deployment? |
|---------|----------|---------|------------------|---------------------|
| EXTERNAL_SECRET_RESOLUTION_FAILED | ERROR | FAIL | Yes (Priority 1) | Yes |
| BAD_CONFIG | ERROR | FAIL | Yes (Priority 2) | Yes |
| CRASH_LOOP | ERROR | FAIL | Yes (Priority 6) | Yes |
| POD_RESTARTS_DETECTED | WARN | PASS | No (Priority 50) | No |
| POD_SANDBOX_RECYCLE | WARN | PASS | No (Priority 51) | No |
| SERVICE_SELECTOR_MISMATCH | WARN | PASS | Yes (Priority 8) | No |

---

## Sanity Check

### Test Case 1: kv-misconfig-app
**Expected**: FAIL + Primary Root Cause  
**Actual**: ✅ FAIL + EXTERNAL_SECRET_RESOLUTION_FAILED

### Test Case 2: cart-app
**Expected**: PASS, no findings  
**Actual**: ✅ PASS, no findings

### Test Case 3: bad-app (NEW)
**Expected**: PASS + WARN findings  
**Actual**: ✅ PASS (with warnings) + POD_RESTARTS_DETECTED + POD_SANDBOX_RECYCLE

---

## Testing Checklist

### Backend
- [x] POD_RESTARTS_DETECTED detects Running + Ready + restarts > 0
- [x] POD_SANDBOX_RECYCLE detects SandboxChanged events
- [x] WARN findings do NOT make overall = FAIL
- [x] WARN findings have low priority (don't become primary)
- [x] Compilation succeeds

### Frontend
- [x] "PASS (with warnings)" displays when appropriate
- [x] Warning icon shows for PASS with warnings
- [x] Findings section shows [Advisory] badge
- [x] WARN findings render with yellow warning icon
- [x] Build succeeds

---

## Future Enhancements

### Additional Risk Signals
1. **DEPLOYMENT_STILL_PROGRESSING** - Rollout taking too long
2. **HIGH_MEMORY_USAGE** - Container using > 80% of memory limit
3. **EVICTED_PODS** - Recent pod evictions
4. **ORPHANED_REPLICA_SETS** - Old ReplicaSets not cleaned up
5. **MISSING_RESOURCE_LIMITS** - Containers without limits
6. **IMAGE_PULL_POLICY_ALWAYS** - May cause registry throttling

### Severity Tuning
Allow users to configure:
- Which signals are WARN vs ERROR
- Thresholds (e.g., "restarts > 5 → ERROR")
- Suppression rules (e.g., "ignore restarts for <app>")

---

## Summary

### Before
- ✅ Detected failures (ERROR → FAIL)
- ❌ Missed risk signals
- ❌ Binary: PASS or FAIL

### After
- ✅ Detected failures (ERROR → FAIL)
- ✅ Detected risk signals (WARN → PASS with warnings)
- ✅ Nuanced: PASS, PASS (with warnings), FAIL

**Result**: Platform Triage now behaves like a mature platform diagnostic tool, matching DBTriage's sophistication.

---

**Status**: ✅ Implemented  
**Build**: ✅ Success (backend + frontend)  
**Maturity**: ✅ Production-grade  
**Last Updated**: January 8, 2026
