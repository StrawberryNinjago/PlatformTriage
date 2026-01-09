# Overall Status: UNKNOWN Handling

## Problem

When no pods or deployments match the provided selector/release, Platform Triage was returning `overall: "PASS"`. This is misleading and erodes trust:

- **User expectation**: If I query for `app=my-app`, I expect to see the health of `my-app`
- **Reality**: If nothing matches, we're not checking anything
- **Previous behavior**: `overall: "PASS"` (implies everything is fine)
- **Issue**: False confidence - saying "PASS" when we can't assess anything

## Solution

Added `UNKNOWN` to the `OverallStatus` enum and logic to return it when no objects are found.

### OverallStatus Enum (Updated)

```java
public enum OverallStatus {
  PASS,     // All checks passed, deployment is healthy
  WARN,     // Non-critical issues detected
  FAIL,     // Critical failures detected
  UNKNOWN   // Cannot assess (no matching objects found)
}
```

### Detection Logic

```java
private OverallStatus computeOverall(List<Finding> findings) {
    // Check for NO_MATCHING_OBJECTS first - cannot assess if nothing found
    boolean noMatching = findings.stream()
            .anyMatch(f -> f.code() == FailureCode.NO_MATCHING_OBJECTS);
    if (noMatching) {
        return OverallStatus.UNKNOWN;
    }
    
    // Check for ERROR or legacy HIGH severity
    boolean hasError = findings.stream().anyMatch(f
            -> f.severity() == Severity.ERROR || f.severity() == Severity.HIGH);
    if (hasError) {
        return OverallStatus.FAIL;
    }

    // Check for WARN or legacy MED severity
    boolean hasWarn = findings.stream().anyMatch(f
            -> f.severity() == Severity.WARN || f.severity() == Severity.MED);
    if (hasWarn) {
        return OverallStatus.WARN;
    }

    return OverallStatus.PASS;
}
```

### NO_MATCHING_OBJECTS Severity

Changed from `INFO` to `WARN`:

```java
NO_MATCHING_OBJECTS(Owner.UNKNOWN, Severity.WARN)  // WARN: Cannot assess without objects
```

**Rationale**: Not finding any objects is suspicious and warrants a warning, not just info.

## Example Scenarios

### Scenario 1: Typo in Selector

**Query**:
```bash
curl "http://localhost:8082/api/deployment/summary?namespace=default&selector=app=my-appp"
# Note the typo: "my-appp" instead of "my-app"
```

**Response**:
```json
{
  "health": {
    "overall": "UNKNOWN",  // ← Changed from PASS
    "deploymentsReady": "0/0"
  },
  "findings": [
    {
      "code": "NO_MATCHING_OBJECTS",
      "severity": "WARN",  // ← Changed from INFO
      "owner": "UNKNOWN",
      "title": "No matching objects",
      "explanation": "No pods or deployments matched the provided selector/release in this namespace.",
      "evidence": [
        { "kind": "Namespace", "name": "default" }
      ],
      "nextSteps": [
        "Verify the selector or release parameter is correct.",
        "Check that resources exist in the namespace: kubectl get pods,deployments -n default",
        "Confirm you're connected to the correct cluster and namespace."
      ]
    }
  ]
}
```

**User sees**: "UNKNOWN" status + warning-level finding → investigates selector

### Scenario 2: Wrong Namespace

**Query**:
```bash
curl "http://localhost:8082/api/deployment/summary?namespace=production&selector=app=my-app"
# But my-app is actually in "default" namespace
```

**Response**:
```json
{
  "health": {
    "overall": "UNKNOWN",
    "deploymentsReady": "0/0"
  },
  "findings": [
    {
      "code": "NO_MATCHING_OBJECTS",
      "severity": "WARN",
      "nextSteps": [
        "Verify the selector or release parameter is correct.",
        "Check that resources exist in the namespace: kubectl get pods,deployments -n production",
        "Confirm you're connected to the correct cluster and namespace."
      ]
    }
  ]
}
```

**User sees**: "UNKNOWN" + suggestions to check namespace

### Scenario 3: Deployment Hasn't Started Yet

**Query**:
```bash
curl "http://localhost:8082/api/deployment/summary?namespace=default&selector=app=new-app"
# Querying immediately after deployment starts
```

**Response**:
```json
{
  "health": {
    "overall": "UNKNOWN",  // Better than PASS - indicates we're still waiting
    "deploymentsReady": "0/0"
  },
  "findings": [
    {
      "code": "NO_MATCHING_OBJECTS",
      "severity": "WARN"
    }
  ]
}
```

**User behavior**: Waits a moment and queries again, rather than assuming everything is fine.

## Status Priority Order

The `computeOverall` method checks in this order:

1. **UNKNOWN** - If NO_MATCHING_OBJECTS finding exists
2. **FAIL** - If any ERROR/HIGH severity findings exist
3. **WARN** - If any WARN/MED severity findings exist
4. **PASS** - If no issues found and objects were checked

This prioritization ensures:
- We never claim "PASS" when we haven't checked anything
- Critical failures (FAIL) take precedence over warnings
- Warnings are surfaced when present
- PASS only when we've actually verified health

## Frontend Implications

### Display Recommendations

**UNKNOWN status should be visually distinct:**

```jsx
const StatusBadge = ({ status }) => {
  const config = {
    PASS: { color: 'success', icon: <CheckCircle />, text: 'Healthy' },
    WARN: { color: 'warning', icon: <Warning />, text: 'Issues Detected' },
    FAIL: { color: 'error', icon: <Error />, text: 'Failed' },
    UNKNOWN: { color: 'default', icon: <HelpOutline />, text: 'Unknown' }
  };
  
  const { color, icon, text } = config[status];
  
  return (
    <Chip 
      color={color} 
      icon={icon} 
      label={text}
      sx={status === 'UNKNOWN' ? { 
        border: '2px dashed',
        backgroundColor: 'transparent' 
      } : {}}
    />
  );
};
```

**Suggested UI:**
- PASS: Green solid badge
- WARN: Yellow solid badge
- FAIL: Red solid badge
- **UNKNOWN: Gray dashed border badge** (indicates uncertainty)

### User Messaging

When `overall === "UNKNOWN"`:

```jsx
{status === 'UNKNOWN' && (
  <Alert severity="info" variant="outlined">
    <AlertTitle>Cannot Assess Health</AlertTitle>
    No pods or deployments were found matching your query. This could mean:
    <ul>
      <li>The selector or release name is incorrect</li>
      <li>Resources exist in a different namespace</li>
      <li>The deployment hasn't been created yet</li>
      <li>You're connected to the wrong cluster</li>
    </ul>
    <Typography variant="body2" sx={{ mt: 1 }}>
      <strong>Suggestions:</strong> Verify your query parameters and check the 
      findings below for specific next steps.
    </Typography>
  </Alert>
)}
```

## Testing

### Test Case 1: No Objects Found

```bash
# Query with non-existent selector
curl "http://localhost:8082/api/deployment/summary?namespace=default&selector=app=does-not-exist"

# Verify response
jq '.health.overall' response.json
# Expected: "UNKNOWN"

jq '.findings[0].code' response.json
# Expected: "NO_MATCHING_OBJECTS"

jq '.findings[0].severity' response.json
# Expected: "WARN"
```

### Test Case 2: Objects Exist and Healthy

```bash
# Query with valid selector
curl "http://localhost:8082/api/deployment/summary?namespace=default&selector=app=healthy-app"

# Verify response
jq '.health.overall' response.json
# Expected: "PASS"

jq '.findings | length' response.json
# Expected: 0 (no findings)
```

### Test Case 3: Objects Exist but Failed

```bash
# Query with failing app
curl "http://localhost:8082/api/deployment/summary?namespace=default&selector=app=broken-app"

# Verify response
jq '.health.overall' response.json
# Expected: "FAIL" (not UNKNOWN)

jq '.findings[0].code' response.json
# Expected: One of the 8 taxonomy codes (e.g., "CRASH_LOOP")
```

## Migration Impact

### Backward Compatibility

**API Breaking Change**: NO (new enum value is additive)

**Client Impact**: 
- Existing clients may not handle `UNKNOWN` status
- They should treat it as "not healthy" (fallback to error state)
- Frontend should add explicit handling for `UNKNOWN`

**Recommended Client Logic**:
```javascript
function getHealthColor(status) {
  switch (status) {
    case 'PASS': return 'green';
    case 'WARN': return 'yellow';
    case 'FAIL': return 'red';
    case 'UNKNOWN': return 'gray';  // New
    default: return 'gray';  // Fallback for old clients
  }
}
```

### Database/Metrics Impact

If you're storing/aggregating health status:
- Add `UNKNOWN` to your metrics schema
- Don't count `UNKNOWN` as "healthy" in uptime calculations
- Consider separate metric: "query_success_rate" (found objects vs didn't)

## Benefits

✅ **Trust**: No false positives - never claims healthy when it hasn't checked anything  
✅ **Clarity**: `UNKNOWN` explicitly communicates "cannot assess"  
✅ **Actionable**: Finding includes next steps to resolve the issue  
✅ **Prevents mistakes**: Users won't deploy based on misleading `PASS` status  
✅ **Better UX**: Frontend can provide helpful guidance when status is `UNKNOWN`  

## Related Documentation

- Main taxonomy: [PLATFORM_FAILURE_TAXONOMY.md](./PLATFORM_FAILURE_TAXONOMY.md)
- Quick reference: [PLATFORM_TAXONOMY_QUICK_REF.md](./PLATFORM_TAXONOMY_QUICK_REF.md)
- Implementation: [PLATFORM_TAXONOMY_IMPLEMENTATION_SUMMARY.md](./PLATFORM_TAXONOMY_IMPLEMENTATION_SUMMARY.md)

---

**Status**: ✅ Implemented  
**Version**: MVP  
**Last Updated**: January 8, 2026
