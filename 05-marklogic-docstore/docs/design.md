# Document design

## Why a document store

X12 transactions are deeply nested and the schema differs by transaction set. A relational decomposition either flattens this (losing structure) or normalizes it into a dozen tables that you constantly re-join. A document store keeps the transaction in the shape its consumers want.

## Backend choice

**BaseX** (Apache-licensed, native XQuery 3.1, single jar).

Trade-off vs. MarkLogic: BaseX is open-source and trivially runnable, so reviewers can clone this repo and execute the queries; the data model and XQuery transfer to MarkLogic with adjustments to admin APIs (range indexes, REST endpoints) but not to the query language itself.

## One document = one transaction set (ST/SE)

Granularity choice. Alternatives considered:

- **One doc per interchange (ISA/IEA):** too coarse — a single eligibility request would force the whole interchange to update.
- **One doc per claim line (SV3):** too fine — loses the claim context that adjudication needs.

Per-transaction is the natural unit: one 837 claim or one 271 eligibility response, plus the envelope context it needs.

## Schema (claim)

```xml
<claim xmlns="urn:x12:837d">
  <meta>
    <transaction-type>dental</transaction-type>
    <control-number>0001</control-number>
    <interchange-control-number>000000001</interchange-control-number>
    <received-at>2024-06-01T12:00:00Z</received-at>
  </meta>
  <billing-provider>
    <name>ACME DENTAL</name>
    <npi>1234567890</npi>
  </billing-provider>
  <subscriber>
    <name>
      <last>DOE</last>
      <first>JANE</first>
    </name>
    <member-id qualifier="MI">M00112233</member-id>
  </subscriber>
  <claim-detail>
    <claim-id>PCN001</claim-id>
    <total-charge>250.00</total-charge>
    <place-of-service>11</place-of-service>
    <service-line number="1">
      <procedure-code>D1110</procedure-code>
      <charge>250.00</charge>
      <service-date>2024-05-15</service-date>
      <units>1</units>
    </service-line>
  </claim-detail>
</claim>
```

## Indexes (MarkLogic terms; BaseX equivalents in parentheses)

- **Path range index** on `claim/meta/control-number` (BaseX: `CREATE INDEX TOKEN`) — point lookups by transaction control number.
- **Path range index** on `claim/subscriber/member-id` — eligibility lookups.
- **Element range index** on `claim-detail/service-line/service-date` — date-bounded reporting.
- **Path range index** on `claim/billing-provider/npi` — provider-scoped queries.
- **Element word index** on `subscriber/name/last` — name searches without normalization.

For BaseX, the equivalent setup is `CREATE INDEX TOKEN`, `CREATE INDEX TEXT`, and `CREATE INDEX ATTRIBUTE` against an open database; commands live in `setup-indexes.bxs`.
