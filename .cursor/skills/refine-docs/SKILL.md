---
name: refine-docs
description: Refine morphe-patches-samsung documentation (README, CONTRIBUTING, AGENTS, AUDIT). Use when asked to polish, deslop, audit, or rewrite project docs. Chains README best-practice skills with anti-slop passes and Samsung/Morphe-specific constraints.
---

# Refine Samsung Morphe Docs

Orchestrated doc refinement for **bigyank/morphe-patches-samsung**. Run on next session when user asks to polish README, CONTRIBUTING, or related docs.

## Installed skills to use (read in order)

| Skill | Path | Role |
|-------|------|------|
| **crafting-effective-readmes** | `~/.agents/skills/crafting-effective-readmes` | Audience-first structure; updating vs creating |
| **readme-blueprint-generator** | `~/.agents/skills/readme-blueprint-generator` | Section checklist (adapt — we are OSS tool, not copilot-template repo) |
| **deslop-ai-lint-skill** | `~/.agents/skills/deslop-ai-lint-skill` | Score + flag AI slop; minimal rewrite pass |
| **anti-ai-slop-writing** | `~/.agents/skills/anti-ai-slop-writing` | Constraint-based human tone |
| **anti-slop** | `~/.agents/skills/anti-slop` | Secondary slop patterns |
| **documentation-writer** | `~/.agents/skills/documentation-writer` | Diátaxis — README = how-to + reference hybrid |
| **technical-writing** | `~/.agents/skills/technical-writing` | Plain language, scannable structure |

Existing code slop skill (`~/.codex/skills/ai-slop-cleaner`) is for **Kotlin patches**, not prose.

## External references

- [maximosovsky/readme-guidelines](https://github.com/maximosovsky/readme-guidelines) — badges, quick start above fold, anti-patterns (see `reference.md`)
- [llms.txt spec](https://llmstxt.org/) — optional root `llms.txt` for agent discoverability
- Inspiration repos: [realme-link-patches](https://github.com/lyyako/realme-link-patches), [pairip-patches](https://github.com/sjshb57/pairip-patches)

## Audience (do not forget)

Knox-tripped (0x1), **unrooted** Samsung phone owners who patch **Samsung Health on-device** with Morphe Manager. They need:

1. One-click add-source link immediately
2. **1280 MB** process runtime warning
3. Both patches enabled by default
4. Uninstall stock Health before install
5. No PC / no custom JKS

## Hard constraints (never break)

- Preserve `<!-- PATCHES_START EXPANDED -->` … `<!-- PATCHES_END -->` — CI regenerates this block via `generatePatchesList`
- Do not add `resourcePatch` mentions as recommendations
- License stays **GPLv3** + NOTICE (not CC BY-SA from generic templates)
- No “Made with ❤️”, star-history charts, or marketing filler unless user asks
- Keep dex-only / OOM rationale — it is the core value prop
- AUDIT.md = technical gap analysis; keep it dense, not duplicated in README

## Refinement workflow

1. **Audit** — Read README.md, CONTRIBUTING.md, AGENTS.md against actual repo (patches, Constants.kt versions, latest release tag).
2. **Structure** — Apply crafting-effective-readmes “Reviewing/refreshing” checklist; compare to `reference.md` ideal OSS tool layout.
3. **Deslop pass 1** — Run deslop-ai-lint-skill on README + CONTRIBUTING; fix Strong/Mixed slop (hedging, “leverage”, em-dash spam, vapid intros).
4. **Deslop pass 2** — anti-ai-slop-writing + anti-slop for tone (direct, technical, no “In today's landscape”).
5. **Verify facts** — Release version, supported Health versions, patch names match `KnoxBypassPatch.kt` / `AccountBypassPatch.kt` vals.
6. **Diff check** — No duplicate “Add to Morphe” sections; quick start in first ~200 words after header.

## Files in scope

| File | Priority |
|------|----------|
| `README.md` | High — user-facing |
| `CONTRIBUTING.md` | Medium — contributors |
| `AGENTS.md` | Medium — agent handoff; update release/version lines |
| `AUDIT.md` | Low — only if facts stale |
| `.github/ISSUE_TEMPLATE/*` | Low — already Knox-specific |

## Optional follow-ups (ask user)

- Add root `llms.txt` pointing to README, AUDIT, patch source URL
- PR to [Awesome-ReVanced](https://github.com/Jman-Github/Awesome-ReVanced) patch list
- Register in [ReVanced-Patch-Bundles](https://github.com/Jman-Github/ReVanced-Patch-Bundles) for awesome-for-morphe indexing

## Output format

After refinement, report:

```text
DOCS REFINE REPORT
==================
Files changed: [...]
Slop fixes: [bullets]
Fact updates: [release version, Health versions, etc.]
Deferred: [llms.txt, Awesome-ReVanced PR, ...]
```
