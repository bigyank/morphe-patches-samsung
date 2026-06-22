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

#### A list of your patches will be automatically shown here after your first patches release is created.

&nbsp;

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
