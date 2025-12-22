import { useState } from 'react';
import {
  Box,
  Button,
  TextField,
  Typography,
  Divider
} from '@mui/material';

export default function ActionButtons({ 
  isConnected, 
  schema, 
  onAction 
}) {
  const [tableName, setTableName] = useState('');
  const [searchQuery, setSearchQuery] = useState('');

  const handleAction = (actionName, requiresTable = false) => {
    if (requiresTable && !tableName) {
      alert('Please enter a table name');
      return;
    }
    onAction(actionName, { tableName, searchQuery });
  };

  return (
    <Box sx={{ p: 2 }}>
      <Typography variant="h6" gutterBottom>
        Ready-to-use Actions
      </Typography>

      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
        <Button
          variant="outlined"
          onClick={() => handleAction('verify-connection')}
          disabled={!isConnected}
          fullWidth
          size="small"
        >
          Verify Connection (DB Identity)
        </Button>

        <Button
          variant="outlined"
          onClick={() => handleAction('flyway-health')}
          disabled={!isConnected}
          fullWidth
          size="small"
        >
          Flyway Health
        </Button>

        <Button
          variant="outlined"
          onClick={() => handleAction('list-tables')}
          disabled={!isConnected}
          fullWidth
          size="small"
        >
          List App Tables (non-system)
        </Button>

        <Divider sx={{ my: 1 }} />

        <TextField
          label="Table name (for table-scoped actions)"
          value={tableName}
          onChange={(e) => setTableName(e.target.value)}
          size="small"
          placeholder="e.g. cart_item"
          fullWidth
        />

        <Button
          variant="outlined"
          onClick={() => handleAction('table-details', true)}
          disabled={!isConnected || !tableName}
          fullWidth
          size="small"
        >
          Table Details (columns+constraints+indexes)
        </Button>

        <Button
          variant="outlined"
          onClick={() => handleAction('check-ownership', true)}
          disabled={!isConnected || !tableName}
          fullWidth
          size="small"
        >
          Check Ownership & Grants
        </Button>

        <Button
          variant="outlined"
          onClick={() => handleAction('list-indexes', true)}
          disabled={!isConnected || !tableName}
          fullWidth
          size="small"
        >
          List Indexes (table)
        </Button>

        <Divider sx={{ my: 1 }} />

        <TextField
          label="Find contains"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          size="small"
          placeholder="e.g. cart"
          fullWidth
        />

        <Button
          variant="outlined"
          onClick={() => handleAction('find-table')}
          disabled={!isConnected || !searchQuery}
          fullWidth
          size="small"
        >
          Find Table
        </Button>
      </Box>
    </Box>
  );
}

