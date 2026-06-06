# 05 — Document-store modeling (BaseX-targeted)

Document design and XQuery for storing X12 dental claims as XML. Targets BaseX (Apache-licensed, open source) for runnable demonstration; the data model and queries port to MarkLogic with adjustments only to admin APIs.

## Status

Verified against BaseX 12.0: documents load, eligibility / claim-status / aggregate queries return expected results. End-to-end JSON → XML bridge from project 04 verified.

## Why this backend

MarkLogic Community Edition's distribution model has tightened; BaseX gives the same XQuery 3.1 semantics with a single jar that anyone can clone and run. The interview talking point: "data model and queries are MarkLogic-compatible; I built on an OSS XQuery engine for portability."

Full reasoning and per-feature mapping in `docs/design.md`.

## Layout

```
05-marklogic-docstore/
├── docs/design.md           # schema, granularity, index choices
├── documents/               # sample claim XML (3 synthetic claims)
├── queries/
│   ├── eligibility-lookup.xqy
│   ├── claim-status.xqy
│   └── claims-by-payer-range.xqy
└── scripts/
    ├── setup-indexes.bxs    # BaseX index setup
    ├── load.sh              # bulk-load documents/ into the "claims" DB
    └── from-json.py         # convert project 04's JSON output to XML
```

## End-to-end (with all toolchains installed)

```sh
# 1. Generate JSON claims from EDI
sbcl --script ../02-x12-parser/bin/emit-plist.lisp ../02-x12-parser/samples/synthetic/minimal-837d.edi \
  | clojure -M:run-cli \
  > /tmp/claims.jsonl

# 2. Convert JSON to XML docs
python3 scripts/from-json.py --multi < /tmp/claims.jsonl > documents/from-pipeline.xml

# 3. Load into BaseX
basex -c "RUN scripts/setup-indexes.bxs"
sh scripts/load.sh

# 4. Run a query
basex -i documents -q queries/eligibility-lookup.xqy -b "member-id=M00112233"
```

## Queries

| File | Purpose |
|---|---|
| `eligibility-lookup.xqy` | All claims for a given member id, sorted by recency. |
| `claim-status.xqy` | Fetch a single claim by provider-issued id (CLM01). |
| `claims-by-payer-range.xqy` | Aggregate claim counts and total billed per provider in a date range. |

Each query declares its external parameters at the top so it can be invoked from the BaseX CLI with `-b name=value`.

## Acceptance criteria

- [x] Schema design committed (`docs/design.md`).
- [x] Sample XML documents committed (synthetic, fake PHI).
- [x] Eligibility / claim-status / aggregate queries (`.xqy`, runnable).
- [x] Loader script + index setup script.
- [x] README explains why a doc store beats relational for X12.
- [x] Bridges to project 04's output (`from-json.py`).
- [x] Verified against BaseX 12.0.
