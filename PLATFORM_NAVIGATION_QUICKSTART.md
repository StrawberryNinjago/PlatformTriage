# Platform Navigation - Quick Start Guide

## 🚀 What's New?

PlatformTriage now has a unified navigation shell with 4 modules:
- **DB Doctor** - Database diagnostics (your existing functionality)
- **Deployment Doctor** - Kubernetes monitoring (new)
- **Exports** - Diagnostic exports (placeholder)
- **Help** - Documentation and help (new)

## 📁 Files Changed/Created

### Modified
- `frontend/src/App.jsx` - Now a navigation shell

### Created
- `frontend/src/pages/DBDoctorPage.jsx` - DB diagnostics module
- `frontend/src/pages/DeploymentDoctorPage.jsx` - K8s monitoring module
- `frontend/src/pages/ExportsPage.jsx` - Export tools (placeholder)
- `frontend/src/pages/HelpPage.jsx` - Help documentation

### Documentation
- `PLATFORM_NAVIGATION_README.md` - Full documentation
- `PLATFORM_NAVIGATION_IMPLEMENTATION.md` - Implementation details
- `PLATFORM_NAVIGATION_VISUAL_GUIDE.md` - Visual diagrams
- `PLATFORM_NAVIGATION_QUICKSTART.md` - This file

## ⚡ Quick Test (2 Minutes)

### 1. Start Frontend
```bash
cd frontend
npm run dev
```

### 2. Open Browser
Navigate to: `http://localhost:5173`

### 3. Test Navigation
- ✅ See "PlatformTriage" in app bar
- ✅ See status chips: `[DB: disconnected] [K8s: not configured]`
- ✅ Click each tab: DB Doctor, Deployment Doctor, Exports, Help
- ✅ Each tab should load without errors

### 4. Test DB Doctor (Should Work Exactly As Before)
1. Click "DB Doctor" tab
2. Enter connection details
3. Click "Connect"
4. Status chip changes to `[DB: connected]` (green)
5. Click "Load Summary"
6. Try action buttons
7. Switch to "Compare Environments" tab
8. Console shows all messages

### 5. Test Deployment Doctor
1. Click "Deployment Doctor" tab
2. Enter namespace: `default`
3. Click "Load"
4. If backend ready: See workload data
5. If not: See graceful error message

### 6. Test Console Persistence
1. Connect DB in "DB Doctor" tab
2. Console shows: ✓ Connected successfully
3. Switch to "Deployment Doctor" tab
4. Click "Load"
5. Console shows: 🔍 Loading deployment summary...
6. Switch back to "DB Doctor"
7. Console shows both messages ✓

## 🎯 Expected Results

### ✅ Success Indicators
- All tabs clickable and render content
- DB Doctor works exactly as before
- Status chips update correctly
- Console messages appear in all tabs
- No errors in browser console
- Smooth transitions between tabs

### ⚠️ Expected Warnings
- Deployment Doctor may show error if backend not configured (normal)
- Export buttons are disabled (coming soon)
- K8s status always shows "not configured" (for now)

## 🐛 Troubleshooting

### "Module not rendering"
**Check:** Browser console for errors  
**Fix:** Refresh page with Ctrl+Shift+R

### "DB Doctor doesn't work"
**Check:** Backend running?  
**Check:** Correct connection details?  
**Fix:** Test old functionality first

### "Deployment Doctor shows error"
**Expected:** Backend may not have K8s configured yet  
**Solution:** Add namespace/selector and try again

### "Console messages not appearing"
**Check:** Browser console for React errors  
**Fix:** Clear browser cache and reload

## 📊 Verification Checklist

Copy this into your terminal notes:

```
[ ] Frontend starts without errors
[ ] App bar shows "PlatformTriage"
[ ] Status chips visible and correct
[ ] All 4 tabs render
[ ] DB Doctor tab: Connection works
[ ] DB Doctor tab: Actions work
[ ] DB Doctor tab: SQL Sandbox works
[ ] DB Doctor tab: Compare Environments works
[ ] Deployment Doctor tab: Form renders
[ ] Deployment Doctor tab: Can click Load
[ ] Exports tab: Cards render
[ ] Help tab: Documentation shows
[ ] Console panel visible at bottom
[ ] Console messages persist across tabs
[ ] Status chip changes to green on connect
[ ] No console errors in browser
[ ] Smooth navigation between tabs
```

## 🔧 Backend Setup (If Testing Deployment Doctor)

### Check if Endpoint Exists
```bash
# Test the deployment summary endpoint
curl "http://localhost:8080/api/deployment/summary?namespace=default"
```

**Expected Response:**
- Success: JSON with deployment data
- Error 404: Endpoint not implemented yet (normal)
- Connection refused: Backend not running

### Start Backend (if needed)
```bash
# From project root
cd apps/dbtriage
mvn spring-boot:run
```

Or for platformtriage:
```bash
cd apps/platformtriage
mvn spring-boot:run
```

## 📝 Quick Reference

### Navigation Shortcuts
- **Module 0:** DB Doctor (default)
- **Module 1:** Deployment Doctor
- **Module 2:** Exports
- **Module 3:** Help

### Console Message Types
```javascript
addConsoleMessage('✓ Success message', 'success');  // Green
addConsoleMessage('✗ Error message', 'error');      // Red
addConsoleMessage('⚠ Warning message', 'warning');  // Orange
addConsoleMessage('ℹ Info message', 'info');        // Blue (default)
```

### Status Chip Colors
- **Gray:** Disconnected / Not configured
- **Green:** Connected / Healthy

## 🚦 Status Check

Run this quick check:

```bash
# 1. Check frontend files exist
ls frontend/src/pages/
# Should see: DBDoctorPage.jsx DeploymentDoctorPage.jsx ExportsPage.jsx HelpPage.jsx

# 2. Check for syntax errors
cd frontend
npm run build
# Should complete without errors

# 3. Start dev server
npm run dev
# Should start on port 5173
```

## 📚 Next Steps

### Immediate (5 minutes)
1. ✅ Test all tabs
2. ✅ Verify DB Doctor works
3. ✅ Check console persistence

### Short-term (30 minutes)
1. Read `PLATFORM_NAVIGATION_README.md`
2. Test with real database
3. Try Deployment Doctor with backend

### Long-term (as needed)
1. Implement Export functionality
2. Add K8s connectivity indicator
3. Add environment context to app bar
4. Customize Help content for your team

## 💡 Tips

### Tip 1: Console is Your Friend
The console panel shows all operations across all modules. Check it often!

### Tip 2: Status Chips are Real-Time
The DB connection chip updates immediately when you connect/disconnect.

### Tip 3: Each Module is Independent
You can work in DB Doctor, switch to Help, switch back, and your state is preserved.

### Tip 4: Graceful Degradation
If Deployment Doctor backend isn't ready, you'll see helpful error messages, not crashes.

## 🎉 Success!

If you can:
- ✅ See all 4 tabs
- ✅ Connect to database
- ✅ See console messages
- ✅ Navigate smoothly

**You're all set!** The platform navigation is working correctly.

## 📞 Need Help?

1. **Check browser console** (F12) for errors
2. **Check backend logs** for API issues
3. **Review documentation:**
   - `PLATFORM_NAVIGATION_README.md` - Full docs
   - `PLATFORM_NAVIGATION_IMPLEMENTATION.md` - Technical details
   - `PLATFORM_NAVIGATION_VISUAL_GUIDE.md` - Visual reference
4. **Check Help tab** in the app for user-facing documentation

---

**Ready to go?** Start with:
```bash
cd frontend && npm run dev
```

Then open `http://localhost:5173` and explore! 🚀

