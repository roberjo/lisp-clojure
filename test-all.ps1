#!/usr/bin/env pwsh
# Windows equivalent of `make test`. Run every project's test suite in dependency
# order and exit non-zero on the first failure.

$ErrorActionPreference = 'Stop'

function Run-Suite {
    param([string]$Label, [string]$Dir, [scriptblock]$Cmd)
    Write-Host ""
    Write-Host "=== $Label ===" -ForegroundColor Cyan
    Push-Location $Dir
    try {
        & $Cmd
        if ($LASTEXITCODE -ne 0) {
            throw "$Label failed (exit $LASTEXITCODE)"
        }
    } finally {
        Pop-Location
    }
}

$repoRoot = $PSScriptRoot

# Project order: 01 -> 03 -> 02 (depends on 03) -> 04 -> 06
Run-Suite "Project 01: kvstore"        "$repoRoot\01-cl-foundations"        { sbcl --non-interactive --load run-tests.lisp }
Run-Suite "Project 03: edi-dsl"        "$repoRoot\03-cl-macros-clos"        { sbcl --non-interactive --load run-tests.lisp }
Run-Suite "Project 02: x12-parser"     "$repoRoot\02-x12-parser"            { sbcl --non-interactive --load run-tests.lisp }
Run-Suite "Project 04: edi.transform"  "$repoRoot\04-clojure-edi-transform" { clojure -X:test }
Run-Suite "Project 06: adjudis-core"   "$repoRoot\06-adjudis-core"          { clojure -X:test }

Write-Host ""
Write-Host "Tip:" -ForegroundColor DarkGray
Write-Host "  clojure -M:serve   in 06-adjudis-core to start the HTTP API" -ForegroundColor DarkGray
Write-Host "  clojure -T:build uber   to build the uberjar"               -ForegroundColor DarkGray
Write-Host "  docker build -t adjudis-core:0.1.0 -f 06-adjudis-core/Dockerfile 06-adjudis-core" -ForegroundColor DarkGray

Write-Host ""
Write-Host "All test suites passed." -ForegroundColor Green
