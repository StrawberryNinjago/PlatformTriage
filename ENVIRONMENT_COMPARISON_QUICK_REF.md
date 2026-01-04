# Environment Comparison - Quick Reference

## How to Use the Enhanced Features

### 1. Evidence-Based Conclusions
**Location:** Top of results, after comparison mode banner

**What You See:**
- Finding (one-line summary)
- Evidence (bullet points with specific data)
- Impact (what will happen)
- Recommendation (what to do)
- Action buttons (quick links)

**Example:**
```
🔴 Compatibility - Flyway mismatch detected

Evidence:
• Source latest version: 3
• Target latest version: 2
• Missing migrations: 1

Impact: Target likely missing migrations
Recommendation: Apply all migrations to target

[Show Missing Migrations] [Copy Diagnostics]
```

### 2. Missing Migrations View
**When It Appears:** Only when Flyway mismatch detected and history readable

**Shows:**
- Rank, Version, Description, Script name
- Who installed it and when (from source)
- Clear explanation

**Use Case:** Forward this list to your DBA or apply migrations yourself

### 3. Blast Radius Analysis
**Location:** After conclusions, before drift details

**Shows:** Runtime symptoms for each drift item

**Example for Missing Column:**
```
🟡 Missing column - cart_items.promo_code
High Risk | Compatibility

Likely Symptoms:
• INSERT/UPDATE fails: column "promo_code" does not exist
• SELECT fails if app queries it
• Application errors: NullPointerException
```

**Use Case:** Paste this in incident tickets to explain production errors

### 4. Filtering Results
**Location:** "Comparison Scope" panel (below comparison mode banner)

**Three Filters:**

**A) Only show differences** (toggle On/Off)
- Default: ON (hides matching items)
- Turn OFF to see everything

**B) Severity filter** (dropdown)
- All Severities (default)
- Errors Only
- Warnings Only

**C) Search objects** (text input)
- Type table or column name
- Example: "cart_item" shows only cart_item-related drift
- Searches object name and message

**Use Case:** Large schema? Filter to errors only. Investigating specific table? Search for it.

### 5. Capability Matrix with Tooltips
**Location:** Expandable "Capability Matrix" accordion

**What Changed:** Hover over 🔒 icons

**Tooltip Shows:**
- Why capability is unavailable
- Specific missing privilege
- Permission denied error (if any)

**Example:**
```
🔒 (hover)
→ "Missing privilege: SELECT on pg_catalog.pg_indexes"
```

**Use Case:** Know exactly what to request from DBA

### 6. Privilege Request Snippet
**Location:** Bottom of results (if privileges missing)

**Contains:**
- Ready-to-send SQL grants
- Explanatory comments
- Minimal required privileges

**How to Use:**
1. Click "Copy" button
2. Email/Slack to your DBA
3. Or run yourself if you have admin access

**Example Snippet:**
```sql
-- Grant read access to pg_catalog
GRANT SELECT ON pg_catalog.pg_indexes TO prod_readonly;
GRANT SELECT ON pg_catalog.pg_constraint TO prod_readonly;
```

**Use Case:** Get unblocked when PROD access is limited

### 7. Identity Chips
**Location:** Top of results (below compare button)

**Shows:**
```
Source: DEV localhost:5433/cartdb (cart_user)
Target: PROD localhost:5434/cartdb (prod_readonly)
```

**Use Case:** Screenshot comparisons for stakeholders without confusion

### 8. Copy All Diagnostics
**Location:** Top-right button in results

**Exports:**
- Complete JSON with all comparison data
- Source/target identity
- Capability matrices
- Flyway comparison
- Missing migrations
- All drift sections
- Blast radius
- Conclusions
- Missing privileges

**Use Case:**
- Attach to JIRA tickets
- Share with team
- Archive for compliance

## Workflow Examples

### Scenario: Production is Broken 🔥

**Step 1:** Run comparison (DEV → PROD)

**Step 2:** Check conclusions
- See "Flyway mismatch detected"
- Evidence shows Source v3, Target v2

**Step 3:** Click "Show Missing Migrations"
- See V3__add_promo_code_column.sql is missing

**Step 4:** Check Blast Radius
- See "INSERT/UPDATE fails: column promo_code does not exist"
- Matches production error!

**Step 5:** Click "Copy Diagnostics"
- Paste in incident ticket
- Forward missing migrations to DBA

**Result:** Root cause found in < 2 minutes ✅

### Scenario: Performance Degradation 🐌

**Step 1:** Run comparison (DEV → PROD)

**Step 2:** Filter: Severity = "Warnings Only"

**Step 3:** Check Index section
- See missing indexes
- Risk level: High (unique index missing)

**Step 4:** Check Blast Radius
- See "Slow queries / table scans"
- "CPU spikes during peak usage"

**Step 5:** Forward index DDL to DBA

**Result:** Performance issue diagnosed ✅

### Scenario: Limited PROD Access 🔒

**Step 1:** Run comparison

**Step 2:** See "Partial Comparison" banner

**Step 3:** Check Capability Matrix
- Hover over 🔒 icons
- See specific missing privileges

**Step 4:** Click "Copy Privilege Request"

**Step 5:** Email SQL to DBA

**Step 6:** After grants applied, re-run comparison

**Result:** Full comparison now possible ✅

### Scenario: Large Schema Noise 📊

**Problem:** 500+ tables, only care about cart_* tables

**Step 1:** Run comparison

**Step 2:** Use filters:
- Only show differences: ON
- Severity: Errors Only
- Search: "cart_"

**Step 3:** Review filtered results

**Result:** Focus on what matters ✅

## Action Button Guide

Conclusions have action buttons that navigate you:

| Button Label | What It Does |
|---|---|
| Show Missing Migrations | Scrolls to missing migrations table |
| Show Drift Details | Scrolls to drift sections |
| Show Blast Radius | Scrolls to blast radius cards |
| Show Index Details | Scrolls to indexes section |
| Show Performance Impact | Scrolls to blast radius |
| Open Flyway Health (Target) | Opens Flyway Health tab (future) |
| Copy Diagnostics | Exports full JSON to clipboard |
| Copy Privilege Request | Copies SQL snippet to clipboard |
| Show Capability Matrix | Scrolls to capability matrix |

## Tips & Best Practices

### ✅ Do This
- **Filter first** if schema is large (>100 tables)
- **Check Blast Radius** to understand production impact
- **Copy diagnostics** before making changes
- **Share privilege snippet** instead of screenshots
- **Use identity chips** when taking screenshots

### ❌ Avoid This
- Don't ignore WARN severity (indexes matter!)
- Don't skip capability matrix tooltips
- Don't compare PROD to PROD by accident
- Don't forget to re-run after applying fixes

## Keyboard Shortcuts

None yet, but could add:
- `Ctrl+F` - Focus search
- `Ctrl+E` - Toggle errors only
- `Ctrl+C` - Copy diagnostics
- `Esc` - Clear filters

## Troubleshooting

### "Partial Comparison" Banner Shows
**Cause:** Target lacks metadata access
**Fix:** Copy privilege request and send to DBA

### Missing Migrations Shows "Not Detectable"
**Cause:** Target Flyway history not readable
**Workaround:** Check source history manually, note version numbers

### Blast Radius is Empty
**Cause:** No drift detected, or only non-critical drift
**This is Good:** No runtime impact expected

### Search Shows No Results
**Cause:** Search is case-sensitive? No, it's lowercase.
**Check:** Object might use different naming (users vs user)

### Next Action Buttons Don't Work
**Cause:** Target section might not exist
**Example:** "Show Missing Migrations" only works if migrations are missing

## Browser Compatibility

**Tested:**
- Chrome 90+
- Firefox 88+
- Safari 14+
- Edge 90+

**Requires:**
- JavaScript enabled
- Clipboard API support (for copy buttons)

## Performance

**Large Schemas (500+ tables):**
- Use "Only show differences" toggle
- Use severity filter
- Use search to narrow scope

**Expected Timing:**
- 10 tables: < 1 second
- 100 tables: 2-3 seconds
- 500 tables: 10-15 seconds
- 1000 tables: 30-45 seconds

**Browser Performance:**
- Rendering 1000+ drift items may lag
- Recommendation: Filter to errors only

## Support

**Questions?**
- Check ENVIRONMENT_COMPARISON_ENHANCEMENTS.md for detailed docs
- Check ENVIRONMENT_COMPARISON_README.md for original feature docs

**Found a Bug?**
- Copy diagnostics
- Note the filter/search settings
- Share both with the team

## Version History

**v2.0 (Current)**
- Evidence-based conclusions
- Missing migrations view
- Blast radius analysis
- Risk categorization
- Filtering system
- Privilege analysis
- Identity chips
- Diagnostic export

**v1.0 (Original)**
- Basic schema comparison
- Capability matrix
- Flyway comparison
- Drift sections

---

**Happy Diagnosing! 🎉**

