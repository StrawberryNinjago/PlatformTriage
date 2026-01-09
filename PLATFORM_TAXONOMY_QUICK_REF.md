# Platform Failure Taxonomy - Quick Reference

## 8 Failure Codes (Priority Order)

| Priority | Code | Owner | Severity | What It Means |
|----------|------|-------|----------|---------------|
| **1** | `EXTERNAL_SECRET_RESOLUTION_FAILED` | PLATFORM | ERROR | Cannot mount external secrets (Key Vault/CSI) |
| **2** | `BAD_CONFIG` | APP | ERROR | Missing/invalid K8s config (Secret/ConfigMap) |
| **3** | `IMAGE_PULL_FAILED` | PLATFORM | ERROR | Image pull error (auth/tag/registry) |
| **4** | `INSUFFICIENT_RESOURCES` | PLATFORM | ERROR | Scheduling blocked (CPU/memory/quotas) |
| **5** | `RBAC_DENIED` | SECURITY | ERROR | Kubernetes RBAC permission denied |
| **6** | `CRASH_LOOP` | APP | ERROR | Container repeatedly crashes |
| **7** | `READINESS_CHECK_FAILED` | APP | ERROR | Pod runs but never Ready |
| **8** | `SERVICE_SELECTOR_MISMATCH` | APP | WARN | Service has 0 endpoints |

---

## Quick Detection Cheat Sheet

### 🔴 EXTERNAL_SECRET_RESOLUTION_FAILED (Priority 1)
**Look for:**
- Events: `FailedMount` with "secrets-store.csi", "SecretProviderClass", "keyvault"
- Pods: `CreateContainerConfigError` + CSI errors

**First check:**
```bash
kubectl get secretproviderclass -n <namespace>
kubectl describe pod <pod> | grep -A10 "Events:"
```

---

### 🔴 BAD_CONFIG (Priority 2)
**Look for:**
- Pod reason: `CreateContainerConfigError`
- Events: "secret not found", "configmap not found" (NOT CSI-related)

**First check:**
```bash
kubectl get secrets,configmaps -n <namespace>
kubectl describe pod <pod>
```

---

### 🔴 IMAGE_PULL_FAILED (Priority 3)
**Look for:**
- Pod reason: `ImagePullBackOff`, `ErrImagePull`
- Events: "manifest unknown", "unauthorized"

**First check:**
```bash
kubectl describe pod <pod> | grep "Image:"
kubectl get pods <pod> -o jsonpath='{.spec.imagePullSecrets}'
```

---

### 🔴 INSUFFICIENT_RESOURCES (Priority 4)
**Look for:**
- Pod phase: `Pending`
- Events: "Insufficient cpu", "Insufficient memory", "Unschedulable"

**First check:**
```bash
kubectl describe nodes
kubectl get resourcequotas -n <namespace>
```

---

### 🔴 RBAC_DENIED (Priority 5)
**Look for:**
- Events: "Forbidden", "RBAC", "permission denied"

**First check:**
```bash
kubectl auth can-i list pods --as=system:serviceaccount:<ns>:<sa>
kubectl get rolebindings,clusterrolebindings -n <namespace>
```

---

### 🔴 CRASH_LOOP (Priority 6)
**Look for:**
- Pod reason: `CrashLoopBackOff`
- Events: "Back-off restarting failed container"
- Restarts > 0

**First check:**
```bash
kubectl logs <pod> --previous
kubectl describe pod <pod> | grep "Last State"
```

---

### 🔴 READINESS_CHECK_FAILED (Priority 7)
**Look for:**
- Pod phase: `Running`, Ready: `False`
- Events: "Readiness probe failed"

**First check:**
```bash
kubectl describe pod <pod> | grep -A5 "Readiness:"
kubectl logs <pod>
```

---

### 🟡 SERVICE_SELECTOR_MISMATCH (Priority 8)
**Look for:**
- Service exists but endpoints = 0
- Pods exist but don't match service selector

**First check:**
```bash
kubectl describe service <service>
kubectl get pods --show-labels
kubectl get endpoints <service>
```

---

## API Response Structure

### Primary Failure Field
```json
{
  "primaryFailure": {
    "code": "EXTERNAL_SECRET_RESOLUTION_FAILED",
    "severity": "ERROR",
    "owner": "PLATFORM",
    "title": "External secret mount failed (CSI / Key Vault)",
    "explanation": "Pod cannot mount external secrets via SecretProviderClass",
    "evidence": [
      { "kind": "Pod", "name": "my-app-xyz" },
      { "kind": "Event", "name": "FailedMount", "message": "..." }
    ],
    "nextSteps": [
      "Confirm SecretProviderClass exists...",
      "Verify Key Vault permissions..."
    ]
  }
}
```

---

## Owner Routing

| Owner | Team | Typical Issues |
|-------|------|----------------|
| **APP** | Application Team | Config errors, crashes, readiness, selectors |
| **PLATFORM** | Platform/DevOps | Secrets, images, resources, infrastructure |
| **SECURITY** | Security Team | RBAC, permissions, policies |
| **UNKNOWN** | Unclear | Needs investigation |

---

## Decision Tree

```
Pod won't start?
├─ Events mention "secrets-store.csi" or "Key Vault"?
│  └─ YES → EXTERNAL_SECRET_RESOLUTION_FAILED (Platform)
├─ Events mention "secret not found" (non-CSI)?
│  └─ YES → BAD_CONFIG (App)
├─ ImagePullBackOff?
│  └─ YES → IMAGE_PULL_FAILED (Platform)
├─ Pod Pending + "Insufficient resources"?
│  └─ YES → INSUFFICIENT_RESOURCES (Platform)
├─ Events mention "Forbidden" or "RBAC"?
│  └─ YES → RBAC_DENIED (Security)
├─ CrashLoopBackOff?
│  └─ YES → CRASH_LOOP (App)
├─ Running but not Ready?
│  └─ YES → READINESS_CHECK_FAILED (App)
└─ Service has 0 endpoints?
   └─ YES → SERVICE_SELECTOR_MISMATCH (App)
```

---

## Overall Status Values

The `health.overall` field can be:

| Status | Meaning | When It Happens |
|--------|---------|-----------------|
| **PASS** | Healthy | All checks passed, no issues found |
| **WARN** | Issues | Non-critical warnings detected |
| **FAIL** | Failed | Critical errors detected |
| **UNKNOWN** | Cannot assess | No matching objects found (typo in selector, wrong namespace, etc.) |

**Important**: `UNKNOWN` is NOT the same as `PASS`. It means we couldn't find anything to check, so health cannot be determined.

---

## Testing Commands

### Test Platform Triage API
```bash
# Check deployment
curl "http://localhost:8082/api/deployment/summary?namespace=default&selector=app=my-app" | jq

# View overall status
curl "http://localhost:8082/api/deployment/summary?namespace=default&selector=app=my-app" \
  | jq '.health.overall'

# View primary failure only
curl "http://localhost:8082/api/deployment/summary?namespace=default&selector=app=my-app" \
  | jq '.primaryFailure'

# View all findings
curl "http://localhost:8082/api/deployment/summary?namespace=default&selector=app=my-app" \
  | jq '.findings[]'
```

### Deploy Test Cases
```bash
# Test BAD_CONFIG
kubectl apply -f apps/platformtriage/chart/templates/bad.yaml

# Test EXTERNAL_SECRET_RESOLUTION_FAILED
kubectl apply -f apps/platformtriage/chart/templates/kv-misconfig-app.yaml

# Test RBAC_DENIED
kubectl apply -f apps/platformtriage/chart/templates/rbac.yaml
```

---

## Integration Checklist

### Backend ✅
- [x] FailureCode enum with 8 codes + priority
- [x] Owner enum (APP, PLATFORM, SECURITY, UNKNOWN)
- [x] Updated Severity (ERROR, WARN, INFO)
- [x] Evidence DTO for structured evidence
- [x] Updated Finding with new contract
- [x] Detection methods for all 8 codes
- [x] Primary failure selection logic
- [x] Updated DeploymentSummaryResponse with primaryFailure

### Frontend TODO
- [ ] Display primaryFailure prominently
- [ ] Add owner badges (colored by team)
- [ ] Show nextSteps as expandable items
- [ ] Display structured evidence
- [ ] Filter findings by owner
- [ ] Color-code severity (ERROR=red, WARN=yellow, INFO=blue)

### Documentation ✅
- [x] PLATFORM_FAILURE_TAXONOMY.md (comprehensive guide)
- [x] PLATFORM_TAXONOMY_QUICK_REF.md (this file)

---

## Common Scenarios

### Scenario 1: Key Vault Mount Failure
**Detection**: `EXTERNAL_SECRET_RESOLUTION_FAILED`  
**Owner**: Platform  
**Quick Fix**: Check SecretProviderClass, verify Key Vault permissions

### Scenario 2: Missing Secret
**Detection**: `BAD_CONFIG`  
**Owner**: App  
**Quick Fix**: Create the secret or fix reference in pod spec

### Scenario 3: Private Registry Image
**Detection**: `IMAGE_PULL_FAILED`  
**Owner**: Platform  
**Quick Fix**: Configure imagePullSecrets or verify registry auth

### Scenario 4: App Dependency Down
**Detection**: `READINESS_CHECK_FAILED`  
**Owner**: App  
**Quick Fix**: Check DB/Redis connectivity, verify probe config

### Scenario 5: Memory Limit Too Low
**Detection**: `CRASH_LOOP` (OOMKilled)  
**Owner**: App (sometimes Platform)  
**Quick Fix**: Increase memory limits or optimize app

### Scenario 6: Label Typo
**Detection**: `SERVICE_SELECTOR_MISMATCH`  
**Owner**: App  
**Quick Fix**: Fix label/selector mismatch in service or deployment

---

## Priority Explanation

**Why EXTERNAL_SECRET_RESOLUTION_FAILED is #1:**
- Hard blocker: Container cannot start at all
- Platform-owned: Requires infrastructure access
- Common in AKS: Key Vault integration is complex

**Why SERVICE_SELECTOR_MISMATCH is #8:**
- Warning level: Traffic issue but pods may be running
- Easy to fix: Usually a label typo
- Less critical: Doesn't block pod startup

**Crash vs Readiness:**
- CRASH_LOOP (#6): Container exits, more critical
- READINESS_CHECK_FAILED (#7): Container runs but unhealthy

---

## Quick Troubleshooting Tips

### Unknown Failure?
1. Check pod phase: `kubectl get pod <pod> -o jsonpath='{.status.phase}'`
2. Check container state: `kubectl get pod <pod> -o jsonpath='{.status.containerStatuses[0].state}'`
3. Check events: `kubectl get events --sort-by='.lastTimestamp' | tail -20`

### Multiple Findings?
- Use `primaryFailure` field for triage decision
- Fix highest priority issue first
- Lower priority issues may resolve automatically

### False Positive?
- Check evidence objects to verify detection
- Review event messages for context
- Consult nextSteps for validation

---

## References

- Full Documentation: `PLATFORM_FAILURE_TAXONOMY.md`
- Implementation: `apps/platformtriage/src/main/java/com/example/platformtriage/`
- Test Resources: `apps/platformtriage/chart/templates/`

---

**Last Updated**: 2026-01-08  
**Version**: MVP (8 codes)  
**Status**: ✅ Production Ready

