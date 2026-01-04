import React, { useState } from 'react';
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
  CircularProgress
} from '@mui/material';
import {
  ExpandMore as ExpandMoreIcon,
  ContentCopy as ContentCopyIcon,
  Warning as WarningIcon,
  Download as DownloadIcon
} from '@mui/icons-material';
import { apiService } from '../services/apiService';

export default function FlywayHealthPanel({ data, connectionId }) {
  const [expandedSections, setExpandedSections] = useState({
    identity: false,
    flywaySummary: false,
    installedBy: false,
    migrationHistory: false
  });
  const [copySuccess, setCopySuccess] = useState(false);
  const [exportSuccess, setExportSuccess] = useState(false);
  const [migrationHistory, setMigrationHistory] = useState(null);
  const [loadingHistory, setLoadingHistory] = useState(false);

  if (!data) return null;

  const { status, message, identity, flywaySummary, expectedUser, warnings = [] } = data;

  // Automatically load migration history when panel loads
  React.useEffect(() => {
    const loadHistory = async () => {
      if (!connectionId || !flywaySummary?.historyTableExists) return;

      setLoadingHistory(true);
      try {
        const response = await apiService.getFlywayHistory(connectionId, 50);
        setMigrationHistory(response.data);
      } catch (error) {
        console.error('Failed to load migration history:', error);
        setMigrationHistory([]);
      } finally {
        setLoadingHistory(false);
      }
    };

    loadHistory();
  }, [connectionId, flywaySummary?.historyTableExists]);
  const latestApplied = flywaySummary?.latestApplied;
  const installedBySummary = flywaySummary?.installedBySummary || [];

  // Status color mapping
  const getStatusColor = (status) => {
    switch (status) {
      case 'HEALTHY': return 'success';
      case 'DEGRADED': return 'warning';
      case 'FAILED': return 'error';
      case 'NOT_CONFIGURED': return 'default';
      default: return 'default';
    }
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
      const diffSec = Math.floor(diffMs / 1000);
      const diffMin = Math.floor(diffSec / 60);
      const diffHour = Math.floor(diffMin / 60);
      const diffDay = Math.floor(diffHour / 24);

      if (diffSec < 60) return 'just now';
      if (diffMin < 60) return `${diffMin} minute${diffMin !== 1 ? 's' : ''} ago`;
      if (diffHour < 24) return `${diffHour} hour${diffHour !== 1 ? 's' : ''} ago`;
      if (diffDay < 30) return `${diffDay} day${diffDay !== 1 ? 's' : ''} ago`;
      if (diffDay < 365) {
        const months = Math.floor(diffDay / 30);
        return `${months} month${months !== 1 ? 's' : ''} ago`;
      }
      const years = Math.floor(diffDay / 365);
      return `${years} year${years !== 1 ? 's' : ''} ago`;
    } catch {
      return new Date(timestamp).toLocaleString();
    }
  };

  const generateDiagnosticText = () => {
    const lines = [];
    lines.push('═══════════════════════════════════════');
    lines.push('FLYWAY HEALTH DIAGNOSTIC REPORT');
    lines.push('═══════════════════════════════════════');
    lines.push('');
    lines.push(`Status: ${status}`);
    lines.push(`Message: ${message}`);
    lines.push('');
    lines.push('─── DATABASE CONNECTION ───');
    lines.push(`Database: ${identity?.database || 'N/A'}`);
    lines.push(`Connected as: ${identity?.currentUser || 'N/A'}`);
    lines.push(`Expected user: ${expectedUser || 'N/A'}`);
    lines.push(`Server: ${identity?.serverAddr || 'N/A'}:${identity?.serverPort || 'N/A'}`);
    lines.push(`Version: ${identity?.serverVersion || 'N/A'}`);
    lines.push('');

    if (latestApplied) {
      lines.push('─── LATEST MIGRATION ───');
      lines.push(`Version: ${latestApplied.version}`);
      lines.push(`Description: ${latestApplied.description}`);
      lines.push(`Installed by: ${latestApplied.installedBy}`);
      lines.push(`Installed on: ${new Date(latestApplied.installedOn).toLocaleString()}`);
      lines.push('');
    }

    if (flywaySummary?.failedCount > 0) {
      lines.push(`⚠️  Failed migrations: ${flywaySummary.failedCount}`);
      lines.push('');
    }

    if (installedBySummary.length > 0) {
      lines.push('─── INSTALLED BY SUMMARY ───');
      installedBySummary.forEach(item => {
        lines.push(`  • ${item.installedBy}: ${item.appliedCount} migration(s) (last: ${formatRelativeTime(item.lastSeen)})`);
      });
      lines.push('');
    }

    if (warnings.length > 0) {
      lines.push('─── WARNINGS ───');
      warnings.forEach(warning => {
        lines.push(`  ⚠️  [${warning.code}] ${warning.message}`);
      });
      lines.push('');
    }

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

  const hasWarnings = warnings.length > 0;
  const hasDrift = warnings.some(w => w.code === 'CREDENTIAL_DRIFT' || w.code === 'MULTIPLE_INSTALLERS');

  return (
    <Box sx={{ width: '100%' }}>
      {/* Warning Banner */}
      {hasWarnings && (
        <Alert severity="warning" sx={{ mb: 2 }} icon={<WarningIcon />}>
          <AlertTitle>Credential Drift Detected</AlertTitle>
          {warnings.map((warning, idx) => (
            <Typography key={idx} variant="body2">
              <strong>[{warning.code}]</strong> {warning.message}
            </Typography>
          ))}
          <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block', fontStyle: 'italic' }}>
            💡 Why this matters: Migrations executed under a different role may create objects or permissions that the application user cannot access.
          </Typography>
        </Alert>
      )}

      {/* Top-Level Compact Header */}
      <Paper elevation={2} sx={{ p: 2, mb: 2, bgcolor: hasDrift ? '#fff3e0' : 'background.paper' }}>
        <Typography variant="h6" sx={{ mb: 2, display: 'flex', alignItems: 'center', gap: 1 }}>
          🛫 Flyway Health
          <Chip
            label={status}
            color={getStatusColor(status)}
            size="small"
          />
        </Typography>

        {/* 4-item compact grid */}
        <Box sx={{
          display: 'grid',
          gridTemplateColumns: { xs: '1fr', md: 'repeat(2, 1fr)' },
          gap: 2,
          mb: 2
        }}>
          {/* 1. Status & Message */}
          <Box>
            <Typography variant="caption" color="text.secondary" display="block">
              Status
            </Typography>
            <Typography variant="body2" sx={{ fontWeight: 'medium' }}>
              {message}
            </Typography>
          </Box>

          {/* 2. Connected As */}
          <Box>
            <Typography variant="caption" color="text.secondary" display="block">
              Connected As
            </Typography>
            <Typography variant="body2" sx={{ fontWeight: 'medium', fontFamily: 'monospace' }}>
              {identity?.currentUser || 'N/A'}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              {identity?.database}@{identity?.serverAddr}:{identity?.serverPort}
            </Typography>
          </Box>

          {/* 3. Last Migration */}
          <Box>
            <Typography variant="caption" color="text.secondary" display="block">
              Last Migration
            </Typography>
            {latestApplied ? (
              <>
                <Typography variant="body2" sx={{ fontWeight: 'medium' }}>
                  {latestApplied.version} - {latestApplied.description}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {formatRelativeTime(latestApplied.installedOn)} by {latestApplied.installedBy}
                </Typography>
              </>
            ) : (
              <Typography variant="body2" color="text.secondary">
                {flywaySummary?.historyTableExists ? 'No migrations found' : 'N/A'}
              </Typography>
            )}
          </Box>

          {/* 4. Failed Migrations (if any) */}
          {flywaySummary?.failedCount > 0 && (
            <Box>
              <Typography variant="caption" color="text.secondary" display="block">
                Failed Migrations
              </Typography>
              <Chip
                label={`${flywaySummary.failedCount} Failed`}
                color="error"
                size="small"
              />
            </Box>
          )}
        </Box>

        {/* Quick Actions Row */}
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mt: 1 }}>
          <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 'bold' }}>
            Quick Actions
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
      </Paper>

      {/* Expandable Sections */}
      <Stack spacing={1}>
        {/* 1. Migration History */}
        {flywaySummary?.historyTableExists && (
          <Paper elevation={1}>
            <Box
              sx={{
                p: 1.5,
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                cursor: 'pointer',
                '&:hover': { bgcolor: 'action.hover' }
              }}
              onClick={() => toggleSection('migrationHistory')}
            >
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <Typography variant="subtitle2" sx={{ fontWeight: 'bold' }}>
                  📋 Migration History
                </Typography>
                {loadingHistory ? (
                  <CircularProgress size={16} />
                ) : migrationHistory ? (
                  <Chip label={`${migrationHistory.length} records`} color="primary" size="small" />
                ) : null}
              </Box>
              <IconButton
                size="small"
                sx={{
                  transform: expandedSections.migrationHistory ? 'rotate(180deg)' : 'rotate(0deg)',
                  transition: 'transform 0.3s'
                }}
              >
                <ExpandMoreIcon />
              </IconButton>
            </Box>
            <Collapse in={expandedSections.migrationHistory}>
              <Divider />
              <Box sx={{ p: 2, maxHeight: 400, overflow: 'auto' }}>
                {loadingHistory ? (
                  <Box sx={{ display: 'flex', justifyContent: 'center', p: 3 }}>
                    <CircularProgress />
                  </Box>
                ) : migrationHistory && migrationHistory.length > 0 ? (
                  <TableContainer>
                    <Table size="small">
                      <TableHead>
                        <TableRow sx={{ backgroundColor: '#f5f5f5' }}>
                          <TableCell><strong>Rank</strong></TableCell>
                          <TableCell><strong>Version</strong></TableCell>
                          <TableCell><strong>Description</strong></TableCell>
                          <TableCell><strong>Type</strong></TableCell>
                          <TableCell><strong>Script</strong></TableCell>
                          <TableCell><strong>Installed By</strong></TableCell>
                          <TableCell><strong>Installed On</strong></TableCell>
                          <TableCell><strong>Time (ms)</strong></TableCell>
                          <TableCell><strong>Status</strong></TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {migrationHistory.map((row, index) => (
                          <TableRow
                            key={index}
                            hover
                            sx={{
                              bgcolor: row.success ? 'inherit' : '#ffebee',
                              '&:hover': { bgcolor: row.success ? 'action.hover' : '#ffcdd2' }
                            }}
                          >
                            <TableCell>{row.installedRank}</TableCell>
                            <TableCell sx={{ fontFamily: 'monospace' }}>{row.version}</TableCell>
                            <TableCell>{row.description}</TableCell>
                            <TableCell>
                              <Chip label={row.type} size="small" variant="outlined" />
                            </TableCell>
                            <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>{row.script}</TableCell>
                            <TableCell sx={{ fontFamily: 'monospace' }}>{row.installedBy}</TableCell>
                            <TableCell>
                              {new Date(row.installedOn).toLocaleString()}
                              <Typography variant="caption" color="text.secondary" display="block">
                                ({formatRelativeTime(row.installedOn)})
                              </Typography>
                            </TableCell>
                            <TableCell align="right">{row.executionTimeMs}</TableCell>
                            <TableCell>
                              <Chip
                                label={row.success ? 'SUCCESS' : 'FAILED'}
                                color={row.success ? 'success' : 'error'}
                                size="small"
                              />
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </TableContainer>
                ) : (
                  <Alert severity="info">No migration history found.</Alert>
                )}
              </Box>
            </Collapse>
          </Paper>
        )}

        {/* 2. Connection Identity */}
        <Paper elevation={1}>
          <Box
            sx={{
              p: 1.5,
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              cursor: 'pointer',
              '&:hover': { bgcolor: 'action.hover' }
            }}
            onClick={() => toggleSection('identity')}
          >
            <Typography variant="subtitle2" sx={{ fontWeight: 'bold' }}>
              🔐 Connection Identity
            </Typography>
            <IconButton
              size="small"
              sx={{
                transform: expandedSections.identity ? 'rotate(180deg)' : 'rotate(0deg)',
                transition: 'transform 0.3s'
              }}
            >
              <ExpandMoreIcon />
            </IconButton>
          </Box>
          <Collapse in={expandedSections.identity}>
            <Divider />
            <Box sx={{ p: 2 }}>
              <TableContainer>
                <Table size="small">
                  <TableBody>
                    <TableRow>
                      <TableCell width="30%"><strong>Database</strong></TableCell>
                      <TableCell sx={{ fontFamily: 'monospace' }}>{identity?.database}</TableCell>
                    </TableRow>
                    <TableRow>
                      <TableCell><strong>Current User</strong></TableCell>
                      <TableCell>{identity?.currentUser}</TableCell>
                    </TableRow>
                    {expectedUser && (
                      <TableRow>
                        <TableCell><strong>Expected User</strong></TableCell>
                        <TableCell>
                          {expectedUser}
                          {expectedUser !== identity?.currentUser && (
                            <Chip label="Mismatch" color="warning" size="small" sx={{ ml: 1 }} />
                          )}
                        </TableCell>
                      </TableRow>
                    )}
                    <TableRow>
                      <TableCell><strong>Schema / search_path</strong></TableCell>
                      <TableCell sx={{ fontFamily: 'monospace' }}>{identity?.schema || 'public'}</TableCell>
                    </TableRow>
                    <TableRow>
                      <TableCell><strong>Server</strong></TableCell>
                      <TableCell>{identity?.serverAddr}:{identity?.serverPort}</TableCell>
                    </TableRow>
                    <TableRow>
                      <TableCell><strong>Server Time</strong></TableCell>
                      <TableCell>{identity?.serverTime ? new Date(identity.serverTime).toLocaleString() : 'N/A'}</TableCell>
                    </TableRow>
                    <TableRow>
                      <TableCell><strong>Version</strong></TableCell>
                      <TableCell sx={{ fontSize: '0.75rem' }}>{identity?.serverVersion}</TableCell>
                    </TableRow>
                  </TableBody>
                </Table>
              </TableContainer>
            </Box>
          </Collapse>
        </Paper>

        {/* 3. Flyway Summary */}
        <Paper elevation={1}>
          <Box
            sx={{
              p: 1.5,
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              cursor: 'pointer',
              '&:hover': { bgcolor: 'action.hover' }
            }}
            onClick={() => toggleSection('flywaySummary')}
          >
            <Typography variant="subtitle2" sx={{ fontWeight: 'bold' }}>
              📊 Flyway Summary
            </Typography>
            <IconButton
              size="small"
              sx={{
                transform: expandedSections.flywaySummary ? 'rotate(180deg)' : 'rotate(0deg)',
                transition: 'transform 0.3s'
              }}
            >
              <ExpandMoreIcon />
            </IconButton>
          </Box>
          <Collapse in={expandedSections.flywaySummary}>
            <Divider />
            <Box sx={{ p: 2 }}>
              <TableContainer>
                <Table size="small">
                  <TableBody>
                    <TableRow>
                      <TableCell width="30%"><strong>History Table Exists</strong></TableCell>
                      <TableCell>
                        {flywaySummary?.historyTableExists ? (
                          <Chip label="Yes" color="success" size="small" />
                        ) : (
                          <Chip label="No" color="error" size="small" />
                        )}
                      </TableCell>
                    </TableRow>
                    <TableRow>
                      <TableCell><strong>Failed Migrations</strong></TableCell>
                      <TableCell>
                        {flywaySummary?.failedCount > 0 ? (
                          <>
                            <Chip label={flywaySummary.failedCount} color="error" size="small" sx={{ mr: 1 }} />
                            <Typography variant="caption" color="error">
                              Requires attention
                            </Typography>
                          </>
                        ) : (
                          <Chip label="0" color="success" size="small" />
                        )}
                      </TableCell>
                    </TableRow>
                    {latestApplied && (
                      <>
                        <TableRow>
                          <TableCell colSpan={2}>
                            <Typography variant="subtitle2" sx={{ mt: 1, fontWeight: 'bold' }}>
                              Latest Applied Migration
                            </Typography>
                          </TableCell>
                        </TableRow>
                        <TableRow>
                          <TableCell><strong>Version</strong></TableCell>
                          <TableCell>{latestApplied.version}</TableCell>
                        </TableRow>
                        <TableRow>
                          <TableCell><strong>Description</strong></TableCell>
                          <TableCell>{latestApplied.description}</TableCell>
                        </TableRow>
                        <TableRow>
                          <TableCell><strong>Script</strong></TableCell>
                          <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.85rem' }}>
                            {latestApplied.script}
                          </TableCell>
                        </TableRow>
                        <TableRow>
                          <TableCell><strong>Installed By</strong></TableCell>
                          <TableCell>{latestApplied.installedBy}</TableCell>
                        </TableRow>
                        <TableRow>
                          <TableCell><strong>Installed On</strong></TableCell>
                          <TableCell>
                            {new Date(latestApplied.installedOn).toLocaleString()}
                            <Typography variant="caption" color="text.secondary" sx={{ ml: 1 }}>
                              ({formatRelativeTime(latestApplied.installedOn)})
                            </Typography>
                          </TableCell>
                        </TableRow>
                      </>
                    )}
                    {!latestApplied && flywaySummary?.historyTableExists && (
                      <TableRow>
                        <TableCell colSpan={2}>
                          <Typography variant="body2" color="text.secondary">
                            No successful migrations found
                          </Typography>
                        </TableCell>
                      </TableRow>
                    )}
                  </TableBody>
                </Table>
              </TableContainer>
            </Box>
          </Collapse>
        </Paper>

        {/* 4. Who has been running migrations? */}
        {installedBySummary.length > 0 && (
          <Paper elevation={1}>
            <Box
              sx={{
                p: 1.5,
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                cursor: 'pointer',
                '&:hover': { bgcolor: 'action.hover' },
                bgcolor: installedBySummary.length > 1 ? '#fff3e0' : 'background.paper'
              }}
              onClick={() => toggleSection('installedBy')}
            >
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <Typography variant="subtitle2" sx={{ fontWeight: 'bold' }}>
                  👥 Who has been running migrations?
                </Typography>
                {installedBySummary.length > 1 && (
                  <Chip label={`${installedBySummary.length} users`} color="warning" size="small" />
                )}
              </Box>
              <IconButton
                size="small"
                sx={{
                  transform: expandedSections.installedBy ? 'rotate(180deg)' : 'rotate(0deg)',
                  transition: 'transform 0.3s'
                }}
              >
                <ExpandMoreIcon />
              </IconButton>
            </Box>
            <Collapse in={expandedSections.installedBy}>
              <Divider />
              <Box sx={{ p: 2 }}>
                <TableContainer>
                  <Table size="small">
                    <TableHead>
                      <TableRow sx={{ backgroundColor: '#f5f5f5' }}>
                        <TableCell><strong>Installed By</strong></TableCell>
                        <TableCell align="right"><strong>Count</strong></TableCell>
                        <TableCell><strong>Last Seen</strong></TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {installedBySummary.map((item, idx) => (
                        <TableRow
                          key={idx}
                          hover
                          sx={{
                            bgcolor: item.installedBy === latestApplied?.installedBy ? '#e3f2fd' : 'inherit'
                          }}
                        >
                          <TableCell sx={{ fontFamily: 'monospace' }}>
                            {item.installedBy}
                            {item.installedBy === latestApplied?.installedBy && (
                              <Chip label="Latest" color="primary" size="small" sx={{ ml: 1 }} />
                            )}
                          </TableCell>
                          <TableCell align="right">{item.appliedCount}</TableCell>
                          <TableCell>
                            {formatRelativeTime(item.lastSeen)}
                            <Typography variant="caption" color="text.secondary" display="block">
                              {new Date(item.lastSeen).toLocaleString()}
                            </Typography>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              </Box>
            </Collapse>
          </Paper>
        )}
      </Stack>
    </Box>
  );
}

