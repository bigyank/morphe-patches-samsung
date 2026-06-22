package app.bigyank.patches.shealth

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.bigyank.patches.shared.Constants.COMPATIBILITY_SHEALTH

/**
 * Bypass Samsung Health Knox/root/warranty/SAK integrity checks.
 *
 * Ported from SamsungAppsPatcher (apply_shealth_knox_bypass.py) for Knox-tripped
 * Samsung phones without root. Tested on Samsung Health 6.32.0.001.
 */
@Suppress("unused")
val disableKnoxIntegrityChecksPatch = bytecodePatch(
    name = "Disable Knox integrity checks",
    description = "Bypass Knox, root, warranty bit, and SAK checks so Samsung Health runs on Knox-tripped devices (0x1) without root.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_SHEALTH)

    execute {
        KnoxAdapterCheckKnoxCompromisedExternalFingerprint.method.apply {
            implementation?.let { impl ->
                removeInstructions(0, impl.instructions.count())
                addInstructions(0, "const/4 v0, 0x0\nreturn-object v0")
            }
        }
        KnoxAdapterCheckKnoxCompromisedInternalFingerprint.method.apply {
            implementation?.let { impl ->
                removeInstructions(0, impl.instructions.count())
                addInstructions(0, "const/4 v0, 0x0\nreturn v0")
            }
        }
        listOf(
            KnoxAdapterIsKnoxAvailableFingerprint,
            KnoxAdapterIsKnoxAvailableCoreFingerprint,
            KnoxAdapterIsAksSakMandatoryFingerprint,
            KnoxAdapterShouldUseKnoxFingerprint,
            KnoxAdapterIsSupportedTimaVersionFingerprint,
            KnoxControlIsKnoxAvailableFingerprint,
            IKnoxControlProxyIsKnoxAvailableFingerprint,
            SakCheckerIsSupportedFingerprint,
        ).forEach { fingerprint ->
            fingerprint.method.apply {
                implementation?.let { impl ->
                    removeInstructions(0, impl.instructions.count())
                    addInstructions(0, "const/4 v0, 0x0\nreturn v0")
                }
            }
        }
        IcccAdapterCheckKnoxCompromisedFingerprint.method.apply {
            implementation?.let { impl ->
                removeInstructions(0, impl.instructions.count())
                addInstructions(0, "const/4 v0, 0x0\nreturn v0")
            }
        }
        KnoxControlCheckKnoxCompromisedFingerprint.method.apply {
            implementation?.let { impl ->
                removeInstructions(0, impl.instructions.count())
                addInstructions(0, "const/4 v0, 0x0\nreturn-object v0")
            }
        }
        KnoxControlCheckWarrantyBitFingerprint.method.apply {
            implementation?.let { impl ->
                removeInstructions(0, impl.instructions.count())
                addInstructions(0, "const/4 v0, 0x0\nreturn v0")
            }
        }
        SamsungSakCheckerImplFingerprint.matchAllOrNull()?.forEach { match ->
            match.method.apply {
                implementation?.let { impl ->
                    removeInstructions(0, impl.instructions.count())
                    addInstructions(0, "const/4 v0, 0x0\nreturn v0")
                }
            }
        }
    }
}
