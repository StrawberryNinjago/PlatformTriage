# Quick Demo Setup — Restart Warning Scenario

## Work Laptop Runbook (Deployment Doctor)

Use this when running Deployment Doctor on a work laptop against AKS.

### 1. Authenticate to Azure and target subscription

```bash
az login
az account set --subscription "<your-subscription-id>"
az aks get-credentials -g "<resource-group>" -n "<aks-cluster>" --overwrite-existing
```

### 2. Verify Kubernetes access

```bash
kubectl config current-context
kubectl get ns
```

### 3. Start the app locally

Backend (port 8082):

```bash
cd /Users/yanalbright/Downloads/Triage/apps/platformtriage
mvn spring-boot:run
```

Frontend (port 3000):

```bash
cd /Users/yanalbright/Downloads/Triage/frontend
npm install
npm run dev
```

### 4. Open Deployment Doctor UI

- Open `http://localhost:3000`
- Go to **Deployment Doctor**

### 5. Required query inputs in UI

- `namespace` is required
- You must provide either `Label Selector` or `Release`
- If you do not use label selectors directly, use `Release`
- `Release` maps to selector: `app.kubernetes.io/instance=<release>`
- Namespace-only is not enough. Use: `namespace + (selector or release)`

Examples:

- `namespace=cart`, `selector=app=cart-app`
- `namespace=cart`, `release=cart`

## The Problem

**Crashing pods = CRASH_LOOP (ERROR) → Shows FAIL**  
**Running pods with restart history = RESTARTS_DETECTED (WARN) → Shows WARN** ✅

For the demo to show WARN (not FAIL), we need pods that are **Running** with restart history.

## Solution: Stable Pod + Forced Restarts

1. **Deploy stable nginx** (doesn't crash)
2. **Force manual restarts** to build restart history
3. **Pod ends up Running** with 3+ restarts in history
4. **Triggers WARN** detection ✅

## Quick Setup (Automated)

```bash
# Make script executable
chmod +x setup-restart-demo.sh

# Run setup (takes ~1 minute)
./setup-restart-demo.sh
```

This script:
1. Deploys stable nginx as bad-app
2. Forces 4 restarts to exceed threshold (3)
3. Leaves pod in Running state with restart history

## Manual Setup

### 1. Deploy Bad-App

```bash
kubectl apply -f apps/platformtriage/chart/templates/bad.yaml
```

### 2. Wait for Pod to be Ready

```bash
kubectl wait --for=condition=ready pod -l app=bad-app -n cart --timeout=60s
```

### 3. Force Restarts (Build History)

```bash
# Force 4 restarts to exceed threshold of 3
for i in {1..4}; do
  echo "Restart $i/4..."
  kubectl delete pod -l app=bad-app -n cart --wait=false
  sleep 8
done

# Wait for pod to stabilize
sleep 10
```

### 4. Verify Setup

```bash
kubectl get pods -n cart -l app=bad-app
```

**Expected Output:**
```
NAME                       READY   STATUS    RESTARTS   AGE
bad-app-xxx-yyy           1/1     Running   4          50s
```

✅ STATUS: **Running** (key requirement)  
✅ RESTARTS: **3+** (exceeds threshold)  
✅ READY: **1/1**

## Current Thresholds

| Restart Count | First Load Result |
|---------------|-------------------|
| 0-2 restarts | PASS |
| 3+ restarts | WARN |

## Expected Demo Results

| Scenario | Selector | State | Restarts | Overall |
|----------|----------|-------|----------|---------|
| Healthy App | `app=cart-app` | Running | 0-2 | PASS ✅ |
| Restart Warning | `app=bad-app` | **Running** | 3+ | WARN ✅ |
| Config Failure | `app=kv-misconfig-app` | Error | N/A | FAIL ✅ |
| Query Validation | `app=` | N/A | N/A | FAIL ✅ |

## Why Running State Matters

**Pod in Error/CrashLoop:**
- Detected as CRASH_LOOP
- Severity: ERROR
- Overall: **FAIL** ❌

**Pod in Running with restarts:**
- Detected as POD_RESTARTS_DETECTED  
- Severity: WARN
- Overall: **WARN** ✅

The demo needs pods that **recovered** from instability but have restart history.

## Troubleshooting

**Issue: Shows FAIL instead of WARN**
```bash
# Check pod status
kubectl get pods -n cart -l app=bad-app

# If STATUS = Error/CrashLoopBackOff:
# Run the setup script to force restarts and stabilize
./setup-restart-demo.sh
```

**Issue: Shows PASS (no restarts detected)**
```bash
# Check restart count
kubectl get pods -n cart -l app=bad-app

# If RESTARTS < 3:
# Force more restarts manually
kubectl delete pod -l app=bad-app -n cart
sleep 10
```

**Issue: Healthy App shows WARN**
```bash
# Check cart-app restarts
kubectl get pods -n cart -l app=cart-app

# If >= 3 restarts, redeploy to reset
kubectl rollout restart deployment cart-app -n cart
```

---

**Key Insight:** The demo models **recovered instability** (running but has restarted) vs **active failure** (crashing now).
