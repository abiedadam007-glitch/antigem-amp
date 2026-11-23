package harness

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"strconv"
	"strings"
	"time"
)

func writeJSON(w http.ResponseWriter, status int, data interface{}) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(data)
}

// ServeHTTP routes /api/harness/.
func (m *Manager) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	path := strings.TrimPrefix(r.URL.Path, "/api/harness")
	switch {
	case path == "/ws":
		m.serveWS(w, r)
	case path == "/rpc" && r.Method == http.MethodPost:
		m.handleRPC(w, r)
	case path == "/requests" && r.Method == http.MethodGet:
		writeJSON(w, http.StatusOK, map[string]interface{}{"success": true, "requests": m.PendingRequests()})
	case strings.HasPrefix(path, "/requests/") && strings.HasSuffix(path, "/reply") && r.Method == http.MethodPost:
		m.handleReply(w, r, strings.TrimSuffix(strings.TrimPrefix(path, "/requests/"), "/reply"))
	case path == "/status" && r.Method == http.MethodGet:
		m.handleStatus(w, r)
	case path == "/start" && r.Method == http.MethodPost:
		if _, err := m.Ensure(r.Context()); err != nil {
			writeJSON(w, http.StatusOK, map[string]interface{}{"success": false, "error": err.Error()})
		} else {
			writeJSON(w, http.StatusOK, map[string]interface{}{"success": true})
		}
	case path == "/restart" && r.Method == http.MethodPost:
		m.StopAll()
		if _, err := m.Ensure(r.Context()); err != nil {
			writeJSON(w, http.StatusOK, map[string]interface{}{"success": false, "error": err.Error()})
		} else {
			writeJSON(w, http.StatusOK, map[string]interface{}{"success": true})
		}
	case path == "/cli/job" && r.Method == http.MethodGet:
		m.jobMu.Lock()
		job := m.job
		m.jobMu.Unlock()
		if job == nil {
			writeJSON(w, http.StatusOK, map[string]interface{}{"running": false})
		} else {
			writeJSON(w, http.StatusOK, job.snapshot())
		}
	case (path == "/cli/install" || path == "/cli/update") && r.Method == http.MethodPost:
		m.handleJob(w, r, path == "/cli/update")
	default:
		http.NotFound(w, r)
	}
}

func (m *Manager) handleStatus(w http.ResponseWriter, r *http.Request) {
	m.mu.Lock()
	running := m.proc != nil
	logs := make([]string, len(m.logs))
	copy(logs, m.logs)
	m.mu.Unlock()

	bin, err := m.resolveBinary()
	installed := err == nil

	writeJSON(w, http.StatusOK, map[string]interface{}{
		"success":   true,
		"installed": installed,
		"binary":    bin,
		"running":   running,
		"logs":      logs,
	})
}

func (m *Manager) handleRPC(w http.ResponseWriter, r *http.Request) {
	var body struct {
		Method    string          `json:"method"`
		Params    json.RawMessage `json:"params"`
		TimeoutMS int             `json:"timeout_ms"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]interface{}{"success": false, "error": "invalid json"})
		return
	}

	p, err := m.Ensure(r.Context())
	if err != nil {
		writeJSON(w, http.StatusOK, map[string]interface{}{"success": false, "error": err.Error()})
		return
	}

	timeout := 60 * time.Second
	if body.TimeoutMS > 0 {
		timeout = time.Duration(body.TimeoutMS) * time.Millisecond
	}

	ctx, cancel := context.WithTimeout(r.Context(), timeout)
	defer cancel()

	res, err := m.callRPC(ctx, p, body.Method, body.Params)
	if err != nil {
		writeJSON(w, http.StatusOK, map[string]interface{}{"success": false, "error": err.Error()})
		return
	}

	writeJSON(w, http.StatusOK, map[string]interface{}{"success": true, "result": res})
}

func (m *Manager) callRPC(ctx context.Context, p *process, method string, params json.RawMessage) (json.RawMessage, error) {
	p.mu.Lock()
	p.nextID++
	id := p.nextID
	ch := make(chan rpcResult, 1)
	p.calls[id] = ch
	p.mu.Unlock()

	defer func() {
		p.mu.Lock()
		delete(p.calls, id)
		p.mu.Unlock()
	}()

	msg := map[string]interface{}{
		"jsonrpc": "2.0",
		"id":      id,
		"method":  method,
	}
	if len(params) > 0 {
		msg["params"] = params
	}

	raw, err := json.Marshal(msg)
	if err != nil {
		return nil, err
	}
	raw = append(raw, '\n')

	p.writeMu.Lock()
	_, err = p.stdin.Write(raw)
	p.writeMu.Unlock()
	if err != nil {
		return nil, err
	}

	select {
	case res := <-ch:
		return res.result, res.err
	case <-ctx.Done():
		return nil, ctx.Err()
	case <-p.exited:
		return nil, errors.New("dsh process exited")
	}
}

func (m *Manager) handleReply(w http.ResponseWriter, r *http.Request, key string) {
	m.reqMu.Lock()
	req, ok := m.requests[key]
	if ok {
		delete(m.requests, key)
	}
	m.reqMu.Unlock()

	if !ok {
		writeJSON(w, http.StatusOK, map[string]interface{}{"success": false, "error": "request not found or already replied"})
		return
	}

	var body struct {
		Result json.RawMessage `json:"result"`
		Error  *RPCError       `json:"error"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]interface{}{"success": false, "error": "invalid json"})
		return
	}

	m.mu.Lock()
	p := m.proc
	m.mu.Unlock()
	if p == nil || p.gen != req.gen {
		writeJSON(w, http.StatusOK, map[string]interface{}{"success": false, "error": "process was restarted"})
		return
	}

	reply := map[string]interface{}{
		"jsonrpc": "2.0",
		"id":      req.rawID,
	}
	if body.Error != nil {
		reply["error"] = body.Error
	} else {
		reply["result"] = body.Result
	}

	raw, _ := json.Marshal(reply)
	raw = append(raw, '\n')

	p.writeMu.Lock()
	_, _ = p.stdin.Write(raw)
	p.writeMu.Unlock()

	writeJSON(w, http.StatusOK, map[string]interface{}{"success": true})
}

func (m *Manager) serveWS(w http.ResponseWriter, r *http.Request) {
	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		return
	}
	defer conn.Close()

	ch := make(chan event, 128)
	m.eventsMu.Lock()
	since := int64(0)
	if s := r.URL.Query().Get("since"); s != "" {
		since, _ = strconv.ParseInt(s, 10, 64)
	}
	// replay missed events
	for _, ev := range m.events {
		if ev.seq > since {
			select {
			case ch <- ev:
			default:
			}
		}
	}
	m.subs[ch] = struct{}{}
	m.eventsMu.Unlock()

	defer func() {
		m.eventsMu.Lock()
		delete(m.subs, ch)
		m.eventsMu.Unlock()
	}()

	go func() {
		for {
			if _, _, err := conn.ReadMessage(); err != nil {
				return
			}
		}
	}()

	for ev := range ch {
		if err := conn.WriteMessage(websocket.TextMessage, ev.frame); err != nil {
			return
		}
	}
}

func (m *Manager) handleJob(w http.ResponseWriter, r *http.Request, update bool) {
	m.jobMu.Lock()
	if m.job != nil && m.job.Running {
		m.jobMu.Unlock()
		writeJSON(w, http.StatusOK, map[string]interface{}{"success": false, "error": "install/update job already in progress"})
		return
	}
	kind := "install"
	if update {
		kind = "update"
	}
	job := &installJob{
		Kind:    kind,
		Running: true,
		Started: time.Now(),
	}
	m.job = job
	m.jobMu.Unlock()

	go func() {
		defer func() {
			job.mu.Lock()
			job.Running = false
			job.mu.Unlock()
		}()
		_ = m.install(context.Background(), job, update)
	}()

	writeJSON(w, http.StatusOK, map[string]interface{}{"success": true})
}
