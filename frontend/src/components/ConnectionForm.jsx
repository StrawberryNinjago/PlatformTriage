import { useState } from 'react';
import {
  Box,
  TextField,
  Button,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  Typography,
  Alert
} from '@mui/material';

export default function ConnectionForm({ onConnect, onLoadSummary, isConnected }) {
  const [formData, setFormData] = useState({
    host: 'localhost',
    port: 5432,
    database: 'cartdb',
    username: 'cart_user',
    password: 'cart_pass',
    sslMode: 'disable',
    schema: 'public'
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
  };

  const handleConnect = async () => {
    setLoading(true);
    setError(null);
    try {
      await onConnect(formData);
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Connection failed');
    } finally {
      setLoading(false);
    }
  };

  const handleLoadSummary = async () => {
    setLoading(true);
    setError(null);
    try {
      await onLoadSummary();
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to load summary');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Box sx={{ p: 2 }}>
      <Typography variant="h6" gutterBottom>
        Connection
      </Typography>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      <FormControl fullWidth sx={{ mb: 2 }}>
        <InputLabel>Engine</InputLabel>
        <Select
          name="engine"
          value="Postgres"
          label="Engine"
          disabled
        >
          <MenuItem value="Postgres">Postgres</MenuItem>
        </Select>
      </FormControl>

      <TextField
        fullWidth
        label="Host"
        name="host"
        value={formData.host}
        onChange={handleChange}
        sx={{ mb: 2 }}
        size="small"
      />

      <Box sx={{ display: 'flex', gap: 2, mb: 2 }}>
        <TextField
          label="Port"
          name="port"
          type="number"
          value={formData.port}
          onChange={handleChange}
          sx={{ width: '30%' }}
          size="small"
        />
        <TextField
          label="Database"
          name="database"
          value={formData.database}
          onChange={handleChange}
          sx={{ width: '70%' }}
          size="small"
        />
      </Box>

      <TextField
        fullWidth
        label="Username"
        name="username"
        value={formData.username}
        onChange={handleChange}
        sx={{ mb: 2 }}
        size="small"
      />

      <TextField
        fullWidth
        label="Password / Token"
        name="password"
        type="password"
        value={formData.password}
        onChange={handleChange}
        sx={{ mb: 2 }}
        size="small"
      />

      <Box sx={{ display: 'flex', gap: 2, mb: 2 }}>
        <FormControl sx={{ width: '50%' }} size="small">
          <InputLabel>SSL Mode</InputLabel>
          <Select
            name="sslMode"
            value={formData.sslMode}
            onChange={handleChange}
            label="SSL Mode"
          >
            <MenuItem value="require">require</MenuItem>
            <MenuItem value="disable">disable</MenuItem>
            <MenuItem value="verify-full">verify-full</MenuItem>
          </Select>
        </FormControl>

        <TextField
          label="Schema"
          name="schema"
          value={formData.schema}
          onChange={handleChange}
          sx={{ width: '50%' }}
          size="small"
        />
      </Box>

      <Box sx={{ display: 'flex', gap: 2 }}>
        <Button
          variant="contained"
          onClick={handleConnect}
          disabled={loading || !formData.password}
          fullWidth
        >
          {loading ? 'Connecting...' : 'Connect'}
        </Button>
        <Button
          variant="outlined"
          onClick={handleLoadSummary}
          disabled={loading || !isConnected}
          fullWidth
        >
          Load Summary
        </Button>
      </Box>

      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 2 }}>
        This UI is diagnosis-first (read-only checks). It is not a DB admin client.
      </Typography>
    </Box>
  );
}

