import { useState } from 'react';
import { 
  Box, 
  Typography, 
  Paper, 
  IconButton,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  ToggleButton,
  ToggleButtonGroup,
  Chip
} from '@mui/material';
import { Clear, TableChart, Code } from '@mui/icons-material';

export default function ResultsPanel({ results, onClear }) {
  const [viewMode, setViewMode] = useState('table'); // 'table' or 'json'

  const handleViewModeChange = (event, newMode) => {
    if (newMode !== null) {
      setViewMode(newMode);
    }
  };

  // Check if results is a tables list
  const isTablesList = results?.tables && Array.isArray(results.tables);
  const isTableSearch = results?.matches && Array.isArray(results.matches);
  const isPrivileges = results?.status && results?.grantedPrivileges;
  const isIdentity = results?.database && results?.currentUser;
  const isFlywayHealth = results?.status && results?.historyTableExists !== undefined;

  const renderTablesView = (data) => {
    const tables = data.tables || data.matches || [];
    return (
      <TableContainer>
        <Table size="small">
          <TableHead>
            <TableRow sx={{ backgroundColor: '#f5f5f5' }}>
              <TableCell><strong>Table</strong></TableCell>
              <TableCell><strong>Owner</strong></TableCell>
              <TableCell align="center"><strong>Est. Rows</strong></TableCell>
              <TableCell><strong>Notes</strong></TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {tables.map((table) => {
              const tableName = table.name || table.tableName;
              const isFlyway = tableName === 'flyway_schema_history';
              const rowCount = table.estimatedRowCount || 0;
              
              return (
                <TableRow key={tableName} hover>
                  <TableCell sx={{ fontFamily: 'monospace' }}>{tableName}</TableCell>
                  <TableCell>{table.owner}</TableCell>
                  <TableCell align="center">
                    {rowCount === -1 || rowCount === 0 ? '~' : rowCount.toLocaleString()}
                  </TableCell>
                  <TableCell>
                    {isFlyway ? (
                      <Chip label="Flyway" size="small" color="info" />
                    ) : (
                      <span style={{ color: 'green' }}>✓</span>
                    )}
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </TableContainer>
    );
  };

  const renderPrivilegesView = (data) => {
    const statusColor = 
      data.status === 'PASS' ? 'success' : 
      data.status === 'WARNING' ? 'warning' : 
      'error';

    return (
      <Box>
        <Box sx={{ mb: 2, display: 'flex', gap: 1, alignItems: 'center' }}>
          <Chip label={data.status} color={statusColor} />
          <Typography variant="body2" color="text.secondary">
            {data.message}
          </Typography>
        </Box>

        <TableContainer>
          <Table size="small">
            <TableBody>
              <TableRow>
                <TableCell><strong>Table</strong></TableCell>
                <TableCell sx={{ fontFamily: 'monospace' }}>{data.schema}.{data.table}</TableCell>
              </TableRow>
              <TableRow>
                <TableCell><strong>Owner</strong></TableCell>
                <TableCell>{data.owner}</TableCell>
              </TableRow>
              <TableRow>
                <TableCell><strong>Current User</strong></TableCell>
                <TableCell>{data.currentUser}</TableCell>
              </TableRow>
              <TableRow>
                <TableCell><strong>Granted Privileges</strong></TableCell>
                <TableCell>
                  {data.grantedPrivileges?.length > 0 ? (
                    data.grantedPrivileges.map(p => (
                      <Chip key={p} label={p} size="small" color="success" sx={{ mr: 0.5 }} />
                    ))
                  ) : (
                    <Typography variant="body2" color="error">None</Typography>
                  )}
                </TableCell>
              </TableRow>
              {data.missingPrivileges?.length > 0 && (
                <TableRow>
                  <TableCell><strong>Missing Privileges</strong></TableCell>
                  <TableCell>
                    {data.missingPrivileges.map(p => (
                      <Chip key={p} label={p} size="small" color="error" sx={{ mr: 0.5 }} />
                    ))}
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </Box>
    );
  };

  const renderIdentityView = (data) => {
    return (
      <TableContainer>
        <Table size="small">
          <TableBody>
            <TableRow>
              <TableCell><strong>Database</strong></TableCell>
              <TableCell sx={{ fontFamily: 'monospace' }}>{data.database}</TableCell>
            </TableRow>
            <TableRow>
              <TableCell><strong>Current User</strong></TableCell>
              <TableCell>{data.currentUser}</TableCell>
            </TableRow>
            <TableRow>
              <TableCell><strong>Session User</strong></TableCell>
              <TableCell>{data.sessionUser}</TableCell>
            </TableRow>
            <TableRow>
              <TableCell><strong>Schema</strong></TableCell>
              <TableCell>{data.schema}</TableCell>
            </TableRow>
            <TableRow>
              <TableCell><strong>Server</strong></TableCell>
              <TableCell>{data.serverAddress}:{data.serverPort}</TableCell>
            </TableRow>
            <TableRow>
              <TableCell><strong>Version</strong></TableCell>
              <TableCell sx={{ fontSize: '0.75rem' }}>{data.serverVersion}</TableCell>
            </TableRow>
            <TableRow>
              <TableCell><strong>Server Time</strong></TableCell>
              <TableCell>{new Date(data.serverTime).toLocaleString()}</TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </TableContainer>
    );
  };

  const renderFlywayView = (data) => {
    const statusColor = 
      data.status === 'HEALTHY' ? 'success' : 
      data.status === 'DEGRADED' ? 'warning' : 
      data.status === 'NOT_CONFIGURED' ? 'default' :
      'error';

    return (
      <Box>
        <Box sx={{ mb: 2, display: 'flex', gap: 1, alignItems: 'center' }}>
          <Chip label={data.status} color={statusColor} />
          <Typography variant="body2" color="text.secondary">
            {data.message}
          </Typography>
        </Box>

        <TableContainer>
          <Table size="small">
            <TableBody>
              <TableRow>
                <TableCell><strong>History Table Exists</strong></TableCell>
                <TableCell>{data.historyTableExists ? '✓ Yes' : '✗ No'}</TableCell>
              </TableRow>
              {data.failedCount > 0 && (
                <TableRow>
                  <TableCell><strong>Failed Migrations</strong></TableCell>
                  <TableCell>
                    <Chip label={data.failedCount} color="error" size="small" />
                  </TableCell>
                </TableRow>
              )}
              {data.latestApplied && (
                <>
                  <TableRow>
                    <TableCell colSpan={2}>
                      <Typography variant="subtitle2" sx={{ mt: 1 }}>
                        Latest Applied Migration
                      </Typography>
                    </TableCell>
                  </TableRow>
                  <TableRow>
                    <TableCell><strong>Version</strong></TableCell>
                    <TableCell>{data.latestApplied.version}</TableCell>
                  </TableRow>
                  <TableRow>
                    <TableCell><strong>Description</strong></TableCell>
                    <TableCell>{data.latestApplied.description}</TableCell>
                  </TableRow>
                  <TableRow>
                    <TableCell><strong>Script</strong></TableCell>
                    <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.875rem' }}>
                      {data.latestApplied.script}
                    </TableCell>
                  </TableRow>
                  <TableRow>
                    <TableCell><strong>Installed On</strong></TableCell>
                    <TableCell>
                      {new Date(data.latestApplied.installedOn).toLocaleString()}
                    </TableCell>
                  </TableRow>
                </>
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </Box>
    );
  };

  const canShowTable = isTablesList || isTableSearch || isPrivileges || isIdentity || isFlywayHealth;

  return (
    <Paper sx={{ p: 2, mb: 2, minHeight: 300, maxHeight: 600, overflow: 'auto' }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <Typography variant="h6">
          Results
        </Typography>
        <Box sx={{ display: 'flex', gap: 1 }}>
          {results && canShowTable && (
            <ToggleButtonGroup
              value={viewMode}
              exclusive
              onChange={handleViewModeChange}
              size="small"
            >
              <ToggleButton value="table">
                <TableChart fontSize="small" />
              </ToggleButton>
              <ToggleButton value="json">
                <Code fontSize="small" />
              </ToggleButton>
            </ToggleButtonGroup>
          )}
          {results && (
            <IconButton size="small" onClick={onClear}>
              <Clear />
            </IconButton>
          )}
        </Box>
      </Box>

      {results ? (
        viewMode === 'table' && canShowTable ? (
          <Box>
            {isTablesList && renderTablesView(results)}
            {isTableSearch && renderTablesView(results)}
            {isPrivileges && renderPrivilegesView(results)}
            {isIdentity && renderIdentityView(results)}
            {isFlywayHealth && renderFlywayView(results)}
          </Box>
        ) : (
          <Box
            component="pre"
            sx={{
              backgroundColor: '#f5f5f5',
              p: 2,
              borderRadius: 1,
              overflow: 'auto',
              fontSize: '0.875rem',
              fontFamily: 'monospace'
            }}
          >
            {JSON.stringify(results, null, 2)}
          </Box>
        )
      ) : (
        <Box sx={{ textAlign: 'center', py: 4 }}>
          <Typography variant="body2" color="text.secondary">
            {}
          </Typography>
        </Box>
      )}
    </Paper>
  );
}

