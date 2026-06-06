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

## Gotchas to capture as you encounter them

- (Add notes here as you hit them — segment-ordering quirks, optional-but-required-by-companion-guide elements, etc.)
