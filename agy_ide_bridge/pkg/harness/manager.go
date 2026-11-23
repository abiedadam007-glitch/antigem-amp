package harness

import (
	"bufio"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"github.com/gorilla/websocket"
)

const (
	startTimeout      = 45 * time.Second
	maxBufferedEvents = 20000
)

var upgrader = websocket.Upgrader{
	CheckOrigin:     func(r *http.Request) bool { return true },
	ReadBufferSize:  64 << 10,
	WriteBufferSize: 64 << 10,
}

// RPCError is a JSON-RPC error.
type RPCError struct {
	Code    int             `json:"code"`
	Message string          `json:"message"`
	Data    json.RawMessage `json:"data,omitempty"`
}

func (e *RPCError) Error() string { return fmt.Sprintf("harness: %s (code %d)", e.Message, e.Code) }

type rpcResult struct {
	result json.RawMessage
	err    error
}

type wireMessage struct {
	ID     json.RawMessage `json:"id,omitempty"`
	Method string          `json:"method,omitempty"`
	Params json.RawMessage `json:"params,omitempty"`
	Result json.RawMessage `json:"result,omitempty"`
	Error  *RPCError       `json:"error,omitempty"`
}

type PendingRequest struct {
	Key       string          `json:"key"`
	Method    string          `json:"method"`
	Params    json.RawMessage `json:"params"`
	SessionID string          `json:"session_id,omitempty"`
	Seq       int64           `json:"seq"`
	Received  time.Time       `json:"received"`
	rawID     json.RawMessage
	gen       int64
}

type event struct {
	seq   int64
	frame []byte
}

type process struct {
	gen     int64
	cmd     *exec.Cmd
	stdin   io.WriteCloser
	writeMu sync.Mutex
	exited  chan struct{}

	mu      sync.Mutex
	nextID  int64
	calls   map[int64]chan rpcResult
	started time.Time
}

// Manager owns the DeepSeek Harness runtime process and serves /api/harness/.
type Manager struct {
	HomeDir      string
	WorkspaceDir string
	BinPath      string

	mu        sync.Mutex
	proc      *process
	gen       int64
	starting  chan struct{}
	startErr  error
	logs      []string

	eventsMu  sync.RWMutex
	nextSeq   int64
	events    []event
	subs      map[chan event]struct{}

	reqMu     sync.Mutex
	requests  map[string]PendingRequest

	jobMu     sync.Mutex
	job       *installJob
}

func NewManager(homeDir, workspaceDir, binPath string) *Manager {
	binPath = strings.TrimSpace(binPath)
	if binPath == "~" || strings.HasPrefix(binPath, "~/") {
		binPath = filepath.Join(homeDir, strings.TrimPrefix(binPath, "~"))
	}
	return &Manager{
		HomeDir:      homeDir,
		WorkspaceDir: workspaceDir,
		BinPath:      binPath,
		subs:         make(map[chan event]struct{}),
		requests:     make(map[string]PendingRequest),
	}
}

func (m *Manager) Ensure(ctx context.Context) (*process, error) {
	m.mu.Lock()
	if m.proc != nil {
		p := m.proc
		m.mu.Unlock()
		return p, nil
	}

	if m.starting != nil {
		starting := m.starting
		m.mu.Unlock()
		select {
		case <-starting:
			m.mu.Lock()
			p, err := m.proc, m.startErr
			m.mu.Unlock()
			return p, err
		case <-ctx.Done():
			return nil, ctx.Err()
		}
	}

	starting := make(chan struct{})
	m.starting = starting
	m.startErr = nil
	m.mu.Unlock()

	defer func() {
		m.mu.Lock()
		m.starting = nil
		close(starting)
		m.mu.Unlock()
	}()

	bin, err := m.resolveBinary()
	if err != nil {
		m.mu.Lock()
		m.startErr = err
		m.mu.Unlock()
		return nil, err
	}

	ctxStart, cancel := context.WithTimeout(ctx, startTimeout)
	defer cancel()

	cmd := exec.CommandContext(ctxStart, bin, "--profile", "acp")
	cmd.Dir = m.WorkspaceDir
	cmd.Env = append(os.Environ(),
		fmt.Sprintf("HOME=%s", m.HomeDir),
		fmt.Sprintf("DSH_HOME=%s", filepath.Join(m.HomeDir, ".dsh")),
	)

	stdin, err := cmd.StdinPipe()
	if err != nil {
		return nil, err
	}

	stdout, err := cmd.StdoutPipe()
	if err != nil {
		stdin.Close()
		return nil, err
	}

	stderr, err := cmd.StderrPipe()
	if err != nil {
		stdin.Close()
		stdout.Close()
		return nil, err
	}

	if err := cmd.Start(); err != nil {
		stdin.Close()
		stdout.Close()
		stderr.Close()
		return nil, err
	}

	m.gen++
	p := &process{
		gen:     m.gen,
		cmd:     cmd,
		stdin:   stdin,
		exited:  make(chan struct{}),
		calls:   make(map[int64]chan rpcResult),
		started: time.Now(),
	}

	go m.readStderr(p, stderr)
	go m.readStdout(p, stdout)

	m.mu.Lock()
	m.proc = p
	m.mu.Unlock()

	return p, nil
}

func (m *Manager) StopAll() {
	m.mu.Lock()
	p := m.proc
	m.proc = nil
	m.mu.Unlock()

	if p != nil && p.cmd != nil && p.cmd.Process != nil {
		_ = p.cmd.Process.Kill()
	}
}

func (m *Manager) PendingRequests() []PendingRequest {
	m.reqMu.Lock()
	defer m.reqMu.Unlock()
	list := make([]PendingRequest, 0, len(m.requests))
	for _, req := range m.requests {
		list = append(list, req)
	}
	return list
}

func (m *Manager) readStderr(p *process, r io.Reader) {
	scanner := bufio.NewScanner(r)
	for scanner.Scan() {
		line := scanner.Text()
		m.mu.Lock()
		m.logs = append(m.logs, line)
		if len(m.logs) > 500 {
			m.logs = m.logs[len(m.logs)-500:]
		}
		m.mu.Unlock()
	}
}

func (m *Manager) readStdout(p *process, r io.Reader) {
	defer close(p.exited)
	scanner := bufio.NewScanner(r)
	buf := make([]byte, 0, 1<<20)
	scanner.Buffer(buf, 10<<20)

	for scanner.Scan() {
		raw := scanner.Bytes()
		if len(raw) == 0 {
			continue
		}

		var wm wireMessage
		if err := json.Unmarshal(raw, &wm); err != nil {
			continue
		}

		if len(wm.ID) > 0 {
			var id int64
			if err := json.Unmarshal(wm.ID, &id); err == nil {
				p.mu.Lock()
				ch, ok := p.calls[id]
				delete(p.calls, id)
				p.mu.Unlock()
				if ok {
					if wm.Error != nil {
						ch <- rpcResult{err: wm.Error}
					} else {
						ch <- rpcResult{result: wm.Result}
					}
					continue
				}
			}
		}

		// broadcast event
		m.broadcastEvent(raw)
	}
}

func (m *Manager) broadcastEvent(frame []byte) {
	m.eventsMu.Lock()
	m.nextSeq++
	ev := event{seq: m.nextSeq, frame: frame}
	m.events = append(m.events, ev)
	if len(m.events) > maxBufferedEvents {
		m.events = m.events[len(m.events)-maxBufferedEvents:]
	}
	subs := make([]chan event, 0, len(m.subs))
	for ch := range m.subs {
		subs = append(subs, ch)
	}
	m.eventsMu.Unlock()

	for _, ch := range subs {
		select {
		case ch <- ev:
		default:
		}
	}
}
