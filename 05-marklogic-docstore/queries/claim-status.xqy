xquery version "3.1";
(: -----------------------------------------------------------------
   Claim status: locate a claim by its provider-issued claim id (CLM01),
   return the full document or an empty <not-found/>.
   ----------------------------------------------------------------- :)

declare namespace c = "urn:x12:837d";

declare variable $claim-id as xs:string external := "PCN001";

let $hit := collection("claims")/c:claim[c:claim-detail/c:claim-id = $claim-id]
return
  if (exists($hit))
  then $hit
  else <not-found claim-id="{$claim-id}"/>
