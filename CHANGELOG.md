## [1.0.14](https://github.com/bigyank/morphe-patches-samsung/compare/v1.0.13...v1.0.14) (2026-06-22)

### 🚀 Updated App Support

* **shealth:** add OOBE Knox stubs and extra SDK integrity bypasses ([7d03ded](https://github.com/bigyank/morphe-patches-samsung/commit/7d03ded0372037fb859e2d427fbce4d48e6a2a9d))

## [1.0.13](https://github.com/bigyank/morphe-patches-samsung/compare/v1.0.12...v1.0.13) (2026-06-22)

### 🚀 Updated App Support

* **shealth:** add Samsung Health 6.31.3.013 compatibility target ([d5fccf6](https://github.com/bigyank/morphe-patches-samsung/commit/d5fccf6e8dccd1cff97e52ebd812496f5493b8db))

## [1.0.12](https://github.com/bigyank/morphe-patches-samsung/compare/v1.0.11...v1.0.12) (2026-06-22)

### 🐛 Bug Fixes

* add BytecodePatchContext to stub helper functions ([33ae16d](https://github.com/bigyank/morphe-patches-samsung/commit/33ae16dcccc574b9ac5f033c208b7a8918e59b5e))
* use BytecodePatchContext extensions for stub helpers ([8cea61c](https://github.com/bigyank/morphe-patches-samsung/commit/8cea61c0d78d8b3d50ba91e0287e6d02a36fd3d7))
* use Morphe MutableMethod APIs in BytecodeStubUtils ([3dcf337](https://github.com/bigyank/morphe-patches-samsung/commit/3dcf33731e54e30dd9b2c407a9f38f0c526ed396))

## [1.0.11](https://github.com/bigyank/morphe-patches-samsung/compare/v1.0.10...v1.0.11) (2026-06-22)

### 🐛 Bug Fixes

* compile account provider stubs using reflection like Knox patch ([8f6447d](https://github.com/bigyank/morphe-patches-samsung/commit/8f6447d9df4517fe7529fc3d2bbc38f0be4f2993))
* dex-only account bypass with provider stubs to avoid OOM loop ([479c339](https://github.com/bigyank/morphe-patches-samsung/commit/479c339bf4543e9ae9fc39fc3c139e143c7236f5))

## [1.0.10](https://github.com/bigyank/morphe-patches-samsung/compare/v1.0.9...v1.0.10) (2026-06-22)

### 🐛 Bug Fixes

* restore manifest/res replacement for Samsung Account bypass ([fefc9e9](https://github.com/bigyank/morphe-patches-samsung/commit/fefc9e9df0e73494ab8a71582c13f9a4defae49f))

## [1.0.9](https://github.com/bigyank/morphe-patches-samsung/compare/v1.0.8...v1.0.9) (2026-06-22)

### 🐛 Bug Fixes

* compile account patch by storing replacement strings during scan ([3f4481b](https://github.com/bigyank/morphe-patches-samsung/commit/3f4481bf486c1e630d2c8acfa98fb4e2ccae22d1))
* replace all Samsung Account package strings in dex like Mac patcher ([70c353d](https://github.com/bigyank/morphe-patches-samsung/commit/70c353dd78718226785d902823d38d206e2df5f4))

## [1.0.8](https://github.com/bigyank/morphe-patches-samsung/compare/v1.0.7...v1.0.8) (2026-06-22)

### 🐛 Bug Fixes

* scan dex read-only before mutating classes for account bypass ([c1536f8](https://github.com/bigyank/morphe-patches-samsung/commit/c1536f8cefae185ef9628bc7e41012eb77e46807))

## [1.0.7](https://github.com/bigyank/morphe-patches-samsung/compare/v1.0.6...v1.0.7) (2026-06-22)

### 🐛 Bug Fixes

* make Samsung Account bypass dex-only to prevent Morphe OOM ([535550f](https://github.com/bigyank/morphe-patches-samsung/commit/535550f01fe06d992d038b8fbf6d8fcf311bf1ec))

## [1.0.6](https://github.com/bigyank/morphe-patches-samsung/compare/v1.0.5...v1.0.6) (2026-06-22)

### 🐛 Bug Fixes

* add Samsung Account signature bypass for patched Health login ([4e34bc4](https://github.com/bigyank/morphe-patches-samsung/commit/4e34bc4857c2bffb27b000edc6dbaa87404cd258))
* iterate mutableClass.methods directly in account bypass patch ([03a4b47](https://github.com/bigyank/morphe-patches-samsung/commit/03a4b47e14723c15da4e412d033fa37acebd7d5c))
* resolve Samsung Account patch compile error in CI ([83b19f4](https://github.com/bigyank/morphe-patches-samsung/commit/83b19f44693debd90de113286d345b601a390589))

## [1.0.5](https://github.com/bigyank/morphe-patches-samsung/compare/v1.0.4...v1.0.5) (2026-06-22)

### 🐛 Bug Fixes

* build Mac-style stubs with dexlib2 instruction builders ([b044de2](https://github.com/bigyank/morphe-patches-samsung/commit/b044de2922712a0297d4760e15f7ed2afdfbdfa7))
* compile Mac-style stubs inside execute context ([65716f4](https://github.com/bigyank/morphe-patches-samsung/commit/65716f494272a378b48f7db496651152644fa7c3))
* match macOS apktool stubs to restore account sign-in ([4f812a2](https://github.com/bigyank/morphe-patches-samsung/commit/4f812a2c6c7405eb6b40aa96a99db0da24b82068))

## [1.0.4](https://github.com/bigyank/morphe-patches-samsung/compare/v1.0.3...v1.0.4) (2026-06-22)

### 🐛 Bug Fixes

* clear try/catch handlers when stubbing Knox methods ([96736a2](https://github.com/bigyank/morphe-patches-samsung/commit/96736a2b6e3322d9d54c72952b75ffc462130d33))
* replace method bodies fully to clear try/catch handlers ([559a2c8](https://github.com/bigyank/morphe-patches-samsung/commit/559a2c89996b1b742be935a6a5b8f2c38a7c4593))

## [1.0.3](https://github.com/bigyank/morphe-patches-samsung/compare/v1.0.2...v1.0.3) (2026-06-22)

### 🐛 Bug Fixes

* avoid sl9 SAK stub that caused startup VerifyError ([c8874a8](https://github.com/bigyank/morphe-patches-samsung/commit/c8874a8dcddc7e6ed29f5e90f90a70246e0ab2da))
* inline register-aware stubs on MutableMethod apply receiver ([59fffa5](https://github.com/bigyank/morphe-patches-samsung/commit/59fffa50f6240247159a07e3bf069e14ffac1dab))
* keep register-safe stubs inside patch execute context ([f37f4aa](https://github.com/bigyank/morphe-patches-samsung/commit/f37f4aa295762353f2212934793a8e635726cc6a))

## [1.0.2](https://github.com/bigyank/morphe-patches-samsung/compare/v1.0.1...v1.0.2) (2026-06-22)

### 🐛 Bug Fixes

* correct isKnoxAvailableCore access flags for Health 6.32 ([0804fa6](https://github.com/bigyank/morphe-patches-samsung/commit/0804fa65269e37b15261c39697773e51a722f447))
* restore inline MutableMethod stubs for Morphe compile ([9bf0a06](https://github.com/bigyank/morphe-patches-samsung/commit/9bf0a06fa3fc3baf993e2862196f8f7113bbc4f4))

## [1.0.1](https://github.com/bigyank/morphe-patches-samsung/compare/v1.0.0...v1.0.1) (2026-06-22)

### 🐛 Bug Fixes

* drop dev backmerge from semantic-release config ([d900692](https://github.com/bigyank/morphe-patches-samsung/commit/d90069234222e823cd0a35033c951073b73efba5))

## 1.0.0 (2026-06-22)

### 🐛 Bug Fixes

* add Morphe 1.3.x compatible PatchListGenerator ([d3bf0eb](https://github.com/bigyank/morphe-patches-samsung/commit/d3bf0ebd5f24c11d53243e4f590a7b24afa4a1d2))
* align Morphe plugin API with 1.3.x for CI build ([3cd5f2d](https://github.com/bigyank/morphe-patches-samsung/commit/3cd5f2d76d0935acf20b5edb934bcf2e1003f10d))
* downgrade Gradle to 8.14.3 for Morphe plugin compatibility ([80cdeda](https://github.com/bigyank/morphe-patches-samsung/commit/80cdedafb7615d19d590c3437da60b3acb5e0dce))
* emit patches-list.json in README-compatible format ([a5bf4f4](https://github.com/bigyank/morphe-patches-samsung/commit/a5bf4f4aaf49928b8a2776af11c0080757f1cc95))
* resolve Kotlin compile errors for Morphe 1.3.x ([342cd20](https://github.com/bigyank/morphe-patches-samsung/commit/342cd20a18b5fb366ae3f913deda999a97d6dcbb))

### ✨ New Features

* add Samsung Health Knox bypass Morphe patches ([dfff4db](https://github.com/bigyank/morphe-patches-samsung/commit/dfff4db1ece82a0e86d1aad067e6c42e47a264da))
