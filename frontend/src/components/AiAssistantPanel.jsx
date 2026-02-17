import { useMemo, useState } from 'react';
import {
  Box,
  Button,
  Chip,
  Divider,
  Paper,
  TextField,
  Typography,
  Switch,
  FormControlLabel,
  CircularProgress
} from '@mui/material';
import {
  AutoAwesome,
  Dns,
  List,
  ListAlt,
  MonitorHeart,
  Psychology,
  Summarize,
  Shield,
} from '@mui/icons-material';
import { apiService } from '../services/apiService';

const assistantProfiles = {
  'db-doctor': {
    title: 'LLM Assistant',
    subtitle: 'Ask questions about the latest DB Doctor diagnostics, or use a quick prompt to get a summary.',
    noMessagesMessage: 'No assistant messages yet. Run a DB Doctor action, then ask for a summary or diagnosis.',
    inputLabel: 'Ask or paste connection params',
    prompts: [
      {
        id: 'summarize',
        label: 'Summarize',
        icon: <Summarize fontSize="small" />,
        text: 'Summarize the latest DB Doctor results and list next steps.'
      },
      {
        id: 'risks',
        label: 'Risks',
        icon: <Psychology fontSize="small" />,
        text: 'What are the top risks or likely root causes from the latest results?'
      },
      {
        id: 'flyway',
        label: 'Flyway Health',
        text: 'I want to check flyway health.'
      },
      {
        id: 'tables',
        label: 'What tables',
        text: 'What are the tables in this schema?'
      },
      {
        id: 'details',
        label: 'Table Details',
        text: 'Show a table details for cart_item.'
      },
      {
        id: 'permission',
        label: 'Table Permission',
        text: 'Do I have permission for cart_item?'
      },
      {
        id: 'sql',
        label: 'SQL Why',
        text: 'Why this SQL does not work: SELECT * FROM cart_item WHERE id = 1;'
      }
    ],
    connectionShortcuts: [
      {
        id: 'dev',
        label: 'Use dev docker (5433)',
        connection: { host: 'localhost', port: 5433, database: 'cartdb', username: 'cart_user', sslMode: 'disable', schema: 'public' }
      },
      {
        id: 'local',
        label: 'Use local default (5432)',
        connection: { host: 'localhost', port: 5432, database: 'cartdb', username: 'cart_user', sslMode: 'disable', schema: 'public' }
      }
    ],
    connectionNote: 'Password is not filled; paste it in the Connection panel before connecting.'
  },
  'deployment-doctor': {
    title: 'Deployment LLM Assistant',
    subtitle: 'Ask questions about deployment findings, root causes, and next actions.',
    noMessagesMessage: 'No assistant messages yet. Load a deployment summary first, then ask for an interpretation.',
    inputLabel: 'Ask about deployment state and findings',
    prompts: [
      {
        id: 'summarize',
        label: 'Summarize',
        icon: <Summarize fontSize="small" />,
        text: 'Summarize the latest deployment summary and top risks.'
      },
      {
        id: 'risks',
        label: 'Top risks',
        icon: <Psychology fontSize="small" />,
        text: 'What are the likely root causes from these findings?'
      },
      {
        id: 'deployment',
        label: 'Primary issue',
        text: 'What is the primary failure and what should I fix first?'
      },
      {
        id: 'pods',
        label: 'Show Pods',
        icon: <List fontSize="small" />,
        text: 'Show my pods.'
      },
      {
        id: 'unhealthyPods',
        label: 'Unhealthy Pods',
        icon: <MonitorHeart fontSize="small" />,
        text: 'Show unhealthy pods only.'
      },
      {
        id: 'logs',
        label: 'Pod Logs',
        icon: <ListAlt fontSize="small" />,
        text: 'Show last 20 lines of logs.'
      },
      {
        id: 'events',
        label: 'Recent Events',
        icon: <ListAlt fontSize="small" />,
        text: 'List recent events.'
      },
      {
        id: 'warningEvents',
        label: 'Warning Events',
        icon: <ListAlt fontSize="small" />,
        text: 'List warning events only.'
      },
      {
        id: 'services',
        label: 'Services',
        icon: <Dns fontSize="small" />,
        text: 'List services and endpoint status.'
      },
      {
        id: 'deploymentRisks',
        label: 'Show Risks',
        icon: <Shield fontSize="small" />,
        text: 'What are the risks in this summary?'
      }
    ]
  }
};

export default function AiAssistantPanel({
  tool = 'db-doctor',
  connectionId,
  currentAction,
  context,
  onApplyConnection,
  onToolResult,
  fitHeight = false
}) {
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState('');
  const [attachContext, setAttachContext] = useState(true);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const hasContext = useMemo(() => !!context, [context]);
  const profile = useMemo(() => assistantProfiles[tool] || assistantProfiles['db-doctor'], [tool]);
  const hasConnectionHelpers = useMemo(() => {
    return Boolean(onApplyConnection) && profile.connectionShortcuts && profile.connectionShortcuts.length > 0;
  }, [onApplyConnection, profile.connectionShortcuts]);

  const includesAny = (text, hints) => hints.some((hint) => text.includes(hint));

  const resolveExecutionChipColor = (msg) => {
    const mode = (msg?.mode || '').toLowerCase();
    if (mode === 'tool_error' || mode === 'error') {
      return 'error';
    }

    const corpus = [
      msg?.content || '',
      ...(msg?.keyFindings || []),
      ...(msg?.nextSteps || []),
      ...(msg?.openQuestions || [])
    ]
      .join(' ')
      .toLowerCase();

    if (includesAny(corpus, [
      'severity: high',
      'status: fail',
      'health: fail',
      'overall status: fail',
      'overall: fail',
      'primary failure identified',
      'primary issue',
      'not ready',
      'missing secret',
      'crashloop',
      'imagepullbackoff',
      'error'
    ])) {
      return 'error';
    }

    if (includesAny(corpus, [
      'severity: med',
      'severity: warn',
      'status: warn',
      'health: warn',
      'overall status: warn',
      'overall: warn',
      'warning',
      'risk'
    ])) {
      return 'warning';
    }

    if (includesAny(corpus, [
      'severity: low',
      'status: pass',
      'health: pass',
      'overall status: pass',
      'overall: pass',
      'connection identity confirmed',
      'privileges look sufficient',
      'healthy'
    ])) {
      return 'success';
    }

    return 'primary';
  };

  const parseConnectionFromText = (text) => {
    if (!text) return null;

    const extract = (labels) => {
      for (const label of labels) {
        const re = new RegExp(`${label}\\s*[:=]\\s*([^\\s,;]+)`, 'i');
        const match = text.match(re);
        if (match && match[1]) {
          return match[1].replace(/^['"]|['"]$/g, '');
        }
      }
      return null;
    };

    const hostPort = extract(['host', 'hostname']);
    let host = hostPort || undefined;
    let port = extract(['port']);

    if (host && host.includes(':')) {
      const [h, p] = host.split(':');
      host = h;
      if (!port && p) port = p;
    }

    const database = extract(['database', 'db']);
    const username = extract(['username', 'user']);
    const password = extract(['password', 'pass', 'token']);
    const sslMode = extract(['sslmode', 'ssl']);
    const schema = extract(['schema']);

    if (!host && !database && !username && !port && !password && !schema) return null;

    return {
      host,
      port: port && !Number.isNaN(Number(port)) ? Number(port) : undefined,
      database,
      username,
      password,
      sslMode,
      schema
    };
  };

  const applyConnection = (candidate, connectNow) => {
    if (!candidate || !onApplyConnection) return;
    onApplyConnection(candidate, connectNow);
  };

  const pushMessage = (role, payload) => {
    setMessages(prev => [
      ...prev,
      {
        id: `${role}-${Date.now()}-${Math.random().toString(16).slice(2)}`,
        role,
        ...payload
      }
    ]);
  };

  const askAssistant = async (question) => {
    if (!question || question.trim().length === 0) return;
    setLoading(true);
    setError(null);

    const userText = question.trim();
    const userCandidate = parseConnectionFromText(userText);
    pushMessage('user', { content: userText, connectionCandidate: userCandidate });

    try {
      const response = await apiService.askTriageAssistant({
        tool,
        question: question.trim(),
        connectionId,
        action: currentAction,
        context: attachContext ? context : null
      });

      const assistantText = response.data?.answer || 'No response received.';
      const assistantCandidate = parseConnectionFromText(assistantText);
      const toolExecuted = response.data?.toolExecuted === true;
      const toolResult = response.data?.toolResult;

      if (toolExecuted && toolResult && onToolResult) {
        onToolResult(response.data?.executedTool, toolResult);
      }

      pushMessage('assistant', {
        content: assistantText,
        keyFindings: response.data?.keyFindings || [],
        nextSteps: response.data?.nextSteps || [],
        openQuestions: response.data?.openQuestions || [],
        mode: response.data?.mode || 'heuristic',
        toolExecuted,
        executedTool: response.data?.executedTool,
        connectionCandidate: assistantCandidate
      });
    } catch (err) {
      const msg = err.response?.data?.message || err.message || 'Assistant request failed.';
      setError(msg);
      pushMessage('assistant', {
        content: `Request failed: ${msg}`,
        keyFindings: [],
        nextSteps: [],
        openQuestions: [],
        mode: 'error'
      });
    } finally {
      setLoading(false);
      setInput('');
    }
  };

  const handleSend = () => {
    askAssistant(input);
  };

  return (
    <Paper
      elevation={2}
      sx={{
        p: 2,
        display: 'flex',
        flexDirection: 'column',
        gap: 1,
        height: { xs: 'auto', lg: fitHeight ? '100%' : 'auto' },
        maxHeight: { xs: 'none', lg: fitHeight ? '100%' : 'none' },
        minHeight: { xs: 620, lg: fitHeight ? 0 : 760 },
        overflow: fitHeight ? 'hidden' : 'visible'
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
        <AutoAwesome color="primary" />
        <Typography variant="h4" sx={{ fontWeight: 900 }}>
          {profile.title}
        </Typography>
        {messages.length > 0 && (
          <Chip
            size="medium"
            label={`${messages.length} messages`}
            sx={{ ml: 'auto', '& .MuiChip-label': { fontSize: '1rem', fontWeight: 700 } }}
          />
        )}
      </Box>

      <Typography
        variant="body1"
        sx={{ mb: 1, fontSize: '1.22rem', fontWeight: 700, lineHeight: 1.35 }}
      >
        {profile.subtitle}
      </Typography>

      {hasConnectionHelpers && (
        <Box sx={{ mb: 1, p: 1.25, bgcolor: '#f8fafc', borderRadius: 1, border: '1px solid #e2e8f0' }}>
          <Typography variant="body1" sx={{ fontSize: '1.2rem', fontWeight: 800, display: 'block', mb: 0.5 }}>
            Connection shortcuts
          </Typography>
          <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
            {profile.connectionShortcuts.map((shortcut) => (
              <Button
                key={shortcut.id}
                size="medium"
                variant="outlined"
                sx={{ fontSize: '1rem', fontWeight: 700 }}
                onClick={() => applyConnection(shortcut.connection, false)}
              >
                {shortcut.label}
              </Button>
            ))}
          </Box>
          {profile.connectionNote && (
            <Typography variant="body1" color="text.secondary" sx={{ mt: 0.5, display: 'block', fontSize: '1.05rem', fontWeight: 600 }}>
              {profile.connectionNote}
            </Typography>
          )}
        </Box>
      )}

      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1, mb: 1 }}>
        {profile.prompts.map((prompt) => (
          <Button
            key={prompt.id}
            size="medium"
            variant="outlined"
            startIcon={prompt.icon}
            sx={{ fontSize: '0.98rem', fontWeight: 700 }}
            onClick={() => askAssistant(prompt.text)}
            disabled={loading}
          >
            {prompt.label}
          </Button>
        ))}
      </Box>

      <FormControlLabel
        control={
          <Switch
            checked={attachContext}
            onChange={(e) => setAttachContext(e.target.checked)}
            size="medium"
          />
        }
        label={
          <Typography
            variant="body1"
            sx={{
              fontSize: '1.2rem',
              fontWeight: 700,
              color: hasContext ? 'text.primary' : 'text.secondary'
            }}
          >
            Attach latest results {hasContext ? '' : '(no results yet)'}
          </Typography>
        }
        sx={{ mb: 1 }}
      />

      <Divider sx={{ mb: 1 }} />

      <Box sx={{
        flex: 1,
        minHeight: 0,
        maxHeight: { xs: 'calc(100vh - 430px)', lg: fitHeight ? 'none' : 'calc(100vh - 460px)' },
        overflowY: 'auto',
        pr: 1,
        '& .chat-role-label': { fontSize: '1.08rem', fontWeight: 600 },
        '& .chat-body': { fontSize: '1.2rem', lineHeight: 1.5 },
        '& .chat-section-title': { fontSize: '1.08rem', fontWeight: 800 },
        '& .chat-list-item': { fontSize: '1.1rem', lineHeight: 1.5 },
        '& .chat-executed-chip .MuiChip-label': { fontSize: '0.95rem', fontWeight: 700 }
      }}>
        {messages.length === 0 && (
          <Box sx={{ p: 2, bgcolor: '#f8fafc', borderRadius: 1, border: '1px dashed #cbd5e1' }}>
            <Typography variant="body1" color="text.secondary" sx={{ fontSize: '1.12rem' }}>
              {profile.noMessagesMessage}
            </Typography>
          </Box>
          )}

        {messages.map((msg) => (
          <Box key={msg.id} sx={{ mb: 2 }}>
            <Typography variant="body2" color="text.secondary" className="chat-role-label">
              {msg.role === 'user'
                ? 'You'
                : `Assistant${msg.mode ? ` (${msg.mode})` : ''}${msg.toolExecuted ? ' • executed tool' : ''}`}
            </Typography>
            <Paper
              elevation={0}
              sx={{
                p: 1.5,
                mt: 0.5,
                bgcolor: msg.role === 'user' ? '#e3f2fd' : '#f5f5f5',
                borderRadius: 1
              }}
            > 
              <Typography variant="body1" sx={{ whiteSpace: 'pre-wrap' }} className="chat-body">
                {msg.content}
              </Typography>

              {msg.connectionCandidate && (
                <Box sx={{ mt: 1, display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                  <Button
                    size="small"
                    variant="contained"
                    onClick={() => applyConnection(msg.connectionCandidate, true)}
                    disabled={!onApplyConnection}
                  >
                    Apply & Connect
                  </Button>
                  <Button
                    size="small"
                    variant="outlined"
                    onClick={() => applyConnection(msg.connectionCandidate, false)}
                    disabled={!onApplyConnection}
                  >
                    Apply Only
                  </Button>
                </Box>
              )}

              {msg.role === 'assistant' && msg.toolExecuted && msg.executedTool && (
                <Box sx={{ mt: 1 }}>
                  <Chip
                    label={`Executed: ${msg.executedTool}`}
                    size="small"
                    color={resolveExecutionChipColor(msg)}
                    className="chat-executed-chip"
                  />
                </Box>
              )}

              {msg.keyFindings?.length > 0 && (
                <Box sx={{ mt: 1 }}>
                  <Typography variant="subtitle2" sx={{ fontWeight: 600 }} className="chat-section-title">
                    Key findings
                  </Typography>
                  <Box component="ul" sx={{ mt: 0.5, mb: 0, pl: 2 }}>
                    {msg.keyFindings.map((item, idx) => (
                      <Typography component="li" variant="body2" key={idx} className="chat-list-item">
                        {item}
                      </Typography>
                    ))}
                  </Box>
                </Box>
              )}

              {msg.nextSteps?.length > 0 && (
                <Box sx={{ mt: 1 }}>
                  <Typography variant="subtitle2" sx={{ fontWeight: 600 }} className="chat-section-title">
                    Next steps
                  </Typography>
                  <Box component="ol" sx={{ mt: 0.5, mb: 0, pl: 2 }}>
                    {msg.nextSteps.map((item, idx) => (
                      <Typography component="li" variant="body2" key={idx} className="chat-list-item">
                        {item}
                      </Typography>
                    ))}
                  </Box>
                </Box>
              )}

              {msg.openQuestions?.length > 0 && (
                <Box sx={{ mt: 1 }}>
                  <Typography variant="subtitle2" sx={{ fontWeight: 600 }} className="chat-section-title">
                    Open questions
                  </Typography>
                  <Box component="ul" sx={{ mt: 0.5, mb: 0, pl: 2 }}>
                    {msg.openQuestions.map((item, idx) => (
                      <Typography component="li" variant="body2" key={idx} className="chat-list-item">
                        {item}
                      </Typography>
                    ))}
                  </Box>
                </Box>
              )}
            </Paper>
          </Box>
        ))}
      </Box>

      {error && (
        <Typography variant="body2" color="error" sx={{ mt: 1 }}>
          {error}
        </Typography>
      )}

      <Box sx={{ mt: 1, display: 'flex', gap: 1, alignItems: 'flex-end' }}>
        <TextField
          label={profile.inputLabel}
          value={input}
          onChange={(e) => setInput(e.target.value)}
          fullWidth
          multiline
          minRows={4}
          size="small"
          sx={{
            '& .MuiInputBase-input': { fontSize: '1.22rem', fontWeight: 400, color: '#111111' },
            '& .MuiInputLabel-root': { fontSize: '1.14rem', fontWeight: 600, color: '#37474f' },
            '& .MuiInputLabel-root.MuiInputLabel-shrink': { fontSize: '1.02rem', color: '#37474f' },
            '& textarea::placeholder': { fontSize: '1.18rem', color: '#607d8b', opacity: 1 }
          }}
        />
        <Button
          variant="contained"
          onClick={handleSend}
          disabled={loading || input.trim().length === 0}
          sx={{ minWidth: 100, minHeight: 52, fontSize: '1.05rem', fontWeight: 700 }}
        >
          {loading ? <CircularProgress size={18} /> : 'Send'}
        </Button>
      </Box>
    </Paper>
  );
}
