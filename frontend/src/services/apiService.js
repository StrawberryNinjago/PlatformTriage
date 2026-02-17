import axios from 'axios';

const API_BASE = 'http://localhost:8081/api/db';
const SQL_API_BASE = 'http://localhost:8081/api/sql';
const DEPLOYMENT_API_BASE = 'http://localhost:8081/api/deployment';
const SMOKE_TEST_API_BASE = 'http://localhost:8081/api/smoke-tests';
const AI_API_BASE = 'http://localhost:8081/api/ai';

export const apiService = {
  // Connection
  connect: (connectionData) => 
    axios.post(`${API_BASE}/connections`, connectionData),
  
  // Summary
  getSummary: (connectionId) => 
    axios.get(`${API_BASE}/summary`, { params: { connectionId } }),
  
  // Identity
  getIdentity: (connectionId) => 
    axios.get(`${API_BASE}/identity`, { params: { connectionId } }),
  
  // Flyway Health
  getFlywayHealth: (connectionId) => 
    axios.get(`${API_BASE}/flyway/health`, { params: { connectionId } }),
  
  getFlywayHistory: (connectionId, limit = 50) =>
    axios.get(`${API_BASE}/connections/${connectionId}/flyway/history`, { params: { connectionId, limit } }),
  
  // Tables
  getTables: (connectionId, schema) => 
    axios.get(`${API_BASE}/tables`, { params: { connectionId, schema } }),
  
  searchTables: (connectionId, schema, q) => 
    axios.get(`${API_BASE}/tables/search`, { params: { connectionId, schema, queryString: q } }),
  
  // Privileges
  getPrivileges: (connectionId, schema, table) => {
    // Convert schema string to DbSchema enum format
    const isPublic = !schema || schema === 'public';
    return axios.post(`${API_BASE}/privileges:check`, {
      connectionId,
      schema: isPublic ? 'PUBLIC' : 'CUSTOM',
      schemaName: isPublic ? null : schema,
      tableName: table
    });
  },
  
  // Table Details
  getTableIndexes: (connectionId, schema, table) => 
    axios.get(`${API_BASE}/tables/indexes`, { params: { connectionId, schema, table } }),
  
  getTableIntrospect: (connectionId, schema, table) => 
    axios.get(`${API_BASE}/tables/introspect`, { params: { connectionId, schema, table } }),
  
  // SQL Analysis
  analyzeSql: (connectionId, sql, operationType) => 
    axios.post(`${SQL_API_BASE}/analyze`, {
      connectionId,
      sql,
      operationType
    }),
  
  // Environment Comparison
  compareEnvironments: (sourceConnectionId, targetConnectionId, sourceEnvName, targetEnvName, schema, specificTables) =>
    axios.post(`${API_BASE}/environments/compare`, {
      sourceConnectionId,
      targetConnectionId,
      sourceEnvironmentName: sourceEnvName,
      targetEnvironmentName: targetEnvName,
      schema,
      specificTables
    }),
  
  // List active connections
  listConnections: () =>
    axios.get(`${API_BASE}/connections/list`),
  
  // Export diagnostics
  exportDiagnostics: (connectionId) =>
    axios.get(`${API_BASE}/diagnostics/export`, { params: { connectionId } }),
  
  // Export diagnostics bundle (new format)
  exportDiagnosticsBundle: (connectionId) =>
    axios.get(`${API_BASE}/diagnostics/export/bundle`, { params: { connectionId } }),
  
  // Deployment Doctor APIs
  getDeploymentSummary: (namespace, selector, release, limitEvents = 50) =>
    axios.get(`${DEPLOYMENT_API_BASE}/summary`, { 
      params: { namespace, selector, release, limitEvents } 
    }),
  
  // Export deployment diagnostics
  exportDeploymentDiagnostics: (namespace, selector, release, limitEvents = 50) =>
    axios.get(`${DEPLOYMENT_API_BASE}/diagnostics/export`, { 
      params: { namespace, selector, release, limitEvents } 
    }),
  
  // Smoke Tests APIs
  runSmokeTests: (config) =>
    axios.post(`${SMOKE_TEST_API_BASE}/run`, config),
  
  validateSmokeTestConfig: (config) =>
    axios.post(`${SMOKE_TEST_API_BASE}/validate`, config),
  
  getSmokeTestHistory: (limit = 20) =>
    axios.get(`${SMOKE_TEST_API_BASE}/history`, { params: { limit } }),
  
  exportSmokeTestResults: (runId) =>
    axios.get(`${SMOKE_TEST_API_BASE}/export`, { params: { runId } }),

  // AI Triage Assistant
  askTriageAssistant: (payload) =>
    axios.post(`${AI_API_BASE}/triage`, payload)
};
