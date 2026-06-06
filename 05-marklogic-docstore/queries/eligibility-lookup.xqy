xquery version "3.1";
(: -----------------------------------------------------------------
   Eligibility lookup: find all claims for a given member id.
   Returns a small summary per claim for the calling app to display.
   ----------------------------------------------------------------- :)

declare namespace c = "urn:x12:837d";

declare variable $member-id as xs:string external := "M00112233";

<eligibility-summary member-id="{$member-id}">{
  for $claim in collection("claims")/c:claim
  where $claim/c:subscriber/c:member-id = $member-id
  order by $claim/c:meta/c:received-at descending
  return
    <claim id="{$claim/c:claim-detail/c:claim-id/text()}">
      <received>{$claim/c:meta/c:received-at/text()}</received>
      <charge>{$claim/c:claim-detail/c:total-charge/text()}</charge>
      <procedure>{$claim/c:claim-detail/c:service-line/c:procedure-code/text()}</procedure>
    </claim>
}</eligibility-summary>
