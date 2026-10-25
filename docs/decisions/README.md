# Decisions

One file per implemented decision, so later changes know why the code is the way it is. Numbered in order: `NNNN-short-title.md`. Never renumber or delete one; if a decision is replaced, write a new file and mark the old one `Superseded by NNNN`.

Closed product decisions that are not implemented yet stay in the design document ("Roadmap and decisions"); a file is added here when the decision lands in code.

Template:

```markdown
# NNNN. Title

- Status: Accepted | Superseded by NNNN
- Date: YYYY-MM-DD

## Context
The problem and the constraints.

## Decision
What was implemented, and where in the code.

## Alternatives
Options considered and why they were not chosen (or are kept for later).

## Consequences
What gets easier or harder; follow-up work.
```
