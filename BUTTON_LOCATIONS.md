# Where to Find Privilege Check Buttons

## 🎯 Two Ways to Check Privileges

There are **TWO locations** where you can check table privileges:

---

## Method 1: Standalone Privilege Check (Left Sidebar)

### Location: Left Panel → Action Buttons Section

```
┌─────────────────────────────────────┐
│ 🔬 Inspect Specific Table           │
├─────────────────────────────────────┤
│ Enter the exact table name you     │
│ want to inspect                     │
│                                     │
│ [Enter exact table name: _______]  │
│                                     │
│ [Show Table Details]     ← Button 1│
│                                     │
│ [Check Ownership & Grants] ← HERE! │  <-- BUTTON LOCATION #1
│                                     │
└─────────────────────────────────────┘
```

### How to Use:
1. **Enter a table name** in the text field (e.g., `cart_item`)
2. **Click "Check Ownership & Grants"** button
3. **Results appear** in the right panel showing:
   - Status (PASS/FAIL/WARNING)
   - Table owner
   - Current user
   - Granted privileges
   - Missing privileges

### What You'll See:
```
┌─────────────────────────────────────┐
│ Results                             │
├─────────────────────────────────────┤
│ Status: FAIL                        │
│                                     │
│ Table: public.cart_item             │
│ Owner: cart_admin                   │
│ Current User: cart_user             │
│                                     │
│ Granted Privileges:                 │
│ [SELECT]                            │
│                                     │
│ Missing Privileges:                 │
│ [INSERT] [UPDATE] [DELETE]          │
└─────────────────────────────────────┘
```

---

## Method 2: Within Table Diagnostics (New Enhanced View)

### Location: Right Panel → Table Diagnostics → Ownership Section

```
1. Enter table name: cart_item
2. Click "Show Table Details"
3. Scroll down to "Ownership & Access Diagnostics" section
```

### Visual Flow:

```
┌─────────────────────────────────────────────────────┐
│ Left Panel                                          │
│ ┌─────────────────────────┐                        │
│ │ [cart_item]             │  Step 1: Enter table   │
│ │ [Show Table Details]    │  Step 2: Click this    │
│ └─────────────────────────┘                        │
└─────────────────────────────────────────────────────┘

THEN in Right Panel:

┌─────────────────────────────────────────────────────┐
│ Table: public.cart_item    [Copy Diagnostics]       │
├─────────────────────────────────────────────────────┤
│                                                     │
│ 📊 Diagnostics Summary                              │
│ [Pills showing status...]                           │
│                                                     │
│ 🔐 Ownership & Access Diagnostics                   │
├─────────────────────────────────────────────────────┤
│                                                     │
│ [Check Ownership & Grants]  ← HERE!                │  <-- BUTTON LOCATION #2
│                                                     │
│ (After clicking, shows persistent diagnostic table) │
└─────────────────────────────────────────────────────┘
```

### How to Use:
1. **Enter table name** in left panel
2. **Click "Show Table Details"** in left panel
3. **Scroll to "🔐 Ownership & Access Diagnostics"** section in right panel
4. **Click "Check Ownership & Grants"** button
5. **Persistent diagnostic card appears** with interpretation

### What You'll See (Enhanced):
```
┌─────────────────────────────────────────────────────┐
│ 🔐 Ownership & Access Diagnostics                   │
├─────────────────────────────────────────────────────┤
│ Check        Result         Details                 │
│ ──────────────────────────────────────────────────  │
│ Table Owner  ❌ cart_admin  Connected as cart_user  │
│ SELECT       ✅ Allowed     Permission granted      │
│ INSERT       ❌ Missing     No permission           │
│ UPDATE       ❌ Missing     No permission           │
│ DELETE       ❌ Missing     No permission           │
│                                                     │
│ ⚠️ Interpretation                                   │
│ This table is owned by cart_admin.                  │
│ The connected user cart_user can read data but      │
│ cannot modify it. This is acceptable for read-only  │
│ services but will fail for write paths.             │
│                                                     │
│ [ℹ️ Why this matters]                               │
└─────────────────────────────────────────────────────┘
```

---

## 🔍 Quick Troubleshooting

### Can't See the Buttons?

#### Issue 1: Button is Grayed Out (Disabled)
**Cause:** Not connected to database OR no table name entered

**Solution:**
1. Make sure you're connected (status shows "connected" in top-right)
2. Enter a table name in the text field
3. Button should become enabled

#### Issue 2: Don't See "Check Ownership & Grants" in Diagnostics View
**Cause:** Need to click "Show Table Details" first

**Solution:**
1. In left panel, enter table name
2. Click **"Show Table Details"** (not "Check Ownership & Grants")
3. New diagnostics view will appear
4. Scroll down to find "🔐 Ownership & Access Diagnostics" section

#### Issue 3: Button Exists But Nothing Happens
**Cause:** Possible API error or connection issue

**Solution:**
1. Check browser console (F12) for errors
2. Verify backend is running on `http://localhost:8081`
3. Check console panel at bottom for error messages

---

## 🎨 Visual Guide - Full Screen Layout

```
╔═══════════════════════════════════════════════════════════════╗
║ PlatformTriage – DB Doctor (MVP)       Connection: connected  ║
╠════════════════════╦══════════════════════════════════════════╣
║ LEFT PANEL (33%)   ║ RIGHT PANEL (67%)                        ║
║                    ║                                          ║
║ ┌────────────────┐ ║ ┌──────────────────────────────────────┐║
║ │ Connection     │ ║ │ Summary Panel                        │║
║ │ Form           │ ║ │                                      │║
║ └────────────────┘ ║ └──────────────────────────────────────┘║
║                    ║                                          ║
║ ┌────────────────┐ ║ ┌──────────────────────────────────────┐║
║ │ Actions        │ ║ │ Results Panel                        │║
║ │                │ ║ │                                      │║
║ │ 📊 General DB  │ ║ │  After "Show Table Details":        │║
║ │ - Verify       │ ║ │                                      │║
║ │ - Flyway       │ ║ │  Table: public.cart_item            │║
║ │ - List Tables  │ ║ │  [Copy Diagnostics]                 │║
║ │                │ ║ │                                      │║
║ │ 🔬 Inspect     │ ║ │  📊 Diagnostics Summary             │║
║ │ [cart_item]    │ ║ │  🟢 🔴 🟡 (clickable pills)          │║
║ │                │ ║ │                                      │║
║ │ [Show Table    │ ║ │  🔐 Ownership & Access              │║
║ │  Details] ←1   │ ║ │  [Check Ownership & Grants] ←2      │║
║ │                │ ║ │  (Shows diagnostic table after      │║
║ │ [Check Owner-  │ ║ │   clicking)                         │║
║ │  ship & Grants]│ ║ │                                      │║
║ │  ↑ BUTTON #1   │ ║ │  🔍 Indexes (click to expand)       │║
║ │                │ ║ │                                      │║
║ │ 🔍 Search      │ ║ │  🔒 Constraints (click to expand)   │║
║ │ [_________]    │ ║ │                                      │║
║ │ [Search]       │ ║ │                                      │║
║ └────────────────┘ ║ └──────────────────────────────────────┘║
║                    ║                                          ║
╠════════════════════╩══════════════════════════════════════════╣
║ Console Panel (Full Width)                                    ║
║ > ✓ Connected successfully                                    ║
║ > ✓ Table details retrieved for cart_item                     ║
║ > ✓ Privileges checked: FAIL                                  ║
╚═══════════════════════════════════════════════════════════════╝
```

---

## 💡 Which Method Should I Use?

### Use Method 1 (Sidebar Button) When:
- ✅ You only want to check privileges quickly
- ✅ You don't need index/constraint information
- ✅ You want a simple pass/fail result

### Use Method 2 (Diagnostics View) When:
- ✅ You want the full diagnostic experience
- ✅ You need to see indexes, constraints, and FKs
- ✅ You want drift detection and timeline
- ✅ You need the interpretation and "Why this matters" context
- ✅ You want to copy complete diagnostics report

**Recommendation:** Use Method 2 (Show Table Details → Check Ownership & Grants) for the enhanced diagnostic experience!

---

## 📸 Screenshot Reference Points

Look for these visual cues:

1. **Left Panel - Action Buttons:**
   - Section header: "🔬 Inspect Specific Table" in blue text
   - Blue text field with "e.g. cart_item" placeholder
   - Blue "Show Table Details" button
   - Outlined "Check Ownership & Grants" button below it

2. **Right Panel - Diagnostics:**
   - After clicking "Show Table Details"
   - Large section with "Table: public.cart_item" header
   - Colored diagnostic pills (green/red/yellow circles)
   - "🔐 Ownership & Access Diagnostics" header
   - Blue "Check Ownership & Grants" button inside this section

---

## ✅ Verification Steps

To verify the buttons are working:

1. **Connect to database**
   - Fill in connection form
   - Click "Connect"
   - Check top-right says "Connection: connected"

2. **Test Method 1:**
   - Enter `cart_item` in table name field
   - "Check Ownership & Grants" button should be enabled (not grayed out)
   - Click it
   - See results in right panel

3. **Test Method 2:**
   - Enter `cart_item` in table name field
   - Click "Show Table Details"
   - See new diagnostics panel in right side
   - Scroll to "🔐 Ownership & Access Diagnostics"
   - Click "Check Ownership & Grants"
   - See persistent diagnostic table appear

If both work, you're all set! 🎉

