#!/bin/bash

echo "==================================="
echo "Demo Scenario Status Check"
echo "==================================="
echo ""

echo "1. Checking if bad-app exists..."
kubectl get deployment bad-app -n cart 2>/dev/null
if [ $? -ne 0 ]; then
    echo "   ❌ bad-app deployment NOT FOUND"
    echo "   → Run: kubectl apply -f apps/platformtriage/chart/templates/bad.yaml"
else
    echo "   ✅ bad-app deployment exists"
fi
echo ""

echo "2. Checking bad-app pod status..."
kubectl get pods -n cart -l app=bad-app --no-headers 2>/dev/null | while read line; do
    pod_name=$(echo $line | awk '{print $1}')
    restarts=$(echo $line | awk '{print $4}')
    status=$(echo $line | awk '{print $3}')
    
    echo "   Pod: $pod_name"
    echo "   Status: $status"
    echo "   Restarts: $restarts"
    
    if [ "$restarts" -lt 5 ]; then
        echo "   ⚠️  RESTARTS < 5 - Demo will show PASS"
        echo "   → Wait for more restarts (need 5+)"
    else
        echo "   ✅ RESTARTS >= 5 - Demo should show WARN"
    fi
done

if [ -z "$(kubectl get pods -n cart -l app=bad-app --no-headers 2>/dev/null)" ]; then
    echo "   ❌ No bad-app pods found"
    echo "   → Deploy: kubectl apply -f apps/platformtriage/chart/templates/bad.yaml"
fi
echo ""

echo "3. Checking bad-app container image..."
image=$(kubectl get deployment bad-app -n cart -o jsonpath='{.spec.template.spec.containers[0].image}' 2>/dev/null)
if [ "$image" == "busybox" ]; then
    echo "   ✅ Using busybox (crashing container)"
else
    echo "   ❌ Using: $image"
    echo "   → Should be busybox for demo"
    echo "   → Redeploy: kubectl apply -f apps/platformtriage/chart/templates/bad.yaml"
fi
echo ""

echo "4. Checking cart-app (Healthy App scenario)..."
kubectl get pods -n cart -l app=cart-app --no-headers 2>/dev/null | while read line; do
    pod_name=$(echo $line | awk '{print $1}')
    restarts=$(echo $line | awk '{print $4}')
    
    if [ "$restarts" -ge 5 ]; then
        echo "   ⚠️  cart-app has $restarts restarts (>= 5)"
        echo "   → This will also show WARN instead of PASS"
        echo "   → Consider redeploying cart-app for clean demo"
    else
        echo "   ✅ cart-app has $restarts restarts (< 5) - good for PASS demo"
    fi
done
echo ""

echo "==================================="
echo "Next Steps:"
echo "==================================="
echo ""
echo "If bad-app needs deployment/redeployment:"
echo "  kubectl apply -f apps/platformtriage/chart/templates/bad.yaml"
echo ""
echo "Watch restarts accumulate:"
echo "  kubectl get pods -n cart -l app=bad-app -w"
echo ""
echo "After 5+ restarts, restart backend and test!"
echo ""
