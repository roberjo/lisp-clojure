# X12 healthcare EDI primer

Working notes on the X12 standard as it applies to healthcare. Living document — expand as you learn.

## The standard at a glance

X12 is a family of EDI standards maintained by ASC X12. The healthcare subset is governed by HIPAA, which mandates specific transaction sets and versions for covered entities.

## Hierarchy

```
Interchange (ISA / IEA)
└── Functional group (GS / GE)
    └── Transaction set (ST / SE)
        └── Loop (logical grouping, not a separate segment pair in all cases)
            └── Segment (e.g. NM1, CLM, SV1)
                └── Element (delimited within a segment)
                    └── Sub-element / composite (delimited within an element)
```

Delimiters are **positional**: the ISA segment defines the element separator (position 4), the sub-element separator (position 105), and the segment terminator (immediately after the ISA's 106th character). A parser cannot assume `*`, `:`, and `~` — it has to read them from the envelope.

## Transactions relevant to this portfolio

| Code | Name | Direction | Used for |
|---|---|---|---|
| 837 | Health Care Claim | Provider → Payer | Submitting claims. Variants: **837P** professional, **837I** institutional, **837D** dental. |
| 835 | Health Care Claim Payment / Advice | Payer → Provider | Remittance — what got paid, what got denied, why. |
| 270 | Eligibility Inquiry | Provider → Payer | "Is this member covered for this service?" |
| 271 | Eligibility Response | Payer → Provider | Response to 270. |
| 276 | Claim Status Request | Provider → Payer | "Where is claim X in your pipeline?" |
| 277 | Claim Status Response | Payer → Provider | Response to 276. |

## Actors

- **Provider** — clinic, hospital, dentist. Submits 837s, receives 835s.
- **Clearinghouse** — middleman. Normalizes, validates, routes. Often the entity a parser like project 02 would integrate with.
- **Payer** — insurance company, Medicaid agency, plan sponsor. Adjudicates claims, issues remittance.
- **Plan sponsor** — employer or government program funding the benefit.

## Versions

HIPAA-mandated version for most transactions today is **5010** (some transactions reference 5010A1, 5010A2 etc. — these are addenda). Older 4010 references still exist in legacy contexts.

## Where to look things up

- CMS companion guides (free, plan-specific usage rules).
- Washington Publishing Company hosts the implementation guides (the authoritative spec PDFs — not free).
- Public clearinghouse sandbox documentation often includes annotated samples.

## Gotchas surfaced while building this repo

### Repetition separator vs. segment terminator must differ

ISA11 is the repetition separator. ISA105 is the segment terminator. The X12 spec requires all delimiters (element, sub-element, repetition, segment) to be **mutually distinct characters**. If they collide, a naive line-by-line splitter splits the ISA segment itself — the parser breaks before it ever reaches the real data.

The first version of the custom-delimiters fixture in this repo used `^` for both, which is invalid. See the commit history for `02-x12-parser/samples/synthetic/custom-delimiters-837d.edi`.

### `parse-error` collides with `cl:parse-error`

When naming conditions in SBCL, beware that `cl:parse-error` is a standard symbol and SBCL applies package locks. A `(define-condition parse-error ...)` in your package will be rejected. Use a domain prefix: `x12-parse-error`.

### CL keyword case → Clojure EDN mismatch

CL's reader/printer defaults to uppercase symbols. CL's `:type` prints as `:TYPE`. EDN is case-sensitive, so the Clojure reader sees `:TYPE` as a different keyword from `:type`. When emitting plists for Clojure consumption, bind `*print-case*` to `:downcase`.

### Class names leak their package when printed

`(prin1 (class-name (class-of obj)))` prints `MY-PKG:DENTAL-CLAIM-TRANSACTION` including the package prefix. For cross-language consumption, intern the symbol name into the keyword package (`:dental-claim-transaction`) before emitting.

### XML namespace silence

A BaseX query against a doc with the wrong namespace returns empty — silently. No error. A production loader should assert post-`ADD` that the document is reachable via the expected namespace prefix.

### Add more here as encountered

- (Segment-ordering quirks specific to 837D loops 2010, 2300, 2400, etc.)
- (Companion-guide rules that mark optional elements as required for specific payers.)
- (5010 vs. 5010A1/A2 addenda differences.)
