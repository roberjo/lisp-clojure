#!/usr/bin/env python3
"""Convert one JSON claim (as produced by project 04's CLI) to an XML document
matching the 05-marklogic-docstore schema.

Usage:
    project04-output.jsonl ─┐
                            ├─> python from-json.py > documents/claim-XYZ.xml
                            │
   or piped into the loader:
    cat project04-output.jsonl |
      python from-json.py --multi |
      basex -c "ADD ..."
"""

import argparse
import json
import sys
import xml.etree.ElementTree as ET
from xml.dom import minidom

NS = "urn:x12:837d"
ET.register_namespace("", NS)


def claim_to_xml(claim: dict) -> ET.Element:
    """Render one Clojure-emitted claim dict as a <claim> Element."""
    root = ET.Element(f"{{{NS}}}claim")

    meta = ET.SubElement(root, f"{{{NS}}}meta")
    ET.SubElement(meta, f"{{{NS}}}transaction-type").text = str(claim["transaction-type"])
    ET.SubElement(meta, f"{{{NS}}}control-number").text = claim["control-number"]

    if claim.get("billing-provider"):
        bp = ET.SubElement(root, f"{{{NS}}}billing-provider")
        ET.SubElement(bp, f"{{{NS}}}name").text = claim["billing-provider"]

    sub_data = claim.get("subscriber")
    if sub_data:
        sub = ET.SubElement(root, f"{{{NS}}}subscriber")
        name = ET.SubElement(sub, f"{{{NS}}}name")
        ET.SubElement(name, f"{{{NS}}}last").text = sub_data.get("last", "")
        if sub_data.get("first"):
            ET.SubElement(name, f"{{{NS}}}first").text = sub_data["first"]
        if sub_data.get("member-id"):
            mid = ET.SubElement(sub, f"{{{NS}}}member-id")
            mid.set("qualifier", "MI")
            mid.text = sub_data["member-id"]

    detail = ET.SubElement(root, f"{{{NS}}}claim-detail")
    ET.SubElement(detail, f"{{{NS}}}claim-id").text = claim["claim-id"]
    ET.SubElement(detail, f"{{{NS}}}total-charge").text = f"{claim['total-charge']:.2f}"

    for line in claim.get("service-lines", []):
        sl = ET.SubElement(detail, f"{{{NS}}}service-line")
        sl.set("number", str(line["line-number"]))
        ET.SubElement(sl, f"{{{NS}}}procedure-code").text = line["procedure-code"]
        ET.SubElement(sl, f"{{{NS}}}charge").text = f"{line['charge']:.2f}"
        if line.get("service-date"):
            ET.SubElement(sl, f"{{{NS}}}service-date").text = line["service-date"]
        if line.get("units") is not None:
            ET.SubElement(sl, f"{{{NS}}}units").text = str(line["units"])

    return root


def pretty(elem: ET.Element) -> str:
    raw = ET.tostring(elem, encoding="unicode")
    return minidom.parseString(raw).toprettyxml(indent="  ")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--multi", action="store_true",
                    help="Read JSONL (one claim per line); emit one XML doc per input line.")
    args = ap.parse_args()

    if args.multi:
        for line in sys.stdin:
            line = line.strip()
            if not line:
                continue
            print(pretty(claim_to_xml(json.loads(line))))
    else:
        claim = json.load(sys.stdin)
        sys.stdout.write(pretty(claim_to_xml(claim)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
