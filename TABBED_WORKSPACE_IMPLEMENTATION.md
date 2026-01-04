# Tabbed Workspace Implementation

## Overview

Restructured the DB Doctor UI into a clean tabbed workspace that provides clear separation between single-environment diagnostics and multi-environment comparison workflows.

**Implementation Date:** January 3, 2026  
**Status:** ✅ Complete

## Architecture

### Layout Structure

```
┌─────────────────────────────────────────────────────────────────┐
│  PlatformTriage – DB Doctor (MVP)                               │
└─────────────────────────────────────────────────────────────────┘

┌──────────────────┬──────────────────────────────────────────────┐
│  LEFT PANEL      │  RIGHT PANEL (Tabbed Workspace)              │
│  (33%)           │  (67%)                                        │
├──────────────────┼──────────────────────────────────────────────┤
│                  │  ┌────────────────────────────────────────┐  │
│  Connection Form │  │ [Single Environment] [Compare Envs]   │  │
│  ┌────────────┐  │  └────────────────────────────────────────┘  │
│  │ Host       │  │                                              │
│  │ Port       │  │  Tab Content Area                            │
│  │ Database   │  │  ┌────────────────────────────────────────┐ │
│  │ Username   │  │  │                                        │ │
│  │ Password   │  │  │  • Single Environment Tab:             │ │
│  │ Schema     │  │  │    - Summary Panel                     │ │
│  └────────────┘  │  │    - SQL Sandbox                       │ │
│  [Connect]       │  │    - Results Panel                     │ │
│                  │  │                                        │ │
│  Action Buttons  │  │  • Compare Environments Tab:           │ │
│  ┌────────────┐  │  │    - Source (read-only)                │ │
│  │ Verify     │  │  │    - Target selector                   │ │
│  │ Flyway     │  │  │    - Comparison controls               │ │
│  │ List Tables│  │  │    - Comparison results                │ │
│  │ Inspect    │  │  │                                        │ │
│  │ Search     │  │  └────────────────────────────────────────┘ │
│  └────────────┘  │                                              │
└──────────────────┴──────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  Console Panel (Full Width)                                     │
└─────────────────────────────────────────────────────────────────┘
```

## Key Components

### Left Panel (Unchanged)
- **Connection Form** - Single source/active connection
- **Action Buttons** - Global actions for active connection:
  - Verify Connection
  - Flyway Health
  - List Tables
  - Inspect Table
  - Search Tables

### Right Panel (New Tabbed Structure)

#### Tab 1: Single Environment
All operations run against the active connection from the left panel.

**Components:**
- Summary Panel
- SQL Sandbox
- Results Panel

**Behavior:**
- Shows diagnostics for current connection only
- All results render in this tab
- No ambiguity about which DB is being inspected

#### Tab 2: Compare Environments
Treats active connection as Source and allows selecting a Target.

**Components:**
- Source Connection Display (read-only)
- Target Connection Selector
- Comparison Controls
- Comparison Results

**Behavior:**
- Source is locked to active connection
- Target is selected from dropdown
- All comparison results render in this tab only
- Prevents mixing comparison output with normal diagnostics

## UX Principles

### 1. Clear Separation
- **Single Environment Tab** = Normal diagnostics
- **Compare Environments Tab** = Schema drift detection
- No confusion about which workflow you're in

### 2. No Ambiguity
- Source is always the active connection (left panel)
- Target is explicitly selected in comparison tab
- Results stay in their respective tabs
- No "Which DB am I looking at?" problem

### 3. State Management

**Global State:**
- `activeConnectionId` - The source connection
- `connectionStatus` - Connected/disconnected
- `schema` - Active schema
- `sourceConnectionDetails` - Source connection metadata

**Single Environment Tab State:**
- `summaryData` - Summary information
- `results` - Action results
- `currentAction` - Last action performed

**Compare Environments Tab State:**
- `targetConnectionId` - Selected target connection
- `sourceEnvName` / `targetEnvName` - Display labels (DEV/PROD)
- `schema` - Comparison schema
- `specificTables` - Optional table filter
- `comparisonResult` - Comparison results

### 4. No Double Credentials UI
- Single connection form in left panel
- No second connection form needed
- Target selected from existing connections
- Scales well for future features

## Source Connection Display

### Read-Only Source Badge

```
┌─────────────────────────────────────────────────────────────────┐
│  Source: DEV                              [Active Connection]    │
├─────────────────────────────────────────────────────────────────┤
│  Host:          dev-host.example.com                             │
│  Database:      cartdb                                           │
│  User:          cart_user                                        │
│  Connection ID: pt-abc123-def456...                              │
└─────────────────────────────────────────────────────────────────┘
```

**Features:**
- Clearly labeled as "Source"
- Badge shows "Active Connection"
- All details visible at a glance
- Connection ID shown for reference
- Cannot be edited (read-only)

## Target Connection Selector

### Dropdown with Preview

```
┌─────────────────────────────────────────────────────────────────┐
│  Target Environment (Compare Against) ▼                         │
├─────────────────────────────────────────────────────────────────┤
│  prod-host.example.com / cartdb (cart_user) - public            │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  Target: PROD                                                    │
├─────────────────────────────────────────────────────────────────┤
│  Host:          prod-host.example.com                            │
│  Database:      cartdb                                           │
│  User:          cart_user                                        │
│  Schema:        public                                           │
│  Access Level:  Will be determined during comparison             │
└─────────────────────────────────────────────────────────────────┘
```

## Comparison Mode Banner

At the top of comparison results:

### Full Comparison
```
┌─────────────────────────────────────────────────────────────────┐
│  ✅ Full Comparison: Full schema comparison available for both  │
│     environments.                                                │
└─────────────────────────────────────────────────────────────────┘
```

### Partial Comparison
```
┌─────────────────────────────────────────────────────────────────┐
│  ⚠️ Partial Comparison: PROD metadata access is limited. Some  │
│     drift results may be unknown.                               │
└─────────────────────────────────────────────────────────────────┘
```

### Blocked Comparison
```
┌─────────────────────────────────────────────────────────────────┐
│  ❌ Blocked Comparison: PROD connection lacks required metadata │
│     access.                                                      │
└─────────────────────────────────────────────────────────────────┘
```

## Capability Matrix (Collapsed by Default)

```
┌─────────────────────────────────────────────────────────────────┐
│  ▶ Capability Matrix                                            │
└─────────────────────────────────────────────────────────────────┘

When expanded:

┌─────────────────────────────────────────────────────────────────┐
│  ▼ Capability Matrix                                            │
├─────────────────────────────────────────────────────────────────┤
│  Capability      │  DEV (Source)  │  PROD (Target)              │
├──────────────────┼────────────────┼─────────────────────────────┤
│  Connect         │  ✅            │  ✅                         │
│  Identity        │  ✅            │  ✅                         │
│  Tables          │  ✅            │  ✅                         │
│  Columns         │  ✅            │  🔒                         │
│  Constraints     │  ✅            │  🔒                         │
│  Indexes         │  ✅            │  🔒                         │
│  Flyway          │  ✅            │  ✅                         │
│  Grants          │  ✅            │  🔒                         │
└──────────────────┴────────────────┴─────────────────────────────┘
```

**Benefits:**
- Collapsed by default to reduce clutter
- Shows side-by-side comparison
- Clear indication of what's accessible
- 🔒 icon for blocked capabilities
- Keeps experience "diagnostic" not "error"

## Files Created/Modified

### New Files (1)
1. `SingleEnvironmentTab.jsx` - Wrapper for single-environment diagnostics

### Modified Files (2)
1. `App.jsx` - Added tabbed workspace structure
2. `EnvironmentComparisonPanel.jsx` - Enhanced with source display and improved layout

### Changes Summary

**App.jsx:**
- Added `Tabs` and `Tab` components
- Added `activeTab` state
- Added `sourceConnectionDetails` state
- Split right panel into tabbed interface
- Moved single-environment components to Tab 1
- Moved comparison panel to Tab 2
- Fetch source details on connection

**EnvironmentComparisonPanel.jsx:**
- Added source connection display (read-only)
- Added "Active Connection" badge
- Removed outer Paper wrapper (now inside tab)
- Improved capability matrix (collapsed accordion)
- Enhanced comparison mode banner
- Better visual hierarchy

**SingleEnvironmentTab.jsx:**
- New wrapper component
- Contains Summary, SQL Sandbox, Results
- Clean separation of concerns

## Benefits

### For Users
1. **Clear Mental Model** - Tabs match workflows
2. **No Confusion** - Results stay in their tab
3. **Better Organization** - Related features grouped
4. **Scalable** - Easy to add new tabs
5. **Professional** - Clean, modern interface

### For Operations
1. **Reduced Support** - Less confusion about which DB
2. **Better UX** - Intuitive navigation
3. **Easier Training** - Clear structure
4. **Future-Ready** - Can add more tabs

## Tab Naming

Instead of generic names, using purpose-driven labels:
- ✅ **"Single Environment"** - Clear what it does
- ✅ **"Compare Environments"** - Clear what it does

Not:
- ❌ "Diagnostics" - Too vague
- ❌ "Comparison" - Too generic
- ❌ "Tab 1" / "Tab 2" - Meaningless

## Future Tabs (Scalability)

The tabbed structure makes it easy to add:
1. **Privilege Simulation** - Test queries with different roles
2. **Diagnostic Export** - Generate reports for JIRA/postmortems
3. **Migration Planner** - Generate DDL to fix drift
4. **Performance Analysis** - Query performance comparison
5. **Data Sampling** - Safe data preview

## Handling PROD Access Limitations

The Compare Environments tab explicitly handles limited access:

1. **Comparison Mode Banner** - Sets expectations upfront
2. **Capability Matrix** - Shows what's accessible
3. **Section-Level Degradation** - Each section handles unavailability
4. **No Hard Failures** - Graceful degradation throughout
5. **Actionable Guidance** - Suggests next steps

## Testing Checklist

- [ ] Tab switching works smoothly
- [ ] Single Environment tab shows all components
- [ ] Compare Environments tab shows source display
- [ ] Source connection details load correctly
- [ ] Target selector shows available connections
- [ ] Comparison results render in correct tab
- [ ] Single environment results don't leak to comparison tab
- [ ] Comparison results don't leak to single environment tab
- [ ] Console messages work across tabs
- [ ] Tab state persists during session
- [ ] Source badge shows "Active Connection"
- [ ] Capability matrix is collapsed by default
- [ ] Comparison mode banner shows correct status

## Migration Notes

- ✅ **Backward Compatible** - No breaking changes
- ✅ **No Data Migration** - Uses existing state
- ✅ **Progressive Enhancement** - Better UX, same functionality
- ✅ **No API Changes** - Backend unchanged

## Performance Considerations

- **Lazy Loading** - Tab content only renders when active
- **State Preservation** - Tab state preserved when switching
- **Efficient Rendering** - React optimizations applied
- **No Unnecessary Re-renders** - Proper dependency arrays

## Accessibility

- ✅ Keyboard navigation between tabs
- ✅ ARIA labels on tabs
- ✅ Focus management
- ✅ Screen reader friendly
- ✅ High contrast support

## Conclusion

The tabbed workspace structure provides:
1. **Clear separation** between workflows
2. **No ambiguity** about which DB is active
3. **Scalable architecture** for future features
4. **Professional UX** that matches user expectations
5. **Explicit handling** of PROD access limitations

This is a significant UX improvement that makes DB Doctor more intuitive and professional while maintaining all existing functionality.

---

**Status:** ✅ Complete and Ready for Testing  
**Breaking Changes:** None  
**Migration Required:** None  
**User Training:** Minimal (intuitive tabs)

