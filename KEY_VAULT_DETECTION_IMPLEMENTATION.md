# Key Vault / External Secret Detection Implementation

## Overview

Deployment Doctor now detects and provides actionable guidance for **AKS + Key Vault integration failures** - one of the most common failure modes in production Kubernetes environments.

## What Was Implemented

### 1. ✅ Backend: Finding Model Enhancement

**File:** `Finding.java`

Added `hints` field to provide actionable troubleshooting steps:

```java
public record Finding(
    Severity severity,
    String code,
    String message,
    List<String> hints,        // ← NEW: Actionable troubleshooting hints
    List<String> evidenceRefs
) {}
```

### 2. ✅ Backend: Key Vault Failure Detection

**File:** `DeploymentDoctorService.java`

New method: `findingsFromExternalSecrets()` detects 3 patterns:

#### Pattern 1: CreateContainerConfigError
Pods stuck in this state often indicate secret mount failures.

#### Pattern 2: CSI Driver Mount Failures
Scans events for:
- `FailedMount` / `FailedAttachVolume` / `MountVolume.SetUp failed`
- Messages containing:
  - `secrets-store.csi.k8s.io`
  - `SecretProviderClass`
  - `keyvault` / `Key Vault`
  - `azure` + `vault`
  - `failed to mount`

#### Pattern 3: Secret/ConfigMap Not Found
Detects when Key Vault secrets fail to sync to K8s Secrets:
- `secret "<name>" not found`
- `configmap "<name>" not found`
- `couldn't find key`
- `key does not exist`

### 3. ✅ Backend: Finding with Hints

When detected, creates HIGH severity finding:

```json
{
  "severity": "HIGH",
  "code": "EXTERNAL_SECRET_RESOLUTION_FAILED",
  "message": "Pod(s) cannot mount/load external secrets (Key Vault / CSI / SecretProviderClass).",
  "hints": [
    "Check Key Vault name/URI in SecretProviderClass matches actual Key Vault.",
    "Verify identity permissions (Workload Identity / managed identity) have 'Get' permission for secrets.",
    "Confirm secret object names in Key Vault match exactly (case-sensitive).",
    "Ensure SecretProviderClass is in the same namespace as the pod.",
    "Check that the Azure tenant ID is correct if using Workload Identity."
  ],
  "evidenceRefs": [
    "pod/crash-app-779c88668f-jj9d4",
    "event/FailedMount:crash-app-779c88668f-jj9d4"
  ]
}
```

### 4. ✅ Frontend: Enhanced Findings Display

**File:** `DeploymentDoctorPage.jsx`

#### A. Hints Rendering
Renders hints as a styled bullet list:

```jsx
{Array.isArray(f.hints) && f.hints.length > 0 && (
  <Box sx={{ /* styled hint box */ }}>
    <Typography>💡 Common causes:</Typography>
    <ul>
      {f.hints.map(hint => <li>{hint}</li>)}
    </ul>
  </Box>
)}
```

**Visual Style:**
- Light blue background
- Left border (info color)
- Bullet list format
- Italic text

#### B. Click-to-Scroll Evidence
Evidence refs are now **clickable links** that scroll to the relevant section:

```jsx
<Typography
  onClick={() => handleEvidenceClick(ref)}
  sx={{ 
    cursor: 'pointer',
    textDecoration: 'underline',
    color: 'primary.main'
  }}
>
  {ref}
</Typography>
```

**Behavior:**
- Click `pod/crash-app-...` → Scrolls to that pod in Pods section
- Click `deployment/api-server` → Scrolls to that deployment
- Click `service/crash-app-svc` → Scrolls to that service
- Briefly highlights the target (2-second blue flash)

### 5. ✅ Section IDs for Navigation

Added unique IDs to all accordion sections:

```jsx
<Accordion id={`pod-${p.name?.replace(/[^a-zA-Z0-9-]/g, '-')}`}>
<Accordion id={`deployment-${d.name?.replace(/[^a-zA-Z0-9-]/g, '-')}`}>
<Accordion id={`service-${s.name?.replace(/[^a-zA-Z0-9-]/g, '-')}`}>
```

This enables direct navigation from findings to evidence.

### 6. ✅ Updated Existing Findings

All existing findings updated to include hints parameter:

- `IMAGE_PULL` - hints: null
- `BAD_CONFIG` - hints: null  
- `PODS_NOT_READY` - hints: null
- `ROLLOUT_STUCK` - hints: null
- `NO_READY_PODS` - hints: null
- **`SERVICE_NO_ENDPOINTS`** - hints: ✅ Added helpful troubleshooting steps
- `CRASH_LOOP` - hints: null
- `NO_MATCHING_OBJECTS` - hints: null
- **`EXTERNAL_SECRET_RESOLUTION_FAILED`** - hints: ✅ 5 actionable steps

## Example: Key Vault Failure Detection

### Scenario
Pod cannot mount secrets from Azure Key Vault due to wrong vault name in SecretProviderClass.

### What Kubernetes Shows
```
Pod: crash-app-779c88668f-jj9d4
Status: Running
Reason: CreateContainerConfigError
Ready: false

Event:
Type: Warning
Reason: FailedMount
Message: MountVolume.SetUp failed for volume "secrets-store-inline" : 
  rpc error: code = Unknown desc = failed to mount secrets store objects for pod 
  default/crash-app-779c88668f-jj9d4, err: keyvault not found
```

### What Deployment Doctor Shows

```
🔴 HIGH: EXTERNAL_SECRET_RESOLUTION_FAILED

Pod(s) cannot mount/load external secrets (Key Vault / CSI / SecretProviderClass).

┌──────────────────────────────────────────────────────────┐
│ 💡 Common causes:                                        │
│ • Check Key Vault name/URI in SecretProviderClass       │
│   matches actual Key Vault.                             │
│ • Verify identity permissions (Workload Identity /      │
│   managed identity) have 'Get' permission for secrets.  │
│ • Confirm secret object names in Key Vault match        │
│   exactly (case-sensitive).                             │
│ • Ensure SecretProviderClass is in the same namespace   │
│   as the pod.                                           │
│ • Check that the Azure tenant ID is correct if using    │
│   Workload Identity.                                    │
└──────────────────────────────────────────────────────────┘

Evidence: pod/crash-app-779c88668f-jj9d4 (clickable)
          event/FailedMount:crash-app-779c88668f-jj9d4 (clickable)
```

Click on evidence → Auto-scrolls to pod details

## Why This Matters

### Problem
- Kubernetes doesn't know "Key Vault name is wrong"
- It only shows downstream symptoms: `CreateContainerConfigError`, `FailedMount`
- Junior engineers struggle to diagnose root cause
- Leads to long MTTR (Mean Time To Resolution)

### Solution
- **Detects symptom patterns** (CSI failures, mount errors, secret not found)
- **Classifies as Key Vault issue** with specific finding code
- **Provides actionable hints** pointing to likely root causes
- **Links evidence to objects** for quick investigation

### Impact
- ✅ Reduces triage time from hours to minutes
- ✅ Helps junior engineers diagnose complex issues
- ✅ Clear, actionable guidance instead of cryptic errors
- ✅ Professional, production-ready UX

## Testing

### Test Scenario 1: Working Key Vault Integration
```bash
kubectl get pods -n production -l app=api-server
# All pods Running and Ready
# Result: No EXTERNAL_SECRET_RESOLUTION_FAILED finding
```

### Test Scenario 2: Wrong Key Vault Name
```bash
# Create SecretProviderClass with wrong vault name
kubectl apply -f secretproviderclass-wrong-vault.yaml

# Deploy pod
kubectl apply -f pod-with-keyvault.yaml

# Load in Deployment Doctor
# Namespace: production
# Selector: app=api-server
# Click Load

# Expected: HIGH finding with 5 hints, evidence links to pod
```

### Test Scenario 3: Click-to-Scroll
```bash
# After loading summary with findings:
# 1. Click on evidence ref "pod/api-server-abc123"
# 2. Page scrolls to Pods section, highlights that pod
# 3. Accordion may auto-expand to show details
```

## API Response Example

```json
{
  "timestamp": "2026-01-06T...",
  "target": {
    "namespace": "production",
    "selector": "app=api-server"
  },
  "health": {
    "overall": "FAIL",
    "deploymentsReady": "0/1",
    "pods": {
      "running": 1,
      "pending": 0,
      "crashLoop": 0,
      "imagePullBackOff": 0,
      "notReady": 1
    }
  },
  "findings": [
    {
      "severity": "HIGH",
      "code": "EXTERNAL_SECRET_RESOLUTION_FAILED",
      "message": "Pod(s) cannot mount/load external secrets (Key Vault / CSI / SecretProviderClass).",
      "hints": [
        "Check Key Vault name/URI in SecretProviderClass matches actual Key Vault.",
        "Verify identity permissions (Workload Identity / managed identity) have 'Get' permission for secrets.",
        "Confirm secret object names in Key Vault match exactly (case-sensitive).",
        "Ensure SecretProviderClass is in the same namespace as the pod.",
        "Check that the Azure tenant ID is correct if using Workload Identity."
      ],
      "evidenceRefs": [
        "pod/api-server-779c88668f-jj9d4",
        "event/FailedMount:api-server-779c88668f-jj9d4"
      ]
    }
  ],
  "objects": {
    "deployments": [...],
    "pods": [...],
    "events": [...],
    "services": [...],
    "endpoints": [...]
  }
}
```

## Future Enhancements

### Phase 2
- [ ] Auto-expand accordion when scrolling to evidence
- [ ] Add "Copy finding" button for sharing
- [ ] Severity filter in Findings section
- [ ] Group related findings together

### Phase 3
- [ ] Detect specific Key Vault error codes
- [ ] Link to Azure Portal (Key Vault / Identity pages)
- [ ] Historical findings tracking
- [ ] Compare findings across time

## Files Modified

### Backend
- ✅ `Finding.java` - Added `hints` field
- ✅ `DeploymentDoctorService.java` - Added Key Vault detection logic

### Frontend
- ✅ `DeploymentDoctorPage.jsx` - Enhanced findings rendering + click-to-scroll

## Summary

This implementation transforms Deployment Doctor from a **passive observer** into an **active diagnostic assistant** that:

1. **Detects complex failure patterns** (Key Vault integration)
2. **Provides actionable guidance** (hints for troubleshooting)
3. **Enables rapid investigation** (click-to-scroll evidence)
4. **Reduces MTTR** (clear root cause analysis)

Perfect for production AKS environments where Key Vault integration failures are a daily reality! 🚀

---

**Status:** ✅ Complete and ready for testing  
**Date:** January 6, 2026

