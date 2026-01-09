# Primary Failure Contract: Before vs After

## Visual Behavior Changes

### Scenario 1: Pods Running with Restarts (Risk Signal)

#### BEFORE ❌
```json
{
  "health": {
    "overall": "PASS",  // ❌ WRONG: should be WARN
    "deploymentsReady": "3/3"
  },
  "findings": [
    {
      "code": "POD_RESTARTS_DETECTED",
      "severity": "WARN",
      "title": "Pod restarts detected"
    }
  ],
  "primaryFailure": {  // ❌ WRONG: WARN shouldn't be primary
    "code": "POD_RESTARTS_DETECTED",
    "severity": "WARN",
    "title": "Pod restarts detected"
  }
}
```

**UI Problem:** Shows scary red "Primary Failure" card when everything is working fine!

---

#### AFTER ✅
```json
{
  "health": {
    "overall": "WARN",  // ✅ CORRECT: advisory state
    "deploymentsReady": "3/3"
  },
  "findings": [
    {
      "code": "POD_RESTARTS_DETECTED",
      "severity": "WARN",
      "title": "Pod restarts detected"
    }
  ],
  "primaryFailure": null  // ✅ CORRECT: no primary failure for WARN
}
```

**UI Behavior:**
- No red failure card
- UI derives top warning: first WARN finding
- Shows yellow advisory card instead
- Clear message: "Something to watch, but not broken"

---

### Scenario 2: Wrong Selector (No Objects)

#### BEFORE ❌
```json
{
  "health": {
    "overall": "PASS",  // ❌ WRONG: can't be PASS with nothing
    "deploymentsReady": "0/0"
  },
  "findings": [
    // ... expensive detection ran on empty data
    {
      "code": "NO_MATCHING_OBJECTS",
      "severity": "WARN",
      "title": "No matching objects"
    }
  ],
  "primaryFailure": {  // ❌ WRONG: treated as low priority
    "code": "SOME_OTHER_FINDING"
  }
}
```

**Problems:**
- Wasted API calls (events, services, endpoints)
- Confusing overall status (PASS with nothing?)
- NO_MATCHING_OBJECTS buried in findings list

---

#### AFTER ✅
```json
{
  "health": {
    "overall": "UNKNOWN",  // ✅ CORRECT: can't assess
    "deploymentsReady": "0/0"
  },
  "findings": [
    {
      "code": "NO_MATCHING_OBJECTS",
      "severity": "WARN",
      "title": "No matching objects"
    }
  ],
  "primaryFailure": {  // ✅ CORRECT: clear explanation
    "code": "NO_MATCHING_OBJECTS",
    "severity": "WARN",
    "title": "No matching objects"
  }
}
```

**UI Behavior:**
- Shows neutral "Unknown State" card
- Clear message: "Can't assess - nothing found"
- Fast response (skipped expensive API calls)

---

### Scenario 3: CrashLoopBackOff (Real Failure)

#### BEFORE ✅ (Already Correct)
```json
{
  "health": {
    "overall": "FAIL",
    "deploymentsReady": "0/3"
  },
  "findings": [
    {
      "code": "CRASH_LOOP",
      "severity": "ERROR",
      "title": "Crash loop detected"
    }
  ],
  "primaryFailure": {
    "code": "CRASH_LOOP",
    "severity": "ERROR",
    "title": "Crash loop detected"
  }
}
```

---

#### AFTER ✅ (Still Correct)
```json
{
  "health": {
    "overall": "FAIL",  // ✅ Real failure
    "deploymentsReady": "0/3"
  },
  "findings": [
    {
      "code": "CRASH_LOOP",
      "severity": "ERROR",
      "title": "Crash loop detected"
    }
  ],
  "primaryFailure": {  // ✅ Shows in red failure card
    "code": "CRASH_LOOP",
    "severity": "ERROR",
    "title": "Crash loop detected"
  }
}
```

**No Change:** Real failures still work as expected

---

## Contract Truth Table

| Scenario | Overall | Primary Failure | UI Card Color | Finding Severity |
|----------|---------|-----------------|---------------|------------------|
| All healthy | PASS | null | Green / None | - |
| Risk signals | WARN | null ⭐ | Yellow | WARN |
| Real failure | FAIL | Set ✅ | Red | ERROR |
| No objects | UNKNOWN | Set ✅ | Gray | WARN |
| Mixed WARN + ERROR | FAIL | Set ✅ | Red | ERROR (highest) |

⭐ = Key fix

---

## UI Decision Tree

```
if (response.primaryFailure !== null) {
  // Case 1: overall = FAIL or UNKNOWN
  if (response.health.overall === 'FAIL') {
    → Show RED "Critical Failure" card
    → Use primaryFailure for title/message
    → Show evidence + next steps
  } else if (response.health.overall === 'UNKNOWN') {
    → Show GRAY "Unknown State" card
    → Use primaryFailure (usually NO_MATCHING_OBJECTS)
    → Guide user to check selector/namespace
  }
} else {
  // Case 2: overall = WARN or PASS
  if (response.health.overall === 'WARN') {
    → Show YELLOW "Advisory" card
    → Find first WARN finding as top warning
    → Message: "Something to watch, but not broken"
  } else {
    → Show GREEN "Healthy" card (or hide card)
    → Everything is working fine
  }
}
```

---

## Key Takeaways

1. **primaryFailure = null does NOT mean "healthy"**
   - It means "no critical failure to escalate"
   - Check `overall` to determine actual state

2. **overall = WARN is NOT a failure**
   - It's advisory / risk signals
   - Don't show red cards for WARN

3. **overall = UNKNOWN is different from FAIL**
   - UNKNOWN = "can't assess" (usually wrong selector)
   - FAIL = "assessed and broken"
   - Both have primaryFailure set

4. **UI must derive top warning**
   - Backend doesn't provide `topWarning` field (yet)
   - UI: `findings.find(f => f.severity === 'WARN')`
   - Only when `overall === 'WARN'`

---

## Testing Scenarios

Run these scenarios to verify the fixes:

```bash
# Test 1: WARN without primaryFailure
curl "localhost:8080/api/deployment-doctor/summary?namespace=prod&release=app-with-restarts"
# Expected: overall=WARN, primaryFailure=null

# Test 2: UNKNOWN with primaryFailure
curl "localhost:8080/api/deployment-doctor/summary?namespace=prod&selector=wrong-label"
# Expected: overall=UNKNOWN, primaryFailure=NO_MATCHING_OBJECTS

# Test 3: FAIL with primaryFailure
curl "localhost:8080/api/deployment-doctor/summary?namespace=prod&release=broken-app"
# Expected: overall=FAIL, primaryFailure=<highest priority error>
```

---

## Migration Checklist

- [ ] Update UI to check `overall` instead of just `primaryFailure`
- [ ] Add UNKNOWN state handling (gray cards)
- [ ] Implement top warning derivation for WARN state
- [ ] Remove assumption that `primaryFailure` can be WARN
- [ ] Test all four states: PASS, WARN, FAIL, UNKNOWN
- [ ] Update color palette: green, yellow, red, gray
