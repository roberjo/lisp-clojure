# Local development environment

Get a clean machine to `make test` (or `./test-all.ps1`) green. Verified flows for Windows, macOS, and Linux.

> The toolchain this repo was built and verified against:
> SBCL **2.6.5**, Quicklisp **2026-01-01 dist**, deps.clj / Clojure CLI **1.12.5**, Java **21+** (built on 24), BaseX **12.0**, Python **3.10+** (built on 3.13).

---

## What you need

| Tool | Purpose | Required for |
|---|---|---|
| SBCL | Common Lisp implementation | projects 01, 02, 03 |
| Quicklisp | CL library manager (FiveAM, ASDF discovery) | projects 01, 02, 03 |
| Clojure CLI (or deps.clj) | run deps.edn projects | projects 04, 06 |
| Java 21+ | JVM for Clojure | projects 04, 06 |
| BaseX 12.0 | XQuery engine | project 05 |
| Python 3.10+ | JSON → XML bridge | project 05 |
| Docker (optional) | build/run the project-06 container image | project 06 |
| `make` (optional) | runs `Makefile`. Windows users can use `test-all.ps1` instead | convenience |
| Git | source control | always |

Project 06 carries its own JVM dep list (Clara, Reitit + ring-jetty, Cheshire, logback + logstash encoder, iapetos). All resolved automatically on first `clojure -X:test` or `clojure -M:serve` — the only thing you need on PATH is `clojure` + `java`.

---

## Windows 11 (verified)

Uses scoop where possible. PowerShell 7+ assumed.

```powershell
# --- Package manager prerequisite ---
# If you don't have scoop:
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
irm get.scoop.sh | iex

# --- SBCL ---
scoop install sbcl
sbcl --version   # → SBCL 2.6.5 (or newer)

# --- Quicklisp (inside SBCL) ---
$ql = "$env:TEMP\quicklisp.lisp"
Invoke-WebRequest "https://beta.quicklisp.org/quicklisp.lisp" -OutFile $ql
sbcl --non-interactive --load $ql `
     --eval "(quicklisp-quickstart:install)" `
     --eval "(let ((ql-util::*do-not-prompt* t))(ql:add-to-init-file))"

# --- Java (skip if java -version works and reports 21+) ---
scoop install temurin21-jdk

# --- Clojure CLI via deps.clj (single-binary, no PowerShell module dance) ---
# Pick the latest from https://github.com/borkdude/deps.clj/releases
$dest = "C:\Tools\clojure"
New-Item -ItemType Directory -Force -Path $dest | Out-Null
$api = Invoke-RestMethod "https://api.github.com/repos/borkdude/deps.clj/releases/latest"
$asset = $api.assets | Where-Object { $_.name -match "windows-amd64.zip$" } | Select-Object -First 1
Invoke-WebRequest $asset.browser_download_url -OutFile "$env:TEMP\depsclj.zip"
Expand-Archive "$env:TEMP\depsclj.zip" -DestinationPath $dest -Force
Copy-Item "$dest\deps.exe" "$dest\clojure.exe"
Copy-Item "$dest\deps.exe" "$dest\clj.exe"

# --- BaseX (just a zip with batch wrappers) ---
$baseXUrl = "https://files.basex.org/releases/12.0/BaseX120.zip"
Invoke-WebRequest $baseXUrl -OutFile "$env:TEMP\basex.zip"
New-Item -ItemType Directory -Force -Path "C:\Tools\basex" | Out-Null
Expand-Archive "$env:TEMP\basex.zip" -DestinationPath "C:\Tools\basex" -Force

# --- Python: usually preinstalled. If not: ---
scoop install python

# --- Add to user PATH (persists across shells) ---
$userPath = [Environment]::GetEnvironmentVariable("Path", "User")
$paths    = ($userPath -split ';') | Where-Object { $_ }
foreach ($p in @("C:\Tools\clojure", "C:\Tools\basex\basex\bin")) {
    if ($paths -notcontains $p) { $paths += $p }
}
[Environment]::SetEnvironmentVariable("Path", ($paths -join ';'), "User")
```

Restart your shell. Verify:

```powershell
sbcl --version; clojure --help | Select-Object -First 1; basex -V; python --version
```

Run the suites:

```powershell
.\test-all.ps1
```

### Optional: GNU Make on Windows

```powershell
scoop install make
```

Then `make test` and `make e2e` work the same as on Unix.

---

## macOS (Apple Silicon or Intel)

Uses Homebrew.

```bash
# --- Package manager ---
# If you don't have brew, see https://brew.sh.

# --- Everything ---
brew install sbcl clojure/tools/clojure basex python@3.12
# JDK if needed:
brew install --cask temurin@21

# --- Quicklisp ---
curl -O https://beta.quicklisp.org/quicklisp.lisp
sbcl --non-interactive --load quicklisp.lisp \
     --eval '(quicklisp-quickstart:install)' \
     --eval "(let ((ql-util::*do-not-prompt* t))(ql:add-to-init-file))"
rm quicklisp.lisp
```

Verify and run:

```bash
sbcl --version && clojure --help | head -1 && basex -V && python3 --version
make test
```

---

## Linux (Debian / Ubuntu)

```bash
# --- System packages ---
sudo apt update
sudo apt install -y sbcl python3 default-jdk make curl unzip

# --- Quicklisp ---
curl -O https://beta.quicklisp.org/quicklisp.lisp
sbcl --non-interactive --load quicklisp.lisp \
     --eval '(quicklisp-quickstart:install)' \
     --eval "(let ((ql-util::*do-not-prompt* t))(ql:add-to-init-file))"
rm quicklisp.lisp

# --- Clojure CLI (Linux installer is well-behaved) ---
curl -L -O https://github.com/clojure/brew-install/releases/latest/download/linux-install.sh
chmod +x linux-install.sh
sudo ./linux-install.sh
rm linux-install.sh

# --- BaseX ---
curl -L -o basex.zip https://files.basex.org/releases/12.0/BaseX120.zip
sudo unzip -q basex.zip -d /opt
sudo ln -sf /opt/basex/bin/basex /usr/local/bin/basex
rm basex.zip
```

Verify and run:

```bash
sbcl --version && clojure --help | head -1 && basex -V && python3 --version
make test
```

---

## Editor setup

### VS Code / Cursor

Recommended extensions:

| Extension | For |
|---|---|
| **Alive** (`rheller.alive`) | SBCL / Common Lisp REPL + completion |
| **Calva** (`betterthantomorrow.calva`) | Clojure REPL, paredit, structural editing |
| **XML Tools** (`dotjoshjohnson.xml`) | XQuery syntax + XML formatting |
| **Python** (`ms-python.python`) | Python LSP |

Suggested `settings.json` additions:

```jsonc
{
  // Alive: point at your SBCL
  "alive.lisp.startCommand": ["sbcl", "--noinform"],

  // Calva: deps.edn auto-discovered

  // Editor hygiene for this project
  "editor.rulers": [100],
  "files.trimTrailingWhitespace": true,
  "files.insertFinalNewline": true,

  // Per-language indent
  "[clojure]":   { "editor.tabSize": 2, "editor.insertSpaces": true },
  "[lisp]":      { "editor.tabSize": 2, "editor.insertSpaces": true },
  "[xml]":       { "editor.tabSize": 2 },
  "[xquery]":    { "editor.tabSize": 2 }
}
```

### Emacs

The de-facto Common Lisp editor:

```elisp
;; Install via package.el or use-package
(use-package sly
  :ensure t
  :config (setq inferior-lisp-program "sbcl"))

(use-package cider :ensure t)        ; Clojure REPL
(use-package paredit :ensure t)
(add-hook 'lisp-mode-hook #'paredit-mode)
(add-hook 'clojure-mode-hook #'paredit-mode)
```

SLY is the modern fork of SLIME; both work. SLY's REPL handling is nicer on Windows.

---

## Verifying the install

After everything is installed, from the repo root:

```bash
make test               # all five test suites
make e2e                # EDI → EDN → JSON → XML pipeline
make adjudicate-demo    # also runs through project 06 adjudication
```

(Windows: `.\test-all.ps1`)

Expected: ~150 assertions across ~95 tests, all green. The `e2e` target prints a fully-formatted XML claim document on stdout. The `adjudicate-demo` target prints a decision JSON with rule citations.

If a single project fails, run just that one:

```bash
make test-01     # CL kvstore
make test-02     # X12 parser  (depends on 03)
make test-03     # DSL + CLOS
make test-04     # Clojure transform
make test-06     # Adjudis core
```

Run the project-06 server locally to see structured logs + the API:

```bash
make serve-06                              # single-tenant (no auth)
# in another shell:
curl http://localhost:8080/health
curl http://localhost:8080/metrics | head -30

# Or multi-tenant mode (uses the shipped fixture):
cd 06-adjudis-core && \
  TENANTS_FILE=resources/fixtures/tenants.edn PORT=8080 clojure -M:serve
# in another shell:
curl -H 'X-API-Key: akey-acme-dev-only-do-not-use-in-prod' \
  http://localhost:8080/catalog | jq '.count, .tenant-id'
```

Build the standalone artifacts:

```bash
make uberjar-06         # → 06-adjudis-core/target/adjudis-core-0.1.0-standalone.jar
make docker-build-06    # → docker image adjudis-core:0.1.0 (requires Docker)
```

---

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `sbcl: command not found` | scoop's shim dir not on PATH | `scoop install` adds it; restart shell |
| `Application not found: quicklisp` | Quicklisp installed but not loaded by sbclrc | rerun `(ql:add-to-init-file)` or load manually |
| Clojure first run hangs for minutes | downloading the toolchain on first invocation | one-time; subsequent runs are fast |
| BaseX prints `writing new configuration file` on first run | benign | nothing to fix |
| `Permission denied` running scripts on Linux | new files lack +x | `chmod +x scripts/load.sh scripts/from-json.py` |
| Tests pass but `make e2e` produces no output | shell quoting around the pipe; PowerShell vs cmd.exe differ | use the explicit pipeline in [README](../README.md#cross-project-pipeline-verified-end-to-end) |
| Git push asks for password | git credential helper not set | `gh auth setup-git` if you use `gh`, otherwise GitHub PAT in credential manager |
| Project 06 server takes 30–60s to start | cold JVM + Clara session compile | one-time per process; subsequent `/adjudicate` requests are sub-100ms |
| Project 06 uberjar build takes 10+ minutes | you're using AOT compilation (Clara generates thousands of inner classes) | the shipped `build.clj` is non-AOT for this reason; don't switch it back |
| Project 06 logs are an unstructured wall of text | something other than logback resolved as the SLF4J provider | check `deps.edn` includes `ch.qos.logback/logback-classic` and `resources/logback.xml` exists |

---

## What's intentionally NOT scripted

- Docker / dev container — overkill for a portfolio repo with five small projects.
- Pre-commit hooks — none of the tools have first-class formatters that everyone agrees on (`cl-format`, `cljfmt`); manual hygiene + PR review covers it.
- LSP servers configured per-OS — every editor's LSP setup is different and changes faster than this doc would.
