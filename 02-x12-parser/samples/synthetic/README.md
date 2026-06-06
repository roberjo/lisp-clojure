# Synthetic 837D samples

**Fake data only.** Real claims contain PHI and must never be committed. These fixtures use obviously-bogus member ids, names, and amounts and are checked in so the test suite has something to chew on.

- `minimal-837d.edi` — smallest well-formed 837D the parser must handle.
- `custom-delimiters-837d.edi` — same payload but with non-default element / segment separators (`|` and `^`) to prove delimiter detection.
