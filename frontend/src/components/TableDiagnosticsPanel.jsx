import React, { useState, useEffect, useRef } from 'react';
import {
  Box,
  Typography,
  Paper,
  Chip,
  Button,
  Collapse,
  IconButton,
  Alert,
  AlertTitle,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Divider,
  Stack,
  CircularProgress,
  Tooltip,
  Select,
  MenuItem,
  FormControl,
  InputLabel
} from '@mui/material';
import {
  ExpandMore as ExpandMoreIcon,
  ContentCopy as ContentCopyIcon,
  CheckCircle as CheckCircleIcon,
  Warning as WarningIcon,
  Error as ErrorIcon,
  Info as InfoIcon,
  KeyboardArrowDown as KeyboardArrowDownIcon,
  Download as DownloadIcon
} from '@mui/icons-material';
import { apiService } from '../services/apiService';

export default function TableDiagnosticsPanel({ data, connectionId, schema }) {
  const [privilegesData, setPrivilegesData] = useState(null);
  const [loadingPrivileges, setLoadingPrivileges] = useState(false);
  const [privilegesChecked, setPrivilegesChecked] = useState(false);
  const [copySuccess, setCopySuccess] = useState(false);
  const [exportSuccess, setExportSuccess] = useState(false);
  const [expandedSections, setExpandedSections] = useState({
    columns: true,
    indexes: false,
    constraints: false,
    ownership: false
  });
  const [expectedAccessProfile, setExpectedAccessProfile] = useState('read-write');

  // Refs for scrolling to sections
  const ownershipRef = useRef(null);
  const columnsRef = useRef(null);
  const indexesRef = useRef(null);
  const constraintsRef = useRef(null);
  const pkRef = useRef(null);
  const fkRef = useRef(null);

  if (!data) return null;

  const { table, owner, currentUser, columns = [], indexes = [], constraints = [], flywayInfo } = data;
  
  // Categorize constraints
  const primaryKeys = constraints.filter(c => c.type === 'PRIMARY KEY');
  const foreignKeys = constraints.filter(c => c.type === 'FOREIGN KEY');
  const uniqueConstraints = constraints.filter(c => c.type === 'UNIQUE');
  const checkConstraints = constraints.filter(c => c.type === 'CHECK');
  const otherConstraints = constraints.filter(c => 
    !['PRIMARY KEY', 'FOREIGN KEY', 'UNIQUE', 'CHECK'].includes(c.type)
  );
  
  // Categorize indexes
  const primaryIndexes = indexes.filter(i => i.primary);
  const uniqueIndexes = indexes.filter(i => i.unique && !i.primary);
  const regularIndexes = indexes.filter(i => !i.unique && !i.primary);
  const requiredColumns = columns.filter(c => !c.nullable && !c.columnDefault);
  const nullableColumns = columns.filter(c => c.nullable);
  const defaultedColumns = columns.filter(c => c.columnDefault);

  // Analyze FK cascades
  const cascadingFKs = foreignKeys.filter(fk => 
    fk.definition && (fk.definition.includes('ON DELETE CASCADE') || fk.definition.includes('ON UPDATE CASCADE'))
  );
  
  // Detect self-referencing FKs (recursive relationships)
  const selfReferencingFKs = foreignKeys.filter(fk => 
    fk.definition && fk.definition.toLowerCase().includes(`references ${schema}.${table}`)
  );
  
  // Categorize FK risk levels
  const getFKRiskLevel = (fk) => {
    const def = fk.definition?.toLowerCase() || '';
    const hasCascade = def.includes('on delete cascade') || def.includes('on update cascade');
    
    if (!hasCascade) return 'low';
    
    // Check if self-referencing (recursive)
    if (def.includes(`references ${schema}.${table}`)) {
      return 'critical';
    }
    
    // Check if it's a root-level cascade (from parent entities like cart, order)
    const rootEntities = ['cart', 'order', 'user', 'account', 'customer'];
    const isRootCascade = rootEntities.some(entity => 
      table.includes(entity) && fk.columns?.some(col => col.includes(entity))
    );
    
    if (isRootCascade) return 'high';
    
    // Reference/lookup tables are usually moderate risk
    return 'moderate';
  };

  // Diagnostic calculations
  const ownershipOk = owner === currentUser;
  const hasSelectAccess = privilegesData?.grantedPrivileges?.includes('SELECT') || false;
  const hasWriteAccess = privilegesData?.grantedPrivileges?.some(p => 
    ['INSERT', 'UPDATE', 'DELETE'].includes(p)
  ) || false;
  const pkIntegrityOk = primaryKeys.length > 0;
  const fkIntegrityOk = foreignKeys.length === 0 || foreignKeys.every(fk => fk.definition);
  const hasCascadeRisk = cascadingFKs.length > 0;
  
  // Flyway drift detection
  const hasFlywayDrift = flywayInfo && owner && flywayInfo.installedBy !== currentUser && owner !== currentUser;

  // Generate impact summary
  const generateImpactSummary = () => {
    const impacts = [];
    
    // Check for cascade delete risks
    if (cascadingFKs.length > 0) {
      const recursiveCascade = cascadingFKs.some(fk => 
        fk.definition?.toLowerCase().includes(`references ${schema}.${table}`)
      );
      
      if (recursiveCascade) {
        impacts.push({
          severity: 'warning',
          message: 'Parent-child recursion exists (self FK with CASCADE). Deep deletes can cause long transactions.'
        });
      } else {
        const parentTable = table.split('_')[0]; // e.g., 'cart' from 'cart_item'
        impacts.push({
          severity: 'warning',
          message: `Deleting a ${parentTable} will cascade to ${table} records.`
        });
      }
    }
    
    // Check for composite unique constraints (complex insert requirements)
    const compositeUnique = uniqueConstraints.filter(u => u.columns && u.columns.length > 2);
    if (compositeUnique.length > 0) {
      const cols = compositeUnique[0].columns?.slice(0, 3).join(', ');
      impacts.push({
        severity: 'success',
        message: `Uniqueness protects duplicate entries on (${cols}...).`
      });
    }
    
    // Check for no primary key
    if (primaryKeys.length === 0) {
      impacts.push({
        severity: 'error',
        message: 'No primary key defined. Updates and deletes may affect multiple rows unintentionally.'
      });
    }
    
    return impacts;
  };

  // Detect common failure patterns
  const detectFailurePatterns = () => {
    const patterns = [];
    
    // Recursive FK with CASCADE
    if (selfReferencingFKs.length > 0 && cascadingFKs.length > 0) {
      patterns.push({
        type: 'Recursive FK with CASCADE',
        description: 'Bulk delete may cause deep cascading deletes and long transactions.',
        severity: 'error'
      });
    }
    
    // Composite unique constraint
    const compositeUnique = uniqueConstraints.filter(u => u.columns && u.columns.length >= 3);
    if (compositeUnique.length > 0) {
      const constraint = compositeUnique[0];
      patterns.push({
        type: 'Composite unique constraint',
        description: `Inserts must include all ${constraint.columns?.length} columns (${constraint.columns?.join(', ')}) or will fail with 23505.`,
        severity: 'warning'
      });
    }
    
    // Check for NOT NULL constraints (from "other" constraints)
    const notNullCount = otherConstraints.filter(c => 
      c.type?.includes('NOT NULL') || c.definition?.includes('NOT NULL')
    ).length;
    
    if (notNullCount > 0) {
      patterns.push({
        type: 'NOT NULL without default',
        description: `${notNullCount} column(s) require explicit values. Inserts with partial payloads will fail with 23502.`,
        severity: 'info'
      });
    }
    
    // Check constraints that limit values
    if (checkConstraints.length > 0) {
      patterns.push({
        type: 'Check constraints on values',
        description: `${checkConstraints.length} check constraint(s) enforce business rules. UPDATEs may fail with 23514.`,
        severity: 'info'
      });
    }
    
    return patterns;
  };

  const handleCheckPrivileges = async () => {
    setLoadingPrivileges(true);
    try {
      const response = await apiService.getPrivileges(connectionId, schema, table);
      setPrivilegesData(response.data);
      setPrivilegesChecked(true);
    } catch (error) {
      console.error('Failed to check privileges:', error);
      setPrivilegesData(null);
    } finally {
      setLoadingPrivileges(false);
    }
  };

  const scrollToSection = (ref, sectionName) => {
    // First expand the section if a section name is provided
    if (sectionName) {
      setExpandedSections(prev => ({
        ...prev,
        [sectionName]: true
      }));
    }
    
    // Scroll after a short delay to allow the section to expand
    setTimeout(() => {
      ref.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }, 100);
  };

  const toggleSection = (section) => {
    setExpandedSections(prev => ({
      ...prev,
      [section]: !prev[section]
    }));
  };

  const formatRelativeTime = (timestamp) => {
    if (!timestamp) return 'N/A';
    try {
      const date = new Date(timestamp);
      const now = new Date();
      const diffMs = now - date;
      const diffDay = Math.floor(diffMs / (1000 * 60 * 60 * 24));

      if (diffDay === 0) return 'today';
      if (diffDay === 1) return 'yesterday';
      if (diffDay < 30) return `${diffDay} days ago`;
      if (diffDay < 365) {
        const months = Math.floor(diffDay / 30);
        return `${months} month${months !== 1 ? 's' : ''} ago`;
      }
      const years = Math.floor(diffDay / 365);
      return `${years} year${years !== 1 ? 's' : ''} ago`;
    } catch {
      return 'N/A';
    }
  };

  const generateDiagnosticText = () => {
    const lines = [];
    lines.push('═══════════════════════════════════════');
    lines.push('TABLE DIAGNOSTICS REPORT');
    lines.push('═══════════════════════════════════════');
    lines.push('');
    lines.push(`Connection ID: ${connectionId}`);
    lines.push(`Table: ${schema}.${table}`);
    lines.push(`Table Owner: ${owner || 'N/A'}`);
    lines.push(`Connected User: ${currentUser || 'N/A'}`);
    lines.push('');
    
    if (privilegesData) {
      lines.push('─── PRIVILEGES ───');
      lines.push(`Status: ${privilegesData.status}`);
      lines.push(`Granted: ${privilegesData.grantedPrivileges?.join(', ') || 'None'}`);
      if (privilegesData.missingPrivileges?.length > 0) {
        lines.push(`Missing: ${privilegesData.missingPrivileges.join(', ')}`);
      }
      lines.push('');
    }

    if (flywayInfo) {
      lines.push('─── FLYWAY MIGRATION ───');
      lines.push(`Version: ${flywayInfo.version}`);
      lines.push(`Description: ${flywayInfo.description}`);
      lines.push(`Installed By: ${flywayInfo.installedBy}`);
      lines.push(`Installed On: ${flywayInfo.installedOn}`);
      lines.push('');
    }

    lines.push('─── COLUMNS ───');
    lines.push(`Total: ${columns.length}`);
    lines.push(`Required (NOT NULL w/o default): ${requiredColumns.length}`);
    lines.push(`Nullable: ${nullableColumns.length}`);
    lines.push(`With defaults: ${defaultedColumns.length}`);
    if (columns.length > 0) {
      lines.push('Column details:');
      columns.forEach((col) => {
        lines.push(`  - ${col.ordinalPosition}. ${col.name} (${col.dataType}) ${col.nullable ? 'NULL' : 'NOT NULL'}${col.columnDefault ? ` DEFAULT ${col.columnDefault}` : ''}`);
      });
    }
    lines.push('');

    lines.push('─── INDEXES ───');
    lines.push(`Total: ${indexes.length}`);
    lines.push(`Primary: ${primaryIndexes.length}, Unique: ${uniqueIndexes.length}, Regular: ${regularIndexes.length}`);
    lines.push('');

    lines.push('─── CONSTRAINTS ───');
    lines.push(`Primary Keys: ${primaryKeys.length}`);
    lines.push(`Foreign Keys: ${foreignKeys.length}`);
    lines.push(`Unique: ${uniqueConstraints.length}`);
    lines.push(`Check: ${checkConstraints.length}`);
    if (cascadingFKs.length > 0) {
      lines.push(`⚠️  Cascading FKs: ${cascadingFKs.length}`);
    }
    lines.push('');

    lines.push('═══════════════════════════════════════');
    lines.push(`Generated: ${new Date().toLocaleString()}`);

    return lines.join('\n');
  };

  const handleCopyDiagnostics = async () => {
    const text = generateDiagnosticText();
    try {
      await navigator.clipboard.writeText(text);
      setCopySuccess(true);
      setTimeout(() => setCopySuccess(false), 2000);
    } catch (err) {
      console.error('Failed to copy diagnostics:', err);
    }
  };

  const handleExportDiagnostics = async () => {
    if (!connectionId) return;
    try {
      const response = await apiService.exportDiagnostics(connectionId);
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

  const DiagnosticPill = ({ icon, label, status, onClick }) => {
    const colors = {
      success: { bg: '#e8f5e9', border: '#4caf50', text: '#2e7d32' },
      warning: { bg: '#fff3e0', border: '#ff9800', text: '#e65100' },
      error: { bg: '#ffebee', border: '#f44336', text: '#c62828' },
      info: { bg: '#e3f2fd', border: '#2196f3', text: '#1565c0' }
    };
    const color = colors[status] || colors.info;

    return (
      <Box
        onClick={onClick}
        sx={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: 0.5,
          px: 2,
          py: 1,
          bgcolor: color.bg,
          border: `2px solid ${color.border}`,
          borderRadius: 2,
          cursor: onClick ? 'pointer' : 'default',
          transition: 'all 0.2s',
          '&:hover': onClick ? {
            transform: 'translateY(-2px)',
            boxShadow: 2
          } : {}
        }}
      >
        {icon}
        <Typography variant="body2" sx={{ fontWeight: 'bold', color: color.text }}>
          {label}
        </Typography>
      </Box>
    );
  };

  const WhyThisMatters = ({ children }) => {
    const [open, setOpen] = useState(false);
    return (
      <Box sx={{ mt: 1 }}>
        <Button
          size="small"
          startIcon={<InfoIcon />}
          onClick={() => setOpen(!open)}
          sx={{ textTransform: 'none', fontSize: '0.75rem' }}
        >
          Why this matters
        </Button>
        <Collapse in={open}>
          <Alert severity="info" sx={{ mt: 1, fontSize: '0.85rem' }}>
            {children}
          </Alert>
        </Collapse>
      </Box>
    );
  };

  return (
    <Box sx={{ width: '100%' }}>
      {/* Header */}
      <Box sx={{ mb: 2, p: 2, bgcolor: '#f5f5f5', borderRadius: 1 }}>
        <Typography variant="h6" sx={{ mb: 1 }}>
          Table: {schema}.{table}
        </Typography>
        <Box sx={{ display: 'flex', gap: 1 }}>
          <Button
            size="small"
            startIcon={<ContentCopyIcon />}
            onClick={handleCopyDiagnostics}
            variant={copySuccess ? 'contained' : 'outlined'}
            color={copySuccess ? 'success' : 'primary'}
          >
            {copySuccess ? 'Copied!' : 'Copy Diagnostics'}
          </Button>
          <Button
            size="small"
            startIcon={<DownloadIcon />}
            onClick={handleExportDiagnostics}
            variant={exportSuccess ? 'contained' : 'outlined'}
            color={exportSuccess ? 'success' : 'primary'}
          >
            {exportSuccess ? 'Exported!' : 'Export for JIRA'}
          </Button>
        </Box>
      </Box>

      {/* Diagnostics Summary Strip */}
      <Paper elevation={2} sx={{ p: 2, mb: 3, bgcolor: hasFlywayDrift ? '#fff3e0' : 'background.paper' }}>
        <Typography variant="subtitle1" sx={{ mb: 2, fontWeight: 'bold' }}>
          📊 Diagnostics Summary
        </Typography>
        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1.5 }}>
          {/* Constraint-related diagnostics */}
          <DiagnosticPill
            icon={<InfoIcon fontSize="small" />}
            label={`${columns.length} Column${columns.length !== 1 ? 's' : ''}`}
            status="info"
            onClick={() => scrollToSection(columnsRef, 'columns')}
          />

          <DiagnosticPill
            icon={pkIntegrityOk ? <CheckCircleIcon fontSize="small" /> : <WarningIcon fontSize="small" />}
            label={pkIntegrityOk ? 'PK Integrity OK' : 'No Primary Key'}
            status={pkIntegrityOk ? 'success' : 'warning'}
            onClick={() => scrollToSection(pkRef, 'constraints')}
          />
          
          <DiagnosticPill
            icon={fkIntegrityOk ? <CheckCircleIcon fontSize="small" /> : <WarningIcon fontSize="small" />}
            label={fkIntegrityOk ? 'FK Integrity OK' : 'FK Issues'}
            status={fkIntegrityOk ? 'success' : 'warning'}
            onClick={() => scrollToSection(constraintsRef, 'constraints')}
          />

          {hasCascadeRisk && (
            <DiagnosticPill
              icon={<WarningIcon fontSize="small" />}
              label={`${cascadingFKs.length} Cascade FK${cascadingFKs.length > 1 ? 's' : ''}`}
              status="warning"
              onClick={() => scrollToSection(constraintsRef, 'constraints')}
            />
          )}
          
          <DiagnosticPill
            icon={<InfoIcon fontSize="small" />}
            label={`${constraints.length} Constraint${constraints.length !== 1 ? 's' : ''}`}
            status="info"
            onClick={() => scrollToSection(constraintsRef, 'constraints')}
          />
          
          {/* Access/Privilege diagnostics */}
          {privilegesChecked && (
            <>
              <DiagnosticPill
                icon={hasSelectAccess ? <CheckCircleIcon fontSize="small" /> : <ErrorIcon fontSize="small" />}
                label={hasSelectAccess ? 'SELECT Access' : 'SELECT Missing'}
                status={hasSelectAccess ? 'success' : 'error'}
                onClick={() => scrollToSection(ownershipRef, null)}
              />
              
              {!hasWriteAccess && (
                <DiagnosticPill
                  icon={<WarningIcon fontSize="small" />}
                  label="Write Access Limited"
                  status="warning"
                  onClick={() => scrollToSection(ownershipRef, null)}
                />
              )}
            </>
          )}
        </Box>
        
        {/* Impact Summary - "So What" */}
        {generateImpactSummary().length > 0 && (
          <Box sx={{ mt: 2, p: 1.5, bgcolor: '#f9fafb', borderRadius: 1, borderLeft: '4px solid #2196f3' }}>
            <Typography variant="caption" sx={{ fontWeight: 'bold', color: 'text.secondary', textTransform: 'uppercase', letterSpacing: 0.5 }}>
              Impact Summary
            </Typography>
            <Box sx={{ mt: 1 }}>
              {generateImpactSummary().map((impact, idx) => (
                <Box key={idx} sx={{ display: 'flex', alignItems: 'flex-start', gap: 0.5, mb: 0.5 }}>
                  {impact.severity === 'warning' && <Typography variant="body2">⚠️</Typography>}
                  {impact.severity === 'success' && <Typography variant="body2">✅</Typography>}
                  {impact.severity === 'error' && <Typography variant="body2">❌</Typography>}
                  <Typography variant="body2" sx={{ fontSize: '0.875rem', color: 'text.primary' }}>
                    {impact.message}
                  </Typography>
                </Box>
              ))}
            </Box>
          </Box>
        )}
      </Paper>

      {/* Flyway Drift Warning */}
      {hasFlywayDrift && (
        <Alert severity="warning" sx={{ mb: 3 }} icon={<WarningIcon />}>
          <AlertTitle>⚠️ Potential Credential Drift</AlertTitle>
          <Typography variant="body2">
            This table was likely created by a different role than the one currently connected.
          </Typography>
          <Typography variant="body2" sx={{ mt: 1 }}>
            <strong>Flyway installed by:</strong> {flywayInfo.installedBy} • 
            <strong> Table owner:</strong> {owner} • 
            <strong> Connected as:</strong> {currentUser}
          </Typography>
          <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block', fontStyle: 'italic' }}>
            💡 This connects Flyway diagnostics with ownership issues and may explain permission problems.
          </Typography>
        </Alert>
      )}

      {/* Diagnostic Timeline */}
      {flywayInfo && (
        <Paper elevation={1} sx={{ p: 2, mb: 3 }}>
          <Typography variant="subtitle1" sx={{ mb: 2, fontWeight: 'bold' }}>
            📅 Diagnostic Timeline
          </Typography>
          <Box sx={{ pl: 2, borderLeft: '3px solid #2196f3' }}>
            <Box sx={{ mb: 2 }}>
              <Typography variant="body2" sx={{ fontWeight: 'bold', color: 'success.main' }}>
                ✔️ Flyway migration applied
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {formatRelativeTime(flywayInfo.installedOn)} by {flywayInfo.installedBy}
              </Typography>
              <Typography variant="caption" display="block" color="text.secondary">
                Version {flywayInfo.version}: {flywayInfo.description}
              </Typography>
            </Box>
            
            <Box sx={{ mb: 2 }}>
              <Typography variant="body2" sx={{ fontWeight: 'bold', color: 'success.main' }}>
                ✔️ Table created
              </Typography>
              <Typography variant="caption" color="text.secondary">
                Owner: {owner}
              </Typography>
            </Box>

            {!ownershipOk && (
              <Box sx={{ mb: 2 }}>
                <Typography variant="body2" sx={{ fontWeight: 'bold', color: 'warning.main' }}>
                  ⚠️ Ownership differs from current user
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  Connected as {currentUser}, but table owned by {owner}
                </Typography>
              </Box>
            )}

            {privilegesChecked && !hasWriteAccess && (
              <Box>
                <Typography variant="body2" sx={{ fontWeight: 'bold', color: 'error.main' }}>
                  ❌ Write privileges missing
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {privilegesData?.missingPrivileges?.join(', ')}
                </Typography>
              </Box>
            )}
          </Box>
        </Paper>
      )}

      {/* Ownership & Access Diagnostics */}
      <Paper elevation={1} sx={{ mb: 3 }} ref={ownershipRef}>
        <Box sx={{ p: 2, bgcolor: '#f5f5f5', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Typography variant="subtitle1" sx={{ fontWeight: 'bold' }}>
            🔐 Ownership & Access Diagnostics
          </Typography>
          {privilegesChecked && (
            <FormControl size="small" sx={{ minWidth: 180 }}>
              <InputLabel>Expected Access</InputLabel>
              <Select
                value={expectedAccessProfile}
                onChange={(e) => setExpectedAccessProfile(e.target.value)}
                label="Expected Access"
              >
                <MenuItem value="read-only">Read-only service</MenuItem>
                <MenuItem value="read-write">Read-write service</MenuItem>
                <MenuItem value="admin">Admin / migration</MenuItem>
              </Select>
            </FormControl>
          )}
        </Box>
        <Box sx={{ p: 2 }}>
          {!privilegesChecked ? (
            // Pre-check state
            <Box>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                Check ownership and access privileges for this table.
              </Typography>
              <Button
                variant="contained"
                onClick={handleCheckPrivileges}
                disabled={loadingPrivileges}
                startIcon={loadingPrivileges ? <CircularProgress size={16} /> : null}
              >
                {loadingPrivileges ? 'Checking...' : 'Check Ownership & Grants'}
              </Button>
            </Box>
          ) : (
            // Post-check state - persistent diagnostic card
            <Box>
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow sx={{ backgroundColor: '#f5f5f5' }}>
                      <TableCell><strong>Check</strong></TableCell>
                      <TableCell><strong>Result</strong></TableCell>
                      <TableCell><strong>Details</strong></TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    <TableRow>
                      <TableCell><strong>Table Owner</strong></TableCell>
                      <TableCell>
                        {ownershipOk ? (
                          <Chip icon={<CheckCircleIcon />} label="Match" color="success" size="small" />
                        ) : (
                          <Chip icon={<ErrorIcon />} label={owner} color="error" size="small" />
                        )}
                      </TableCell>
                      <TableCell>
                        {ownershipOk ? (
                          <Typography variant="caption">Connected user owns this table</Typography>
                        ) : (
                          <Typography variant="caption">Connected as {currentUser}</Typography>
                        )}
                      </TableCell>
                    </TableRow>
                    
                    {['SELECT', 'INSERT', 'UPDATE', 'DELETE'].map(priv => {
                      const hasPriv = privilegesData?.grantedPrivileges?.includes(priv);
                      
                      // Determine if this is expected based on access profile
                      let isExpected = true;
                      let isMismatch = false;
                      
                      if (expectedAccessProfile === 'read-only') {
                        isExpected = priv === 'SELECT';
                        isMismatch = !hasPriv && priv === 'SELECT'; // Missing SELECT is bad
                      } else if (expectedAccessProfile === 'read-write') {
                        isExpected = true; // All are expected
                        isMismatch = !hasPriv; // Any missing is a mismatch
                      } else if (expectedAccessProfile === 'admin') {
                        isExpected = true;
                        isMismatch = !hasPriv || !ownershipOk; // Admin should have everything + ownership
                      }
                      
                      return (
                        <TableRow key={priv} sx={{ bgcolor: isMismatch ? '#ffebee' : 'inherit' }}>
                          <TableCell><strong>{priv}</strong></TableCell>
                          <TableCell>
                            {hasPriv ? (
                              <Chip icon={<CheckCircleIcon />} label="Allowed" color="success" size="small" />
                            ) : (
                              <Chip 
                                icon={<ErrorIcon />} 
                                label="Missing" 
                                color={isMismatch ? "error" : "default"}
                                size="small" 
                              />
                            )}
                          </TableCell>
                          <TableCell>
                            <Typography variant="caption">
                              {hasPriv ? (
                                'Permission granted'
                              ) : (
                                <>
                                  {isMismatch ? '❌ ' : ''}No permission
                                  {!isExpected && !isMismatch && ' (expected for this profile)'}
                                </>
                              )}
                            </Typography>
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </TableContainer>

              {/* Interpretation */}
              <Alert severity={ownershipOk && hasSelectAccess && hasWriteAccess ? 'success' : 'warning'} sx={{ mt: 2 }}>
                <AlertTitle>Interpretation</AlertTitle>
                <Typography variant="body2">
                  {ownershipOk ? (
                    <>This table is owned by <strong>{owner}</strong> (you). You have full control.</>
                  ) : (
                    <>This table is owned by <strong>{owner}</strong>.</>
                  )}
                </Typography>
                <Typography variant="body2" sx={{ mt: 1 }}>
                  The connected user <strong>{currentUser}</strong> can {hasSelectAccess ? 'read data' : 'NOT read data'}
                  {hasWriteAccess ? ' and can modify it.' : ' but cannot modify it.'}
                </Typography>
                {!hasWriteAccess && (
                  <Typography variant="body2" sx={{ mt: 1, fontStyle: 'italic' }}>
                    ⚠️ This is acceptable for read-only services but will fail for write paths.
                  </Typography>
                )}
              </Alert>
            </Box>
          )}

          <WhyThisMatters>
            Ownership and access control determine what operations your application can perform. 
            Mismatched ownership is a common cause of production failures, especially when combined 
            with Flyway migrations run under different credentials.
          </WhyThisMatters>
        </Box>
      </Paper>

      {/* Common Failure Patterns */}
      {detectFailurePatterns().length > 0 && (
        <Paper elevation={1} sx={{ mb: 3, border: '2px solid #ff9800' }}>
          <Box sx={{ p: 2, bgcolor: '#fff3e0' }}>
            <Typography variant="subtitle1" sx={{ fontWeight: 'bold', color: '#e65100' }}>
              🚨 Common Failure Patterns (Detected)
            </Typography>
          </Box>
          <Box sx={{ p: 2 }}>
            <TableContainer>
              <Table size="small">
                <TableHead>
                  <TableRow sx={{ backgroundColor: '#f5f5f5' }}>
                    <TableCell width="30%"><strong>Pattern</strong></TableCell>
                    <TableCell><strong>Description & Impact</strong></TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {detectFailurePatterns().map((pattern, idx) => (
                    <TableRow key={idx} hover sx={{ 
                      bgcolor: pattern.severity === 'error' ? '#ffebee' : 
                               pattern.severity === 'warning' ? '#fff3e0' : 'inherit' 
                    }}>
                      <TableCell>
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                          {pattern.severity === 'error' && <ErrorIcon color="error" fontSize="small" />}
                          {pattern.severity === 'warning' && <WarningIcon color="warning" fontSize="small" />}
                          {pattern.severity === 'info' && <InfoIcon color="info" fontSize="small" />}
                          <Typography variant="body2" sx={{ fontWeight: 'bold' }}>
                            {pattern.type}
                          </Typography>
                        </Box>
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2">
                          {pattern.description}
                        </Typography>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
            
            <WhyThisMatters>
              These patterns are detected from your table structure and represent common failure modes 
              encountered in production: constraint violations (23xxx errors), cascade depth issues, 
              and partial payload failures.
            </WhyThisMatters>
          </Box>
        </Paper>
      )}

      {/* Columns Section */}
      <Paper elevation={1} sx={{ mb: 3 }} ref={columnsRef}>
        <Box
          sx={{
            p: 2,
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            cursor: 'pointer',
            bgcolor: '#f5f5f5',
            '&:hover': { bgcolor: '#eeeeee' }
          }}
          onClick={() => toggleSection('columns')}
        >
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Typography variant="subtitle1" sx={{ fontWeight: 'bold' }}>
              🧱 Columns
            </Typography>
            <Chip label={`${columns.length} total`} size="small" color="info" />
          </Box>
          <IconButton
            size="small"
            sx={{
              transform: expandedSections.columns ? 'rotate(180deg)' : 'rotate(0deg)',
              transition: 'transform 0.3s'
            }}
          >
            <ExpandMoreIcon />
          </IconButton>
        </Box>
        <Collapse in={expandedSections.columns}>
          <Divider />
          <Box sx={{ p: 2 }}>
            {columns.length > 0 ? (
              <Box>
                <Box sx={{ mb: 2, display: 'flex', flexWrap: 'wrap', gap: 1 }}>
                  <Chip label={`${requiredColumns.length} required`} size="small" color="warning" />
                  <Chip label={`${nullableColumns.length} nullable`} size="small" color="default" />
                  <Chip label={`${defaultedColumns.length} with default`} size="small" color="success" />
                </Box>
                <TableContainer>
                  <Table size="small">
                    <TableHead>
                      <TableRow sx={{ backgroundColor: '#f5f5f5' }}>
                        <TableCell><strong>#</strong></TableCell>
                        <TableCell><strong>Name</strong></TableCell>
                        <TableCell><strong>Type</strong></TableCell>
                        <TableCell><strong>Nullable</strong></TableCell>
                        <TableCell><strong>Default</strong></TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {columns.map((col) => (
                        <TableRow key={col.name} hover>
                          <TableCell>{col.ordinalPosition}</TableCell>
                          <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.9rem' }}>{col.name}</TableCell>
                          <TableCell>{col.dataType || '-'}</TableCell>
                          <TableCell>
                            <Chip
                              label={col.nullable ? 'YES' : 'NO'}
                              color={col.nullable ? 'default' : 'warning'}
                              size="small"
                            />
                          </TableCell>
                          <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>
                            {col.columnDefault || '-'}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              </Box>
            ) : (
              <Typography variant="body2" color="text.secondary">No column metadata found</Typography>
            )}
          </Box>
        </Collapse>
      </Paper>

      {/* Indexes Section */}
      <Paper elevation={1} sx={{ mb: 3 }} ref={indexesRef}>
        <Box
          sx={{
            p: 2,
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            cursor: 'pointer',
            bgcolor: '#f5f5f5',
            '&:hover': { bgcolor: '#eeeeee' }
          }}
          onClick={() => toggleSection('indexes')}
        >
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Typography variant="subtitle1" sx={{ fontWeight: 'bold' }}>
              🔍 Indexes
            </Typography>
            <Chip label={`${indexes.length} total`} size="small" color="primary" />
          </Box>
          <IconButton
            size="small"
            sx={{
              transform: expandedSections.indexes ? 'rotate(180deg)' : 'rotate(0deg)',
              transition: 'transform 0.3s'
            }}
          >
            <ExpandMoreIcon />
          </IconButton>
        </Box>
        <Collapse in={expandedSections.indexes}>
          <Divider />
          <Box sx={{ p: 2 }}>
            {indexes.length > 0 ? (
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow sx={{ backgroundColor: '#f5f5f5' }}>
                      <TableCell><strong>Name</strong></TableCell>
                      <TableCell><strong>Type</strong></TableCell>
                      <TableCell><strong>Columns</strong></TableCell>
                      <TableCell><strong>Method</strong></TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {primaryIndexes.map(idx => (
                      <TableRow key={idx.name} hover>
                        <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.85rem' }}>{idx.name}</TableCell>
                        <TableCell>
                          <Chip label="🔑 PRIMARY" color="error" size="small" />
                        </TableCell>
                        <TableCell>{idx.columns?.join(', ') || '-'}</TableCell>
                        <TableCell>{idx.accessMethod}</TableCell>
                      </TableRow>
                    ))}
                    {uniqueIndexes.map(idx => (
                      <TableRow key={idx.name} hover>
                        <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.85rem' }}>{idx.name}</TableCell>
                        <TableCell>
                          <Chip label="UNIQUE" color="success" size="small" />
                        </TableCell>
                        <TableCell>{idx.columns?.join(', ') || '-'}</TableCell>
                        <TableCell>{idx.accessMethod}</TableCell>
                      </TableRow>
                    ))}
                    {regularIndexes.map(idx => (
                      <TableRow key={idx.name} hover>
                        <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.85rem' }}>{idx.name}</TableCell>
                        <TableCell>
                          <Chip label="INDEX" color="default" size="small" />
                        </TableCell>
                        <TableCell>{idx.columns?.join(', ') || '-'}</TableCell>
                        <TableCell>{idx.accessMethod}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            ) : (
              <Typography variant="body2" color="text.secondary">No indexes found</Typography>
            )}

            <WhyThisMatters>
              Missing or misaligned indexes can cause severe performance degradation under load, 
              especially for cart and LOS queries. Each query should have appropriate indexes on 
              frequently filtered or joined columns.
            </WhyThisMatters>
          </Box>
        </Collapse>
      </Paper>

      {/* Constraints Section */}
      <Paper elevation={1} sx={{ mb: 3 }} ref={constraintsRef}>
        <Box
          sx={{
            p: 2,
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            cursor: 'pointer',
            bgcolor: '#f5f5f5',
            '&:hover': { bgcolor: '#eeeeee' }
          }}
          onClick={() => toggleSection('constraints')}
        >
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Typography variant="subtitle1" sx={{ fontWeight: 'bold' }}>
              🔒 Constraints
            </Typography>
            <Chip label={`${constraints.length} total`} size="small" color="info" />
          </Box>
          <IconButton
            size="small"
            sx={{
              transform: expandedSections.constraints ? 'rotate(180deg)' : 'rotate(0deg)',
              transition: 'transform 0.3s'
            }}
          >
            <ExpandMoreIcon />
          </IconButton>
        </Box>
        <Collapse in={expandedSections.constraints}>
          <Divider />
          <Box sx={{ p: 2 }}>
            {/* Constraint Summary */}
            {constraints.length > 0 && (
              <Box sx={{ mb: 3, p: 2, bgcolor: '#f5f5f5', borderRadius: 1 }}>
                <Typography variant="subtitle2" sx={{ mb: 1, fontWeight: 'bold' }}>
                  Constraint Breakdown
                </Typography>
                <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
                  {primaryKeys.length > 0 && (
                    <Chip label={`${primaryKeys.length} Primary Key${primaryKeys.length !== 1 ? 's' : ''}`} color="error" size="small" />
                  )}
                  {foreignKeys.length > 0 && (
                    <Chip label={`${foreignKeys.length} Foreign Key${foreignKeys.length !== 1 ? 's' : ''}`} color="info" size="small" />
                  )}
                  {uniqueConstraints.length > 0 && (
                    <Chip label={`${uniqueConstraints.length} Unique`} color="success" size="small" />
                  )}
                  {checkConstraints.length > 0 && (
                    <Chip label={`${checkConstraints.length} Check`} color="warning" size="small" />
                  )}
                  {otherConstraints.length > 0 && (
                    <Chip label={`${otherConstraints.length} Other`} color="default" size="small" />
                  )}
                </Box>
              </Box>
            )}
            
            {/* Primary Keys */}
            {primaryKeys.length > 0 && (
              <Box sx={{ mb: 3 }} ref={pkRef}>
                <Typography variant="subtitle2" sx={{ mb: 1, fontWeight: 'bold', display: 'flex', alignItems: 'center', gap: 0.5 }}>
                  🔑 Primary Keys
                </Typography>
                <TableContainer>
                  <Table size="small">
                    <TableHead>
                      <TableRow sx={{ backgroundColor: '#ffebee' }}>
                        <TableCell><strong>Name</strong></TableCell>
                        <TableCell><strong>Columns</strong></TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {primaryKeys.map((c, idx) => (
                        <TableRow key={c.name || `pk-${idx}`} hover>
                          <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.85rem' }}>{c.name || '(unnamed)'}</TableCell>
                          <TableCell>{c.columns?.join(', ') || '-'}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              </Box>
            )}

            {/* Foreign Keys */}
            {foreignKeys.length > 0 && (
              <Box sx={{ mb: 3 }} ref={fkRef}>
                <Typography variant="subtitle2" sx={{ mb: 1, fontWeight: 'bold', display: 'flex', alignItems: 'center', gap: 0.5 }}>
                  🔗 Foreign Keys
                  {hasCascadeRisk && (
                    <Chip label={`${cascadingFKs.length} with CASCADE`} color="warning" size="small" sx={{ ml: 1 }} />
                  )}
                </Typography>
                <TableContainer>
                  <Table size="small">
                    <TableHead>
                      <TableRow sx={{ backgroundColor: '#e3f2fd' }}>
                        <TableCell><strong>Name</strong></TableCell>
                        <TableCell><strong>Columns</strong></TableCell>
                        <TableCell><strong>Definition</strong></TableCell>
                        <TableCell><strong>Risk</strong></TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {foreignKeys.map((c, idx) => {
                        const riskLevel = getFKRiskLevel(c);
                        const def = c.definition?.toLowerCase() || '';
                        const hasCascade = def.includes('on delete cascade') || def.includes('on update cascade');
                        const isRecursive = def.includes(`references ${schema}.${table}`);
                        
                        let riskLabel = 'Normal';
                        let riskColor = 'default';
                        let riskTooltip = 'No cascade behavior';
                        
                        if (riskLevel === 'critical') {
                          riskLabel = '🔴 Critical (recursive)';
                          riskColor = 'error';
                          riskTooltip = 'Self-referencing FK with CASCADE - deep deletes can cause long transactions';
                        } else if (riskLevel === 'high') {
                          riskLabel = '🟠 High (root delete)';
                          riskColor = 'warning';
                          riskTooltip = 'FK from parent entity - deleting parent will cascade to many records';
                        } else if (riskLevel === 'moderate') {
                          riskLabel = '🟡 Moderate';
                          riskColor = 'warning';
                          riskTooltip = 'Cascade from reference/lookup table';
                        }
                        
                        return (
                          <TableRow key={c.name || `fk-${idx}`} hover sx={{ 
                            bgcolor: riskLevel === 'critical' ? '#ffebee' : 
                                     riskLevel === 'high' ? '#fff3e0' : 'inherit' 
                          }}>
                            <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.85rem' }}>{c.name || '(unnamed)'}</TableCell>
                            <TableCell>{c.columns?.join(', ') || '-'}</TableCell>
                            <TableCell sx={{ fontSize: '0.75rem' }}>{c.definition}</TableCell>
                            <TableCell>
                              {hasCascade ? (
                                <Tooltip title={riskTooltip}>
                                  <Chip label={riskLabel} color={riskColor} size="small" />
                                </Tooltip>
                              ) : (
                                <Chip label="Normal" size="small" variant="outlined" />
                              )}
                            </TableCell>
                          </TableRow>
                        );
                      })}
                    </TableBody>
                  </Table>
                </TableContainer>

                <WhyThisMatters>
                  Foreign key cascades can amplify delete operations and cause unexpected data loss or 
                  latency spikes. CASCADE rules should be carefully reviewed, especially in high-traffic 
                  tables like cart items.
                </WhyThisMatters>
              </Box>
            )}

            {/* Unique Constraints */}
            {uniqueConstraints.length > 0 && (
              <Box sx={{ mb: 3 }}>
                <Typography variant="subtitle2" sx={{ mb: 1, fontWeight: 'bold' }}>
                  Unique Constraints
                </Typography>
                <TableContainer>
                  <Table size="small">
                    <TableHead>
                      <TableRow sx={{ backgroundColor: '#e8f5e9' }}>
                        <TableCell><strong>Name</strong></TableCell>
                        <TableCell><strong>Columns</strong></TableCell>
                        <TableCell><strong>CRUD Implications</strong></TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {uniqueConstraints.map((c, idx) => {
                        const colCount = c.columns?.length || 0;
                        let crudImplication = '';
                        
                        if (colCount >= 3) {
                          crudImplication = `INSERT requires all ${colCount} fields stable and deterministic. Partial updates may violate uniqueness (23505).`;
                        } else if (colCount === 2) {
                          crudImplication = 'Composite uniqueness. Both columns must be provided on INSERT.';
                        } else {
                          crudImplication = 'Single-column uniqueness enforced.';
                        }
                        
                        return (
                          <TableRow key={c.name || `unique-${idx}`} hover>
                            <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.85rem' }}>{c.name || '(unnamed)'}</TableCell>
                            <TableCell>{c.columns?.join(', ') || '-'}</TableCell>
                            <TableCell>
                              <Typography variant="caption" sx={{ fontSize: '0.75rem', color: 'text.secondary' }}>
                                {crudImplication}
                              </Typography>
                            </TableCell>
                          </TableRow>
                        );
                      })}
                    </TableBody>
                  </Table>
                </TableContainer>
              </Box>
            )}

            {/* Check Constraints */}
            {checkConstraints.length > 0 && (
              <Box sx={{ mb: 3 }}>
                <Typography variant="subtitle2" sx={{ mb: 1, fontWeight: 'bold', display: 'flex', alignItems: 'center', gap: 0.5 }}>
                  🧪 Check Constraints
                </Typography>
                <TableContainer>
                  <Table size="small">
                    <TableHead>
                      <TableRow sx={{ backgroundColor: '#fff3e0' }}>
                        <TableCell><strong>Name</strong></TableCell>
                        <TableCell><strong>Columns</strong></TableCell>
                        <TableCell><strong>Definition</strong></TableCell>
                        <TableCell><strong>CRUD Implications</strong></TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {checkConstraints.map((c, idx) => {
                        const def = c.definition?.toLowerCase() || '';
                        let crudImplication = '';
                        
                        if (def.includes('>') || def.includes('<') || def.includes('=')) {
                          crudImplication = 'INSERT/UPDATE will fail with 23514 if condition not met.';
                        } else if (def.includes('in (')) {
                          crudImplication = 'Value must be in allowed set. Invalid values fail with 23514.';
                        } else {
                          crudImplication = 'Business rule enforced at database level.';
                        }
                        
                        return (
                          <TableRow key={c.name || `check-${idx}`} hover>
                            <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.85rem' }}>{c.name || '(unnamed)'}</TableCell>
                            <TableCell>{c.columns?.join(', ') || '-'}</TableCell>
                            <TableCell sx={{ fontSize: '0.75rem' }}>{c.definition}</TableCell>
                            <TableCell>
                              <Typography variant="caption" sx={{ fontSize: '0.75rem', color: 'text.secondary' }}>
                                {crudImplication}
                              </Typography>
                            </TableCell>
                          </TableRow>
                        );
                      })}
                    </TableBody>
                  </Table>
                </TableContainer>
                
                <Alert severity="info" sx={{ mt: 2, fontSize: '0.85rem' }}>
                  <Typography variant="caption">
                    💡 <strong>Example:</strong> If you see <code>CHECK (quantity &gt; 0)</code>, then 
                    <code>UPDATE quantity = 0</code> will fail. Frontend validations should mirror these rules.
                  </Typography>
                </Alert>
              </Box>
            )}

            {/* Other Constraints (NOT NULL, DEFAULT, etc.) */}
            {otherConstraints.length > 0 && (
              <Box sx={{ mb: 3 }}>
                <Typography variant="subtitle2" sx={{ mb: 1, fontWeight: 'bold', display: 'flex', alignItems: 'center', gap: 0.5 }}>
                  📋 Other Constraints
                  <Chip label={`${otherConstraints.length}`} size="small" sx={{ ml: 0.5 }} />
                </Typography>
                <TableContainer>
                  <Table size="small">
                    <TableHead>
                      <TableRow sx={{ backgroundColor: '#f5f5f5' }}>
                        <TableCell><strong>Name</strong></TableCell>
                        <TableCell><strong>Type</strong></TableCell>
                        <TableCell><strong>Columns</strong></TableCell>
                        <TableCell><strong>Definition</strong></TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {otherConstraints.map((c, idx) => (
                        <TableRow key={c.name || `other-${idx}`} hover>
                          <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.85rem' }}>{c.name || '(unnamed)'}</TableCell>
                          <TableCell>
                            <Chip label={c.type || 'OTHER'} size="small" variant="outlined" />
                          </TableCell>
                          <TableCell>{c.columns?.join(', ') || '-'}</TableCell>
                          <TableCell sx={{ fontSize: '0.75rem' }}>{c.definition || '-'}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              </Box>
            )}

            {constraints.length === 0 && (
              <Typography variant="body2" color="text.secondary">No constraints found</Typography>
            )}
          </Box>
        </Collapse>
      </Paper>
    </Box>
  );
}
