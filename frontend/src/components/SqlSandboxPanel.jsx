import { useRef, useState } from 'react';
import {
  Box,
  Button,
  TextField,
  Typography,
  MenuItem,
  Paper,
  Alert,
  Chip,
  Collapse,
  IconButton,
  Divider,
  List,
  ListItem,
  ListItemText
} from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ExpandLessIcon from '@mui/icons-material/ExpandLess';
import ScienceIcon from '@mui/icons-material/Science';

export default function SqlSandboxPanel({ isConnected, connectionId, onAnalyze }) {
  const [expanded, setExpanded] = useState(false);
  const [sql, setSql] = useState('');
  const [operationType, setOperationType] = useState('');
  const [analysisResult, setAnalysisResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const textAreaRef = useRef(null);

  const handleAnalyze = async () => {
    if (!sql.trim()) {
      setError('SQL cannot be empty');
      return;
    }

    setLoading(true);
    setError(null);
    
    try {
      const result = await onAnalyze(sql, operationType || null);
      setAnalysisResult(result);
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Analysis failed');
      setAnalysisResult(null);
    } finally {
      setLoading(false);
    }
  };

  const handleClear = () => {
    setSql('');
    setOperationType('');
    setAnalysisResult(null);
    setError(null);
  };

  const handleTextareaKeyDown = async (e) => {
    const isMac = navigator.platform.toUpperCase().indexOf('MAC') >= 0;
    const modifierKey = isMac ? e.metaKey : e.ctrlKey;
    if (!modifierKey)
      return;

    const ta = textAreaRef.current;
    if (!ta)
      return;

    const key = e.key.toLowerCase();

    // Select All
    if (key === 'a') {
      e.preventDefault();
      ta.focus();
      ta.select();
      return;
    }

    // Copy
    if (key === 'c') {
      e.preventDefault();
      ta.focus();
      ta.select();
      try {
        if (navigator.clipboard?.writeText) {
          await navigator.clipboard.writeText(ta.value);
        } else {
          document.execCommand('copy');
        }
      } catch {
        document.execCommand('copy');
      }
      return;
    }

    // Cut
    if (key === 'x') {
      e.preventDefault();
      ta.focus();
      ta.select();
      try {
        if (navigator.clipboard?.writeText) {
          await navigator.clipboard.writeText(ta.value);
          setSql('');
        } else {
          document.execCommand('cut');
          setSql(ta.value.slice(ta.selectionEnd));
        }
      } catch {
        document.execCommand('cut');
        setSql(ta.value.slice(ta.selectionEnd));
      }
      return;
    }

    // Quick analyze: Cmd/Ctrl + Enter
    if (key === 'enter') {
      e.preventDefault();
      if (!loading && sql.trim() && isConnected) {
        handleAnalyze();
      }
    }
  };

  const getSeverityColor = (severity) => {
    switch (severity) {
      case 'ERROR': return 'error';
      case 'WARN': return 'warning';
      case 'INFO': return 'info';
      default: return 'default';
    }
  };

  const getOperationColor = (op) => {
    switch (op) {
      case 'SELECT': return 'primary';
      case 'INSERT': return 'success';
      case 'UPDATE': return 'warning';
      case 'DELETE': return 'error';
      default: return 'default';
    }
  };

  return (
    <Paper elevation={2} sx={{ mt: 2 }}>
      <Box
        sx={{
          p: 2,
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          cursor: 'pointer',
          bgcolor: expanded ? '#e3f2fd' : 'transparent',
          transition: 'background-color 0.2s'
        }}
        onClick={() => setExpanded(!expanded)}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <ScienceIcon color="primary" />
          <Typography variant="h6" sx={{ fontWeight: 'bold' }}>
            🧪 SQL Diagnostic Sandbox (Static Analysis)
          </Typography>
        </Box>
        <IconButton size="small">
          {expanded ? <ExpandLessIcon /> : <ExpandMoreIcon />}
        </IconButton>
      </Box>

      <Collapse in={expanded}>
        <Box sx={{ p: 2, borderTop: '1px solid #e0e0e0' }}>
          <Alert severity="info" sx={{ mb: 2 }}>
            <Typography variant="body2" sx={{ fontWeight: 'bold' }}>
              ⚠️ This analysis is static. SQL is NEVER executed.
            </Typography>
            <Typography variant="caption" sx={{ display: 'block', mt: 0.5 }}>
              DB Doctor will analyze your SQL for index coverage, constraint violations, cascade deletes, and performance issues.
            </Typography>
            <Typography variant="caption" sx={{ display: 'block', mt: 0.5, fontStyle: 'italic', color: 'text.secondary' }}>
              💡 Tip: Use Ctrl+V (Cmd+V on Mac) to paste, Ctrl+Enter to analyze
            </Typography>
          </Alert>

          <Box 
            sx={{ mb: 2, userSelect: 'text', WebkitUserSelect: 'text', MozUserSelect: 'text' }}
            onClick={(e) => e.stopPropagation()}
            onMouseDown={(e) => e.stopPropagation()}
          >
            <Typography variant="body2" sx={{ mb: 0.5, fontWeight: 500, color: 'text.secondary', userSelect: 'none' }}>
              Paste INSERT / UPDATE / DELETE / SELECT SQL
            </Typography>
            <textarea
              ref={textAreaRef}
              value={sql}
              onChange={(e) => setSql(e.target.value)}
              placeholder="Example:
SELECT * FROM cart_item
WHERE cart_id = ?
  AND los_id = ?
  AND product_code = ?"
              disabled={!isConnected}
              readOnly={false}
              tabIndex={0}
              aria-label="SQL query input"
              onKeyDown={handleTextareaKeyDown}
              spellCheck={false}
              autoComplete="off"
              autoCorrect="off"
              autoCapitalize="off"
              style={{
                width: '100%',
                minHeight: '200px',
                padding: '12px',
                fontFamily: 'monospace',
                fontSize: '0.9rem',
                lineHeight: '1.5',
                border: '1px solid rgba(0, 0, 0, 0.23)',
                borderRadius: '4px',
                backgroundColor: isConnected ? '#fafafa' : '#f5f5f5',
                color: isConnected ? '#000' : '#999',
                resize: 'vertical',
                outline: 'none',
                transition: 'border-color 0.2s',
                userSelect: 'text',
                WebkitUserSelect: 'text',
                MozUserSelect: 'text',
                msUserSelect: 'text',
              }}
              onFocus={(e) => {
                e.target.style.borderColor = '#1976d2';
                e.target.style.borderWidth = '2px';
                e.target.style.padding = '11px'; // Adjust for thicker border
              }}
              onBlur={(e) => {
                e.target.style.borderColor = 'rgba(0, 0, 0, 0.23)';
                e.target.style.borderWidth = '1px';
                e.target.style.padding = '12px'; // Reset padding
              }}
            />
          </Box>

          <Box sx={{ display: 'flex', gap: 2, mb: 2 }}>
            <TextField
              select
              label="Operation Type (optional - auto-detected)"
              value={operationType}
              onChange={(e) => setOperationType(e.target.value)}
              disabled={!isConnected}
              sx={{ minWidth: 250 }}
              size="small"
            >
              <MenuItem value="">Auto-detect</MenuItem>
              <MenuItem value="SELECT">SELECT</MenuItem>
              <MenuItem value="INSERT">INSERT</MenuItem>
              <MenuItem value="UPDATE">UPDATE</MenuItem>
              <MenuItem value="DELETE">DELETE</MenuItem>
            </TextField>

            <Button
              variant="contained"
              color="primary"
              onClick={handleAnalyze}
              disabled={!isConnected || !sql.trim() || loading}
              sx={{ minWidth: 120 }}
            >
              {loading ? 'Analyzing...' : 'Analyze SQL'}
            </Button>

            <Button
              variant="outlined"
              onClick={handleClear}
              disabled={!sql && !analysisResult}
            >
              Clear
            </Button>
          </Box>

          {error && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {error}
            </Alert>
          )}

          {analysisResult && (
            <Box sx={{ mt: 3 }}>
              <Divider sx={{ mb: 2 }} />
              
              <Box sx={{ mb: 2, display: 'flex', gap: 2, alignItems: 'center' }}>
                <Typography variant="h6">Analysis Results</Typography>
                <Chip
                  label={analysisResult.detectedOperation}
                  color={getOperationColor(analysisResult.detectedOperation)}
                  size="small"
                />
                <Chip
                  label={analysisResult.isValid ? '✓ Valid SQL' : '✗ Invalid SQL'}
                  color={analysisResult.isValid ? 'success' : 'error'}
                  size="small"
                />
              </Box>

              {/* Outcome Summary */}
              {analysisResult.outcomeSummary && (
                <Alert 
                  severity={
                    analysisResult.outcomeSummary.startsWith('✅') ? 'success' :
                    analysisResult.outcomeSummary.startsWith('⚠️') ? 'warning' : 'error'
                  } 
                  sx={{ mb: 2, fontWeight: 'bold' }}
                >
                  {analysisResult.outcomeSummary}
                </Alert>
              )}

              {analysisResult.parseError && (
                <Alert severity="error" sx={{ mb: 2 }}>
                  <Typography variant="body2" sx={{ fontWeight: 'bold' }}>
                    Parse Error
                  </Typography>
                  {analysisResult.parseError}
                </Alert>
              )}

              {/* Findings */}
              {analysisResult.findings && analysisResult.findings.length > 0 && (
                <Paper variant="outlined" sx={{ p: 2, mb: 2, bgcolor: '#fafafa' }}>
                  <Typography variant="subtitle1" sx={{ fontWeight: 'bold', mb: 1 }}>
                    🔍 Findings ({analysisResult.findings.length})
                  </Typography>
                  <List dense>
                    {analysisResult.findings.map((finding, idx) => (
                      <ListItem key={idx} sx={{ flexDirection: 'column', alignItems: 'flex-start', gap: 0.5 }}>
                        <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', width: '100%' }}>
                          <Chip
                            label={finding.severity}
                            color={getSeverityColor(finding.severity)}
                            size="small"
                          />
                          <Chip label={finding.category} size="small" variant="outlined" />
                          <Typography variant="body2" sx={{ fontWeight: 'bold', flex: 1 }}>
                            {finding.title}
                          </Typography>
                        </Box>
                        <Typography variant="body2" sx={{ pl: 2 }}>
                          {finding.description}
                        </Typography>
                        <Typography variant="body2" sx={{ pl: 2, color: 'primary.main', fontStyle: 'italic' }}>
                          💡 {finding.recommendation}
                        </Typography>
                      </ListItem>
                    ))}
                  </List>
                </Paper>
              )}

              {/* Index Analysis */}
              {analysisResult.indexAnalysis && (
                <Paper variant="outlined" sx={{ p: 2, mb: 2, bgcolor: '#f5f5ff' }}>
                  <Typography variant="subtitle1" sx={{ fontWeight: 'bold', mb: 1 }}>
                    📊 Index Coverage Analysis
                  </Typography>
                  <Typography variant="body2">
                    <strong>Table:</strong> {analysisResult.indexAnalysis.tableName}
                  </Typography>
                  <Typography variant="body2">
                    <strong>Query Columns:</strong> {analysisResult.indexAnalysis.queryColumns?.join(', ') || 'N/A'}
                  </Typography>
                  <Typography variant="body2">
                    <strong>Composite Index:</strong> {analysisResult.indexAnalysis.hasCompositeIndex ? '✅' : '❌'}
                  </Typography>
                  <Typography variant="body2">
                    <strong>Partial Coverage:</strong> {analysisResult.indexAnalysis.hasPartialCoverage ? '⚠️ Yes' : 'No'}
                  </Typography>
                  
                  {analysisResult.indexAnalysis.matchedIndexes?.length > 0 && (
                    <Box sx={{ mt: 1 }}>
                      <Typography variant="body2" sx={{ fontWeight: 'bold' }}>Matched Indexes:</Typography>
                      <List dense>
                        {analysisResult.indexAnalysis.matchedIndexes.map((idx, i) => (
                          <ListItem key={i} sx={{ py: 0 }}>
                            <ListItemText primary={idx} primaryTypographyProps={{ variant: 'body2', fontFamily: 'monospace' }} />
                          </ListItem>
                        ))}
                      </List>
                    </Box>
                  )}

                  {analysisResult.indexAnalysis.suggestedIndexes?.length > 0 && (
                    <Box sx={{ mt: 1, p: 1, bgcolor: '#fff3e0', borderRadius: 1 }}>
                      <Typography variant="body2" sx={{ fontWeight: 'bold', color: 'warning.main' }}>
                        💡 Suggested Indexes:
                      </Typography>
                      <List dense>
                        {analysisResult.indexAnalysis.suggestedIndexes.map((suggestion, i) => (
                          <ListItem key={i} sx={{ py: 0 }}>
                            <ListItemText 
                              primary={suggestion} 
                              primaryTypographyProps={{ 
                                variant: 'body2', 
                                fontFamily: 'monospace',
                                fontSize: '0.85rem'
                              }} 
                            />
                          </ListItem>
                        ))}
                      </List>
                    </Box>
                  )}
                </Paper>
              )}

              {/* Constraint Risks */}
              {analysisResult.constraintRisks && analysisResult.constraintRisks.hasRisks && (
                <Paper variant="outlined" sx={{ p: 2, mb: 2, bgcolor: '#fff9f0' }}>
                  <Typography variant="subtitle1" sx={{ fontWeight: 'bold', mb: 1, color: 'warning.main' }}>
                    ⚠️ Constraint Violation Risks
                  </Typography>
                  
                  {analysisResult.constraintRisks.missingNotNullColumns?.length > 0 && (
                    <Alert severity="error" sx={{ mb: 1 }}>
                      <Typography variant="body2" sx={{ fontWeight: 'bold' }}>
                        Missing NOT NULL Columns:
                      </Typography>
                      <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>
                        {analysisResult.constraintRisks.missingNotNullColumns.join(', ')}
                      </Typography>
                    </Alert>
                  )}

                  {analysisResult.constraintRisks.uniqueConstraintColumns?.length > 0 && (
                    <Alert severity="warning" sx={{ mb: 1 }}>
                      <Typography variant="body2" sx={{ fontWeight: 'bold' }}>
                        Unique Constraint Columns:
                      </Typography>
                      <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>
                        {analysisResult.constraintRisks.uniqueConstraintColumns.join(', ')}
                      </Typography>
                    </Alert>
                  )}

                  {analysisResult.constraintRisks.foreignKeyViolations?.length > 0 && (
                    <Alert severity="warning">
                      <Typography variant="body2" sx={{ fontWeight: 'bold' }}>
                        Foreign Key Columns:
                      </Typography>
                      <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>
                        {analysisResult.constraintRisks.foreignKeyViolations.join(', ')}
                      </Typography>
                    </Alert>
                  )}
                </Paper>
              )}

              {/* Cascade Analysis */}
              {analysisResult.cascadeAnalysis && analysisResult.cascadeAnalysis.cascadingForeignKeys > 0 && (
                <Paper variant="outlined" sx={{ p: 2, bgcolor: '#ffebee' }}>
                  <Typography variant="subtitle1" sx={{ fontWeight: 'bold', mb: 1, color: 'error.main' }}>
                    🔥 Cascade Delete Analysis
                  </Typography>
                  <Typography variant="body2">
                    <strong>Cascading Foreign Keys:</strong> {analysisResult.cascadeAnalysis.cascadingForeignKeys}
                  </Typography>
                  <Typography variant="body2">
                    <strong>Cascade Depth:</strong> {analysisResult.cascadeAnalysis.cascadeDepth}
                  </Typography>
                  <Typography variant="body2">
                    <strong>Recursive Cascade:</strong> {analysisResult.cascadeAnalysis.hasRecursiveCascade ? '⚠️ YES' : 'No'}
                  </Typography>

                  {analysisResult.cascadeAnalysis.affectedTables?.length > 0 && (
                    <Box sx={{ mt: 1 }}>
                      <Typography variant="body2" sx={{ fontWeight: 'bold' }}>
                        Affected Tables:
                      </Typography>
                      <List dense>
                        {analysisResult.cascadeAnalysis.affectedTables.map((table, i) => (
                          <ListItem key={i} sx={{ py: 0 }}>
                            <ListItemText 
                              primary={table} 
                              primaryTypographyProps={{ variant: 'body2', fontFamily: 'monospace' }} 
                            />
                          </ListItem>
                        ))}
                      </List>
                    </Box>
                  )}
                </Paper>
              )}
            </Box>
          )}
        </Box>
      </Collapse>
    </Paper>
  );
}

