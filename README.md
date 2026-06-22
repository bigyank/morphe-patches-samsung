# Bigyank Samsung Morphe Patches

Morphe patches for **Samsung Health** on Knox-tripped Samsung Galaxy phones — patch directly on your device with [Morphe Manager](https://morphe.software/).

## Who this is for

- Samsung phone with **Knox tripped** (0x1) from past root/unlock
- Currently **unrooted** — KnoxPatch / LSPosed is not an option
- Samsung Health blocks you with Knox / integrity errors, or login fails after patching

## Patches

Both patches are **on by default** and should stay enabled for Samsung Health 6.32.0.001.

| Patch | What it does |
|-------|----------------|
| **Disable Knox integrity checks** | Stubs Knox availability, warranty bit, root detection, and SAK checks (14 SDK methods) |
| **Bypass Samsung Account provider checks** | Replaces `com.osp.app.signin` → `com.notsamsung.dummy` in dex and routes account lookups through Android `AccountManager` instead of Samsung Account's signature-checked provider |

## Supported apps

| App | Package | Tested version |
|-----|---------|----------------|
| Samsung Health | `com.sec.android.app.shealth` | 6.32.0.001 |

Download the universal APK from [APKMirror](https://www.apkmirror.com/apk/samsung-electronics-co-ltd/s-health/).

## Quick start

1. Install [Morphe Manager](https://github.com/MorpheApp/morphe-manager/releases/latest).
2. Add this patch source: **https://github.com/bigyank/morphe-patches-samsung**  
   Or click: [Add to Morphe](https://morphe.software/add-source?github=bigyank/morphe-patches-samsung)
3. Morphe Manager → **Settings** → **Advanced** → **Process runtime** → enable and set **1280 MB** (Samsung Health is ~300 MB; the default 256 MB can OOM during patching).
4. Obtain Samsung Health APK (APKMirror or extract from your phone).
5. Enable **both patches** (defaults) and patch.
6. **Uninstall** stock Samsung Health first (signature mismatch), then install the patched APK.

**Signing:** the default Morphe keystore works — no custom JKS import required. Login and sync were confirmed on Knox 0x1 devices with Morphe's built-in signing key.

## How the account bypass works

Samsung Health normally calls Samsung Account's `AccountManagerProvider`, which checks the **APK signing certificate** against an allowlist. Patched Health gets blocked (`SignatureInfoDbHelper … mismatched` in logcat).

The account patch fixes this in two layers (dex-only — no manifest/resource decode, so Morphe can patch on-device without OOM):

1. **String replacement** — every `com.osp.app.signin` const-string and static field default becomes `com.notsamsung.dummy`.
2. **Provider stubs** — disable the provider path and redirect `getSamsungAccountId` to `AccountManager.getAccountsByType("com.osp.app.signin")`, which reads the account already on the device.

Manifest/res may still contain `com.osp.app.signin` in sync-adapter XML; that is fine — the runtime provider calls are what matter for login.

## Troubleshooting

### Morphe stuck looping / out of memory

1. **Force-stop** Morphe Manager.
2. Set process runtime to **1280 MB** (see quick start).
3. Close other apps before patching.
4. Use the latest release — older versions that decoded resources could OOM-loop on device.

### Login still fails

- Confirm **both** patches are enabled.
- Check logcat for `AccountManagerProvider` / `SignatureInfoDbHelper` — if those lines appear, the account patch did not apply; update to the latest release.
- Uninstall stock Health before installing the patched APK.

## Important notes

- **Disable auto-update** for Samsung Health after installing.
- **Cloud restore** from Samsung account may hang on Knox 0x1 — cancel restore and use fresh local data.
- **Galaxy Wearable / Fit 3**: basic band sync often works with stock Wearable + patched Health.
- Knox status remains tripped at the system level — this only fixes the Health app.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

GPLv3 — see [LICENSE](LICENSE). Not affiliated with Samsung or Morphe.

<!-- PATCHES_START EXPANDED -->
> **[v1.0.11](https://github.com/bigyank/morphe-patches-samsung/releases/tag/v1.0.11)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;2 patches total
<details open>
<summary>📦 Samsung Health&nbsp;&nbsp;•&nbsp;&nbsp;2 patches</summary>
<br>

**🎯 Supported versions:**

| 6.32.0.001 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Bypass Samsung Account provider checks](#bypass-samsung-account-provider-checks) | Replaces com.osp.app.signin with com.notsamsung.dummy in dex and routes account lookups through Android AccountManager instead of Samsung Account's provider. |  |
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
