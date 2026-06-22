# Bigyank Samsung Morphe Patches

Morphe patches for **Samsung Health** on Knox-tripped Samsung Galaxy phones — patch on your device with [Morphe Manager](https://morphe.software/), no PC required.

Community fork of the Knox bypass logic from [bigyank/SamsungAppsPatcher](https://github.com/bigyank/SamsungAppsPatcher) (originally [adil192/SamsungAppsPatcher](https://github.com/adil192/SamsungAppsPatcher)).

## Who this is for

- Samsung phone with **Knox tripped** (0x1) from past root/unlock
- Currently **unrooted** — KnoxPatch / LSPosed is not an option
- Samsung Health blocks you with Knox / integrity errors

## Patches

| Patch | Description |
|-------|-------------|
| **Disable Knox integrity checks** | Bypasses Knox availability, warranty bit, root detection, and SAK checks |

## Supported apps

| App | Package | Tested version |
|-----|---------|----------------|
| Samsung Health | `com.sec.android.app.shealth` | 6.32.0.001 |

Download the universal APK from [APKMirror](https://www.apkmirror.com/apk/samsung-electronics-co-ltd/s-health/).

## Quick start

1. Install [Morphe Manager](https://github.com/MorpheApp/morphe-manager/releases/latest).
2. Add this patch source:

   **https://github.com/bigyank/morphe-patches-samsung**

   Or click: [Add to Morphe](https://morphe.software/add-source?github=bigyank/morphe-patches-samsung)

3. Obtain Samsung Health APK (APKMirror or extract from your phone).
4. Enable **Disable Knox integrity checks** and patch.
5. **Uninstall** stock Samsung Health first (signature mismatch), then install the patched APK.

## Signing (required for Samsung Account)

Morphe patch bundles (`.mpp`) **cannot embed a keystore** — they only change bytecode. Morphe Manager **signs the output APK** after patching, the same way ReVanced Manager does.

### How Morphe handles signatures (YouTube, Reddit, etc.)

| Step | What happens |
|------|----------------|
| **Input APK** | Optional SHA-256 fingerprint check in patch metadata (`signatures` field) — verifies you picked the right *stock* APK build, not your signing key |
| **Patching** | Bytecode/resource changes only |
| **Output APK** | Signed with **Morphe Manager’s keystore** (default: alias `Morphe`, password `Morphe`) |

For YouTube/Reddit that is enough: no server checks the patch signature against a whitelist.

### Why Samsung Health is different

Samsung Health talks to **Samsung Account**, which maintains its own allowlist of which **app signing certificates** may call account APIs for `com.sec.android.app.shealth`.

| Signing key | Samsung Account login |
|-------------|----------------------|
| Default Morphe (`CN=Morphe`) | **Blocked** — logcat: `SignatureInfoDbHelper … mismatched` → `blocked application` |
| SamsungPatch keystore **only** (no account patch) | **Still blocked** — cert is correct but Health still calls `com.osp.app.signin` |
| SamsungPatch keystore **+** *Bypass Samsung Account signature check* patch | **Works** — matches Mac/PC SamsungAppsPatcher (keystore + `com.notsamsung.dummy` workaround) |

The original fork ships this keystore **in the repo** (not a secret — it is a shared community key so everyone’s patched apps can update each other and interop with watch plugins):

- [`keystore.jks`](https://github.com/adil192/SamsungAppsPatcher/blob/main/keystore.jks) — also copied to [`signing/keystore.jks`](signing/keystore.jks) in this repo
- [`ks-pass.txt`](https://github.com/adil192/SamsungAppsPatcher/blob/main/ks-pass.txt) — also in [`signing/ks-pass.txt`](signing/ks-pass.txt)

| Field | Value |
|-------|--------|
| **Keystore file** | `keystore.jks` |
| **Alias** | `key0` |
| **Store password** | `Uwa4V2FvQLVqgUhAN6c` |
| **Key password** | `Uwa4V2FvQLVqgUhAN6c` (same as store) |
| **Certificate** | `CN=SamsungPatch PatchCertificate` |
| **SHA-256** | `0E:0E:2D:7E:6C:5D:BA:…:3D:00:E7` |

### Import into Morphe Manager (one-time)

1. Copy `signing/keystore.jks` to your phone (or download from this repo / SamsungAppsPatcher).
2. Morphe Manager → **Settings** → **Advanced** → **Import signing keystore**.
3. Select the JKS file; enter alias **`key0`** and password **`Uwa4V2FvQLVqgUhAN6c`** for both store and key.
4. Enable **Bypass Samsung Account signature check** (on by default) — this is separate from signing.
5. Uninstall Samsung Health → patch again → install.

After import, all Morphe patches use SamsungPatch signing until you change the keystore.

The Mac/PC patcher also rewrites a few manifest/res XML entries; this Morphe patch covers the dex strings that fix Samsung Account login. It intentionally skips resource decoding so Morphe Manager does not OOM on Health’s ~300 MB APK.

If patching still fails with out-of-memory, close other apps and retry, or patch on a PC with [SamsungAppsPatcher](https://github.com/bigyank/SamsungAppsPatcher) and sideload the APK. **Use the same key for updates** so you can install over an existing patched Health without wiping data (same rule as ReVanced’s keystore docs).

### How the PC patcher also uses signatures (two layers)

The original repo does **two** signature-related things:

1. **APK signing** — `apksigner sign --ks keystore.jks` after rebuild (same keystore as above).
2. **In-app signature allowlists** — `*_custom_cert.patch` files embed the SamsungPatch cert hex so Health / Wearable plugins trust each other at runtime (`Signaturechecker.smali`, etc.). Health 6.32 Knox bypass uses a Python script instead of those legacy patches; Morphe Knox patches replace that layer. **Samsung Account blocking is separate** — it checks the **APK signing cert**, not those internal allowlists.

## Important notes

- **Disable auto-update** for Samsung Health after installing, or the store will replace the patched app.
- **Cloud restore** from Samsung account may hang on Knox 0x1 — cancel restore and use fresh local data.
- **Galaxy Wearable / Fit 3**: basic band sync often works with stock Wearable + patched Health; full wearable suite patching is still on the PC repo.
- Knox status remains tripped at the system level — this only fixes the Health app.

## PC alternative

Pre-built or script-patched APKs: [bigyank/SamsungAppsPatcher](https://github.com/bigyank/SamsungAppsPatcher)

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). To add a new Samsung Health version, test fingerprints and update `Constants.kt`.

## License

GPLv3 — see [LICENSE](LICENSE). Not affiliated with Samsung or Morphe.

<!-- PATCHES_START EXPANDED -->
> **[v1.0.6](https://github.com/bigyank/morphe-patches-samsung/releases/tag/v1.0.6)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;2 patches total
<details open>
<summary>📦 Samsung Health&nbsp;&nbsp;•&nbsp;&nbsp;2 patches</summary>
<br>

**🎯 Supported versions:**

| 6.32.0.001 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Bypass Samsung Account signature check](#bypass-samsung-account-signature-check) | Routes Samsung Account SDK calls away from com.osp.app.signin so login works on patched APKs. Matches the Mac/PC SamsungAppsPatcher build (required alongside the SamsungPatch keystore). |  |
| [Disable Knox integrity checks](#disable-knox-integrity-checks) | Bypass Knox, root, warranty bit, and SAK checks so Samsung Health runs on Knox-tripped devices (0x1) without root. |  |

</details>

<!-- PATCHES_END -->

### How to use these patches

Click here to add these patches to Morphe: https://morphe.software/add-source?github=bigyank/morphe-patches-samsung

Or manually add this repository url as a patch source in Morphe: https://github.com/bigyank/morphe-patches-samsung

## Building

Requires Java 21 and a GitHub token with `read:packages` for Morphe Maven packages.

```bash
export GITHUB_TOKEN="$(gh auth token)"
./gradlew :patches:buildAndroid generatePatchesList
```

Output: `patches/build/libs/patches-*.mpp`
