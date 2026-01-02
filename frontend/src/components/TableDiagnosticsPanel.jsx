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
  Tooltip
} from '@mui/material';
import {
  ExpandMore as ExpandMoreIcon,
  ContentCopy as ContentCopyIcon,
  CheckCircle as CheckCircleIcon,
  Warning as WarningIcon,
  Error as ErrorIcon,
  Info as InfoIcon,
  KeyboardArrowDown as KeyboardArrowDownIcon
} from '@mui/icons-material';
import { apiService } from '../services/apiService';

export default function TableDiagnosticsPanel({ data, connectionId, schema }) {
  const [privilegesData, setPrivilegesData] = useState(null);
  const [loadingPrivileges, setLoadingPrivileges] = useState(false);
  const [privilegesChecked, setPrivilegesChecked] = useState(false);
  const [copySuccess, setCopySuccess] = useState(false);
  const [expandedSections, setExpandedSections] = useState({
    indexes: false,
    constraints: false,
    ownership: false
  });

  // Refs for scrolling to sections
  const ownershipRef = useRef(null);
  const indexesRef = useRef(null);
  const constraintsRef = useRef(null);
  const fkRef = useRef(null);

  if (!data) return null;

  const { table, owner, currentUser, indexes = [], constraints = [], flywayInfo } = data;
  
  // Categorize constraints
  const primaryKeys = constraints.filter(c => c.type === 'PRIMARY KEY');
  const foreignKeys = constraints.filter(c => c.type === 'FOREIGN KEY');
  const uniqueConstraints = constraints.filter(c => c.type === 'UNIQUE');
  const checkConstraints = constraints.filter(c => c.type === 'CHECK');
  
  // Categorize indexes
  const primaryIndexes = indexes.filter(i => i.primary);
  const uniqueIndexes = indexes.filter(i => i.unique && !i.primary);
  const regularIndexes = indexes.filter(i => !i.unique && !i.primary);

  // Analyze FK cascades
  const cascadingFKs = foreignKeys.filter(fk => 
    fk.definition && (fk.definition.includes('ON DELETE CASCADE') || fk.definition.includes('ON UPDATE CASCADE'))
  );

  // Diagnostic calculations
  const ownershipOk = owner === currentUser;
  const hasSelectAccess = privilegesData?.grantedPrivileges?.includes('SELECT') || false;
  const hasWriteAccess = privilegesData?.grantedPrivileges?.some(p => 
    ['INSERT', 'UPDATE', 'DELETE'].includes(p)
  ) || false;
  const fkIntegrityOk = foreignKeys.length === 0 || foreignKeys.every(fk => fk.definition);
  const hasCascadeRisk = cascadingFKs.length > 0;
  
  // Flyway drift detection
  const hasFlywayDrift = flywayInfo && owner && flywayInfo.installedBy !== currentUser && owner !== currentUser;

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

  const scrollToSection = (ref) => {
    ref.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
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
        <Button
          size="small"
          startIcon={<ContentCopyIcon />}
          onClick={handleCopyDiagnostics}
          variant={copySuccess ? 'contained' : 'outlined'}
          color={copySuccess ? 'success' : 'primary'}
        >
          {copySuccess ? 'Copied!' : 'Copy Diagnostics'}
        </Button>
      </Box>

      {/* Diagnostics Summary Strip */}
      <Paper elevation={2} sx={{ p: 2, mb: 3, bgcolor: hasFlywayDrift ? '#fff3e0' : 'background.paper' }}>
        <Typography variant="subtitle1" sx={{ mb: 2, fontWeight: 'bold' }}>
          📊 Diagnostics Summary
        </Typography>
        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1.5 }}>
          <DiagnosticPill
            icon={ownershipOk ? <CheckCircleIcon fontSize="small" /> : <ErrorIcon fontSize="small" />}
            label={ownershipOk ? 'Ownership OK' : 'Ownership Mismatch'}
            status={ownershipOk ? 'success' : 'error'}
            onClick={() => scrollToSection(ownershipRef)}
          />
          
          {privilegesChecked && (
            <>
              <DiagnosticPill
                icon={hasSelectAccess ? <CheckCircleIcon fontSize="small" /> : <ErrorIcon fontSize="small" />}
                label={hasSelectAccess ? 'SELECT Access' : 'SELECT Missing'}
                status={hasSelectAccess ? 'success' : 'error'}
                onClick={() => scrollToSection(ownershipRef)}
              />
              
              {!hasWriteAccess && (
                <DiagnosticPill
                  icon={<WarningIcon fontSize="small" />}
                  label="Write Access Limited"
                  status="warning"
                  onClick={() => scrollToSection(ownershipRef)}
                />
              )}
            </>
          )}

          <DiagnosticPill
            icon={fkIntegrityOk ? <CheckCircleIcon fontSize="small" /> : <WarningIcon fontSize="small" />}
            label={fkIntegrityOk ? 'FK Integrity OK' : 'FK Issues'}
            status={fkIntegrityOk ? 'success' : 'warning'}
            onClick={() => scrollToSection(fkRef)}
          />

          {hasCascadeRisk && (
            <DiagnosticPill
              icon={<WarningIcon fontSize="small" />}
              label={`${cascadingFKs.length} Cascade FK${cascadingFKs.length > 1 ? 's' : ''}`}
              status="warning"
              onClick={() => scrollToSection(fkRef)}
            />
          )}
        </Box>
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
        <Box sx={{ p: 2, bgcolor: '#f5f5f5' }}>
          <Typography variant="subtitle1" sx={{ fontWeight: 'bold' }}>
            🔐 Ownership & Access Diagnostics
          </Typography>
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
                      return (
                        <TableRow key={priv}>
                          <TableCell><strong>{priv}</strong></TableCell>
                          <TableCell>
                            {hasPriv ? (
                              <Chip icon={<CheckCircleIcon />} label="Allowed" color="success" size="small" />
                            ) : (
                              <Chip icon={<ErrorIcon />} label="Missing" color="error" size="small" />
                            )}
                          </TableCell>
                          <TableCell>
                            <Typography variant="caption">
                              {hasPriv ? 'Permission granted' : 'No permission'}
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
            {/* Primary Keys */}
            {primaryKeys.length > 0 && (
              <Box sx={{ mb: 3 }}>
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
                      {primaryKeys.map(c => (
                        <TableRow key={c.name} hover>
                          <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.85rem' }}>{c.name}</TableCell>
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
                      {foreignKeys.map(c => {
                        const hasCascade = c.definition && (
                          c.definition.includes('ON DELETE CASCADE') || 
                          c.definition.includes('ON UPDATE CASCADE')
                        );
                        return (
                          <TableRow key={c.name} hover sx={{ bgcolor: hasCascade ? '#fff3e0' : 'inherit' }}>
                            <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.85rem' }}>{c.name}</TableCell>
                            <TableCell>{c.columns?.join(', ') || '-'}</TableCell>
                            <TableCell sx={{ fontSize: '0.75rem' }}>{c.definition}</TableCell>
                            <TableCell>
                              {hasCascade ? (
                                <Tooltip title="Deletes/updates will cascade to related records">
                                  <Chip label="🟡 High impact" color="warning" size="small" />
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
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {uniqueConstraints.map(c => (
                        <TableRow key={c.name} hover>
                          <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.85rem' }}>{c.name}</TableCell>
                          <TableCell>{c.columns?.join(', ') || '-'}</TableCell>
                        </TableRow>
                      ))}
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
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {checkConstraints.map(c => (
                        <TableRow key={c.name} hover>
                          <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.85rem' }}>{c.name}</TableCell>
                          <TableCell>{c.columns?.join(', ') || '-'}</TableCell>
                          <TableCell sx={{ fontSize: '0.75rem' }}>{c.definition}</TableCell>
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

