# Kubernetes Setup Guide for Deployment Doctor

## Issue: "Request failed with status code 500"

This error occurs when the Deployment Doctor backend cannot connect to a Kubernetes cluster.

## Prerequisites

The Deployment Doctor feature requires:
1. **kubectl** installed and configured
2. **Valid kubeconfig** file at `~/.kube/config`
3. **Access to a Kubernetes cluster** (local or remote)

## Quick Check

### 1. Check if kubectl is installed
```bash
kubectl version --client
```

**Expected output:**
```
Client Version: v1.xx.x
```

**If not installed:** Install kubectl following [official instructions](https://kubernetes.io/docs/tasks/tools/)

### 2. Check if you have a kubeconfig file
```bash
ls -la ~/.kube/config
```

**Expected output:**
```
-rw-------  1 user  staff  xxxxx date ~/.kube/config
```

**If missing:** You need to configure kubectl to connect to a cluster

### 3. Check if you can connect to a cluster
```bash
kubectl cluster-info
```

**Expected output:**
```
Kubernetes control plane is running at https://...
```

**If error:** Your cluster is not accessible

### 4. Check if you can list pods
```bash
kubectl get pods -n default
```

**Expected output:**
```
NAME                    READY   STATUS    RESTARTS   AGE
pod-name-xxx            1/1     Running   0          10m
```

**If error:** You might not have permissions

## Solutions

### Option 1: Use Minikube (Local Kubernetes)

**Install Minikube:**
```bash
# macOS
brew install minikube

# Start cluster
minikube start

# Verify
kubectl get nodes
```

### Option 2: Use Docker Desktop Kubernetes

**Enable Kubernetes in Docker Desktop:**
1. Open Docker Desktop
2. Go to Settings → Kubernetes
3. Check "Enable Kubernetes"
4. Click "Apply & Restart"
5. Wait for Kubernetes to start

**Verify:**
```bash
kubectl config current-context
# Should show: docker-desktop
```

### Option 3: Use Kind (Kubernetes in Docker)

```bash
# Install kind
brew install kind

# Create cluster
kind create cluster

# Verify
kubectl cluster-info --context kind-kind
```

### Option 4: Connect to Remote Cluster

If you have access to a remote Kubernetes cluster:

```bash
# Get kubeconfig from your cluster admin
# Copy it to ~/.kube/config

# Or merge it
export KUBECONFIG=~/.kube/config:/path/to/new/config
kubectl config view --flatten > ~/.kube/config.new
mv ~/.kube/config.new ~/.kube/config

# Verify
kubectl config get-contexts
kubectl config use-context <your-context>
```

## Testing the Setup

### 1. Create a test namespace
```bash
kubectl create namespace cart
```

### 2. Deploy a simple app
```bash
# Create a deployment
kubectl create deployment cart-app \
  --image=nginx \
  --replicas=2 \
  -n cart

# Add label
kubectl label deployment cart-app app=cart-app -n cart

# Check status
kubectl get deployments -n cart
kubectl get pods -n cart
```

### 3. Test the Deployment Doctor

Now restart the PlatformTriage backend and try loading the deployment summary:
- Namespace: `cart`
- Selector: `app=cart-app`
- Click "Load"

You should see:
- Overall status
- 2 deployments ready
- 2 pods running
- No errors

## Troubleshooting Specific Errors

### Error: "Failed to initialize Kubernetes ApiClient"

**Cause:** No kubeconfig found or invalid

**Fix:**
```bash
# Check if file exists
ls -la ~/.kube/config

# If missing, create a cluster (see options above)

# If exists but invalid, try:
kubectl config view
```

### Error: "Cannot connect to Kubernetes cluster"

**Cause:** Cluster not running or not accessible

**Fix:**
```bash
# Check cluster status
kubectl cluster-info

# If using minikube
minikube status
minikube start

# If using Docker Desktop
# Restart Docker Desktop and ensure Kubernetes is enabled
```

### Error: "Unauthorized to access Kubernetes"

**Cause:** Invalid credentials

**Fix:**
```bash
# Check current context
kubectl config current-context

# Try to access cluster
kubectl get pods

# If unauthorized, you need new credentials from cluster admin
```

### Error: "Forbidden: insufficient permissions"

**Cause:** Your service account doesn't have RBAC permissions

**Fix:**
```bash
# Check your permissions
kubectl auth can-i get pods -n cart

# If using local cluster, you should have admin access
# If using remote cluster, ask admin to grant permissions
```

## Backend Restart

After setting up Kubernetes, restart the PlatformTriage backend:

```bash
# Stop current backend (Ctrl+C in terminal)

# Restart
cd apps/platformtriage
mvn spring-boot:run
```

**Look for this log message:**
```
✓ Kubernetes ApiClient initialized successfully
```

**If you see:**
```
✗ Failed to initialize Kubernetes ApiClient
```

Then Kubernetes is not properly configured. Follow steps above.

## Permissions Required

The Deployment Doctor needs these Kubernetes permissions:

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: deployment-doctor
rules:
- apiGroups: [""]
  resources: ["pods", "services", "endpoints", "events"]
  verbs: ["get", "list"]
- apiGroups: ["apps"]
  resources: ["deployments", "replicasets"]
  verbs: ["get", "list"]
```

For **local development**, kubectl admin access is usually sufficient.

For **production**, create a ServiceAccount with these permissions.

## Quick Kubernetes Setup Commands

```bash
# 1. Install Docker Desktop (includes kubectl)
# OR install kubectl separately:
brew install kubectl

# 2. Enable Kubernetes in Docker Desktop
# Settings → Kubernetes → Enable Kubernetes

# 3. Wait for Kubernetes to start (green indicator in Docker Desktop)

# 4. Verify
kubectl get nodes

# 5. Create test resources
kubectl create namespace cart
kubectl create deployment cart-app --image=nginx --replicas=2 -n cart
kubectl label deployment cart-app app=cart-app -n cart

# 6. Check
kubectl get all -n cart

# 7. Restart PlatformTriage backend
cd apps/platformtriage
mvn spring-boot:run

# 8. Test in UI
# - Open http://localhost:5173
# - Click "Deployment Doctor"
# - Namespace: cart
# - Selector: app=cart-app
# - Click Load
```

## Alternative: Mock Mode (Coming Soon)

If you don't want to set up Kubernetes, we can add a mock/demo mode:

```yaml
# application.yaml
platform-triage:
  kubernetes:
    mock-mode: true
```

This would return sample data without requiring a real cluster.

## Still Having Issues?

1. **Check backend logs** for detailed error messages
2. **Check frontend console** (F12) for client errors
3. **Verify ports:**
   - Frontend: http://localhost:5173
   - Backend: http://localhost:8082
4. **Test API directly:**
   ```bash
   curl "http://localhost:8082/api/deployment/summary?namespace=cart&selector=app=cart-app"
   ```

## Summary

To use Deployment Doctor, you need:
- ✅ kubectl installed
- ✅ Valid kubeconfig (~/.kube/config)
- ✅ Running Kubernetes cluster
- ✅ Permissions to read pods, deployments, services, events

**Easiest option for local development:** Docker Desktop with Kubernetes enabled

