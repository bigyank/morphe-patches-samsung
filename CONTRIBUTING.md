# Bigyank Samsung Morphe Patches

Thank you for contributing.

## Adding a new Samsung Health version

1. Download the target APK from APKMirror.
2. Decompile with `apktool d shealth.apk`.
3. Verify Knox-related classes still exist at the paths in `Fingerprints.kt`.
4. Add `AppTarget(version = "x.y.z")` to `Constants.kt`.
5. Test patch with Morphe CLI or Manager.
6. Open a PR with device model, Knox status, and test results.

## Commit messages

Use [conventional commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `bump:`) — releases are automated.
