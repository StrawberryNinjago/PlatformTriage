# Platform Failure Taxonomy - README

## 🎯 What Is This?

A **minimal but platform-grade taxonomy** for Kubernetes deployment failures in Platform Triage. This system classifies every failure into one of **8 mutually exclusive codes**, each with:

- **Clear ownership** (APP, PLATFORM, SECURITY)
- **Structured evidence** (points to specific K8s objects)
- **Actionable next steps** (2-5 specific actions to take)
- **Priority ordering** (automatic primary failure selection)

---

## 🚀 Quick Start

### For Operators

**Query Platform Triage:**
```bash
curl "http://localhost:8082/api/deployment/summary?namespace=default&selector=app=my-app" | jq
```

**View primary failure:**
```bash
curl "http://localhost:8082/api/deployment/summary?namespace=default&selector=app=my-app" \
  | jq '.primaryFailure'
```

**Expected response:**
```json
{
  "primaryFailure": {
    "code": "EXTERNAL_SECRET_RESOLUTION_FAILED",
    "severity": "ERROR",
    "owner": "PLATFORM",
    "title": "External secret mount failed (CSI / Key Vault)",
    "explanation": "Pod cannot mount external secrets via SecretProviderClass; container will not start.",
    "evidence": [
      { "kind": "Pod", "name": "my-app-xyz" },
      { "kind": "Event", "name": "FailedMount", "message": "..." }
    ],
    "nextSteps": [
      "Confirm SecretProviderClass exists in the same namespace.",
      "Verify Key Vault object names match exactly (case-sensitive).",
      "Verify workload identity permissions include Get on secrets."
    ]
  }
}
```

---

### For Developers

**The 8 failure codes:**

| Code | Owner | What It Means |
|------|-------|---------------|
| `EXTERNAL_SECRET_RESOLUTION_FAILED` | PLATFORM | Cannot mount external secrets (Key Vault/CSI) |
| `BAD_CONFIG` | APP | Missing/invalid K8s config (Secret/ConfigMap) |
| `IMAGE_PULL_FAILED` | PLATFORM | Image pull error (auth/tag/registry) |
| `INSUFFICIENT_RESOURCES` | PLATFORM | Scheduling blocked (CPU/memory/quotas) |
| `RBAC_DENIED` | SECURITY | Kubernetes RBAC permission denied |
| `CRASH_LOOP` | APP | Container repeatedly crashes |
| `READINESS_CHECK_FAILED` | APP | Pod runs but never Ready |
| `SERVICE_SELECTOR_MISMATCH` | APP | Service has 0 endpoints |

**Java implementation:**
```java
// Detection method example
private List<Finding> detectImagePullFailed(List<PodInfo> pods, List<EventInfo> events) {
    List<Evidence> evidence = new ArrayList<>();
    
    // Collect evidence
    pods.stream()
        .filter(p -> "ImagePullBackOff".equalsIgnoreCase(p.reason()))
        .forEach(p -> evidence.add(new Evidence("Pod", p.name())));
    
    if (evidence.isEmpty()) {
        return List.of();
    }
    
    // Return finding with actionable next steps
    return List.of(new Finding(
        FailureCode.IMAGE_PULL_FAILED,
        "Image pull failed",
        "Container image cannot be pulled (authentication, missing tag, or registry access issue).",
        evidence,
        List.of(
            "Verify image tag exists in the registry.",
            "Verify imagePullSecrets configured if using private registry.",
            "Check network/egress policy allows access to the registry."
        )
    ));
}
```

---

## 📚 Documentation

### Full Documentation
- **[PLATFORM_FAILURE_TAXONOMY.md](./PLATFORM_FAILURE_TAXONOMY.md)** - Comprehensive guide (550 lines)
  - Full taxonomy specification
  - Detection rules for each code
  - API response contract
  - Usage examples

- **[PLATFORM_TAXONOMY_QUICK_REF.md](./PLATFORM_TAXONOMY_QUICK_REF.md)** - Quick reference (350 lines)
  - Cheat sheet for operators
  - Detection patterns
  - kubectl commands
  - Decision tree

- **[PLATFORM_TAXONOMY_IMPLEMENTATION_SUMMARY.md](./PLATFORM_TAXONOMY_IMPLEMENTATION_SUMMARY.md)** - Implementation details (600 lines)
  - Code statistics
  - Design decisions
  - Testing strategy
  - Frontend integration guide

---

## 🏗️ Architecture

### Core Components

```
┌─────────────────────────────────────────────────┐
│  DeploymentDoctorService                        │
│                                                 │
│  ┌───────────────────────────────────────────┐ │
│  │ detectBadConfig()                         │ │
│  │ detectExternalSecretResolutionFailed()    │ │
│  │ detectImagePullFailed()                   │ │
│  │ detectReadinessCheckFailed()              │ │
│  │ detectCrashLoop()                         │ │
│  │ detectServiceSelectorMismatch()           │ │
│  │ detectInsufficientResources()             │ │
│  │ detectRbacDenied()                        │ │
│  └───────────────────────────────────────────┘ │
│                                                 │
│  selectPrimaryFailure(findings)                 │
│     ↓                                           │
│  Finding (code, owner, evidence, nextSteps)     │
└─────────────────────────────────────────────────┘
```

### Data Model

```
Finding
├── code: FailureCode (enum with priority)
├── severity: Severity (ERROR/WARN/INFO)
├── owner: Owner (APP/PLATFORM/SECURITY/UNKNOWN)
├── title: String (short summary)
├── explanation: String (detailed description)
├── evidence: List<Evidence> (K8s objects)
└── nextSteps: List<String> (actionable items)

Evidence
├── kind: String (Pod/Event/Deployment/Service)
├── name: String (K8s object name)
└── message: String (optional context)
```

---

## 🧪 Testing

### Deploy Test Cases

```bash
# Test BAD_CONFIG
kubectl apply -f apps/platformtriage/chart/templates/bad.yaml

# Test EXTERNAL_SECRET_RESOLUTION_FAILED
kubectl apply -f apps/platformtriage/chart/templates/kv-misconfig-app.yaml

# Test RBAC_DENIED
kubectl apply -f apps/platformtriage/chart/templates/rbac.yaml
```

### Query Results

```bash
# Get summary
curl "http://localhost:8082/api/deployment/summary?namespace=default&selector=app=kv-misconfig-app"

# Get primary failure only
curl "http://localhost:8082/api/deployment/summary?namespace=default&selector=app=kv-misconfig-app" \
  | jq '.primaryFailure.code'

# Get all findings
curl "http://localhost:8082/api/deployment/summary?namespace=default&selector=app=kv-misconfig-app" \
  | jq '.findings[].code'
```

---

## 🎨 Frontend Integration

### Display Primary Failure

```jsx
import { Alert, AlertTitle, Chip, List, ListItem } from '@mui/material';

function PrimaryFailurePanel({ primaryFailure }) {
  if (!primaryFailure) return null;
  
  const ownerColors = {
    APP: 'primary',
    PLATFORM: 'secondary',
    SECURITY: 'error',
    UNKNOWN: 'default'
  };
  
  return (
    <Alert severity="error" sx={{ mb: 2 }}>
      <AlertTitle>
        <Chip 
          label={primaryFailure.owner} 
          color={ownerColors[primaryFailure.owner]}
          size="small"
          sx={{ mr: 1 }}
        />
        {primaryFailure.title}
      </AlertTitle>
      
      <Typography variant="body2" sx={{ mb: 2 }}>
        {primaryFailure.explanation}
      </Typography>
      
      <Typography variant="subtitle2">Evidence:</Typography>
      <List dense>
        {primaryFailure.evidence.map((ev, i) => (
          <ListItem key={i}>
            {ev.kind}: {ev.name}
            {ev.message && ` - ${ev.message}`}
          </ListItem>
        ))}
      </List>
      
      <Typography variant="subtitle2">Next Steps:</Typography>
      <List dense>
        {primaryFailure.nextSteps.map((step, i) => (
          <ListItem key={i}>• {step}</ListItem>
        ))}
      </List>
    </Alert>
  );
}
```

---

## 🔍 Troubleshooting

### No findings detected?
1. Check pod phase: `kubectl get pod <pod> -o jsonpath='{.status.phase}'`
2. Check events: `kubectl get events --sort-by='.lastTimestamp' | tail -20`
3. Check logs: `kubectl logs <pod>`

### Multiple findings?
- Use the `primaryFailure` field for triage decision
- Fix highest priority issue first
- Lower priority issues may resolve automatically

### Wrong owner assigned?
- Check the evidence to verify detection
- Review event messages for context
- Report false positives to improve detection rules

---

## 📊 Statistics

### Code Coverage
- **8 failure codes** (MVP taxonomy)
- **8 detection methods** (~500 lines)
- **4 enums** (FailureCode, Owner, Severity, OverallStatus)
- **3 DTOs** (Finding, Evidence, DeploymentSummaryResponse)

### Detection Accuracy
Target scenarios covered:
- ✅ Azure Key Vault + CSI failures
- ✅ Missing secrets/configmaps
- ✅ Image pull errors (public/private registries)
- ✅ Readiness probe failures
- ✅ Crash loops (OOM, app errors)
- ✅ Service selector mismatches
- ✅ Resource constraints (CPU/memory)
- ✅ RBAC permission errors

---

## 🚀 Deployment

### Build
```bash
cd /Users/yanalbright/Downloads/Triage
mvn clean package -pl apps/platformtriage -am
```

### Run
```bash
java -jar apps/platformtriage/target/platformtriage-0.0.1-SNAPSHOT.jar
```

### Test
```bash
curl http://localhost:8082/api/deployment/summary?namespace=default&selector=app=test
```

---

## 🤝 Contributing

### Adding a New Failure Code

1. **Add enum value** in `FailureCode.java`:
```java
MY_NEW_CODE(Owner.PLATFORM, Severity.ERROR)
```

2. **Implement detection method** in `DeploymentDoctorService.java`:
```java
private List<Finding> detectMyNewCode(List<PodInfo> pods, List<EventInfo> events) {
    // Detection logic
}
```

3. **Call detection method** in `getSummary()`:
```java
findings.addAll(detectMyNewCode(podInfos, relatedEvents));
```

4. **Update priority** in `FailureCode.getPriority()`:
```java
case MY_NEW_CODE -> 9;  // Set appropriate priority
```

5. **Test** with real Kubernetes resources

6. **Document** in PLATFORM_FAILURE_TAXONOMY.md

---

## 📝 License

Part of the Triage project. See main README for license information.

---

## 🙏 Acknowledgments

- Inspired by DBTriage's diagnostic quality
- Designed for real-world AKS + Key Vault scenarios
- Built on Kubernetes client-java

---

## 📞 Support

### For Questions
- Check **PLATFORM_TAXONOMY_QUICK_REF.md** for common scenarios
- Review **PLATFORM_FAILURE_TAXONOMY.md** for detailed specs
- Inspect evidence in API response for debugging

### For Issues
- Verify detection logic in **DeploymentDoctorService.java**
- Check event patterns in Kubernetes
- Review kubectl output for comparison

---

**Version**: MVP (8 codes)  
**Status**: ✅ Production Ready  
**Last Updated**: January 8, 2026  

---

## Quick Links

- [Full Taxonomy Specification](./PLATFORM_FAILURE_TAXONOMY.md)
- [Quick Reference Guide](./PLATFORM_TAXONOMY_QUICK_REF.md)
- [Implementation Summary](./PLATFORM_TAXONOMY_IMPLEMENTATION_SUMMARY.md)
- [Source Code](./apps/platformtriage/src/main/java/com/example/platformtriage/)

**Start here:** Try the Quick Reference guide for hands-on commands and examples.

