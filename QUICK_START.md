# Quick Start Guide - New Table Diagnostics

## 🚀 See It In Action (2 Minutes)

### Step 1: Connect to Database
```
1. Open http://localhost:5173
2. Enter connection details:
   - Host: localhost
   - Port: 5432
   - Database: cart
   - Username: cart_user
   - Password: cart_password
3. Click "Connect"
```

### Step 2: Open Table Diagnostics
```
1. In "Inspect Specific Table" section
2. Enter: cart_item
3. Click "Show Table Details"
```

### Step 3: Explore New Features

#### 🎯 Instant Diagnosis (5 seconds)
**Look at the top of the page:**
- See colored pills showing status at a glance
- Green = Good, Red = Problem, Yellow = Warning
- Click any pill to jump to details

#### 🔐 Check Permissions (10 seconds)
**Scroll to "Ownership & Access Diagnostics":**
1. Click "Check Ownership & Grants"
2. See persistent diagnostic table
3. Read plain English interpretation
4. Click "Why this matters" for context

#### ⚠️ Spot Drift (Automatic)
**If you see a yellow banner:**
- This means Flyway, owner, and current user don't match
- This is a common cause of permission failures
- The banner explains the issue automatically

#### 📅 See Timeline (If Flyway is configured)
**Look for "Diagnostic Timeline":**
- Shows when table was created
- Who created it
- What permissions are missing
- Tells the story of how you got here

#### 🔗 Check Foreign Keys
**Expand "Constraints" section:**
1. Look for yellow highlighted rows
2. These are CASCADE foreign keys
3. They can cause unexpected data loss
4. Hover over risk indicator for details

#### 📋 Share with Team (2 seconds)
**Click "Copy Diagnostics" button:**
1. Complete report copied to clipboard
2. Paste into Slack/ticket
3. All context included

---

## 🎨 What You'll See

### The New Layout
```
┌─────────────────────────────────────────────┐
│ Table: public.cart_item    [Copy]           │  ← Header
└─────────────────────────────────────────────┘

┌─────────────────────────────────────────────┐
│ 📊 Diagnostics Summary                      │  ← Fast Answer
│ 🟢 Ownership OK  🔴 SELECT Missing          │
│ 🟡 Write Limited  🟡 2 Cascade FKs          │
└─────────────────────────────────────────────┘

┌─────────────────────────────────────────────┐
│ ⚠️ Potential Credential Drift               │  ← Automatic Alert
│ Flyway: flyway | Owner: admin | You: user   │
└─────────────────────────────────────────────┘

┌─────────────────────────────────────────────┐
│ 📅 Timeline                                 │  ← Story
│ ✔️ Migration applied 10 days ago            │
│ ⚠️ Ownership mismatch detected              │
└─────────────────────────────────────────────┘

┌─────────────────────────────────────────────┐
│ 🔐 Ownership & Access                       │  ← Diagnosis
│ [Check Ownership & Grants]                  │
│ (Shows persistent results after check)      │
└─────────────────────────────────────────────┘

┌─────────────────────────────────────────────┐
│ 🔍 Indexes                          [▼]     │  ← Details
│ (Click to expand)                           │
└─────────────────────────────────────────────┘

┌─────────────────────────────────────────────┐
│ 🔒 Constraints                      [▼]     │  ← Details
│ (Click to expand - shows risk indicators)  │
└─────────────────────────────────────────────┘
```

---

## 💡 Key Features to Try

### 1. Clickable Diagnostic Pills
```
Click: 🟢 Ownership OK
  ↓
Smoothly scrolls to Ownership section
```

### 2. Persistent Diagnostics
```
Before: Click button → See results → Results disappear
After:  Click button → See results → Results STAY
```

### 3. Plain English Interpretation
```
Before: "Status: FAIL, Missing: INSERT, UPDATE, DELETE"
After:  "The connected user can read data but cannot 
         modify it. This is acceptable for read-only 
         services but will fail for write paths."
```

### 4. Automatic Drift Detection
```
Compares:
- Who ran Flyway migrations
- Who owns the table
- Who you're connected as

If they differ → Shows warning automatically
```

### 5. Risk Indicators
```
Foreign Keys with CASCADE:
- Yellow background
- 🟡 "High impact" badge
- Tooltip explanation
- Count in section header
```

### 6. Educational Context
```
Every major section has:
[ℹ️ Why this matters]
  ↓ Click to expand
  Explanation of why this matters
  and what can go wrong
```

### 7. One-Click Sharing
```
[Copy Diagnostics]
  ↓
Complete formatted report in clipboard
Ready to paste in Slack/tickets
```

---

## 🎯 Common Scenarios

### Scenario 1: "Can my app write to this table?"
```
1. Look at Diagnostics Summary pills
2. If you see 🔴 SELECT Missing → No read access
3. If you see 🟡 Write Limited → No write access
4. Click pill to see details
5. Read interpretation for impact

Time: 5 seconds
```

### Scenario 2: "Why am I getting permission denied?"
```
1. Look for ⚠️ Credential Drift warning
2. Check Diagnostic Timeline
3. Click "Check Ownership & Grants"
4. Read interpretation

Time: 30 seconds
```

### Scenario 3: "Is this CASCADE dangerous?"
```
1. Expand Constraints section
2. Look for yellow highlighted rows
3. Check 🟡 "High impact" badges
4. Read "Why this matters"

Time: 15 seconds
```

### Scenario 4: "Share this with my team"
```
1. Click "Copy Diagnostics"
2. Paste in Slack
3. Done

Time: 2 seconds
```

---

## 🔍 What Changed?

### Old Way
```
1. Scroll through flat lists
2. Click temporary buttons
3. Manually correlate data
4. Take multiple screenshots
5. Explain in your own words

Time: 5+ minutes
```

### New Way
```
1. Look at summary pills
2. Check for warnings
3. Click persistent diagnostics
4. Copy complete report

Time: 30 seconds
```

---

## 🎓 Tips for Best Experience

### For Quick Checks
- Just look at the Diagnostics Summary
- Click pills to jump to problems
- Read interpretations for impact

### For Deep Investigation
- Expand all sections
- Read "Why this matters" explanations
- Check timeline for history

### For Collaboration
- Use "Copy Diagnostics" button
- Share complete context
- Avoid back-and-forth questions

### For Learning
- Click all "Why this matters" buttons
- Read interpretations carefully
- Understand the connections

---

## ❓ FAQ

### Q: Do I need to check privileges every time?
**A:** No, only when you need to verify access. The ownership pill shows basic info without checking.

### Q: What if I don't see a drift warning?
**A:** That's good! It means your Flyway installer, table owner, and current user are aligned.

### Q: Why are some sections collapsed?
**A:** To reduce clutter. Click to expand when you need details.

### Q: What does the timeline show?
**A:** The sequence of events that led to the current state (Flyway migration, table creation, permission issues).

### Q: How do I know if a foreign key is dangerous?
**A:** Look for yellow background and 🟡 "High impact" badge in the Constraints section.

---

## 🚨 What to Watch For

### Red Flags
- 🔴 Red pills in summary (immediate problems)
- ⚠️ Credential drift warning (common cause of failures)
- 🟡 Multiple cascade FKs (data loss risk)
- ❌ Missing write privileges (will fail on updates)

### Good Signs
- 🟢 All green pills (healthy state)
- No drift warnings (aligned credentials)
- Clear interpretation (you understand the impact)

---

## 📞 Need Help?

1. **Check TESTING_GUIDE.md** for detailed testing steps
2. **Review BEFORE_AFTER.md** to see what changed
3. **Read COMPONENT_STRUCTURE.md** for technical details
4. **See DIAGNOSTICS_ENHANCEMENTS.md** for feature explanations

---

## 🎉 You're Ready!

The new table diagnostics page is designed to:
- ✅ Answer questions in seconds, not minutes
- ✅ Explain impact, not just show facts
- ✅ Connect related diagnostics automatically
- ✅ Enable easy collaboration

**Just connect, inspect a table, and explore!**

---

**Pro Tip:** Try the "Copy Diagnostics" button first. It's the fastest way to see all the new information in one place.

