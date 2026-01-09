# Risk Signals UX Fixes - Production Polish

## 5 Critical UX Issues Fixed

These fixes transform the risk signals feature from "works" to "production-grade" by addressing credibility and clarity issues.

---

## ✅ Fix 1: Don't Label WARN as "PRIMARY ROOT CAUSE"

### Problem
The "Primary Root Cause" panel was showing WARN findings (POD_RESTARTS_DETECTED). This is **conceptually incorrect** and confuses users.

**Mental model**: "Root cause" implies a failure. Warnings are advisories, not root causes.

### Solution
Different panel labels based on severity:

| Overall Status | Severity | Panel Label |
|---------------|----------|-------------|
| FAIL | ERROR | 🎯 PRIMARY ROOT CAUSE |
| WARN | WARN | ⚠️ TOP WARNING |
| PASS (with warnings) | WARN | ⚠️ TOP WARNING |
| UNKNOWN | - | ❓ WHY NO DATA |
| PASS | - | (no panel shown) |

### Implementation

**Frontend** (`DeploymentDoctorPage.jsx`):
```jsx
<Typography variant="overline">
  {summary.primaryFailure.severity === 'ERROR' || summary.primaryFailure.severity === 'HIGH' 
    ? '🎯 PRIMARY ROOT CAUSE'           // For failures
    : summary.primaryFailure.severity === 'WARN' || summary.primaryFailure.severity === 'MED'
    ? '⚠️ TOP WARNING'                  // For risk signals
    : 'ℹ️ NOTABLE SIGNAL'}              // For info
</Typography>
```

### Before/After

**Before** (Confusing):
```
╔════════════════════════════════════╗
║ 🎯 PRIMARY ROOT CAUSE              ║  ← Wrong!
║                                    ║
║ ⚠️ Pod restarts detected           ║
╚════════════════════════════════════╝
```

**After** (Clear):
```
╔════════════════════════════════════╗
║ ⚠️ TOP WARNING                     ║  ← Correct!
║                                    ║
║ ⚠️ Pod restarts detected           ║
╚════════════════════════════════════╝
```

---

## ✅ Fix 2: Deduplicate Primary Panel from "Additional Findings"

### Problem
The same finding appeared twice:
1. In the primary panel (top)
2. In "Additional Findings" list (below)

This makes the page feel longer and less intentional.

### Solution
Filter out the primary failure from the "Additional Findings" section.

### Implementation

**Frontend** (`DeploymentDoctorPage.jsx`):
```jsx
{summary.findings
  .filter(f => !summary.primaryFailure || f.code !== summary.primaryFailure.code)  // ← Dedupe
  .map((f, idx) => (
    // Render finding...
  ))}
```

### Before/After

**Before** (Duplicate):
```
⚠️ TOP WARNING
   Pod restarts detected
   Pod: my-app-xyz (2 restarts)

Additional Findings:
⚠️ POD_RESTARTS_DETECTED        ← Duplicate!
   Pod: my-app-xyz (2 restarts)

⚠️ POD_SANDBOX_RECYCLE
   Sandbox changed
```

**After** (Clean):
```
⚠️ TOP WARNING
   Pod restarts detected
   Pod: my-app-xyz (2 restarts)

Additional Findings:
⚠️ POD_SANDBOX_RECYCLE          ← Only other findings
   Sandbox changed
```

---

## ✅ Fix 3: Fix Restart Count Mismatch

### Problem
Description said "restarted 1 time(s)" while evidence showed "2 restarts". This **hurts credibility**.

**Root cause**: Using `evidence.size()` (number of pods) instead of actual restart count.

### Solution
Track total restarts across all pods and use consistent numbers.

### Implementation

**Backend** (`DeploymentDoctorService.java`):
```java
private List<Finding> detectPodRestarts(List<PodInfo> pods) {
    List<Evidence> evidence = new ArrayList<>();
    int totalRestarts = 0;  // ← Track actual total

    for (PodInfo p : pods) {
        if ("Running".equalsIgnoreCase(p.phase()) && p.ready() && p.restarts() > 0) {
            totalRestarts += p.restarts();  // ← Sum actual restarts
            
            String evidenceMsg = p.restarts() + " restart" + 
                (p.restarts() > 1 ? "s" : "") + " (currently Ready)";
            
            evidence.add(new Evidence("Pod", p.name(), evidenceMsg));
        }
    }

    // Use totalRestarts, not evidence.size()
    String explanation;
    if (evidence.size() == 1) {
        explanation = "Pod has restarted " + totalRestarts + " time" + 
            (totalRestarts > 1 ? "s" : "") + " but is currently running.";
    } else {
        explanation = evidence.size() + " pods have restarted " + 
            totalRestarts + " total times but are currently running.";
    }
    
    return List.of(new Finding(
        FailureCode.POD_RESTARTS_DETECTED,
        "Pod restarts detected",
        explanation,  // ← Now accurate!
        evidence,
        nextSteps
    ));
}
```

### Before/After

**Before** (Mismatch):
```
Description: "Pod has restarted 1 time(s)"
Evidence: Pod my-app-xyz (2 restarts)     ← Numbers don't match!
```

**After** (Accurate):
```
Description: "Pod has restarted 2 times"
Evidence: Pod my-app-xyz (2 restarts)     ← Numbers match!
```

**Multiple pods**:
```
Description: "3 pods have restarted 7 total times"
Evidence: 
  • Pod my-app-1 (2 restarts)
  • Pod my-app-2 (3 restarts)
  • Pod my-app-3 (2 restarts)
```

---

## ✅ Fix 4: Add Termination Reason

### Problem
Evidence didn't show **why** the pod restarted (OOMKilled, Error, etc.). This reduces actionability.

### Solution
Include last termination reason in evidence message when available.

### Implementation

**Backend** (`DeploymentDoctorService.java`):
```java
String evidenceMsg = p.restarts() + " restart" + 
    (p.restarts() > 1 ? "s" : "") + " (currently Ready)";

// Add termination reason if available
if (p.reason() != null && !p.reason().isEmpty()) {
    evidenceMsg += " - Last reason: " + p.reason();  // ← Added!
}

evidence.add(new Evidence("Pod", p.name(), evidenceMsg));
```

### Before/After

**Before** (No reason):
```
📋 Evidence:
  • Pod: my-app-xyz
    2 restarts (currently Ready)
```

**After** (With reason):
```
📋 Evidence:
  • Pod: my-app-xyz
    2 restarts (currently Ready) - Last reason: OOMKilled
```

**Reasons shown**:
- `OOMKilled` → Out of memory, increase limits
- `Error` → Application error, check logs
- `Completed` → Normal exit, may be expected
- `CrashLoopBackOff` → Repeated crashes

**Actionability increase**: User immediately knows if it's OOM vs app error.

---

## ✅ Fix 5: Next Steps Enhancement

### Problem
Generic next steps didn't prioritize the most common issue (OOM).

### Solution
Updated next steps to mention OOM exit codes specifically.

### Implementation

**Backend** (`DeploymentDoctorService.java`):
```java
List.of(
    "Review pod logs for crash patterns: kubectl logs <pod> -n <namespace> --previous",
    "Check if restarts correlate with deployments or config changes.",
    "Verify readiness/liveness probe settings are appropriate for startup time.",
    "Look for OOM events (exit code 137): kubectl describe pod <pod> -n <namespace>",  // ← Specific!
    "Consider if restarts are expected (e.g., app restarts on config reload)."
)
```

**Before**: "Look for OOM events"  
**After**: "Look for OOM events (exit code 137)"

Small detail, but helps less experienced users.

---

## Summary of All Fixes

| # | Issue | Fix | Impact |
|---|-------|-----|--------|
| 1 | WARN labeled "PRIMARY ROOT CAUSE" | Use "TOP WARNING" for WARN | Conceptual clarity |
| 2 | Duplicate findings | Filter primary from additional | Cleaner UI |
| 3 | Restart count mismatch | Track totalRestarts accurately | Credibility |
| 4 | No termination reason | Add "Last reason: OOMKilled" | Actionability |
| 5 | Generic next steps | Mention exit code 137 | Helps new users |

---

## Complete Example: Before vs After

### Before (Issues)
```
╔════════════════════════════════════════════════════╗
║ 🎯 PRIMARY ROOT CAUSE                              ║  ← WRONG label
║                                                     ║
║ ⚠️ Pod restarts detected                           ║
║                                                     ║
║ Pod has restarted 1 time(s).                       ║  ← WRONG count
║                                                     ║
║ 📋 Evidence:                                       ║
║   • Pod: my-app-xyz                                 ║
║     2 restarts (currently Ready)                    ║  ← NO reason
╚════════════════════════════════════════════════════╝

Additional Findings:
⚠️ POD_RESTARTS_DETECTED                              ← DUPLICATE
   Pod has restarted 1 time(s).
   Evidence: Pod my-app-xyz (2 restarts)

⚠️ POD_SANDBOX_RECYCLE
   Sandbox changed
```

### After (Fixed)
```
╔════════════════════════════════════════════════════╗
║ ⚠️ TOP WARNING                                     ║  ✅ Correct label
║                                                     ║
║ ⚠️ Pod restarts detected                           ║
║                                                     ║
║ Pod has restarted 2 times but is currently running.║  ✅ Correct count
║                                                     ║
║ 📋 Evidence:                                       ║
║   • Pod: my-app-xyz                                 ║
║     2 restarts (currently Ready)                    ║
║     Last reason: OOMKilled                          ║  ✅ Shows reason
║                                                     ║
║ 💡 Next Steps:                                     ║
║   1. Review pod logs (--previous)                   ║
║   2. Look for OOM (exit code 137)                   ║  ✅ Specific
╚════════════════════════════════════════════════════╝

Additional Findings:
⚠️ POD_SANDBOX_RECYCLE                                ✅ No duplicate
   Sandbox changed
```

---

## Testing Verification

### Test Case 1: Single Pod, 2 Restarts, OOMKilled
**Input**:
- Pod: my-app-xyz
- Phase: Running, Ready: true
- Restarts: 2
- Last termination: OOMKilled

**Expected Output**:
```
⚠️ TOP WARNING
Pod restarts detected

Pod has restarted 2 times but is currently running.

📋 Evidence:
  • Pod: my-app-xyz
    2 restarts (currently Ready) - Last reason: OOMKilled

💡 Next Steps:
  1. Review pod logs...
  2. Look for OOM events (exit code 137)...
```

### Test Case 2: Multiple Pods, Mixed Restarts
**Input**:
- Pod 1: 3 restarts, Error
- Pod 2: 2 restarts, OOMKilled
- Pod 3: 1 restart, Completed

**Expected Output**:
```
⚠️ TOP WARNING
Pod restarts detected

3 pods have restarted 6 total times but are currently running.

📋 Evidence:
  • Pod: app-1 (3 restarts, currently Ready) - Last reason: Error
  • Pod: app-2 (2 restarts, currently Ready) - Last reason: OOMKilled
  • Pod: app-3 (1 restart, currently Ready) - Last reason: Completed
```

### Test Case 3: Failure (Not Warning)
**Input**:
- Pod: kv-app
- ERROR: EXTERNAL_SECRET_RESOLUTION_FAILED

**Expected Output**:
```
🎯 PRIMARY ROOT CAUSE                    ← Correct label for ERROR
External secret mount failed
```

---

## Build Status

```bash
✅ Backend compilation: SUCCESS
✅ Frontend build: SUCCESS
✅ No linter errors
✅ Backward compatible
```

---

## Impact on User Trust

### Issue 3 (Restart Count Mismatch)
**Before**: "Says 1 restart but shows 2? Can I trust this tool?"  
**After**: "Numbers match. This tool is credible."

### Issue 4 (No Termination Reason)
**Before**: "Why did it restart? Have to dig through logs."  
**After**: "OOMKilled! I know exactly what to fix."

### Issue 1 (Wrong Label)
**Before**: "'Root cause' but it's running fine? Confusing."  
**After**: "'Top warning' - ah, advisory signal. Makes sense."

### Issue 2 (Duplication)
**Before**: "Same thing shown twice? Looks unfinished."  
**After**: "Clean separation: top warning + other findings. Professional."

---

## Production Readiness

These fixes demonstrate **attention to detail** that separates:
- ❌ "Works on my machine" → ✅ "Production-grade"
- ❌ "Good enough" → ✅ "Trustworthy"
- ❌ "Functional" → ✅ "Professional"

**Result**: Platform Triage now meets the quality bar of enterprise diagnostic tools.

---

**Status**: ✅ All 5 issues fixed  
**Build**: ✅ Success  
**Quality**: ✅ Production-grade  
**Last Updated**: January 8, 2026
