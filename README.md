<div align="center">

# Samsung Health Morphe Patches

**Dex-only Morphe patches for Samsung Health on Knox-tripped (0x1) Samsung phones — no PC, no root, no custom keystore.**

[![Release](https://img.shields.io/github/v/release/bigyank/morphe-patches-samsung?style=for-the-badge&logo=github)](https://github.com/bigyank/morphe-patches-samsung/releases/latest)
[![Morphe](https://img.shields.io/badge/Morphe-Patches-8B5CF6?style=for-the-badge)](https://morphe.software)

</div>

---

## Add to Morphe

<div align="center">

### 👉 [Add Samsung Health Patches to Morphe Manager](https://morphe.software/add-source?github=bigyank/morphe-patches-samsung) 👈

</div>

Open that link on the phone where Morphe is installed — it adds this patch source automatically.

**Or add manually:** Morphe Manager → patch sources **+** → **Remote** → paste:

```
https://github.com/bigyank/morphe-patches-samsung
```

> [!NOTE]
> All patches are **dex-only** (bytecode). No apktool, no PC, no Termux scripts. Morphe rewrites DEX on-device. Samsung Health is ~300 MB — set **Process runtime** to **1280 MB** before patching (see [Quick start](#quick-start)).

---

## Who this is for

- Samsung phone with **Knox tripped** (0x1) from past root/unlock
- Currently **unrooted** — [KnoxPatch](https://github.com/salvogiangri/KnoxPatch) / LSPosed is not an option
- Samsung Health blocks you with Knox / integrity errors, or login fails after patching

---

## Patches

<!-- PATCHES_START EXPANDED -->
> **[v1.0.16](https://github.com/bigyank/morphe-patches-samsung/releases/tag/v1.0.16)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;2 patches total
<details open>
<summary>📦 Samsung Health&nbsp;&nbsp;•&nbsp;&nbsp;2 patches</summary>
<br>

**🎯 Supported versions:**

| 6.32.0.001 | 6.31.3.013 |
| :---: | :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Bypass Samsung Account provider checks](#bypass-samsung-account-provider-checks) | Replaces com.osp.app.signin with com.notsamsung.dummy in dex and routes account lookups through Android AccountManager instead of Samsung Account's provider. |  |
| [Disable Knox integrity checks](#disable-knox-integrity-checks) | Bypass Knox, root, warranty bit, and SAK checks so Samsung Health runs on Knox-tripped devices (0x1) without root. |  |

</details>

<!-- PATCHES_END -->

Both patches are **on by default**. Use a [supported Health version](#patches) (latest: **6.32.0.001**).

---

## Quick start

1. Install [Morphe Manager](https://github.com/MorpheApp/morphe-manager/releases/latest).
2. [Add this patch source](#add-to-morphe).
3. Morphe Manager → **Settings** → **Advanced** → **Process runtime** → enable and set **1280 MB**.
4. Obtain Samsung Health APK ([APKMirror](https://www.apkmirror.com/apk/samsung-electronics-co-ltd/s-health/) or extract from your phone).
5. Enable **both patches** (defaults) and patch.
6. **Uninstall** stock Samsung Health first (signature mismatch), then install the patched APK.

**Signing:** Morphe's default keystore works — no custom JKS import. Login and sync confirmed on Knox 0x1 devices.

**Version check** (Galaxy Store builds may differ from APKMirror):

```bash
adb shell dumpsys package com.sec.android.app.shealth | grep versionName
```

If yours is not listed in the patch table, open an issue with the version string.

---

## How the patches work

<details>
<summary><b>Disable Knox integrity checks</b> — SDK stubs + content-scanned OOBE gates</summary>

<br>

| Layer | What it does |
| :--- | :--- |
| **SDK fingerprints** | Stubs 17 stable Knox/SAK methods (`KnoxAdapter`, `IcccAdapter`, `KnoxControl`, `SakChecker`, etc.) to return safe values |
| **OOBE dex scan** | Finds Knox popup launchers, `HomeAppCloseActivity` warranty-bit checks, and `KnoxHandlerViewModel` flags by string/type patterns — obfuscated class names change every release |
| **Root file scan** | Stubs static `(File)Z` methods containing the Kotlin synthetic string `$this$isRooted` |

Same approach as the PC-side [SamsungAppsPatcher](https://github.com/bigyank/SamsungAppsPatcher) Python scanner, ported to Morphe bytecode patches. Details: [AUDIT.md](./AUDIT.md).

</details>

<details>
<summary><b>Bypass Samsung Account provider checks</b> — login without custom signing cert</summary>

<br>

Samsung Health calls Samsung Account's `AccountManagerProvider`, which checks the **APK signing certificate** against an allowlist. Patched Health gets blocked (`SignatureInfoDbHelper … mismatched` in logcat).

| Layer | What it does |
| :--- | :--- |
| **String replacement** | Every `com.osp.app.signin` const-string and static field default → `com.notsamsung.dummy` |
| **Provider stubs** | Disable the provider path; redirect `getSamsungAccountId` to `AccountManager.getAccountsByType("com.osp.app.signin")` |

Manifest/res may still reference `com.osp.app.signin` in sync-adapter XML — runtime provider calls are what matter for login.

</details>

---

## Troubleshooting

### Morphe stuck looping / out of memory

1. **Force-stop** Morphe Manager.
2. Set process runtime to **1280 MB** (see [Quick start](#quick-start)).
3. Close other apps before patching.
4. Use the [latest release](https://github.com/bigyank/morphe-patches-samsung/releases/latest) — older versions that decoded resources could OOM-loop on device.

### Login still fails

- Confirm **both** patches are enabled.
- Check logcat for `AccountManagerProvider` / `SignatureInfoDbHelper` — if those lines appear, the account patch did not apply; update to the [latest release](https://github.com/bigyank/morphe-patches-samsung/releases/latest).
- Uninstall stock Health before installing the patched APK.

---

## Important notes

- **Disable auto-update** for Samsung Health after installing.
- **Cloud restore** from Samsung account may hang on Knox 0x1 — cancel restore and use fresh local data.
- **Galaxy Wearable / Fit 3**: basic band sync often works with stock Wearable + patched Health.
- Knox status remains tripped at the system level — this only fixes the Health app.

> [!WARNING]
> Do not re-enable manifest/resource patching for account bypass — it causes OOM on ~300 MB Health APKs during on-device patching.

---

## Related projects

| Project | Relationship |
| :--- | :--- |
| [SamsungAppsPatcher](https://github.com/bigyank/SamsungAppsPatcher) | PC apktool predecessor — Galaxy Wearable + watch plugin patches still live here |
| [Morphe documentation](https://github.com/MorpheApp/morphe-documentation) | Patching guide, FAQ, troubleshooting |
| [Awesome for Morphe](https://nvbangg.github.io/awesome-for-morphe) | Curated Morphe patch index |
| [jadx-morphe](https://github.com/hoo-dles/jadx-morphe) | JADX plugin — useful for finding fingerprints on new Health builds |

Full gap analysis vs SamsungAppsPatcher: **[AUDIT.md](./AUDIT.md)**.

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

---

## Building

Requires Java 21 and a GitHub token with `read:packages` for Morphe Maven packages.

```bash
export GITHUB_TOKEN="$(gh auth token)"
./gradlew :patches:buildAndroid generatePatchesList
```

Output: `patches/build/libs/patches-*.mpp`

---

## License

GPLv3 — see [LICENSE](LICENSE) and [NOTICE](NOTICE). Not affiliated with Samsung or Morphe.
