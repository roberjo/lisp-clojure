#!/usr/bin/env sh
# Load every XML document in documents/ into the BaseX "claims" database.
# Idempotent: ADD replaces same-path documents.

set -eu

DB_NAME="claims"
HERE="$(dirname "$0")"
DOCS="$HERE/../documents"

basex -c "
CHECK $DB_NAME
$(for f in "$DOCS"/*.xml; do
    echo "ADD TO \"$(basename "$f")\" \"$f\""
  done)
INFO DB
"
