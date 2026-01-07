import { useState } from 'react';
import { Container, Grid, Paper, Box, Tabs, Tab } from '@mui/material';
import ConnectionForm from '../components/ConnectionForm';
import ActionButtons from '../components/ActionButtons';
import SummaryPanel from '../components/SummaryPanel';
import ResultsPanel from '../components/ResultsPanel';
import SqlSandboxPanel from '../components/SqlSandboxPanel';
import EnvironmentComparisonPanel from '../components/EnvironmentComparisonPanel';
import { apiService } from '../services/apiService';

function DBDoctorPage({ 
  connectionId, 
  setConnectionId,
  connectionStatus,
  setConnectionStatus,
  addConsoleMessage 
}) {
  const [summaryData, setSummaryData] = useState(null);
  const [results, setResults] = useState(null);
  const [schema, setSchema] = useState('public');
  const [currentAction, setCurrentAction] = useState(null);
  const [activeTab, setActiveTab] = useState(0);
  const [sourceConnectionDetails, setSourceConnectionDetails] = useState(null);

  const handleConnect = async (formData) => {
    try {
      const response = await apiService.connect(formData);
      const connId = response.data.connectionId;
      setConnectionId(connId);
      setConnectionStatus('connected');
      setSchema(formData.schema || 'public');
      addConsoleMessage('✓ Connected successfully', 'success');
      setResults(response.data);
      setCurrentAction('connect');
      
      // Fetch source connection details for comparison tab
      try {
        const identityResponse = await apiService.getIdentity(connId);
        setSourceConnectionDetails({
          host: formData.host,
          database: formData.database,
          username: formData.username,
          schema: formData.schema || 'public',
          ...identityResponse.data
        });
      } catch (err) {
        console.error('Failed to fetch connection details:', err);
      }
    } catch (error) {
      setConnectionStatus('disconnected');
      addConsoleMessage(`✗ Connection failed: ${error.response?.data?.message || error.message}`, 'error');
      throw error;
    }
  };

  const handleTabChange = (event, newValue) => {
    setActiveTab(newValue);
  };

  const handleLoadSummary = async () => {
    if (!connectionId) return;
    try {
      const response = await apiService.getSummary(connectionId);
      setSummaryData(response.data);
      setResults(response.data);
      setCurrentAction('load-summary');
      addConsoleMessage('✓ Summary loaded', 'success');
    } catch (error) {
      addConsoleMessage(`✗ Failed to load summary: ${error.message}`, 'error');
      throw error;
    }
  };

  const handleAction = async (actionName, params) => {
    if (!connectionId) {
      addConsoleMessage('✗ Not connected. Please connect first.', 'error');
      return;
    }

    try {
      let response;
      switch (actionName) {
        case 'verify-connection':
          response = await apiService.getIdentity(connectionId);
          addConsoleMessage('✓ Identity verified', 'success');
          break;

        case 'flyway-health':
          response = await apiService.getFlywayHealth(connectionId);
          setSummaryData(prev => ({ ...prev, flyway: response.data }));
          addConsoleMessage(`✓ Flyway health: ${response.data.status}`,
            response.data.status === 'HEALTHY' ? 'success' : 'warning');
          break;

        case 'list-tables':
          response = await apiService.getTables(connectionId, schema);
          addConsoleMessage(`✓ Found ${response.data.tables.length} tables`, 'success');
          break;

        case 'find-table':
          response = await apiService.searchTables(connectionId, schema, params.searchQuery);
          addConsoleMessage(`✓ Found ${response.data.tables?.length || 0} matching tables`, 'success');
          break;

        case 'check-ownership':
          response = await apiService.getPrivileges(connectionId, schema, params.tableName);
          setSummaryData(prev => ({ ...prev, privileges: response.data }));
          addConsoleMessage(`✓ Privileges checked: ${response.data.status}`,
            response.data.status === 'PASS' ? 'success' : 'warning');
          break;

        case 'table-details':
          response = await apiService.getTableIntrospect(connectionId, schema, params.tableName);
          addConsoleMessage(`✓ Table details retrieved for ${params.tableName}`, 'success');
          break;

        default:
          addConsoleMessage(`Unknown action: ${actionName}`, 'error');
          return;
      }

      setResults(response.data);
      setCurrentAction(actionName);
    } catch (error) {
      const errorMsg = error.response?.data?.message || error.message;
      addConsoleMessage(`✗ ${actionName} failed: ${errorMsg}`, 'error');
    }
  };

  const handleAnalyzeSql = async (sql, operationType) => {
    if (!connectionId) {
      addConsoleMessage('✗ Not connected. Please connect first.', 'error');
      throw new Error('Not connected');
    }

    try {
      addConsoleMessage('🧪 Analyzing SQL...', 'info');
      const response = await apiService.analyzeSql(connectionId, sql, operationType);
      
      const findings = response.data.findings || [];
      const errorCount = findings.filter(f => f.severity === 'ERROR').length;
      const warnCount = findings.filter(f => f.severity === 'WARN').length;
      
      addConsoleMessage(
        `✓ SQL analysis complete: ${errorCount} errors, ${warnCount} warnings`,
        errorCount > 0 ? 'error' : warnCount > 0 ? 'warning' : 'success'
      );
      
      return response.data;
    } catch (error) {
      const errorMsg = error.response?.data?.message || error.message;
      addConsoleMessage(`✗ SQL analysis failed: ${errorMsg}`, 'error');
      throw error;
    }
  };

  const handleCompareEnvironments = async (
    sourceConnectionId,
    targetConnectionId,
    sourceEnvName,
    targetEnvName,
    schema,
    specificTables
  ) => {
    try {
      addConsoleMessage(
        `🔍 Comparing environments: ${sourceEnvName} → ${targetEnvName}...`,
        'info'
      );
      
      const response = await apiService.compareEnvironments(
        sourceConnectionId,
        targetConnectionId,
        sourceEnvName,
        targetEnvName,
        schema,
        specificTables
      );
      
      const totalDrift = response.data.driftSections.reduce(
        (sum, section) => sum + section.differCount,
        0
      );
      
      addConsoleMessage(
        `✓ Environment comparison complete: ${totalDrift} differences found`,
        totalDrift > 0 ? 'warning' : 'success'
      );
      
      return response;
    } catch (error) {
      const errorMsg = error.response?.data?.message || error.message;
      addConsoleMessage(`✗ Environment comparison failed: ${errorMsg}`, 'error');
      throw error;
    }
  };

  return (
    <Container maxWidth="xl" sx={{ mt: 3, pb: 3 }}>
      <Grid container spacing={3} sx={{ flexWrap: 'nowrap' }}>
        {/* Left Panel - Fixed Width */}
        <Grid item sx={{ width: '33.33%', minWidth: '33.33%', maxWidth: '33.33%' }}>
          <Paper elevation={2}>
            <ConnectionForm
              onConnect={handleConnect}
              onLoadSummary={handleLoadSummary}
              isConnected={connectionStatus === 'connected'}
            />
          </Paper>

          <Paper elevation={2} sx={{ mt: 2, maxHeight: '400px', overflow: 'auto' }}>
            <ActionButtons
              isConnected={connectionStatus === 'connected'}
              schema={schema}
              onAction={handleAction}
              currentAction={currentAction}
            />
          </Paper>
        </Grid>

        {/* Right Panel - Fluid Content */}
        <Grid item sx={{ width: '66.67%', minWidth: '66.67%', maxWidth: '66.67%' }}>
          <Paper elevation={2}>
            <Tabs 
              value={activeTab} 
              onChange={handleTabChange}
              sx={{ borderBottom: 1, borderColor: 'divider' }}
            >
              <Tab label="Single Environment" />
              <Tab label="Compare Environments" />
            </Tabs>

            <Box sx={{ p: 3 }}>
              {/* Tab 1: Single Environment */}
              {activeTab === 0 && (
                <Box>
                  <SummaryPanel
                    connectionStatus={connectionStatus}
                    summaryData={summaryData}
                  />

                  <SqlSandboxPanel
                    isConnected={connectionStatus === 'connected'}
                    connectionId={connectionId}
                    onAnalyze={handleAnalyzeSql}
                  />

                  <Box sx={{ mt: 2 }}>
                    <ResultsPanel
                      results={results}
                      onClear={() => setResults(null)}
                      connectionId={connectionId}
                    />
                  </Box>
                </Box>
              )}

              {/* Tab 2: Compare Environments */}
              {activeTab === 1 && (
                <EnvironmentComparisonPanel
                  isConnected={connectionStatus === 'connected'}
                  currentConnectionId={connectionId}
                  sourceConnectionDetails={sourceConnectionDetails}
                  onCompare={handleCompareEnvironments}
                />
              )}
            </Box>
          </Paper>
        </Grid>
      </Grid>
    </Container>
  );
}

export default DBDoctorPage;

