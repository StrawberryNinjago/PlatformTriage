# Platform Failure Taxonomy (MVP)

## Overview

The Platform Failure Taxonomy is a minimal but platform-grade classification system for Kubernetes deployment failures. It provides 8 mutually exclusive failure codes that are:

- **Owner-routable**: Clear escalation path to the appropriate team
- **Evidence-driven**: Every finding points to specific pods/events/deployments
- **Composable**: Multiple findings can be detected per run
- **Priority-based**: Automatic primary failure selection for quick triage

This taxonomy makes Platform Triage feel "as solid as DBTriage" by providing consistent, actionable failure classifications.

---

## The 8 Failure Codes

### 1. BAD_CONFIG
**What it means**: Pod cannot start due to missing or invalid Kubernetes configuration (Secret/ConfigMap/env/volume refs).

**Owner**: App Team  
**Severity**: ERROR  
**Priority**: 2 (high)

**Detection Triggers**:
- Pod container state waiting reason: `CreateContainerConfigError`
- Event contains: "secret not found", "configmap not found", "MountVolume.SetUp failed" (non-CSI)

**Next Steps**:
- Verify referenced Secret/ConfigMap exists in namespace
- Verify key names match the keys referenced in pod spec
- Check volumeMount names match volume definitions
- If using Helm: verify values rendered to expected resource names
- Review pod spec for typos in secret/configmap references

**Example**:
```json
{
  "code": "BAD_CONFIG",
  "severity": "ERROR",
  "owner": "APP",
  "title": "Bad configuration",
  "explanation": "Pod cannot start due to missing or invalid Kubernetes configuration.",
  "evidence": [
    { "kind": "Pod", "name": "cart-app-7d4f8c-abc" },
    { "kind": "Event", "name": "FailedMount", "message": "secret 'db-credentials' not found" }
  ],
  "nextSteps": [...]
}
```

---

### 2. EXTERNAL_SECRET_RESOLUTION_FAILED
**What it means**: Workload cannot mount/load external secrets (Azure Key Vault + CSI + SecretProviderClass + identity).

**Owner**: Platform/DevOps  
**Severity**: ERROR  
**Priority**: 1 (highest)

**Detection Triggers**:
- Events mention SecretProviderClass OR CSI mount failures
- Event contains: "secrets-store.csi", "SecretProviderClass not found", "permission denied", "keyvault", "azure vault"
- Pods with `CreateContainerConfigError` + CSI-related events

**Next Steps**:
- Confirm SecretProviderClass exists in same namespace
- Verify Key Vault name/URI and object names match exactly (case-sensitive)
- Verify workload identity/managed identity has 'Get' permission on secrets
- Check tenant ID and federated identity binding
- Confirm CSI driver is installed: `kubectl get pods -n kube-system | grep csi-secrets-store`

**Example**:
```json
{
  "code": "EXTERNAL_SECRET_RESOLUTION_FAILED",
  "severity": "ERROR",
  "owner": "PLATFORM",
  "title": "External secret mount failed (CSI / Key Vault)",
  "explanation": "Pod cannot mount external secrets via SecretProviderClass; container will not start.",
  "evidence": [
    { "kind": "Pod", "name": "kv-misconfig-app-c5f9cc746-r2x8g" },
    { "kind": "Event", "name": "FailedMount", "message": "secret 'kv-secrets-does-not-exist' not found" }
  ],
  "nextSteps": [...]
}
```

---

### 3. IMAGE_PULL_FAILED
**What it means**: Container image cannot be pulled (authentication, tag missing, registry access).

**Owner**: Platform/DevOps  
**Severity**: ERROR  
**Priority**: 3

**Detection Triggers**:
- Pod waiting reason: `ImagePullBackOff`, `ErrImagePull`
- Event reason: `Failed`, message contains "pull", "manifest unknown", "unauthorized"

**Next Steps**:
- Verify image tag exists in registry
- Verify imagePullSecrets configured if using private registry
- Check network/egress policy allows access to registry
- Confirm registry URL is correct
- If using ACR: verify AKS has pull permissions
- Test: `docker pull <image:tag>`

---

### 4. READINESS_CHECK_FAILED
**What it means**: Pods run but never become Ready (readiness probe / app health).

**Owner**: App Team  
**Severity**: ERROR  
**Priority**: 7

**Detection Triggers**:
- Pod phase `Running`, but `Ready=False` for >60s
- Events include "Readiness probe failed"

**Next Steps**:
- Verify readiness probe path/port matches application endpoint
- Check application logs for startup errors
- Verify dependencies are reachable (DB, cache, APIs)
- Confirm service port mapping matches container port
- Test readiness endpoint manually
- Check if initialDelaySeconds is too short

---

### 5. CRASH_LOOP
**What it means**: Containers repeatedly crash (CrashLoopBackOff / OOM / exit codes).

**Owner**: App Team (sometimes Platform for OOM)  
**Severity**: ERROR  
**Priority**: 6

**Detection Triggers**:
- Pod waiting reason: `CrashLoopBackOff`
- Restarts > 0 and last termination non-zero
- Events: "Back-off restarting failed container"

**Next Steps**:
- Inspect last termination reason: `kubectl describe pod <pod>`
- Check logs from previous instance: `kubectl logs <pod> --previous`
- Look for OOMKilled (out of memory)
- Validate required environment variables
- Review exit codes: 137 = OOMKilled, 143 = SIGTERM

---

### 6. SERVICE_SELECTOR_MISMATCH
**What it means**: Service has zero endpoints due to label/selector mismatch or readiness gating.

**Owner**: App Team  
**Severity**: WARN  
**Priority**: 8 (lowest)

**Detection Triggers**:
- Service exists, but endpoints count = 0 while pods exist
- Selector labels don't match any pod labels (or pods not Ready)

**Next Steps**:
- Compare Service selector labels vs pod labels
- If pods exist but not Ready, fix readiness first
- Verify service and pods are in same namespace
- Test: `kubectl get pods -l <selector> -n <namespace>`

---

### 7. INSUFFICIENT_RESOURCES
**What it means**: Scheduling blocked or evictions due to CPU/memory/node capacity/quotas.

**Owner**: Platform/DevOps  
**Severity**: ERROR  
**Priority**: 4

**Detection Triggers**:
- Pod status `Pending` with reason `Unschedulable`
- Events mention: "Insufficient cpu", "Insufficient memory", "taint", "quota exceeded", "evicted"

**Next Steps**:
- Check requests/limits vs node capacity: `kubectl describe nodes`
- Check namespace quotas: `kubectl get resourcequotas`
- Check node taints/tolerations
- Consider reducing resource requests or scaling cluster
- Check pod disruption budgets

---

### 8. RBAC_DENIED
**What it means**: Tool or workload is denied by Kubernetes RBAC (for required reads/operations).

**Owner**: Platform/Security  
**Severity**: ERROR  
**Priority**: 5

**Detection Triggers**:
- Kubernetes API responses contain "Forbidden" / "RBAC"
- Events show "forbidden" for service account operations

**Next Steps**:
- Confirm triage service account has list/get/watch for required resources
- Confirm workload service account permissions
- Review ClusterRole/Role bindings
- Test: `kubectl auth can-i <verb> <resource> --as=system:serviceaccount:<namespace>:<sa>`
- Check pod security policies or admission controllers

---

## Primary Failure Selection

When multiple findings exist, a **primary failure** is selected using this priority order (lower number = higher priority):

1. **EXTERNAL_SECRET_RESOLUTION_FAILED** - Hard blocker preventing container start
2. **BAD_CONFIG** - Configuration error blocking startup
3. **IMAGE_PULL_FAILED** - Cannot fetch container image
4. **INSUFFICIENT_RESOURCES** - Cannot schedule pod
5. **RBAC_DENIED** - Permission issues
6. **CRASH_LOOP** - Container crashes after start
7. **READINESS_CHECK_FAILED** - App runs but not healthy
8. **SERVICE_SELECTOR_MISMATCH** - Service routing issue (warning level)

**Rationale**: Top items are "hard blockers" that prevent container start or scheduling; lower items are downstream symptoms or warnings.

The primary failure is exposed in the API response as `primaryFailure` field for quick triage decisions.

---

## API Response Contract

### Finding Object Structure

```json
{
  "code": "EXTERNAL_SECRET_RESOLUTION_FAILED",
  "severity": "ERROR",
  "owner": "PLATFORM",
  "title": "External secret mount failed (CSI / Key Vault)",
  "explanation": "Pod cannot mount external secrets via SecretProviderClass; container will not start.",
  "evidence": [
    { "kind": "Pod", "name": "kv-misconfig-app-c5f9cc746-r2x8g" },
    { "kind": "Event", "name": "FailedMount", "message": "secret 'kv-secrets-does-not-exist' not found" }
  ],
  "nextSteps": [
    "Confirm SecretProviderClass exists in the same namespace.",
    "Verify Key Vault object names match exactly (case-sensitive).",
    "Verify workload identity permissions include Get on secrets."
  ]
}
```

### Full Response Structure

```json
{
  "timestamp": "2026-01-08T10:30:00Z",
  "target": {
    "namespace": "default",
    "selector": "app=kv-misconfig-app",
    "release": null
  },
  "health": {
    "overall": "FAIL",  // Can be: PASS, WARN, FAIL, or UNKNOWN
    "deploymentsReady": "0/1",
    "breakdown": {
      "running": 0,
      "pending": 1,
      "crashLoop": 0,
      "imagePullBackOff": 0,
      "notReady": 0
    }
  },
  "findings": [
    { /* Finding 1 */ },
    { /* Finding 2 */ }
  ],
  "primaryFailure": {
    "code": "EXTERNAL_SECRET_RESOLUTION_FAILED",
    "severity": "ERROR",
    "owner": "PLATFORM",
    "title": "External secret mount failed (CSI / Key Vault)",
    "explanation": "...",
    "evidence": [...],
    "nextSteps": [...]
  },
  "objects": {
    "workloads": [...],
    "pods": [...],
    "events": [...],
    "services": [...],
    "endpoints": [...]
  }
}
```

---

## Owner Enum

Findings are routed to the appropriate team via the `owner` field:

- **APP**: Application team (code, config, probes)
- **PLATFORM**: Platform/DevOps team (infrastructure, K8s, networking, resources)
- **SECURITY**: Security team (RBAC, policies, permissions)
- **UNKNOWN**: Ownership unclear or not determined

---

## Severity Levels

- **ERROR**: Critical failure blocking operation (was `HIGH`)
- **WARN**: Non-blocking issue that should be addressed (was `MED`)
- **INFO**: Informational message (was `LOW`)

Legacy severity levels (`HIGH`, `MED`, `LOW`) are preserved for backward compatibility.

---

## Overall Status Values

The `overall` field in the health response can be:

- **PASS**: All checks passed, deployment is healthy
- **WARN**: Non-critical issues detected (warnings present)
- **FAIL**: Critical failures detected (errors present)
- **UNKNOWN**: Cannot assess health (no matching objects found)

**Important**: When no objects match the selector/release, overall status is **UNKNOWN**, not PASS. This prevents false confidence when the query found nothing to check.

---

## Implementation Details

### Java Classes

**Enums**:
- `FailureCode` - The 8 taxonomy codes + legacy codes
- `Owner` - APP, PLATFORM, SECURITY, UNKNOWN
- `Severity` - ERROR, WARN, INFO (+ legacy HIGH, MED, LOW)

**DTOs**:
- `Finding` - Main finding record with code, severity, owner, title, explanation, evidence, nextSteps
- `Evidence` - Kubernetes object reference (kind, name, optional message)
- `DeploymentSummaryResponse` - Top-level response with primaryFailure field

**Service**:
- `DeploymentDoctorService` - Detection logic for all 8 codes
  - `detectBadConfig()`
  - `detectExternalSecretResolutionFailed()`
  - `detectImagePullFailed()`
  - `detectReadinessCheckFailed()`
  - `detectCrashLoop()`
  - `detectServiceSelectorMismatch()`
  - `detectInsufficientResources()`
  - `detectRbacDenied()`
  - `selectPrimaryFailure()` - Priority-based selection

### Detection Strategy

Each detection method:
1. Analyzes pod statuses and events
2. Collects evidence (pods, events, deployments)
3. Returns a Finding if the pattern is detected
4. Provides 2-5 actionable next steps

All findings are collected, normalized (removing redundant ones), and the primary failure is selected based on priority.

---

## Usage Example

### Query Platform Triage

```bash
curl "http://localhost:8082/api/deployment/summary?namespace=default&selector=app=kv-misconfig-app"
```

### Response (Simplified)

```json
{
  "health": {
    "overall": "FAIL"
  },
  "findings": [
    {
      "code": "EXTERNAL_SECRET_RESOLUTION_FAILED",
      "owner": "PLATFORM",
      "title": "External secret mount failed (CSI / Key Vault)"
    }
  ],
  "primaryFailure": {
    "code": "EXTERNAL_SECRET_RESOLUTION_FAILED",
    "owner": "PLATFORM",
    "nextSteps": [
      "Confirm SecretProviderClass exists...",
      "Verify Key Vault object names...",
      "Verify workload identity permissions..."
    ]
  }
}
```

### Frontend Display

The frontend can now:
- Display all findings with clear owner badges (APP, PLATFORM, SECURITY)
- Highlight the primary failure for quick decision-making
- Show actionable next steps for each finding
- Route escalations to the correct team based on owner

---

## Testing

Test with the sample Kubernetes resources in `apps/platformtriage/chart/`:

1. **Bad Config**: Deploy with missing secret
   ```bash
   kubectl apply -f chart/templates/bad.yaml
   ```

2. **Key Vault Failure**: Deploy with misconfigured SecretProviderClass
   ```bash
   kubectl apply -f chart/templates/kv-misconfig-app.yaml
   ```

3. **RBAC Denied**: Deploy with restricted service account
   ```bash
   kubectl apply -f chart/templates/rbac.yaml
   ```

---

## Migration Notes

### Backward Compatibility

- Legacy severity levels (HIGH, MED, LOW) are preserved
- Legacy finding codes (NO_MATCHING_OBJECTS, ROLLOUT_STUCK, NO_READY_PODS) are supported
- Existing clients continue to work without changes

### Frontend Integration

Update the frontend to:
1. Display `primaryFailure` prominently at the top
2. Add owner badges (colored by team: APP=blue, PLATFORM=purple, SECURITY=red)
3. Show `nextSteps` as expandable action items
4. Filter findings by owner
5. Display structured evidence (kind/name/message)

---

## Benefits

✅ **Consistent Classification**: Every failure maps to one of 8 clear codes  
✅ **Clear Ownership**: Findings route to the right team automatically  
✅ **Actionable**: Every finding includes specific next steps  
✅ **Evidence-Based**: Always points to concrete Kubernetes objects  
✅ **Priority-Driven**: Automatic selection of the most critical issue  
✅ **Production-Ready**: Handles real-world AKS + Key Vault scenarios  

This taxonomy transforms Platform Triage from a diagnostic tool into a **triage decision system** that immediately tells teams:
- **What failed** (code + title)
- **Who owns it** (owner)
- **Why it matters** (severity + explanation)
- **What to do next** (nextSteps)
- **Where to look** (evidence)

---

## Future Enhancements

Potential additions (beyond MVP):
- `INGRESS_MISCONFIGURED` - Ingress routing failures
- `PERSISTENT_VOLUME_FAILED` - PVC binding issues
- `NETWORK_POLICY_BLOCKED` - NetworkPolicy blocking traffic
- `ADMISSION_WEBHOOK_FAILED` - Admission controller rejections
- `CERT_EXPIRED` - TLS certificate issues
- Custom detection rules via configuration
- Machine learning-based failure prediction
- Historical failure trend analysis

