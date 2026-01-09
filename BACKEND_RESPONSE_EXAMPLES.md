# Backend Response Examples

## Concrete examples of the new deterministic status contract

### Scenario 1: No Objects Found (UNKNOWN)

**Request:**
```bash
GET /api/deployment/summary?namespace=nonexistent&release=missing-app
```

**Response:**
```json
{
  "timestamp": "2026-01-08T20:53:58Z",
  "target": {
    "namespace": "nonexistent",
    "selector": "app.kubernetes.io/instance=missing-app",
    "release": "missing-app"
  },
  "health": {
    "overall": "UNKNOWN",
    "deploymentsReady": "0/0",
    "breakdown": {
      "running": 0,
      "pending": 0,
      "crashLoop": 0,
      "imagePullBackOff": 0,
      "notReady": 0
    }
  },
  "findings": [
    {
      "code": "NO_MATCHING_OBJECTS",
      "severity": "WARN",
      "owner": "UNKNOWN",
      "title": "No matching objects",
      "explanation": "No pods or deployments matched the provided selector/release in this namespace.",
      "evidence": [
        {
          "type": "Namespace",
          "name": "nonexistent"
        }
      ],
      "nextSteps": [
        "Verify the selector or release parameter is correct.",
        "Check that resources exist in the namespace: kubectl get pods,deployments -n nonexistent",
        "Confirm you're connected to the correct cluster and namespace."
      ]
    }
  ],
  "primaryFailure": {
    "code": "NO_MATCHING_OBJECTS",
    "...": "..."
  },
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

**UI Behavior:**
- Show "Unknown" status badge (gray/neutral color)
- Display primaryFailure message prominently
- Show "Cannot assess without matching objects"
- Provide next steps from primaryFailure

---

### Scenario 2: Critical Failure (FAIL)

**Request:**
```bash
GET /api/deployment/summary?namespace=cart&selector=app=broken-secrets-app
```

**Response:**
```json
{
  "timestamp": "2026-01-08T20:53:58Z",
  "target": {
    "namespace": "cart",
    "selector": "app=broken-secrets-app",
    "release": null
  },
  "health": {
    "overall": "FAIL",
    "deploymentsReady": "0/3",
    "breakdown": {
      "running": 0,
      "pending": 3,
      "crashLoop": 0,
      "imagePullBackOff": 0,
      "notReady": 0
    }
  },
  "findings": [
    {
      "code": "EXTERNAL_SECRET_RESOLUTION_FAILED",
      "severity": "ERROR",
      "owner": "PLATFORM",
      "title": "External secret mount failed (CSI / Key Vault)",
      "explanation": "Pod cannot mount external secrets via SecretProviderClass; container will not start.",
      "evidence": [
        {
          "type": "Event",
          "name": "cart-app-abc123",
          "detail": "MountVolume.SetUp failed for volume \"secrets-store-inline\" : rpc error: code = Unknown desc = failed to mount secrets store objects for pod cart/cart-app-abc123: key vault access denied"
        },
        {
          "type": "Pod",
          "name": "cart-app-abc123"
        }
      ],
      "nextSteps": [
        "Confirm SecretProviderClass exists in the same namespace: kubectl get secretproviderclass -n cart",
        "Verify Key Vault name/URI and object names match exactly (case-sensitive).",
        "Verify workload identity/managed identity has 'Get' permission on secrets in Key Vault.",
        "Check tenant ID and client ID match in federated identity binding.",
        "Confirm CSI driver is installed: kubectl get pods -n kube-system | grep csi-secrets-store",
        "Check pod service account is correctly annotated for workload identity."
      ]
    },
    {
      "code": "POD_RESTARTS_DETECTED",
      "severity": "WARN",
      "owner": "APP",
      "title": "Pod restarts detected",
      "explanation": "Pod has restarted 5 times but is currently running. This may indicate transient crashes, config reloads, or unstable startup behavior.",
      "evidence": [
        {
          "type": "Pod",
          "name": "cart-app-xyz789",
          "detail": "5 restarts (currently Ready)"
        }
      ],
      "nextSteps": [
        "Review pod logs for crash patterns: kubectl logs cart-app-xyz789 -n cart --previous",
        "Check if restarts correlate with deployments or config changes.",
        "Verify readiness/liveness probe settings are appropriate for startup time.",
        "Look for OOM events (exit code 137): kubectl describe pod cart-app-xyz789 -n cart",
        "Consider if restarts are expected (e.g., app restarts on config reload)."
      ]
    }
  ],
  "primaryFailure": {
    "code": "EXTERNAL_SECRET_RESOLUTION_FAILED",
    "...": "... (full finding object)"
  },
  "topWarning": {
    "code": "POD_RESTARTS_DETECTED",
    "...": "... (full finding object)"
  },
  "objects": {
    "workloads": [...],
    "pods": [...],
    "events": [...],
    "services": [],
    "endpoints": []
  }
}
```

**UI Behavior:**
- Show "Failed" status badge (red)
- Display primaryFailure prominently:
  - Title: "External secret mount failed (CSI / Key Vault)"
  - Owner: Platform (route to platform team)
  - Next steps: Show actionable list
- Show topWarning in advisory section (yellow/warning color):
  - "Also detected: Pod restarts (5 restarts on cart-app-xyz789)"
- Show all findings in expandable list

---

### Scenario 3: Warnings Only (WARN)

**Request:**
```bash
GET /api/deployment/summary?namespace=cart&selector=app=stable-but-restarting-app
```

**Response:**
```json
{
  "timestamp": "2026-01-08T20:53:58Z",
  "target": {
    "namespace": "cart",
    "selector": "app=stable-but-restarting-app",
    "release": null
  },
  "health": {
    "overall": "WARN",
    "deploymentsReady": "3/3",
    "breakdown": {
      "running": 3,
      "pending": 0,
      "crashLoop": 0,
      "imagePullBackOff": 0,
      "notReady": 0
    }
  },
  "findings": [
    {
      "code": "POD_RESTARTS_DETECTED",
      "severity": "WARN",
      "owner": "APP",
      "title": "Pod restarts detected",
      "explanation": "3 pods have restarted 12 total times but are currently running. This may indicate transient crashes, config reloads, or unstable startup behavior.",
      "evidence": [
        {
          "type": "Pod",
          "name": "cart-app-1",
          "detail": "4 restarts (currently Ready)"
        },
        {
          "type": "Pod",
          "name": "cart-app-2",
          "detail": "5 restarts (currently Ready)"
        },
        {
          "type": "Pod",
          "name": "cart-app-3",
          "detail": "3 restarts (currently Ready)"
        }
      ],
      "nextSteps": [
        "Review pod logs for crash patterns: kubectl logs <pod> -n cart --previous",
        "Check if restarts correlate with deployments or config changes.",
        "Verify readiness/liveness probe settings are appropriate for startup time.",
        "Look for OOM events (exit code 137): kubectl describe pod <pod> -n cart",
        "Consider if restarts are expected (e.g., app restarts on config reload)."
      ]
    },
    {
      "code": "POD_SANDBOX_RECYCLE",
      "severity": "WARN",
      "owner": "PLATFORM",
      "title": "Pod sandbox recycled",
      "explanation": "Pod sandbox changed and pod will be killed and re-created. This may indicate node-level issues, runtime problems, or network policy changes.",
      "evidence": [
        {
          "type": "Event",
          "name": "cart-app-1",
          "detail": "Pod sandbox changed, will be killed and re-created"
        }
      ],
      "nextSteps": [
        "Check node health: kubectl describe node <node>",
        "Review container runtime logs on the node.",
        "Check for network policy or CNI changes.",
        "Look for node resource pressure or eviction events.",
        "Verify pod security policies or admission webhooks."
      ]
    }
  ],
  "primaryFailure": null,
  "topWarning": {
    "code": "POD_RESTARTS_DETECTED",
    "...": "... (highest priority WARN finding)"
  },
  "objects": {
    "workloads": [...],
    "pods": [...],
    "events": [...],
    "services": [],
    "endpoints": []
  }
}
```

**UI Behavior:**
- Show "Healthy (with warnings)" status badge (yellow/amber)
  - Or "PASS (with warnings)" if you prefer that label
- Display topWarning in advisory section:
  - Title: "Pod restarts detected"
  - Owner: App team
  - Explanation: "3 pods have restarted 12 total times..."
  - Show next steps
- Show "No critical failures, but investigate warnings"
- Show all warnings in expandable list

---

### Scenario 4: All Healthy (PASS)

**Request:**
```bash
GET /api/deployment/summary?namespace=cart&selector=app=healthy-app
```

**Response:**
```json
{
  "timestamp": "2026-01-08T20:53:58Z",
  "target": {
    "namespace": "cart",
    "selector": "app=healthy-app",
    "release": null
  },
  "health": {
    "overall": "PASS",
    "deploymentsReady": "3/3",
    "breakdown": {
      "running": 3,
      "pending": 0,
      "crashLoop": 0,
      "imagePullBackOff": 0,
      "notReady": 0
    }
  },
  "findings": [],
  "primaryFailure": null,
  "topWarning": null,
  "objects": {
    "workloads": [
      {
        "name": "cart-app",
        "type": "Deployment",
        "ready": "3/3",
        "conditions": [
          "Available=True",
          "Progressing=True (NewReplicaSetAvailable)"
        ]
      }
    ],
    "pods": [
      {
        "name": "cart-app-abc123",
        "phase": "Running",
        "reason": null,
        "ready": true,
        "restarts": 0
      },
      {
        "name": "cart-app-def456",
        "phase": "Running",
        "reason": null,
        "ready": true,
        "restarts": 0
      },
      {
        "name": "cart-app-ghi789",
        "phase": "Running",
        "reason": null,
        "ready": true,
        "restarts": 0
      }
    ],
    "events": [...],
    "services": [...],
    "endpoints": [...]
  }
}
```

**UI Behavior:**
- Show "Healthy" status badge (green)
- Show "All checks passed" message
- Display summary:
  - "3/3 deployments ready"
  - "3 pods running, 0 not ready"
  - "No failures or warnings detected"
- Optionally show expandable objects list

---

## Contract Summary Table

| Scenario | overall | primaryFailure | topWarning | UI Label |
|----------|---------|----------------|------------|----------|
| No objects found | UNKNOWN | NO_MATCHING_OBJECTS | null | "Unknown" |
| Critical failure + warnings | FAIL | EXTERNAL_SECRET... (priority 1) | POD_RESTARTS... | "Failed" |
| Critical failure only | FAIL | CRASH_LOOP (priority 6) | null | "Failed" |
| Warnings only | WARN | null | POD_RESTARTS... | "Healthy (with warnings)" |
| All healthy | PASS | null | null | "Healthy" |

## Priority Selection Examples

### Multiple ERROR findings

**Findings:**
- BAD_CONFIG (priority 2)
- CRASH_LOOP (priority 6)
- IMAGE_PULL_FAILED (priority 3)

**Selection:**
- `primaryFailure` = BAD_CONFIG (lowest priority number = highest priority)
- `topWarning` = null (no warnings)

### Multiple WARN findings

**Findings:**
- POD_RESTARTS_DETECTED (priority 50)
- POD_SANDBOX_RECYCLE (priority 51)
- SERVICE_SELECTOR_MISMATCH (priority 8)

**Selection:**
- `primaryFailure` = SERVICE_SELECTOR_MISMATCH (priority 8, highest ERROR)
  - Wait, SERVICE_SELECTOR_MISMATCH has default severity WARN, not ERROR!
  - So `primaryFailure` = null (no ERROR findings)
- `topWarning` = SERVICE_SELECTOR_MISMATCH (priority 8, highest WARN)

### Mixed ERROR and WARN findings

**Findings:**
- EXTERNAL_SECRET_RESOLUTION_FAILED (priority 1, ERROR)
- CRASH_LOOP (priority 6, ERROR)
- POD_RESTARTS_DETECTED (priority 50, WARN)
- POD_SANDBOX_RECYCLE (priority 51, WARN)

**Selection:**
- `primaryFailure` = EXTERNAL_SECRET_RESOLUTION_FAILED (priority 1)
- `topWarning` = POD_RESTARTS_DETECTED (priority 50)

## Frontend Simplification

### Before (Frontend had to implement this)

```typescript
// Complex logic to find primary failure
const errorFindings = findings.filter(f => f.severity === 'ERROR');
const primaryFailure = errorFindings.length > 0
  ? errorFindings.sort((a, b) => getPriority(a.code) - getPriority(b.code))[0]
  : null;

// Complex logic to find top warning
const warnFindings = findings.filter(f => f.severity === 'WARN');
const topWarning = warnFindings.length > 0
  ? warnFindings.sort((a, b) => getPriority(a.code) - getPriority(b.code))[0]
  : null;

// Complex logic to determine what to show
let displayFinding;
if (health.overall === 'FAIL' || health.overall === 'UNKNOWN') {
  displayFinding = primaryFailure;
} else if (health.overall === 'WARN') {
  displayFinding = topWarning;
}
```

### After (Backend provides everything)

```typescript
// Simple, declarative UI logic
const { health, primaryFailure, topWarning } = response;

let displayFinding;
if (health.overall === 'FAIL' || health.overall === 'UNKNOWN') {
  displayFinding = primaryFailure;  // Backend guarantees this is set
} else if (health.overall === 'WARN') {
  displayFinding = topWarning;      // Backend guarantees this is set
}

// Even simpler: just trust the backend
const mainFinding = primaryFailure || topWarning;
```

## Next Steps for Frontend

1. **Update TypeScript types:**
   ```typescript
   interface DeploymentSummaryResponse {
     timestamp: string;
     target: Target;
     health: Health;
     findings: Finding[];
     primaryFailure: Finding | null;  // Already exists
     topWarning: Finding | null;       // NEW - add this
     objects: Objects;
   }
   ```

2. **Update UI components:**
   - Use `primaryFailure` for FAIL/UNKNOWN states
   - Use `topWarning` for WARN states
   - Remove local sorting/filtering logic

3. **Update tests:**
   - Mock responses should include `topWarning`
   - Test all 4 scenarios (UNKNOWN, FAIL, WARN, PASS)

4. **Update documentation:**
   - API docs should show new field
   - Add examples from this file
