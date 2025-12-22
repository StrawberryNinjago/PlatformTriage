import axios from 'axios';

const API_BASE = 'http://localhost:8081/api/db';

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
  
  // Tables
  getTables: (connectionId, schema) => 
    axios.get(`${API_BASE}/tables`, { params: { connectionId, schema } }),
  
  searchTables: (connectionId, schema, q) => 
    axios.get(`${API_BASE}/tables/search`, { params: { connectionId, schema, q } }),
  
  // Privileges
  getPrivileges: (connectionId, schema, table) => 
    axios.get(`${API_BASE}/privileges`, { params: { connectionId, schema, table } }),
  
  // Table Details
  getTableIndexes: (connectionId, schema, table) => 
    axios.get(`${API_BASE}/tables/indexes`, { params: { connectionId, schema, table } }),
  
  getTableIntrospect: (connectionId, schema, table) => 
    axios.get(`${API_BASE}/tables/introspect`, { params: { connectionId, schema, table } })
};

