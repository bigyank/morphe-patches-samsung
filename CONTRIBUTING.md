# Contributing

## Adding a new Samsung Health version

1. Download the target APK from APKMirror.
2. Decompile with `apktool d shealth.apk` (or use Morphe's dex dump).
3. Verify Knox-related classes still exist at the paths in `Fingerprints.kt`.
4. Verify Samsung Account provider methods (`SamsungAccountUtils`, `com.osp.app.signin.sasdk.common.Util`) still match the fingerprints.
5. Add `AppTarget(version = "x.y.z")` to `Constants.kt`.
6. Test both patches with Morphe Manager on a Knox 0x1 device — confirm launch, login, and sync.
7. Open a PR with device model, Knox status, and test results.

## Patch layout

```
patches/src/main/kotlin/app/bigyank/patches/shealth/
├── KnoxBypassPatch.kt       # disableKnoxIntegrityChecksPatch
├── AccountBypassPatch.kt    # bypassSamsungAccountSignatureCheckPatch
├── Fingerprints.kt          # method fingerprints for both patches
├── BytecodeStubUtils.kt     # shared stub helpers (try/catch-safe body replacement)
└── SigninPackageReplacer.kt # dex string/field com.osp.app.signin → com.notsamsung.dummy
```

## Patch design notes

The Knox patch stubs 14 stable SDK methods. Obfuscated OOBE/root helpers are intentionally out of scope — the SDK stubs are sufficient for Health 6.32.0.001 on-device. If a future version needs more, add fingerprints rather than broad dex scans.

The account patch is **dex-only** by design. Do not re-add `resourcePatch` for manifest/res replacement — it causes OOM on ~300 MB Health APKs during on-device patching.

## Commit messages

Use [conventional commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `refactor:`, `bump:`) — releases are automated via semantic-release.
