# Workflow: Docs and troubleshooting notes

**When to use this:** a troubleshooting entry, a fix to `PROMPT.md`, or any other docs change.

- `docs/TROUBLESHOOTING.md` is grouped under headings (Connecting, Working on the TV, then one per larger
  problem). An entry is a bullet that opens with the symptom in bold, then the cause and the fix, naming the
  TV model and firmware it was seen on.
- A fix for something your agent got stuck on usually belongs in `PROMPT.md` as one extra sentence, kept
  brand-neutral. Brand-specific knowledge goes in `examples/` or `docs/devices/`. Changes to the safety rules
  themselves go to the maintainer.
- Several tests pin facts in `docs/devices/` notes, `PROMPT.md` and `docs/TROUBLESHOOTING.md` (the
  `tests/test_*_note.py` files). If you reword one of those passages, run the suite and keep the fact.
- Keep Neovia's `README.md` and its guides in step. Inherited TV and tool tables live in `UPSTREAM.md`;
  theme and add-on indexes remain in their respective folders. Add rows where the others are.

Then run the tests: `python3 -m unittest discover -s tests -v`.
