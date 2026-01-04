# Frontend Implementation Plan

## Overview
Complete rewrite of EnvironmentComparisonPanel to implement 3-level disclosure pattern.

## Components to Add

### 1. KPI Cards Section
```jsx
const renderKPICards = (kpis) => (
  <Grid container spacing={2} sx={{ mb: 3 }}>
    <Grid item xs={4}>
      <Card variant="outlined" sx={{ borderLeft: 6, borderLeftColor: 'error.main' }}>
        <CardContent>
          <Typography variant="h4" color="error">{kpis.compatibilityErrors}</Typography>
          <Typography variant="body2">Compatibility Errors (Critical)</Typography>
        </CardContent>
      </Card>
    </Grid>
    <Grid item xs={4}>
      <Card variant="outlined" sx={{ borderLeft: 6, borderLeftColor: 'warning.main' }}>
        <CardContent>
          <Typography variant="h4" color="warning">{kpis.missingMigrations}</Typography>
          <Typography variant="body2">Missing Migrations</Typography>
        </CardContent>
      </Card>
    </Grid>
    <Grid item xs={4}>
      <Card variant="outlined" sx={{ borderLeft: 6, borderLeftColor: 'info.main' }}>
        <CardContent>
          <Typography variant="h4" color="info">{kpis.performanceWarnings}</Typography>
          <Typography variant="body2">Performance Warnings</Typography>
        </CardContent>
      </Card>
    </Grid>
  </Grid>
);
```

### 2. Collapsible Conclusions
```jsx
const [expandedConclusion, setExpandedConclusion] = useState(null);

const renderConclusions = (conclusions) => (
  <Box sx={{ mb: 2 }}>
    <Typography variant="h6" gutterBottom>Diagnostic Conclusions</Typography>
    {conclusions.map((conclusion, idx) => (
      <Card key={idx} variant="outlined" sx={{ mb: 1 }}>
        <CardActionArea onClick={() => setExpandedConclusion(expanded === idx ? null : idx)}>
          <CardContent>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              {getSeverityIcon(conclusion.severity)}
              <Chip label={conclusion.category} size="small" />
              <Typography variant="body1" sx={{ flexGrow: 1 }}>
                {conclusion.finding}
              </Typography>
              {expandedConclusion === idx ? <ExpandLessIcon /> : <ExpandMoreIcon />}
            </Box>
          </CardContent>
        </CardActionArea>
        
        <Collapse in={expandedConclusion === idx}>
          <CardContent>
            {/* Evidence, impact, actions */}
          </CardContent>
        </Collapse>
      </Card>
    ))}
  </Box>
);
```

### 3. Top 5 Blast Radius
```jsx
const [showAllBlastRadius, setShowAllBlastRadius] = useState(false);

const renderBlastRadius = (blastRadius) => {
  const displayItems = showAllBlastRadius ? blastRadius : blastRadius.slice(0, 5);
  
  return (
    <Card variant="outlined" sx={{ mb: 2 }}>
      <CardContent>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
          <Typography variant="h6">
            Blast Radius {!showAllBlastRadius && blastRadius.length > 5 && `(Top 5 of ${blastRadius.length})`}
          </Typography>
          {blastRadius.length > 5 && (
            <Button size="small" onClick={() => setShowAllBlastRadius(!showAllBlastRadius)}>
              {showAllBlastRadius ? 'Show Top 5' : `Show All (${blastRadius.length})`}
            </Button>
          )}
        </Box>
        {/* Render items with grouping support */}
      </CardContent>
    </Card>
  );
};
```

### 4. Auto-Expand Logic for Drift Sections
```jsx
const shouldSectionExpand = (section) => {
  if (section.sectionName === 'Columns') {
    return section.driftItems.some(item => item.severity === 'ERROR');
  }
  if (section.sectionName === 'Indexes') {
    return section.driftItems.some(item => 
      item.attribute === 'exists' && 
      item.sourceValue === true && 
      item.targetValue === false
    );
  }
  if (section.sectionName === 'Constraints') {
    return section.differCount > 0;
  }
  return false;
};

<Accordion defaultExpanded={shouldSectionExpand(section)}>
```

### 5. Fixed Identity Chips
```jsx
// Extract from response correctly
const sourceIdentity = `${sourceConn.host}:${sourceConn.port}/${sourceConn.database} (${sourceConn.username})`;
```

### 6. Primary CTA Row
```jsx
<Box sx={{ display: 'flex', gap: 2, mb: 3 }}>
  <Button 
    variant="contained" 
    onClick={() => scrollToSection('drift-sections')}
  >
    Show Drift Details
  </Button>
  <Button 
    variant="outlined"
    startIcon={<ContentCopyIcon />}
    onClick={exportDiagnostics}
  >
    Copy Diagnostics
  </Button>
</Box>
```

## State Management

```jsx
const [expandedConclusion, setExpandedConclusion] = useState(null);
const [showAllBlastRadius, setShowAllBlastRadius] = useState(false);
const [showOnlyDifferences, setShowOnlyDifferences] = useState(true); // Keep default ON
```

## Default View Order

1. Comparison Mode Banner
2. Identity Chips
3. KPI Cards
4. Primary CTA Row
5. Flyway Comparison (expanded)
6. Missing Migrations (if any)
7. Diagnostic Conclusions (collapsed)
8. Comparison Scope Filters (collapsed accordion)
9. Drift Analysis (collapsed, auto-expand logic)
10. Blast Radius (top 5, collapsed accordion)
11. Capability Matrix (collapsed)
12. Privilege Request (if needed)

## Layout Changes Summary

**Before**:
- Everything expanded
- 500+ rows visible
- Repeated symptoms
- Multiple scrolling required

**After**:
- KPIs + Flyway visible
- 3 collapsed conclusions
- 5 blast radius items max (initially)
- Everything fits in viewport

## Implementation Steps

1. ✅ Add KPI cards rendering
2. ✅ Convert conclusions to collapsible cards
3. ✅ Add top 5 blast radius logic
4. ✅ Implement auto-expand for drift sections
5. ✅ Fix identity chip duplication
6. ✅ Simplify button layout
7. ✅ Reorder sections for optimal flow
8. ✅ Test all interactions

