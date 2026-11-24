package harness

import (
	"context"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"sync"
	"time"
)

// installJob represents an in-progress or completed install/update job for dsh CLI.
type installJob struct {
	mu       sync.Mutex
	Kind     string
	Running  bool
	ExitCode *int
	Log      strings.Builder
	Started  time.Time
}

func (j *installJob) logf(format string, a ...interface{}) {
	j.mu.Lock()
	defer j.mu.Unlock()
	fmt.Fprintf(&j.Log, format+"\n", a...)
}

func (j *installJob) snapshot() map[string]interface{} {
	j.mu.Lock()
	defer j.mu.Unlock()
	return map[string]interface{}{
		"kind":      j.Kind,
		"running":   j.Running,
		"exit_code": j.ExitCode,
		"log":       j.Log.String(),
		"started":   j.Started,
	}
}

// resolveBinary looks for the dsh binary in configured path or system PATH.
func (m *Manager) resolveBinary() (string, error) {
	if m.BinPath != "" {
		if fi, err := os.Stat(m.BinPath); err == nil && !fi.IsDir() {
			return m.BinPath, nil
		}
	}

	candidates := []string{
		"dsh",
		filepath.Join(m.HomeDir, ".dsh", "bin", "dsh"),
		filepath.Join(m.HomeDir, ".local", "bin", "dsh"),
		filepath.Join(m.HomeDir, ".npm-global", "bin", "dsh"),
		"/data/user/0/ai.antigem/files/usr/bin/dsh",
		"/data/user/0/com.amp.ai/files/usr/bin/dsh",
	}

	for _, c := range candidates {
		if p, err := exec.LookPath(c); err == nil {
			return p, nil
		}
		if fi, err := os.Stat(c); err == nil && !fi.IsDir() {
			return c, nil
		}
	}

	return "", fmt.Errorf("deepseek harness CLI (dsh) not found on PATH or in ~/.dsh/bin")
}

func (m *Manager) install(ctx context.Context, j *installJob, update bool) error {
	j.logf("Checking package managers for DeepSeek Harness (@deepseek-ai/dsh)...")

	pm := "pnpm"
	if _, err := exec.LookPath("pnpm"); err != nil {
		pm = "npm"
	}

	j.logf("Using %s to install @deepseek-ai/dsh...", pm)
	var cmd *exec.Cmd
	if pm == "pnpm" {
		cmd = exec.CommandContext(ctx, "pnpm", "add", "-g", "@deepseek-ai/dsh@latest")
	} else {
		cmd = exec.CommandContext(ctx, "npm", "install", "-g", "@deepseek-ai/dsh@latest")
	}

	cmd.Env = os.Environ()
	out, err := cmd.CombinedOutput()
	j.logf("%s", string(out))
	if err != nil {
		j.logf("Installation failed: %v", err)
		code := 1
		if exitErr, ok := err.(*exec.ExitError); ok {
			code = exitErr.ExitCode()
		}
		j.ExitCode = &code
		return err
	}

	code := 0
	j.ExitCode = &code
	j.logf("DeepSeek Harness successfully installed/updated.")
	return nil
}
