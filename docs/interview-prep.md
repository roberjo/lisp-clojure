# Interview prep — talking points mapped to requirements

A working set of answers to the questions a senior Lisp interview is likely to surface. Refine each as the corresponding project ships.

## "Walk me through your Common Lisp experience"

- Frame the portfolio as recent, focused, domain-relevant work — not a substitute for years in seat, but evidence of ramp speed and engineering judgment.
- Lead with project 02 (X12 parser) because it's the on-domain artifact.
- Have a one-minute and a five-minute version ready.

## "Show me a macro you've written and explain why it had to be a macro"

- The `define-segment` DSL from project 03 is the candidate.
- Be ready to explain: what runs at compile time vs. runtime, how hygiene is preserved, what the alternative (data-driven table) would have looked like and why you chose the macro.

## "When would you reach for CLOS vs. plain structs or plists?"

- Concrete answer drawn from project 02's internal representation choice.
- Multiple dispatch, method combination, and runtime class redefinition are the real CLOS-only wins. Have an example.

## "How does your CL background transfer to Clojure?"

- Syntax and the REPL workflow transfer immediately.
- What doesn't: macro hygiene model (Clojure's syntax-quote namespacing vs. CL's package system), mutability defaults, the JVM ecosystem.
- Project 04 is the evidence.

## "Why is a document store appropriate for EDI?"

- Transactions are self-contained nested documents; the schema is hierarchical and varies by transaction set.
- Relational normalization loses the structure that downstream consumers need.
- XQuery operates on the document tree natively; SQL would require constant joins.

## "Describe a time you worked in an unfamiliar large codebase"

- Use the open-source CL contribution (target library to be identified by month 5).
- Structure the answer: how you oriented, what you read first, the bug or feature, how you validated.

## Questions to ask them

- What does "interface with clearinghouses" mean in their day-to-day — direct sockets, SFTP drops, vendor APIs?
- What's the read/write split with MarkLogic? Reporting reads, transactional writes, or both?
- How do they handle the HIPAA / PHI side — sandbox data, scrubbed prod, synthetic generators?
- Team size, code review culture, on-call expectations.

## The experience-bar conversation

The 3+ years CL line is the gating risk. Plan: email **jobs@luzontech.com** early with a short note referencing this portfolio, asking how firm the experience requirement is. Don't apply cold against a hard filter.
