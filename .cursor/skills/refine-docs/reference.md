# README layout for morphe-patches-samsung

Adapted from [maximosovsky/readme-guidelines](https://github.com/maximosovsky/readme-guidelines) and Morphe OEM patch repos. **Not a generic SaaS template.**

## Recommended section order

1. **Title + tagline + badges** — Release, Morphe (functional only; skip Discord/star-history)
2. **Add to Morphe** — centered one-click link + manual Remote URL (pairip pattern)
3. **Who this is for** — 3 bullets max (Knox 0x1, unrooted, Health blocked)
4. **Patches** — auto-generated block (`PATCHES_START` / `PATCHES_END`)
5. **Quick start** — numbered steps, copy-paste adb version check
6. **How patches work** — `<details>` collapsibles (Knox table + account table)
7. **Troubleshooting** — OOM + login only
8. **Important notes** — auto-update, cloud restore, Wearable
9. **Related projects** — table linking SamsungAppsPatcher, Morphe docs, jadx-morphe
10. **Contributing / Building / License**

## Anti-patterns for this repo

| Avoid | Why |
|-------|-----|
| Hero GIF / demo video | No visual demo yet; don't fake it |
| 800–1500 word marketing essay | Users need steps, not SEO padding |
| Emoji on every `##` heading | Morphe community uses some emoji; keep moderate |
| Duplicating AUDIT.md in README | Link instead |
| “Revolutionary / seamless / powerful” | AI slop; say what patches stub |
| Three-item feature lists where two suffice | Common slop pattern |

## Slop patterns to ban (docs pass)

From deslop-ai-lint + anti-slop ecosystems:

- **Vocabulary:** leverage, delve, unlock, harness, seamlessly, robust, comprehensive (without specifics)
- **Structure:** “It's not X, it's Y”; “In today's …”; “Furthermore/Moreover” openers
- **Formatting:** decorative emoji rows; excessive bold; em-dash chains
- **Endings:** “The possibilities are endless”; vague calls to action

## Good tone examples

**Bad:** “This powerful patch suite seamlessly unlocks Samsung Health on compromised devices.”

**Good:** “Dex-only Morphe patches for Samsung Health on Knox-tripped (0x1) phones — patch on-device, no PC.”

**Bad:** “Furthermore, it is important to note that both patches should be enabled.”

**Good:** “Enable both patches (defaults). Login fails if the account patch is off.”

## llms.txt sketch (optional)

```markdown
# Samsung Health Morphe Patches

> On-device Morphe patches for Samsung Health on Knox 0x1 Samsung phones.

- [README](https://github.com/bigyank/morphe-patches-samsung/blob/main/README.md): Quick start, troubleshooting
- [AUDIT](https://github.com/bigyank/morphe-patches-samsung/blob/main/AUDIT.md): vs SamsungAppsPatcher
- [Add patch source](https://morphe.software/add-source?github=bigyank/morphe-patches-samsung)
- [Latest release](https://github.com/bigyank/morphe-patches-samsung/releases/latest)
```
