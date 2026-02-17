import { useState } from 'react';
import { Container, Paper, Box, Tabs, Tab } from '@mui/material';
import ConnectionForm from '../components/ConnectionForm';
import SummaryPanel from '../components/SummaryPanel';
import ResultsPanel from '../components/ResultsPanel';
import SqlSandboxPanel from '../components/SqlSandboxPanel';
import EnvironmentComparisonPanel from '../components/EnvironmentComparisonPanel';
import AiAssistantPanel from '../components/AiAssistantPanel';
import { apiService } from '../services/apiService';

function DBDoctorPage({ 
  connectionId, 
  setConnectionId,
  connectionStatus,
  setConnectionStatus,
  addConsoleMessage 
}) {
  const [connectionForm, setConnectionForm] = useState({
    host: 'localhost',
    port: 5432,
    database: 'cartdb',
    username: 'cart_user',
    password: '',
    sslMode: 'disable',
    schema: 'public'
  });
  const [summaryData, setSummaryData] = useState(null);
  const [results, setResults] = useState(null);
  const [currentAction, setCurrentAction] = useState(null);
  const [activeTab, setActiveTab] = useState(0);
  const [sourceConnectionDetails, setSourceConnectionDetails] = useState(null);

  const handleConnect = async (formData) => {
    try {
      const response = await apiService.connect(formData);
      const connId = response.data.connectionId;
      setConnectionId(connId);
      setConnectionStatus('connected');
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

  const handleApplyConnection = (preset, connectNow = false) => {
    const nextForm = { ...connectionForm };
    Object.entries(preset || {}).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        nextForm[key] = value;
      }
    });
    setConnectionForm(nextForm);
    if (connectNow) {
      handleConnect(nextForm);
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

  const handleAiActionResult = (actionName, payload) => {
    if (actionName) {
      setCurrentAction(actionName);
    }

    if (payload && typeof payload === 'object') {
      setResults(payload);

      if (actionName === 'flyway_health') {
        setSummaryData(prev => ({
          ...prev,
          flyway: payload
        }));
      }
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
    <Container
      maxWidth={false}
      sx={{
        mt: 3,
        pb: 3,
        width: '100%',
        px: { xs: 2, sm: 2.5, md: 3 }
      }}
    >
      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: {
            xs: '1fr',
            lg: 'minmax(540px, 1.2fr) minmax(0, 1.8fr)'
          },
          minHeight: { xs: 'auto', lg: 'calc(100vh - 250px)' },
          width: '100%',
          gap: 2,
          alignItems: 'stretch',
          minWidth: 0
        }}
      >
        {/* Left Rail - LLM */}
        <Box sx={{ 
          position: { lg: 'sticky' }, 
          top: { lg: 16 }, 
          minWidth: 0, 
          alignSelf: 'stretch',
          height: { xs: 'auto', lg: 'calc(100vh - 250px)' }
        }}>
          <AiAssistantPanel
            tool="db-doctor"
            connectionId={connectionId}
            currentAction={currentAction}
            context={results}
            onApplyConnection={handleApplyConnection}
            onToolResult={handleAiActionResult}
            fitHeight={true}
          />
        </Box>

        {/* Right Rail - Diagnostics */}
        <Paper
          elevation={2}
          sx={{
            minWidth: 0,
            minHeight: 0,
            height: { xs: 'auto', lg: 'calc(100vh - 250px)' },
            display: 'flex',
            flexDirection: 'column',
            '& .MuiTypography-caption': { fontSize: '1rem' },
            '& .MuiTableCell-root': { fontSize: '1.05rem' }
          }}
        >
          <Box sx={{ borderBottom: 1, borderColor: 'divider', bgcolor: '#fafcff' }}>
            <ConnectionForm
              formData={connectionForm}
              setFormData={setConnectionForm}
              onConnect={handleConnect}
              onLoadSummary={handleLoadSummary}
              isConnected={connectionStatus === 'connected'}
            />
          </Box>

          <Tabs 
            value={activeTab} 
            onChange={handleTabChange}
            sx={{
              borderBottom: 1,
              borderColor: 'divider',
              '& .MuiTab-root': {
                fontSize: '1.24rem',
                textTransform: 'none',
                fontWeight: 800,
                minHeight: 56,
                color: 'text.secondary'
              },
              '& .MuiTab-root.Mui-selected': {
                color: 'text.primary'
              }
            }}
          >
            <Tab label="Single Environment" />
            <Tab label="Compare Environments" />
          </Tabs>

          <Box sx={{ p: 3, flex: 1, minHeight: 0, overflowY: 'auto' }}>
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
      </Box>
    </Container>
  );
}

export default DBDoctorPage;
