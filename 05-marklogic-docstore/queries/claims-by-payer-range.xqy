xquery version "3.1";
(: -----------------------------------------------------------------
   Aggregate: claim counts and total billed, grouped by billing provider,
   for a date range. The kind of query a payer-side ops dashboard runs.
   ----------------------------------------------------------------- :)

declare namespace c = "urn:x12:837d";

declare variable $from as xs:date external := xs:date("2024-05-01");
declare variable $to   as xs:date external := xs:date("2024-05-31");

<claims-by-provider from="{$from}" to="{$to}">{
  let $claims := collection("claims")/c:claim
                 [c:claim-detail/c:service-line/c:service-date >= xs:string($from)
                  and c:claim-detail/c:service-line/c:service-date <= xs:string($to)]
  for $provider in distinct-values($claims/c:billing-provider/c:npi)
  let $for-provider := $claims[c:billing-provider/c:npi = $provider]
  let $name         := ($for-provider/c:billing-provider/c:name)[1]/text()
  let $total        := sum(for $c in $for-provider
                           return xs:decimal($c/c:claim-detail/c:total-charge))
  order by $total descending
  return
    <provider npi="{$provider}" name="{$name}">
      <claim-count>{count($for-provider)}</claim-count>
      <total-billed>{$total}</total-billed>
    </provider>
}</claims-by-provider>
