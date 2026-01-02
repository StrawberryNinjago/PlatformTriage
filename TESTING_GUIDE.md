# Testing Guide - Table Diagnostics Enhancements

## Prerequisites

✅ Backend is running on `http://localhost:8081`
✅ Frontend is running on `http://localhost:5173`
✅ PostgreSQL database is accessible (cart-pg Docker container)

## Quick Start Test

### 1. Connect to Database
1. Open `http://localhost:5173`
2. Fill in connection form:
   - **Host:** `localhost`
   - **Port:** `5432`
   - **Database:** `cart`
   - **Username:** `cart_user` (or `cart_admin`)
   - **Password:** `cart_password`
   - **Schema:** `public`
3. Click **Connect**
4. Verify connection success

### 2. Test Table Diagnostics
1. In the "Inspect Specific Table" section, enter: `cart_item`
2. Click **Show Table Details**
3. You should now see the enhanced diagnostics panel!

## What to Verify

### ✅ Diagnostics Summary Strip
**Expected:**
- Appears immediately below table header
- Shows 4-6 colored pills depending on state
- Pills are clickable
- Colors match status (green=good, red=error, yellow=warning)

**Test:**
1. Look for pills showing:
   - Ownership status
   - SELECT access (after checking privileges)
   - Write access status (after checking privileges)
   - FK integrity
   - Cascade warnings (if applicable)
2. Click each pill
3. Verify page scrolls to relevant section

### ✅ Ownership & Access Diagnostics
**Expected:**
- Section appears with "Check Ownership & Grants" button initially
- After clicking, shows persistent diagnostic table
- Shows interpretation in plain English

**Test:**
1. Click **Check Ownership & Grants** button
2. Wait for API call to complete
3. Verify table appears with:
   - Table Owner row
   - SELECT, INSERT, UPDATE, DELETE rows
   - Each with ✅ or ❌ status
4. Read interpretation section
5. Verify it explains what the permissions mean
6. Click "Why this matters" button
7. Verify explanation expands

### ✅ Flyway Drift Detection
**Expected (if drift exists):**
- Yellow warning banner appears
- Shows comparison of Flyway installer, table owner, and connected user
- Explains potential impact

**Test:**
1. If you connected as `cart_user` but table was created by `cart_admin`:
   - Verify warning banner appears
   - Check it shows all three roles
   - Read explanation text
2. If no drift exists:
   - Verify no warning banner appears

### ✅ Diagnostic Timeline
**Expected (if Flyway info available):**
- Shows chronological events
- Color-coded status indicators
- Relative time (e.g., "10 days ago")

**Test:**
1. Look for timeline section
2. Verify events are shown in order:
   - Flyway migration applied
   - Table created
   - Ownership mismatch (if applicable)
   - Missing privileges (if applicable)
3. Check relative time formatting

### ✅ Indexes Section
**Expected:**
- Collapsed by default
- Shows count chip
- Expands to show table with icons

**Test:**
1. Verify section is collapsed initially
2. Click header to expand
3. Verify indexes table appears
4. Check for 🔑 icon on primary key indexes
5. Click "Why this matters"
6. Verify explanation appears

### ✅ Constraints Section
**Expected:**
- Collapsed by default
- Shows subsections for each constraint type
- Foreign keys show risk indicators

**Test:**
1. Verify section is collapsed initially
2. Click header to expand
3. Verify subsections appear:
   - 🔑 Primary Keys
   - 🔗 Foreign Keys
   - Unique Constraints
   - 🧪 Check Constraints
4. Look for Foreign Keys with CASCADE:
   - Should have yellow background
   - Should show 🟡 "High impact" chip
   - Hover over chip to see tooltip
5. Click "Why this matters" under Foreign Keys
6. Verify explanation about cascades

### ✅ Copy Diagnostics
**Expected:**
- Button at top of page
- Copies formatted text to clipboard
- Shows success feedback

**Test:**
1. Click **Copy Diagnostics** button
2. Verify button changes to "Copied!" with green color
3. Paste clipboard contents into text editor
4. Verify formatted report includes:
   - Connection ID
   - Table name
   - Owner and current user
   - Privileges (if checked)
   - Flyway info (if available)
   - Index summary
   - Constraint summary
   - Timestamp
5. Verify button returns to normal after 2 seconds

## Test Scenarios

### Scenario 1: Perfect Setup (No Issues)
**Setup:**
- Connect as table owner
- Table has proper indexes and constraints
- No cascading FKs

**Expected:**
- All diagnostic pills are green
- No warning banners
- Interpretation says "You have full control"

### Scenario 2: Read-Only User
**Setup:**
- Connect as `cart_user` (not owner)
- Check privileges

**Expected:**
- Ownership pill is red
- SELECT pill is green
- Write access pill is yellow/red
- Interpretation explains read-only access
- Warning about write paths failing

### Scenario 3: Credential Drift
**Setup:**
- Table created by Flyway as `flyway` user
- Table owned by `cart_admin`
- Connected as `cart_user`

**Expected:**
- Yellow drift warning banner appears
- Shows all three different roles
- Timeline shows ownership mismatch
- Diagnostic pills reflect issues

### Scenario 4: Cascading Foreign Keys
**Setup:**
- Table has FK with `ON DELETE CASCADE`

**Expected:**
- Cascade warning pill in summary
- FK table shows yellow background for cascade FK
- Risk indicator shows "🟡 High impact"
- "Why this matters" explains cascade risk

## Edge Cases to Test

### No Flyway Info
**Test:** Table not created by Flyway
**Expected:** No timeline section, no drift detection

### No Constraints
**Test:** Table with no constraints
**Expected:** Constraints section shows "No constraints found"

### No Indexes
**Test:** Table with no indexes (unlikely)
**Expected:** Indexes section shows "No indexes found"

### Privileges Not Checked
**Test:** Don't click "Check Ownership & Grants"
**Expected:** 
- Only ownership pill shows
- No SELECT/Write access pills
- Button remains visible

## Performance Testing

### Response Times
- **Table Details:** Should load in < 1 second
- **Check Privileges:** Should complete in < 500ms
- **Scroll to Section:** Should be smooth (< 300ms)
- **Copy Diagnostics:** Should be instant

### Large Tables
Test with tables that have:
- 20+ indexes
- 30+ constraints
- Multiple cascading FKs

**Expected:** UI remains responsive, sections are collapsible

## Browser Compatibility

Test in:
- ✅ Chrome/Edge (primary)
- ✅ Firefox
- ✅ Safari

Verify:
- Pills wrap properly on narrow screens
- Tables scroll horizontally if needed
- Tooltips appear correctly
- Clipboard copy works

## API Testing

### Backend Endpoints
Test these endpoints directly:

```bash
# Get table details (should now include owner, currentUser, flywayInfo)
curl "http://localhost:8081/api/db/tables/introspect?connectionId=XXX&schema=public&table=cart_item"

# Check privileges
curl -X POST "http://localhost:8081/api/db/privileges:check" \
  -H "Content-Type: application/json" \
  -d '{
    "connectionId": "XXX",
    "schema": "PUBLIC",
    "tableName": "cart_item"
  }'
```

**Expected Response:**
```json
{
  "schema": "public",
  "table": "cart_item",
  "owner": "cart_admin",
  "currentUser": "cart_user",
  "indexes": [...],
  "constraints": [...],
  "flywayInfo": {
    "version": "1.0",
    "description": "Create cart schema",
    "installedBy": "flyway",
    "installedOn": "2025-12-22T10:30:00"
  }
}
```

## Troubleshooting

### Issue: Diagnostic pills not showing
**Check:**
- Backend returned owner and currentUser fields
- Privileges have been checked (for access pills)

### Issue: No Flyway info in timeline
**Check:**
- Flyway table exists: `SELECT * FROM flyway_schema_history LIMIT 1;`
- Migration script name contains table name
- Backend query is finding the migration

### Issue: Scroll not working
**Check:**
- Browser console for errors
- Refs are properly set on sections

### Issue: Copy not working
**Check:**
- Browser has clipboard permissions
- HTTPS or localhost (required for clipboard API)

## Success Criteria

All tests pass when:
- ✅ All diagnostic pills appear and are clickable
- ✅ Ownership check shows persistent diagnostic card
- ✅ Drift warning appears when applicable
- ✅ Timeline shows events in order
- ✅ Indexes and constraints are collapsible
- ✅ FK cascade warnings are visible
- ✅ "Why this matters" explanations work
- ✅ Copy diagnostics generates complete report
- ✅ No console errors
- ✅ Smooth user experience

## Next Steps After Testing

If all tests pass:
1. Test with real production-like data
2. Gather feedback from team
3. Consider additional enhancements:
   - Performance risk indicators
   - Column-level diagnostics
   - Historical tracking
   - Privilege recommendations

## Reporting Issues

When reporting issues, include:
1. Browser and version
2. Steps to reproduce
3. Expected vs actual behavior
4. Console errors (if any)
5. Screenshot
6. Connection details (without passwords!)

