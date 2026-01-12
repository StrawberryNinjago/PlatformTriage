#!/bin/bash

echo "========================================="
echo "Setting up Restart Warning Demo"
echo "========================================="
echo ""

# Deploy bad-app with auto-restart container
echo "1. Deploying bad-app (will crash 3x then stabilize)..."
kubectl apply -f apps/platformtriage/chart/templates/bad.yaml

# Wait and watch the pod crash and restart
echo "2. Watching pod build restart history..."
echo "   (Pod will crash 3 times, then stabilize - takes ~20 seconds)"
echo ""

sleep 5

# Show live status for 25 seconds
for i in {1..5}; do
  echo "   Status check $i/5:"
  kubectl get pods -n cart -l app=bad-app --no-headers 2>/dev/null || echo "   Pod not ready yet..."
  sleep 5
done

echo ""
echo "3. Waiting for pod to fully stabilize..."
kubectl wait --for=condition=ready pod -l app=bad-app -n cart --timeout=60s 2>/dev/null || true

# Show final status
echo ""
echo "========================================="
echo "Setup Complete!"
echo "========================================="
echo ""
kubectl get pods -n cart -l app=bad-app

echo ""
echo "✅ Bad-app should now have 3 restarts and be Running"
echo "✅ Click 'Restart Warning' button - should show WARN"
echo ""
echo "If RESTARTS < 3, wait a few more seconds for it to finish crashing"
echo ""
