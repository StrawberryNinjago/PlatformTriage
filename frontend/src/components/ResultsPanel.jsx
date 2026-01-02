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
import FlywayHealthPanel from './FlywayHealthPanel';
import TableDiagnosticsPanel from './TableDiagnosticsPanel';

export default function ResultsPanel({ results, onClear, connectionId }) {
  const [viewMode, setViewMode] = useState('table'); // 'table' or 'json'

  const handleViewModeChange = (event, newMode) => {
    if (newMode !== null) {
      setViewMode(newMode);
    }
  };

  // Check if results is a tables list
  const isTablesList = results?.tables && Array.isArray(results.tables) && !results?.queryString;
  const isTableSearch = results?.tables && Array.isArray(results.tables) && results?.queryString;
  const isPrivileges = results?.status && results?.grantedPrivileges;
  const isIdentity = results?.database && results?.currentUser;
  const isFlywayHealth = results?.status && results?.flywaySummary !== undefined;
  const isFlywayHistory = Array.isArray(results) && results.length > 0 && results[0]?.installedRank !== undefined;
  const isConnection = results?.connectionId && results?.connected !== undefined;
  const isSummary = results?.identity && results?.flyway;
  const isTableDetails = results?.indexes && results?.constraints && results?.schema && results?.table;
  const isIndexesOnly = results?.indexes && !results?.constraints && results?.schema && results?.table;
  
  // Determine which schema to use for API calls
  const effectiveSchema = results?.schema || 'public';

  const renderTablesView = (data) => {
    const tables = data.tables || [];
    const isSearch = !!data.queryString;
    return (
      <Box>
        {isSearch && (
          <Box sx={{ mb: 2, p: 2, bgcolor: '#e3f2fd', borderRadius: 1, borderLeft: '4px solid #1976d2' }}>
            <Typography variant="subtitle2" sx={{ mb: 0.5 }}>
              🔍 Table Name Search Results for: <strong>"{data.queryString}"</strong>
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Found {tables.length} table{tables.length !== 1 ? 's' : ''} with name containing this text
            </Typography>
            {tables.length === 0 && (
              <Typography variant="caption" display="block" sx={{ mt: 1, color: 'warning.main' }}>
                💡 Tip: This searches for table names, not constraints or columns. Try searching for "cart" or "item".
              </Typography>
            )}
          </Box>
        )}
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
              {tables.length > 0 ? (
                tables.map((table) => {
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
                })
              ) : (
                <TableRow>
                  <TableCell colSpan={4} align="center" sx={{ py: 3 }}>
                    <Typography variant="body2" color="text.secondary">
                      No tables found
                    </Typography>
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </Box>
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


  const renderFlywayHistoryView = (historyData) => {
    return (
      <Box>
        {/* Header */}
        <Box sx={{ mb: 2, p: 2, bgcolor: '#e3f2fd', borderRadius: 1, borderLeft: '4px solid #1976d2' }}>
          <Typography variant="h6" sx={{ mb: 0.5 }}>
            🛫 Flyway Migration History
          </Typography>
          <Typography variant="caption" color="text.secondary">
            Showing {historyData.length} most recent migration{historyData.length !== 1 ? 's' : ''}
          </Typography>
        </Box>

        {/* History Table */}
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
              {historyData.map((row, idx) => (
                <TableRow key={idx} hover sx={{ backgroundColor: row.success ? 'inherit' : '#ffebee' }}>
                  <TableCell>{row.installedRank}</TableCell>
                  <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.85rem' }}>
                    {row.version}
                  </TableCell>
                  <TableCell>{row.description}</TableCell>
                  <TableCell>
                    <Chip label={row.type} size="small" />
                  </TableCell>
                  <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.75rem', maxWidth: 200 }}>
                    {row.script}
                  </TableCell>
                  <TableCell>{row.installedBy}</TableCell>
                  <TableCell sx={{ fontSize: '0.75rem' }}>
                    {row.installedOn ? new Date(row.installedOn).toLocaleString() : 'N/A'}
                  </TableCell>
                  <TableCell align="right">{row.executionTimeMs?.toLocaleString()}</TableCell>
                  <TableCell>
                    <Chip 
                      label={row.success ? 'Success' : 'Failed'} 
                      color={row.success ? 'success' : 'error'} 
                      size="small" 
                    />
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </Box>
    );
  };

  const renderConnectionView = (data) => {
    return (
      <TableContainer>
        <Table size="small">
          <TableBody>
            <TableRow>
              <TableCell><strong>Connection ID</strong></TableCell>
              <TableCell sx={{ fontFamily: 'monospace' }}>{data.connectionId}</TableCell>
            </TableRow>
            <TableRow>
              <TableCell><strong>Status</strong></TableCell>
              <TableCell>
                <Chip 
                  label={data.connected ? 'Connected' : 'Disconnected'} 
                  color={data.connected ? 'success' : 'error'} 
                  size="small" 
                />
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </TableContainer>
    );
  };

  const renderSummaryView = (data) => {
    return (
      <Box>
        {/* Identity Section */}
        {data.identity && (
          <Box sx={{ mb: 3 }}>
            <Typography variant="subtitle1" sx={{ mb: 1, fontWeight: 'bold' }}>
              🔐 Database Identity
            </Typography>
            <TableContainer>
              <Table size="small">
                <TableBody>
                  <TableRow>
                    <TableCell width="30%"><strong>Database</strong></TableCell>
                    <TableCell sx={{ fontFamily: 'monospace' }}>{data.identity.database}</TableCell>
                  </TableRow>
                  <TableRow>
                    <TableCell><strong>User</strong></TableCell>
                    <TableCell>{data.identity.user}</TableCell>
                  </TableRow>
                  <TableRow>
                    <TableCell><strong>Server</strong></TableCell>
                    <TableCell>{data.identity.serverAddr}:{data.identity.serverPort}</TableCell>
                  </TableRow>
                  <TableRow>
                    <TableCell><strong>Version</strong></TableCell>
                    <TableCell sx={{ fontSize: '0.75rem' }}>{data.identity.serverVersion}</TableCell>
                  </TableRow>
                </TableBody>
              </Table>
            </TableContainer>
          </Box>
        )}

        {/* Flyway Section */}
        {data.flyway && (
          <Box sx={{ mb: 3 }}>
            <Typography variant="subtitle1" sx={{ mb: 1, fontWeight: 'bold' }}>
              🛫 Flyway Migration Status
            </Typography>
            <Box sx={{ mb: 1 }}>
              <Chip 
                label={data.flyway.status || 'UNKNOWN'} 
                color={
                  data.flyway.status === 'HEALTHY' ? 'success' : 
                  data.flyway.status === 'DEGRADED' ? 'warning' : 
                  'default'
                } 
                size="small" 
              />
            </Box>
            <TableContainer>
              <Table size="small">
                <TableBody>
                  <TableRow>
                    <TableCell width="30%"><strong>History Table</strong></TableCell>
                    <TableCell>{data.flyway.historyTableExists ? '✓ Exists' : '✗ Not Found'}</TableCell>
                  </TableRow>
                  {data.flyway.latestApplied && (
                    <>
                      <TableRow>
                        <TableCell><strong>Latest Version</strong></TableCell>
                        <TableCell>{data.flyway.latestApplied.version}</TableCell>
                      </TableRow>
                      <TableRow>
                        <TableCell><strong>Description</strong></TableCell>
                        <TableCell>{data.flyway.latestApplied.description}</TableCell>
                      </TableRow>
                      <TableRow>
                        <TableCell><strong>Installed On</strong></TableCell>
                        <TableCell>
                          {data.flyway.latestApplied.installedOn 
                            ? new Date(data.flyway.latestApplied.installedOn).toLocaleString()
                            : 'N/A'}
                        </TableCell>
                      </TableRow>
                    </>
                  )}
                  {data.flyway.failedCount > 0 && (
                    <TableRow>
                      <TableCell><strong>Failed Migrations</strong></TableCell>
                      <TableCell>
                        <Chip label={data.flyway.failedCount} color="error" size="small" />
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </TableContainer>
          </Box>
        )}

        {/* Schema Summary Section */}
        {data.publicSchema && (
          <Box>
            <Typography variant="subtitle1" sx={{ mb: 1, fontWeight: 'bold' }}>
              📊 Schema Summary
            </Typography>
            <TableContainer>
              <Table size="small">
                <TableBody>
                  <TableRow>
                    <TableCell width="30%"><strong>Table Count</strong></TableCell>
                    <TableCell>{data.publicSchema.tableCount || 0}</TableCell>
                  </TableRow>
                </TableBody>
              </Table>
            </TableContainer>
          </Box>
        )}
      </Box>
    );
  };

  const renderIndexesOnlyView = (data) => {
    const { schema, table, indexes = [] } = data;
    
    // Categorize indexes
    const primaryIndexes = indexes.filter(i => i.primary);
    const uniqueIndexes = indexes.filter(i => i.unique && !i.primary);
    const regularIndexes = indexes.filter(i => !i.unique && !i.primary);

    return (
      <Box>
        {/* Header */}
        <Box sx={{ mb: 3, p: 2, bgcolor: '#f5f5f5', borderRadius: 1 }}>
          <Typography variant="h6" sx={{ mb: 1 }}>
            🔍 Indexes for {schema}.{table}
          </Typography>
          <Chip label={`${indexes.length} Total Indexes`} size="small" color="primary" />
        </Box>

        {/* Indexes Table */}
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
                      <Chip label="PRIMARY" color="error" size="small" />
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
      </Box>
    );
  };

  const renderTableDetailsView = (data) => {
    const { schema, table, indexes = [], constraints = [] } = data;
    
    // Categorize constraints
    const primaryKeys = constraints.filter(c => c.type === 'PRIMARY KEY');
    const foreignKeys = constraints.filter(c => c.type === 'FOREIGN KEY');
    const uniqueConstraints = constraints.filter(c => c.type === 'UNIQUE');
    const checkConstraints = constraints.filter(c => c.type === 'CHECK');
    const notNullConstraints = constraints.filter(c => c.type?.includes('OTHER'));
    
    // Categorize indexes
    const primaryIndexes = indexes.filter(i => i.primary);
    const uniqueIndexes = indexes.filter(i => i.unique && !i.primary);
    const regularIndexes = indexes.filter(i => !i.unique && !i.primary);

    return (
      <Box>
        {/* Summary Header */}
        <Box sx={{ mb: 3, p: 2, bgcolor: '#f5f5f5', borderRadius: 1 }}>
          <Typography variant="h6" sx={{ mb: 1 }}>
            Table: {schema}.{table}
          </Typography>
          <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
            <Chip label={`${indexes.length} Indexes`} size="small" color="primary" />
            <Chip label={`${foreignKeys.length} Foreign Keys`} size="small" color="info" />
            <Chip label={`${uniqueConstraints.length} Unique Constraints`} size="small" color="success" />
            <Chip label={`${checkConstraints.length} Check Constraints`} size="small" color="warning" />
          </Box>
        </Box>

        {/* Indexes Section */}
        <Box sx={{ mb: 3 }}>
          <Typography variant="subtitle1" sx={{ mb: 1, fontWeight: 'bold' }}>
            🔍 Indexes
          </Typography>
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
                        <Chip label="PRIMARY" color="error" size="small" />
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
        </Box>

        {/* Constraints Section */}
        <Box sx={{ mb: 3 }}>
          <Typography variant="subtitle1" sx={{ mb: 1, fontWeight: 'bold' }}>
            🔒 Constraints
          </Typography>
          
          {/* Primary Keys */}
          {primaryKeys.length > 0 && (
            <Box sx={{ mb: 2 }}>
              <Typography variant="subtitle2" sx={{ mb: 1, color: 'error.main' }}>
                Primary Keys
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
            <Box sx={{ mb: 2 }}>
              <Typography variant="subtitle2" sx={{ mb: 1, color: 'info.main' }}>
                Foreign Keys
              </Typography>
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow sx={{ backgroundColor: '#e3f2fd' }}>
                      <TableCell><strong>Name</strong></TableCell>
                      <TableCell><strong>Columns</strong></TableCell>
                      <TableCell><strong>Definition</strong></TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {foreignKeys.map(c => (
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

          {/* Unique Constraints */}
          {uniqueConstraints.length > 0 && (
            <Box sx={{ mb: 2 }}>
              <Typography variant="subtitle2" sx={{ mb: 1, color: 'success.main' }}>
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
            <Box sx={{ mb: 2 }}>
              <Typography variant="subtitle2" sx={{ mb: 1, color: 'warning.main' }}>
                Check Constraints
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
      </Box>
    );
  };

  const canShowTable = isTablesList || isTableSearch || isPrivileges || isIdentity || isFlywayHealth || isFlywayHistory || isConnection || isSummary || isTableDetails || isIndexesOnly;

  return (
    <Paper sx={{ p: 2, mb: 2, display: 'flex', flexDirection: 'column' }}>
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

      <Box sx={{ overflow: 'auto' }}>
        {results ? (
          isFlywayHealth ? (
            // Use dedicated FlywayHealthPanel for flyway health data
            <FlywayHealthPanel data={results} connectionId={connectionId} />
          ) : isFlywayHistory ? (
            renderFlywayHistoryView(results)
          ) : viewMode === 'table' && canShowTable ? (
            <Box>
              {isTablesList && renderTablesView(results)}
              {isTableSearch && renderTablesView(results)}
              {isPrivileges && renderPrivilegesView(results)}
              {isIdentity && renderIdentityView(results)}
              {isConnection && renderConnectionView(results)}
              {isSummary && renderSummaryView(results)}
              {isTableDetails && <TableDiagnosticsPanel data={results} connectionId={connectionId} schema={effectiveSchema} />}
              {isIndexesOnly && renderIndexesOnlyView(results)}
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
              No results to display. Click an action button to get started.
            </Typography>
          </Box>
        )}
      </Box>
    </Paper>
  );
}

