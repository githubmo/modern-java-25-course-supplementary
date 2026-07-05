# labs/

One folder per course day. Each day's lab builds on the previous day's reference-app state.

```
labs/day-NN/
├─ README.md     # the lab brief (objective, steps, acceptance criteria)
└─ starter/      # the scaffold students begin from (often a copy of checkpoint/day-(N-1))
```

**Checkpoints.** The *solution* for each day is the reference app at Git tag
`checkpoint/day-NN` — students can `git checkout checkpoint/day-NN` to catch up, and
instructors generate teaching diffs with `git diff checkpoint/day-(N-1) checkpoint/day-NN`.
Don't keep full solution copies in `labs/`; point at the tag instead.

Use `_TEMPLATE/README.md` as the brief format for every day.
