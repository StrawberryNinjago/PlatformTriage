import { useState, useEffect } from 'react';
import {
  Box,
  Typography,
  Button,
  TextField,
  Alert,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  Chip,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Card,
  CardContent,
  Grid,
  Divider,
  CircularProgress,
  MenuItem,
  Select,
  FormControl,
  InputLabel,
  Badge,
  Tooltip,
  IconButton,
  Snackbar,
  Paper,
  ToggleButton,
  ToggleButtonGroup,
  List,
  ListItem,
  ListItemText,
  ListItemIcon,
  Collapse,
  CardActionArea,
  Menu
} from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ExpandLessIcon from '@mui/icons-material/ExpandLess';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import WarningIcon from '@mui/icons-material/Warning';
import LockIcon from '@mui/icons-material/Lock';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import FilterListIcon from '@mui/icons-material/FilterList';
import InfoIcon from '@mui/icons-material/Info';
import SearchIcon from '@mui/icons-material/Search';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import DownloadIcon from '@mui/icons-material/Download';
import { apiService } from '../services/apiService';

export default function EnvironmentComparisonPanel({ 
  isConnected, 
  currentConnectionId,
  sourceConnectionDetails,
  externalComparisonResult,
  onCompare 
}) {
  const [sourceConnectionId, setSourceConnectionId] = useState('');
  const [targetConnectionId, setTargetConnectionId] = useState('');
  const [comparisonResult, setComparisonResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [availableConnections, setAvailableConnections] = useState([]);
  
  // Filtering state
  const [showOnlyDifferences, setShowOnlyDifferences] = useState(true);
  const [severityFilter, setSeverityFilter] = useState('all');
  const [searchQuery, setSearchQuery] = useState('');
  const createEmptySuggestions = () => ({
    tables: [],
    columns: [],
    indexes: [],
    constraints: []
  });
  const [searchSuggestions, setSearchSuggestions] = useState(createEmptySuggestions);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [snackbarOpen, setSnackbarOpen] = useState(false);
  const [snackbarMessage, setSnackbarMessage] = useState('');
  const [exportSuccess, setExportSuccess] = useState(false);
  
  // Disclosure state (3-level pattern)
  const [expandedConclusion, setExpandedConclusion] = useState(null);
  const [showAllBlastRadius, setShowAllBlastRadius] = useState(false);
  const [actionMenuAnchor, setActionMenuAnchor] = useState(null);

  // Keep available connection list current when active connection or external compare result changes.
  useEffect(() => {
    if (isConnected) {
      fetchAvailableConnections();
    }
  }, [isConnected, currentConnectionId, externalComparisonResult]);

  useEffect(() => {
    if (externalComparisonResult) {
      setComparisonResult(externalComparisonResult);
    }
  }, [externalComparisonResult]);

  useEffect(() => {
    if (availableConnections.length === 0) {
      return;
    }

    if (!sourceConnectionId) {
      const hasCurrent = availableConnections.some((conn) => conn.connectionId === currentConnectionId);
      const preferredSource = hasCurrent
        ? currentConnectionId
        : availableConnections[0].connectionId;
      setSourceConnectionId(preferredSource);

      if (!targetConnectionId || targetConnectionId === preferredSource) {
        const preferredTarget = availableConnections.find((conn) => conn.connectionId !== preferredSource);
        if (preferredTarget) {
          setTargetConnectionId(preferredTarget.connectionId);
        }
      }
      return;
    }

    if (!targetConnectionId || targetConnectionId === sourceConnectionId) {
      const preferredTarget = availableConnections.find((conn) => conn.connectionId !== sourceConnectionId);
      if (preferredTarget) {
        setTargetConnectionId(preferredTarget.connectionId);
      }
    }
  }, [availableConnections, currentConnectionId, sourceConnectionId, targetConnectionId]);

  const fetchAvailableConnections = async () => {
    try {
      const response = await apiService.listConnections();
      // Remove duplicates based on connectionId
      const uniqueConnections = response.data.filter((conn, index, self) =>
        index === self.findIndex((c) => c.connectionId === conn.connectionId)
      );
      setAvailableConnections(uniqueConnections);
    } catch (err) {
      console.error('Failed to fetch connections:', err);
    }
  };

  const handleCompare = async () => {
    if (!sourceConnectionId || !targetConnectionId) {
      setError('Please select both source and target environments');
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const sourceConn = availableConnections.find(c => c.connectionId === sourceConnectionId);
      const targetConn = availableConnections.find(c => c.connectionId === targetConnectionId);
      
      const sourceEnvName = sourceConn ? `${sourceConn.host}:${sourceConn.port}/${sourceConn.database}` : 'Source';
      const targetEnvName = targetConn ? `${targetConn.host}:${targetConn.port}/${targetConn.database}` : 'Target';

      const result = await onCompare(
        sourceConnectionId,
        targetConnectionId,
        sourceEnvName,
        targetEnvName,
        'public', // default schema
        null // no specific tables filter
      );

      setComparisonResult(result.data);
    } catch (err) {
      setError(err.response?.data?.message || err.message);
    } finally {
      setLoading(false);
    }
  };

  const copyToClipboard = (text, label) => {
    navigator.clipboard.writeText(text);
    setSnackbarMessage(`${label} copied to clipboard`);
    setSnackbarOpen(true);
  };

  const exportDiagnostics = () => {
    const diagnostics = {
      timestamp: comparisonResult.timestamp,
      sourceIdentity: comparisonResult.sourceIdentity,
      targetIdentity: comparisonResult.targetIdentity,
      comparisonMode: comparisonResult.comparisonMode,
      sourceCapabilities: comparisonResult.sourceCapabilities,
      targetCapabilities: comparisonResult.targetCapabilities,
      flywayComparison: comparisonResult.flywayComparison,
      flywayMigrationGap: comparisonResult.flywayMigrationGap,
      driftSections: comparisonResult.driftSections,
      blastRadius: comparisonResult.blastRadius,
      conclusions: comparisonResult.conclusions,
      missingPrivileges: comparisonResult.missingPrivileges
    };
    
    copyToClipboard(JSON.stringify(diagnostics, null, 2), 'Diagnostics');
  };

  const handleExportForJira = async () => {
    if (!currentConnectionId) return;
    try {
      const response = await apiService.exportDiagnostics(currentConnectionId);
      const blob = new Blob([JSON.stringify(response.data, null, 2)], { type: 'application/json' });
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `db-doctor-diagnostics-${new Date().toISOString().split('T')[0]}.json`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(url);
      
      setExportSuccess(true);
      setTimeout(() => setExportSuccess(false), 2000);
    } catch (err) {
      console.error('Failed to export diagnostics:', err);
    }
  };

  const getComparisonModeColor = (mode) => {
    switch (mode) {
      case 'FULL': return 'success';
      case 'PARTIAL': return 'warning';
      case 'BLOCKED': return 'error';
      default: return 'default';
    }
  };

  const getSeverityIcon = (severity) => {
    switch (severity) {
      case 'ERROR': return <ErrorIcon color="error" fontSize="small" />;
      case 'WARN': return <WarningIcon color="warning" fontSize="small" />;
      case 'INFO': return <CheckCircleIcon color="info" fontSize="small" />;
      default: return null;
    }
  };

  const getStatusIcon = (status) => {
    switch (status) {
      case 'MATCH': return <CheckCircleIcon color="success" fontSize="small" />;
      case 'DIFFER': return <ErrorIcon color="error" fontSize="small" />;
      case 'UNKNOWN': return <LockIcon color="disabled" fontSize="small" />;
      default: return null;
    }
  };

  const getRiskColor = (riskLevel) => {
    switch (riskLevel) {
      case 'High': return 'error';
      case 'Medium': return 'warning';
      case 'Low': return 'info';
      default: return 'default';
    }
  };

  const norm = (val) => (val ?? '').toString().toUpperCase();

  const scrollToSection = (sectionId) => {
    const element = document.getElementById(sectionId);
    if (element) {
      element.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  };

  const renderKPICards = (kpis) => {
    if (!kpis) return null;
    
    return (
      <Grid container spacing={2} sx={{ mb: 3 }}>
        <Grid item xs={12} sm={4}>
          <Card 
            variant="outlined" 
            sx={{ 
              borderLeft: 6, 
              borderLeftColor: kpis.compatibilityErrors > 0 ? 'error.main' : 'success.main',
              cursor: kpis.compatibilityErrors > 0 ? 'pointer' : 'default'
            }}
            onClick={() => kpis.compatibilityErrors > 0 && scrollToSection('drift-sections')}
          >
            <CardContent>
              <Typography variant="h3" color={kpis.compatibilityErrors > 0 ? 'error' : 'success'}>
                {kpis.compatibilityErrors}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Compatibility Errors
              </Typography>
              <Typography variant="caption" color="error">
                {kpis.compatibilityErrors > 0 ? 'CRITICAL' : 'None detected'}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={4}>
          <Card 
            variant="outlined" 
            sx={{ 
              borderLeft: 6, 
              borderLeftColor: kpis.missingMigrations > 0 ? 'warning.main' : 'success.main',
              cursor: kpis.missingMigrations > 0 ? 'pointer' : 'default'
            }}
            onClick={() => kpis.missingMigrations > 0 && scrollToSection('missing-migrations')}
          >
            <CardContent>
              <Typography variant="h3" color={kpis.missingMigrations > 0 ? 'warning' : 'success'}>
                {kpis.missingMigrations}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Missing Migrations
              </Typography>
              <Typography variant="caption" color={kpis.missingMigrations > 0 ? 'warning' : 'success'}>
                {kpis.missingMigrations > 0 ? 'Needs attention' : 'Up to date'}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={4}>
          <Card 
            variant="outlined" 
            sx={{ 
              borderLeft: 6, 
              borderLeftColor: kpis.performanceWarnings > 0 ? 'info.main' : 'success.main',
              cursor: kpis.performanceWarnings > 0 ? 'pointer' : 'default'
            }}
            onClick={() => kpis.performanceWarnings > 0 && scrollToSection('indexes-section')}
          >
            <CardContent>
              <Typography variant="h3" color={kpis.performanceWarnings > 0 ? 'info' : 'success'}>
                {kpis.performanceWarnings}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Performance Warnings
              </Typography>
              <Typography variant="caption" color={kpis.performanceWarnings > 0 ? 'info' : 'success'}>
                {kpis.performanceWarnings > 0 ? 'Review indexes' : 'All good'}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    );
  };

  const parseSearchQuery = (query) => {
    if (!query) return { type: null, term: '' };
    
    // Check for power user syntax: table:cart_item, column:promo_code, etc.
    const match = query.match(/^(table|column|index|constraint|migration):(.+)$/i);
    if (match) {
      return { type: match[1].toLowerCase(), term: match[2].toLowerCase() };
    }
    
    // Plain text search
    return { type: null, term: query.toLowerCase() };
  };

  const matchesSearch = (item, searchType, searchTerm) => {
    if (!searchTerm) return true;
    
    const objectName = (item.objectName || '').toLowerCase();
    const message = (item.message || '').toLowerCase();
    
    // Support wildcards (* → .*)
    const regexTerm = searchTerm.replace(/\*/g, '.*');
    const regex = new RegExp(regexTerm, 'i'); // case-insensitive
    
    // Type-specific matching
    if (searchType) {
      switch (searchType) {
        case 'table':
          // Match table name (before first dot or entire name)
          const tableName = objectName.includes('.') ? objectName.split('.')[0] : objectName;
          return regex.test(tableName);
        case 'column':
          // Match column name (after last dot)
          return objectName.includes('.') && regex.test(objectName.split('.').pop());
        case 'index':
          return item.category === 'Performance' && regex.test(objectName);
        case 'constraint':
          return item.category === 'Compatibility' && 
                 item.attribute === 'type' && 
                 regex.test(objectName);
        case 'migration':
          // Search in message for migration script names
          return regex.test(message);
        default:
          return regex.test(objectName) || regex.test(message);
      }
    }
    
    // Plain text: match anything containing the term
    // This catches cart_item in "cart_item", "cart_item.promo_code", "cart_item_cart_id_fkey", etc.
    const matches = regex.test(objectName) || regex.test(message);
    return matches;
  };

  const filterDriftItems = (items) => {
    let filtered = items;
    const initialCount = items.length;

    // Filter by "only differences"
    if (showOnlyDifferences) {
      filtered = filtered.filter(item => norm(item.status) !== 'MATCH');
    }

    // Filter by severity
    if (severityFilter !== 'all') {
      filtered = filtered.filter(item => norm(item.severity) === severityFilter.toUpperCase());
    }

    // Filter by search query with type support
    if (searchQuery) {
      const { type, term } = parseSearchQuery(searchQuery);
      console.log('[Filter] Searching for:', { query: searchQuery, type, term });
      filtered = filtered.filter(item => {
        const matches = matchesSearch(item, type, term);
        if (matches) {
          console.log('[Filter] Match:', item.objectName, item.category);
        }
        return matches;
      });
    }

    if (searchQuery && filtered.length !== initialCount) {
      console.log(`[Filter] Filtered ${initialCount} → ${filtered.length} items for query "${searchQuery}"`);
    }

    return filtered;
  };

  const generateSearchSuggestions = (query) => {
    if (!query || query.length < 2 || !comparisonResult) {
      setSearchSuggestions(createEmptySuggestions());
      setShowSuggestions(false);
      return;
    }
    
    const lowerQuery = query.toLowerCase();
    const suggestions = {
      tables: new Set(),
      columns: new Set(),
      indexes: new Set(),
      constraints: new Set()
    };
    
    console.log('[Suggestions] Generating for query:', query);
    
    comparisonResult.driftSections.forEach(section => {
      console.log(`[Suggestions] Section: ${section.sectionName}, Items: ${section.driftItems.length}`);
      
      section.driftItems.forEach(item => {
        const objName = item.objectName || '';
        const lowerObjName = objName.toLowerCase();
        
        // Match if query appears anywhere in the object name
        if (!lowerObjName.includes(lowerQuery)) {
          return; // Skip this item
        }
        
        if (objName.includes('.')) {
          // Has dot: format is "table.column", "table.index", etc.
          const parts = objName.split('.');
          const tableName = parts[0];
          const childName = parts[parts.length - 1];
          
          // Always add the table if it matches
          if (tableName.toLowerCase().includes(lowerQuery)) {
            suggestions.tables.add(tableName);
          }
          
          // Add to appropriate category based on section
          if (section.sectionName === 'Columns') {
            suggestions.columns.add(objName);
          } else if (section.sectionName === 'Indexes') {
            suggestions.indexes.add(objName);
          } else if (section.sectionName === 'Constraints') {
            suggestions.constraints.add(objName);
          }
        } else {
          // No dot: likely a table name or a simple constraint/index name
          if (section.sectionName === 'Tables') {
            suggestions.tables.add(objName);
          } else if (section.sectionName === 'Indexes') {
            suggestions.indexes.add(objName);
          } else if (section.sectionName === 'Constraints') {
            suggestions.constraints.add(objName);
          } else {
            // Fallback: treat as table name
            suggestions.tables.add(objName);
          }
        }
      });
    });
    
    const result = {
      tables: Array.from(suggestions.tables).slice(0, 5),
      columns: Array.from(suggestions.columns).slice(0, 5),
      indexes: Array.from(suggestions.indexes).slice(0, 5),
      constraints: Array.from(suggestions.constraints).slice(0, 5)
    };
    
    console.log('[Suggestions] Result:', result);
    setSearchSuggestions(result);
    setShowSuggestions(true);
  };

  const handleSearchChange = (value) => {
    setSearchQuery(value);
    if (value.length >= 2) {
      generateSearchSuggestions(value);
    } else {
      setSearchSuggestions(createEmptySuggestions());
      setShowSuggestions(false);
    }
  };

  const hasAnySuggestions =
    searchSuggestions.tables.length > 0 ||
    searchSuggestions.columns.length > 0 ||
    searchSuggestions.indexes.length > 0 ||
    searchSuggestions.constraints.length > 0;

  const renderDriftSection = (section) => {
    if (!section.availability.available) {
      return (
        <Card variant="outlined" sx={{ mb: 2, bgcolor: '#f5f5f5' }} id={section.sectionName.toLowerCase() + '-section'}>
          <CardContent>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
              <LockIcon color="disabled" />
              <Typography variant="h6">
                {section.sectionName} (Unavailable)
              </Typography>
            </Box>
            <Alert severity="warning" sx={{ mb: 1 }}>
              {section.availability.unavailabilityReason}
            </Alert>
            <Typography variant="body2" color="text.secondary">
              <strong>Needed:</strong> {section.availability.neededPrivilege}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              <strong>Impact:</strong> {section.availability.impact}
            </Typography>
          </CardContent>
        </Card>
      );
    }

    const filteredItems = filterDriftItems(section.driftItems);
    const matchItems = section.driftItems.filter(i => norm(i.status) === 'MATCH');
    const differItems = section.driftItems.filter(i => norm(i.status) === 'DIFFER');
    const showMatchNotice = !showOnlyDifferences && matchItems.length === 0 && section.matchCount > 0;
    
    // Count filtered items
    const filteredDifferCount = filteredItems.filter(i => norm(i.status) === 'DIFFER').length;
    const isFiltered = searchQuery || severityFilter !== 'all' || showOnlyDifferences;
    const showFilteredCounts = isFiltered && filteredDifferCount !== section.differCount;

    // Auto-expand logic for important errors
    const shouldAutoExpand = () => {
      if (section.sectionName === 'Columns') {
        return section.driftItems.some(item => norm(item.severity) === 'ERROR');
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
      return section.differCount > 0;
    };

    return (
      <Accordion defaultExpanded={shouldAutoExpand()} id={section.sectionName.toLowerCase() + '-section'}>
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, width: '100%' }}>
            <Typography variant="h6">{section.sectionName}</Typography>
            <Box sx={{ display: 'flex', gap: 1 }}>
              <Chip 
                label={`${section.matchCount} Match`} 
                color="success" 
                size="small" 
              />
              {section.differCount > 0 && (
                <Chip 
                  label={showFilteredCounts 
                    ? `${filteredDifferCount} of ${section.differCount} Differ` 
                    : `${section.differCount} Differ`
                  } 
                  color="error" 
                  size="small" 
                />
              )}
              {section.unknownCount > 0 && (
                <Chip 
                  label={`${section.unknownCount} Unknown`} 
                  color="default" 
                  size="small" 
                />
              )}
              {showFilteredCounts && (
                <Chip 
                  label="Filtered" 
                  variant="outlined"
                  size="small"
                  color="info"
                />
              )}
            </Box>
          </Box>
        </AccordionSummary>
        <AccordionDetails>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            {section.description}
          </Typography>
          
          {section.availability.partial && (
            <Alert severity="warning" sx={{ mb: 2 }}>
              Partial comparison: {section.availability.unavailabilityReason}
            </Alert>
          )}

          {filteredItems.length === 0 ? (
            searchQuery ? (
              <Alert severity="info">
                No items match your search "{searchQuery}" in this section. 
                Try adjusting your filters or search term.
              </Alert>
            ) : (
              <Alert severity="success">No differences detected</Alert>
            )
          ) : (
            <>
              {!showOnlyDifferences && section.matchCount > 100 && (
                <Alert severity="info" sx={{ mb: 2 }}>
                  Showing first 100 of {section.matchCount} matching items. 
                  Use "Only show differences" toggle to reduce clutter.
                </Alert>
              )}
              {!showOnlyDifferences && showMatchNotice && (
                <Alert severity="info" sx={{ mb: 2 }}>
                  No matching rows are loaded for this section (match count reported: {section.matchCount}). 
                  This is expected when the backend caps matches for large schemas.
                </Alert>
              )}
              <TableContainer>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell sx={{ width: 60 }}>Status</TableCell>
                    <TableCell sx={{ width: 200, minWidth: 150 }}>Object</TableCell>
                    <TableCell sx={{ width: 100 }}>Attribute</TableCell>
                    <TableCell sx={{ width: 100 }}>Source Value</TableCell>
                    <TableCell sx={{ width: 100 }}>Target Value</TableCell>
                    <TableCell sx={{ width: 120 }}>Category</TableCell>
                    <TableCell sx={{ width: 100 }}>Severity</TableCell>
                    <TableCell sx={{ width: 80 }}>Risk</TableCell>
                    <TableCell sx={{ minWidth: 250 }}>Message</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {filteredItems.map((item, idx) => {
                    const statusNorm = norm(item.status);
                    const severityNorm = norm(item.severity);
                    return (
                    <TableRow 
                      key={idx}
                      sx={{ 
                        bgcolor: severityNorm === 'ERROR' ? '#ffebee' : 
                                severityNorm === 'WARN' ? '#fff3e0' : 
                                statusNorm === 'MATCH' ? '#f5f5f5' : 'inherit',
                        opacity: statusNorm === 'MATCH' ? 0.7 : 1
                      }}
                    >
                      <TableCell sx={{ width: 60 }}>{getStatusIcon(statusNorm)}</TableCell>
                      <TableCell sx={{ width: 200, minWidth: 150, wordBreak: 'break-word' }}>
                        <code style={{ 
                          color: statusNorm === 'MATCH' ? '#757575' : 'inherit',
                          fontSize: '0.85rem',
                          wordBreak: 'break-word'
                        }}>
                          {item.objectName}
                        </code>
                      </TableCell>
                      <TableCell sx={{ width: 100 }}>{item.attribute}</TableCell>
                      <TableCell sx={{ width: 100, wordBreak: 'break-word' }}>
                        {String(item.sourceValue)}
                      </TableCell>
                      <TableCell sx={{ width: 100, wordBreak: 'break-word' }}>
                        {String(item.targetValue)}
                      </TableCell>
                      <TableCell sx={{ width: 120 }}>
                        <Chip label={item.category} size="small" variant="outlined" />
                      </TableCell>
                      <TableCell sx={{ width: 100 }}>
                        <Chip 
                          label={severityNorm || 'INFO'} 
                          color={severityNorm === 'ERROR' ? 'error' : 
                                 severityNorm === 'WARN' ? 'warning' : 
                                 'success'}
                          size="small"
                        />
                      </TableCell>
                      <TableCell sx={{ width: 80 }}>
                        {item.riskLevel && (
                          <Chip 
                            label={item.riskLevel} 
                            color={getRiskColor(item.riskLevel)}
                            size="small"
                          />
                        )}
                      </TableCell>
                      <TableCell sx={{ 
                        minWidth: 250,
                        wordBreak: 'break-word',
                        whiteSpace: 'normal',
                        color: statusNorm === 'MATCH' ? '#757575' : 'inherit'
                      }}>
                        {item.message}
                      </TableCell>
                    </TableRow>
                  )})}
                </TableBody>
              </Table>
            </TableContainer>
            </>
          )}
        </AccordionDetails>
      </Accordion>
    );
  };

  const renderFlywayComparison = (flyway) => {
    if (!flyway.available) {
      return (
        <Alert severity="info" sx={{ mb: 2 }}>
          {flyway.message}
        </Alert>
      );
    }

    return (
      <Card variant="outlined" sx={{ mb: 2 }} id="flyway-comparison">
        <CardContent>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
            <Typography variant="h6">
              Flyway Comparison
            </Typography>
            <Chip 
              label={flyway.versionMatch ? 'Versions Match' : 'Version Mismatch'}
              color={flyway.versionMatch ? 'success' : 'error'}
            />
          </Box>
          <Grid container spacing={2}>
            <Grid item xs={6}>
              <Typography variant="body2" color="text.secondary">Source Version</Typography>
              <Typography variant="body1">{flyway.sourceLatestVersion || 'N/A'}</Typography>
            </Grid>
            <Grid item xs={6}>
              <Typography variant="body2" color="text.secondary">Target Version</Typography>
              <Typography variant="body1">{flyway.targetLatestVersion || 'N/A'}</Typography>
            </Grid>
            <Grid item xs={6}>
              <Typography variant="body2" color="text.secondary">Source Failed Count</Typography>
              <Typography variant="body1">{flyway.sourceFailedCount || 0}</Typography>
            </Grid>
            <Grid item xs={6}>
              <Typography variant="body2" color="text.secondary">Target Failed Count</Typography>
              <Typography variant="body1">{flyway.targetFailedCount || 0}</Typography>
            </Grid>
          </Grid>
          <Alert 
            severity={flyway.versionMatch ? 'success' : 'error'} 
            sx={{ mt: 2 }}
          >
            {flyway.message}
          </Alert>
        </CardContent>
      </Card>
    );
  };

  const renderMissingMigrations = (migrationGap) => {
    if (!migrationGap || !migrationGap.detectable || migrationGap.missingMigrations.length === 0) {
      return null;
    }

    return (
      <Card variant="outlined" sx={{ mb: 2 }} id="missing-migrations">
        <CardContent>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
            <Typography variant="h6">
              Missing Migrations
            </Typography>
            <Chip 
              label={`${migrationGap.missingMigrations.length} missing`}
              color="error"
            />
          </Box>
          <Alert severity="error" sx={{ mb: 2 }}>
            {migrationGap.message}
          </Alert>
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Rank</TableCell>
                  <TableCell>Version</TableCell>
                  <TableCell>Description</TableCell>
                  <TableCell>Script</TableCell>
                  <TableCell>Installed By</TableCell>
                  <TableCell>Installed On</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {migrationGap.missingMigrations.map((migration, idx) => (
                  <TableRow key={idx}>
                    <TableCell>{migration.installedRank}</TableCell>
                    <TableCell><code>{migration.version}</code></TableCell>
                    <TableCell>{migration.description}</TableCell>
                    <TableCell><code>{migration.script}</code></TableCell>
                    <TableCell>{migration.installedBy}</TableCell>
                    <TableCell>{new Date(migration.installedOn).toLocaleString()}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>
            <strong>Explanation:</strong> Target environment has not applied these migrations from source. 
            Apply them in order to align schemas.
          </Typography>
        </CardContent>
      </Card>
    );
  };

  const renderBlastRadius = (blastRadius) => {
    if (!blastRadius || blastRadius.length === 0) {
      return null;
    }

    return (
      <Card variant="outlined" sx={{ mb: 2 }} id="blast-radius">
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Blast Radius Analysis
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Likely runtime symptoms from detected drift
          </Typography>
          {blastRadius.map((item, idx) => (
            <Card key={idx} variant="outlined" sx={{ mb: 2, bgcolor: '#fff3e0' }}>
              <CardContent>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
                  <Chip label={item.driftType} color="error" size="small" />
                  <Chip label={item.category} variant="outlined" size="small" />
                  <Chip label={`${item.riskLevel} Risk`} color={getRiskColor(item.riskLevel)} size="small" />
                </Box>
                <Typography variant="subtitle2" sx={{ mb: 1 }}>
                  <code>{item.objectName}</code>
                </Typography>
                <Typography variant="body2" sx={{ mb: 1 }}>
                  <strong>Likely Symptoms:</strong>
                </Typography>
                <List dense>
                  {item.likelySymptoms.map((symptom, sIdx) => (
                    <ListItem key={sIdx}>
                      <ListItemIcon sx={{ minWidth: 30 }}>
                        <ErrorIcon fontSize="small" color="error" />
                      </ListItemIcon>
                      <ListItemText primary={symptom} />
                    </ListItem>
                  ))}
                </List>
              </CardContent>
            </Card>
          ))}
        </CardContent>
      </Card>
    );
  };

  const renderConclusions = (conclusions) => (
    <Box sx={{ mb: 2 }} id="conclusions">
      <Typography variant="h6" gutterBottom>
        Diagnostic Conclusions
      </Typography>
      {conclusions.map((conclusion, idx) => (
        <Card 
          key={idx}
          variant="outlined"
          sx={{ 
            mb: 2,
            borderLeft: 6,
            borderLeftColor: conclusion.severity === 'ERROR' ? 'error.main' :
                             conclusion.severity === 'WARN' ? 'warning.main' : 'info.main'
          }}
        >
          <CardContent>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
              {getSeverityIcon(conclusion.severity)}
              <Chip label={conclusion.category} size="small" variant="outlined" />
              <Typography variant="subtitle1" sx={{ fontWeight: 'bold', flexGrow: 1 }}>
                {conclusion.finding}
              </Typography>
            </Box>
            
            {conclusion.evidence && conclusion.evidence.length > 0 && (
              <Box sx={{ mb: 2 }}>
                <Typography variant="body2" sx={{ fontWeight: 'bold', mb: 1 }}>
                  Evidence:
                </Typography>
                <List dense>
                  {conclusion.evidence.map((evidence, eIdx) => (
                    <ListItem key={eIdx} sx={{ py: 0 }}>
                      <ListItemIcon sx={{ minWidth: 20 }}>
                        •
                      </ListItemIcon>
                      <ListItemText 
                        primary={evidence}
                        primaryTypographyProps={{ variant: 'body2' }}
                      />
                    </ListItem>
                  ))}
                </List>
              </Box>
            )}
            
            <Typography variant="body2" sx={{ mb: 1 }}>
              <strong>Impact:</strong> {conclusion.impact}
            </Typography>
            
            <Typography variant="body2" sx={{ mb: 2 }}>
              <strong>Recommendation:</strong> {conclusion.recommendation}
            </Typography>
            
            {conclusion.nextActions && conclusion.nextActions.length > 0 && (
              <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                {conclusion.nextActions.map((action, aIdx) => (
                  <Button
                    key={aIdx}
                    variant="outlined"
                    size="small"
                    onClick={() => {
                      const targetElement = document.getElementById(action.target);
                      if (targetElement) {
                        targetElement.scrollIntoView({ behavior: 'smooth' });
                      } else if (action.target === 'copy-diagnostics') {
                        exportDiagnostics();
                      } else if (action.target === 'privilege-request') {
                        const snippet = comparisonResult.privilegeRequestSnippet;
                        if (snippet) {
                          copyToClipboard(snippet, 'Privilege request');
                        }
                      }
                    }}
                  >
                    {action.label}
                  </Button>
                ))}
              </Box>
            )}
          </CardContent>
        </Card>
      ))}
    </Box>
  );

  const renderCapabilityMatrix = () => {
    if (!comparisonResult) return null;

    const capabilities = [
      { name: 'Connect', sourceKey: 'connect', targetKey: 'connect' },
      { name: 'Identity', sourceKey: 'identity', targetKey: 'identity' },
      { name: 'Tables', sourceKey: 'tables', targetKey: 'tables' },
      { name: 'Columns', sourceKey: 'columns', targetKey: 'columns' },
      { name: 'Constraints', sourceKey: 'constraints', targetKey: 'constraints' },
      { name: 'Indexes', sourceKey: 'indexes', targetKey: 'indexes' },
      { name: 'Flyway', sourceKey: 'flywayHistory', targetKey: 'flywayHistory' },
      { name: 'Grants', sourceKey: 'grants', targetKey: 'grants' }
    ];

    return (
      <Accordion sx={{ mb: 3 }} id="capability-matrix">
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
          <Typography variant="h6">Capability Matrix</Typography>
        </AccordionSummary>
        <AccordionDetails>
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell><strong>Capability</strong></TableCell>
                  <TableCell align="center">
                    <strong>{comparisonResult.sourceEnvironment}</strong>
                  </TableCell>
                  <TableCell align="center">
                    <strong>{comparisonResult.targetEnvironment}</strong>
                  </TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {capabilities.map((cap) => {
                  const sourceStatus = comparisonResult.sourceCapabilities[cap.sourceKey];
                  const targetStatus = comparisonResult.targetCapabilities[cap.targetKey];
                  
                  return (
                    <TableRow key={cap.name}>
                      <TableCell>{cap.name}</TableCell>
                      <TableCell align="center">
                        <Tooltip title={sourceStatus.message || ''}>
                          <span>
                            {sourceStatus.available ? '✅' : '❌'}
                          </span>
                        </Tooltip>
                      </TableCell>
                      <TableCell align="center">
                        <Tooltip 
                          title={
                            targetStatus.available 
                              ? targetStatus.message || ''
                              : `${targetStatus.message || ''}${targetStatus.missingPrivilege ? '\nMissing: ' + targetStatus.missingPrivilege : ''}`
                          }
                        >
                          <span>
                            {targetStatus.available ? '✅' : '🔒'}
                          </span>
                        </Tooltip>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </TableContainer>
        </AccordionDetails>
      </Accordion>
    );
  };

  const renderPrivilegeRequest = () => {
    if (!comparisonResult?.privilegeRequestSnippet) {
      return null;
    }

    return (
      <Card variant="outlined" sx={{ mb: 3, bgcolor: '#f9f9f9' }} id="privilege-request">
        <CardContent>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
            <Typography variant="h6">
              Privilege Request Snippet
            </Typography>
            <Button
              variant="outlined"
              size="small"
              startIcon={<ContentCopyIcon />}
              onClick={() => copyToClipboard(comparisonResult.privilegeRequestSnippet, 'Privilege request snippet')}
            >
              Copy
            </Button>
          </Box>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Ready-to-send SQL grants for your DBA:
          </Typography>
          <Paper sx={{ p: 2, bgcolor: '#272822', color: '#f8f8f2', fontFamily: 'monospace', fontSize: '0.85rem', overflow: 'auto' }}>
            <pre style={{ margin: 0, whiteSpace: 'pre-wrap' }}>
              {comparisonResult.privilegeRequestSnippet}
            </pre>
          </Paper>
        </CardContent>
      </Card>
    );
  };

  return (
    <Box>
      {!isConnected && (
        <Alert severity="warning">
          Please connect to a database first using the connection form on the left panel.
        </Alert>
      )}

      {isConnected && (
        <>
          <Alert severity="info" sx={{ mb: 3 }}>
            <strong>Compare Environments:</strong> Select two database connections to compare their schemas and detect drift.
          </Alert>

          {/* Configuration Section */}
          <Box sx={{ mb: 3 }}>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              Select the source and target database connections to compare:
            </Typography>

            {availableConnections.length > 0 && (
              <Box sx={{ mb: 2, display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                {availableConnections.map((conn) => (
                  <Chip
                    key={conn.connectionId}
                    size="small"
                    color={conn.connectionId === currentConnectionId ? 'primary' : 'default'}
                    variant={conn.connectionId === currentConnectionId ? 'filled' : 'outlined'}
                    label={`${conn.host}:${conn.port}/${conn.database} (${conn.username})`}
                  />
                ))}
              </Box>
            )}

            <Grid container spacing={2}>
              <Grid item xs={6}>
                <FormControl fullWidth sx={{ minWidth: 240 }}>
                  <Select
                    displayEmpty
                    value={sourceConnectionId}
                    onChange={(e) => setSourceConnectionId(e.target.value)}
                    disabled={availableConnections.length === 0}
                    renderValue={(selected) => {
                      if (!selected) return 'Select source environment...';
                      const conn = availableConnections.find(c => c.connectionId === selected);
                      return conn ? `${conn.host}:${conn.port}/${conn.database} (${conn.username})` : 'Select source environment...';
                    }}
                    inputProps={{ 'aria-label': 'Source environment' }}
                  >
                    <MenuItem value="">
                      <em>Select source environment...</em>
                    </MenuItem>
                    {availableConnections.map((conn) => (
                      <MenuItem key={conn.connectionId} value={conn.connectionId}>
                        {conn.host}:{conn.port}/{conn.database} ({conn.username})
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Grid>
              <Grid item xs={6}>
                <FormControl fullWidth sx={{ minWidth: 240 }}>
                  <Select
                    displayEmpty
                    value={targetConnectionId}
                    onChange={(e) => setTargetConnectionId(e.target.value)}
                    disabled={availableConnections.length === 0}
                    renderValue={(selected) => {
                      if (!selected) return 'Select target environment...';
                      const conn = availableConnections.find(c => c.connectionId === selected);
                      return conn ? `${conn.host}:${conn.port}/${conn.database} (${conn.username})` : 'Select target environment...';
                    }}
                    inputProps={{ 'aria-label': 'Target environment' }}
                  >
                    <MenuItem value="">
                      <em>Select target environment...</em>
                    </MenuItem>
                    {availableConnections.map((conn) => (
                      <MenuItem key={conn.connectionId} value={conn.connectionId}>
                        {conn.host}:{conn.port}/{conn.database} ({conn.username})
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Grid>
            </Grid>

            {availableConnections.length === 0 && (
              <Alert severity="info" sx={{ mt: 2 }}>
                No connections available. Connect to at least two databases to enable comparison.
              </Alert>
            )}

            {availableConnections.length > 0 && availableConnections.length < 2 && (
              <Alert severity="warning" sx={{ mt: 2 }}>
                You have {availableConnections.length} active connection. Connect at least one more database to enable comparison.
              </Alert>
            )}

            <Button
              variant="contained"
              color="primary"
              onClick={handleCompare}
              disabled={!sourceConnectionId || !targetConnectionId || loading}
              fullWidth
              size="large"
              sx={{ mt: 3 }}
            >
              {loading ? (
                <>
                  <CircularProgress size={20} sx={{ mr: 1 }} color="inherit" />
                  Comparing Environments...
                </>
              ) : (
                'Compare Environments'
              )}
            </Button>
          </Box>
        </>
      )}

      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      {/* Results Section */}
      {comparisonResult && (
        <Box>
          <Divider sx={{ my: 3 }} />
          
          {/* Environment Identity Chips */}
          <Box sx={{ mb: 3, display: 'flex', gap: 2, flexWrap: 'wrap' }}>
            <Chip 
              label={`Source: ${comparisonResult.sourceIdentity}`}
              color="primary"
              variant="outlined"
              sx={{ fontFamily: 'monospace', fontSize: '0.9rem' }}
            />
            <Chip 
              label={`Target: ${comparisonResult.targetIdentity}`}
              color="secondary"
              variant="outlined"
              sx={{ fontFamily: 'monospace', fontSize: '0.9rem' }}
            />
            <Box sx={{ flexGrow: 1 }} />
            <Button
              variant="outlined"
              startIcon={<ContentCopyIcon />}
              onClick={exportDiagnostics}
              id="copy-diagnostics"
            >
              Copy All Diagnostics
            </Button>
            <Button
              variant={exportSuccess ? 'contained' : 'outlined'}
              color={exportSuccess ? 'success' : 'primary'}
              startIcon={<DownloadIcon />}
              onClick={handleExportForJira}
            >
              {exportSuccess ? 'Exported!' : 'Export for JIRA'}
            </Button>
          </Box>
          
          {/* Comparison Mode Banner */}
          <Alert 
            severity={getComparisonModeColor(comparisonResult.comparisonMode)}
            sx={{ mb: 3, fontSize: '1rem', fontWeight: 'bold' }}
          >
            {comparisonResult.modeBanner}
          </Alert>

          {/* Comparison Scope Filters */}
          <Card variant="outlined" sx={{ mb: 3, bgcolor: '#f5f5f5' }}>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2 }}>
                <FilterListIcon color="action" />
                <Typography variant="h6">Comparison Scope</Typography>
              </Box>
              <Grid container spacing={2} alignItems="center">
                <Grid item xs={12} sm={4}>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    <Typography variant="body2">Only show differences:</Typography>
                    <ToggleButtonGroup
                      value={showOnlyDifferences}
                      exclusive
                      onChange={(e, newValue) => {
                        if (newValue !== null) {
                          setShowOnlyDifferences(newValue);
                        }
                      }}
                      size="small"
                    >
                      <ToggleButton value={true}>ON</ToggleButton>
                      <ToggleButton value={false}>OFF</ToggleButton>
                    </ToggleButtonGroup>
                  </Box>
                </Grid>
                <Grid item xs={12} sm={4}>
                  <FormControl fullWidth size="small">
                    <InputLabel>Severity</InputLabel>
                    <Select
                      value={severityFilter}
                      label="Severity"
                      onChange={(e) => setSeverityFilter(e.target.value)}
                    >
                      <MenuItem value="all">All Severities</MenuItem>
                      <MenuItem value="ERROR">Errors Only</MenuItem>
                      <MenuItem value="WARN">Warnings Only</MenuItem>
                    </Select>
                  </FormControl>
                </Grid>
                <Grid item xs={12} sm={4}>
                  <Box sx={{ position: 'relative' }}>
                    <TextField
                      fullWidth
                      size="small"
                      label="Search objects"
                      placeholder="cart_item or table:cart_* or column:promo_code"
                      value={searchQuery}
                      onChange={(e) => handleSearchChange(e.target.value)}
                      onFocus={() => searchQuery.length >= 2 && generateSearchSuggestions(searchQuery)}
                      onBlur={() => setTimeout(() => setShowSuggestions(false), 200)}
                      InputProps={{
                        startAdornment: <SearchIcon sx={{ mr: 1, color: 'action.disabled' }} />
                      }}
                      helperText="Supports: plain text, table:, column:, index:, constraint:, wildcards (*)"
                    />
                    
                    {/* Typeahead suggestions */}
                    {showSuggestions && (
                      <Paper 
                        sx={{ 
                          position: 'absolute', 
                          top: '100%', 
                          left: 0, 
                          right: 0, 
                          zIndex: 1300,
                          maxHeight: 300,
                          overflowY: 'auto',
                          overflowX: 'hidden',
                          mt: 1,
                          pt: 0.5,
                          boxShadow: 6,
                          borderRadius: 1
                        }}
                        elevation={8}
                      >
                        <Box sx={{ maxHeight: 300, overflowY: 'auto' }}>
                          {hasAnySuggestions ? (
                            <>
                              {searchSuggestions.tables.length > 0 && (
                                <>
                                  <Typography variant="caption" sx={{ px: 2, pt: 1, pb: 0.5, display: 'block', fontWeight: 'bold', color: 'text.secondary', bgcolor: 'grey.50', position: 'sticky', top: 0, zIndex: 1 }}>
                                    Tables
                                  </Typography>
                                  {searchSuggestions.tables.map((table, idx) => (
                                    <MenuItem 
                                      key={`table-${idx}`}
                                      onMouseDown={(e) => {
                                        e.preventDefault();
                                        setSearchQuery(table);
                                        setShowSuggestions(false);
                                      }}
                                      sx={{ fontSize: '0.875rem', py: 1 }}
                                    >
                                      <code>{table}</code>
                                    </MenuItem>
                                  ))}
                                </>
                              )}
                              
                              {searchSuggestions.columns.length > 0 && (
                                <>
                                  <Typography variant="caption" sx={{ px: 2, pt: 1, pb: 0.5, display: 'block', fontWeight: 'bold', color: 'text.secondary', bgcolor: 'grey.50', position: 'sticky', top: 0, zIndex: 1 }}>
                                    Columns
                                  </Typography>
                                  {searchSuggestions.columns.map((col, idx) => (
                                    <MenuItem 
                                      key={`col-${idx}`}
                                      onMouseDown={(e) => {
                                        e.preventDefault();
                                        setSearchQuery(col);
                                        setShowSuggestions(false);
                                      }}
                                      sx={{ fontSize: '0.875rem', py: 1 }}
                                    >
                                      <code>{col}</code>
                                    </MenuItem>
                                  ))}
                                </>
                              )}
                              
                              {searchSuggestions.indexes.length > 0 && (
                                <>
                                  <Typography variant="caption" sx={{ px: 2, pt: 1, pb: 0.5, display: 'block', fontWeight: 'bold', color: 'text.secondary', bgcolor: 'grey.50', position: 'sticky', top: 0, zIndex: 1 }}>
                                    Indexes
                                  </Typography>
                                  {searchSuggestions.indexes.map((idx, i) => (
                                    <MenuItem 
                                      key={`idx-${i}`}
                                      onMouseDown={(e) => {
                                        e.preventDefault();
                                        setSearchQuery(idx);
                                        setShowSuggestions(false);
                                      }}
                                      sx={{ fontSize: '0.875rem', py: 1 }}
                                    >
                                      <code>{idx}</code>
                                    </MenuItem>
                                  ))}
                                </>
                              )}
                              
                              {searchSuggestions.constraints.length > 0 && (
                                <>
                                  <Typography variant="caption" sx={{ px: 2, pt: 1, pb: 0.5, display: 'block', fontWeight: 'bold', color: 'text.secondary', bgcolor: 'grey.50', position: 'sticky', top: 0, zIndex: 1 }}>
                                    Constraints
                                  </Typography>
                                  {searchSuggestions.constraints.map((con, idx) => (
                                    <MenuItem 
                                      key={`con-${idx}`}
                                      onMouseDown={(e) => {
                                        e.preventDefault();
                                        setSearchQuery(con);
                                        setShowSuggestions(false);
                                      }}
                                      sx={{ fontSize: '0.875rem', py: 1 }}
                                    >
                                      <code>{con}</code>
                                    </MenuItem>
                                  ))}
                                </>
                              )}
                            </>
                          ) : (
                            <Typography
                              variant="body2"
                              sx={{ px: 2, py: 1, color: 'text.secondary' }}
                            >
                              No matching objects yet. Keep typing (min 2 characters) or adjust filters.
                            </Typography>
                          )}
                        </Box>
                      </Paper>
                    )}
                  </Box>
                </Grid>
              </Grid>
            </CardContent>
          </Card>

          {/* Search Results Summary */}
          {searchQuery && comparisonResult && (() => {
            const allMatchedItems = comparisonResult.driftSections.map(section => ({
              sectionName: section.sectionName,
              items: filterDriftItems(section.driftItems).filter(item => norm(item.status) !== 'MATCH')
            })).filter(result => result.items.length > 0);
            
            const totalMatches = allMatchedItems.reduce((sum, result) => sum + result.items.length, 0);
            
            return totalMatches > 0 ? (
              <Alert severity="info" sx={{ mb: 3 }}>
                <Typography variant="body1" sx={{ fontWeight: 'bold', mb: 1 }}>
                  Search Results for "{searchQuery}": {totalMatches} item{totalMatches !== 1 ? 's' : ''} found
                </Typography>
                <Box component="ul" sx={{ margin: 0, paddingLeft: 2 }}>
                  {allMatchedItems.map((result, idx) => (
                    <li key={idx}>
                      <Typography variant="body2">
                        <strong>{result.sectionName}:</strong> {result.items.length} item{result.items.length !== 1 ? 's' : ''}
                        {result.items.length <= 3 && (
                          <span style={{ color: '#666', marginLeft: '8px' }}>
                            ({result.items.map(item => item.objectName).join(', ')})
                          </span>
                        )}
                      </Typography>
                    </li>
                  ))}
                </Box>
              </Alert>
            ) : (
              <Alert severity="warning" sx={{ mb: 3 }}>
                <Typography variant="body1">
                  No results found for "{searchQuery}". Try a different search term or check your filters.
                </Typography>
              </Alert>
            );
          })()}

          {/* Capability Matrix Comparison */}
          {renderCapabilityMatrix()}

          {/* Flyway Comparison */}
          {renderFlywayComparison(comparisonResult.flywayComparison)}

          {/* Missing Migrations */}
          {renderMissingMigrations(comparisonResult.flywayMigrationGap)}

          {/* Diagnostic Conclusions */}
          {renderConclusions(comparisonResult.conclusions)}

          {/* Blast Radius */}
          {renderBlastRadius(comparisonResult.blastRadius)}

          {/* Drift Sections */}
          <Typography variant="h6" gutterBottom id="drift-sections">
            Drift Analysis
          </Typography>
          {comparisonResult.driftSections.map((section, idx) => (
            <Box key={idx}>
              {renderDriftSection(section)}
            </Box>
          ))}

          {/* Privilege Request */}
          {renderPrivilegeRequest()}

          {/* Access Requirements Panel */}
          <Card variant="outlined" sx={{ mt: 3, bgcolor: '#f9f9f9' }}>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Access Requirements for Full Comparison
              </Typography>
              <Typography variant="body2" component="div">
                <ul>
                  <li>Read access to <code>information_schema</code></li>
                  <li>Read access to <code>pg_catalog</code> metadata</li>
                  <li>Optional: Flyway history table if Flyway is in use</li>
                </ul>
              </Typography>
              {comparisonResult.missingPrivileges && comparisonResult.missingPrivileges.length > 0 && (
                <Alert severity="warning" sx={{ mt: 2 }}>
                  <Typography variant="body2" sx={{ mb: 1 }}>
                    <strong>Missing Privileges Detected:</strong>
                  </Typography>
                  <List dense>
                    {comparisonResult.missingPrivileges.map((priv, idx) => (
                      <ListItem key={idx}>
                        <ListItemText 
                          primary={`${priv.capability}: ${priv.reason}`}
                          secondary={priv.missingPrivilege}
                        />
                      </ListItem>
                    ))}
                  </List>
                </Alert>
              )}
            </CardContent>
          </Card>
        </Box>
      )}

      <Snackbar
        open={snackbarOpen}
        autoHideDuration={3000}
        onClose={() => setSnackbarOpen(false)}
        message={snackbarMessage}
      />
    </Box>
  );
}
