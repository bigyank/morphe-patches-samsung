package app.bigyank.patches.shealth

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.bigyank.patches.shared.Constants.COMPATIBILITY_SHEALTH
import com.android.tools.smali.dexlib2.iface.Method

private fun Method.stubReturnBooleanFalse() {
    implementation?.let { impl ->
        removeInstructions(0, impl.instructions.count())
        addInstructions(0, "const/4 v0, 0x0\nreturn v0")
    }
}

private fun Method.stubReturnIntZero() {
    implementation?.let { impl ->
        removeInstructions(0, impl.instructions.count())
        addInstructions(0, "const/4 v0, 0x0\nreturn v0")
    }
}

private fun Method.stubReturnNullReference() {
    implementation?.let { impl ->
        removeInstructions(0, impl.instructions.count())
        addInstructions(0, "const/4 v0, 0x0\nreturn-object v0")
    }
}

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
        KnoxAdapterCheckKnoxCompromisedExternalFingerprint.method.stubReturnNullReference()
        KnoxAdapterCheckKnoxCompromisedInternalFingerprint.method.stubReturnIntZero()

        listOf(
            KnoxAdapterIsKnoxAvailableFingerprint,
            KnoxAdapterIsKnoxAvailableCoreFingerprint,
            KnoxAdapterIsAksSakMandatoryFingerprint,
            KnoxAdapterShouldUseKnoxFingerprint,
            KnoxAdapterIsSupportedTimaVersionFingerprint,
            KnoxControlIsKnoxAvailableFingerprint,
            IKnoxControlProxyIsKnoxAvailableFingerprint,
            SakCheckerIsSupportedFingerprint,
        ).forEach { it.method.stubReturnBooleanFalse() }

        IcccAdapterCheckKnoxCompromisedFingerprint.method.stubReturnIntZero()
        KnoxControlCheckKnoxCompromisedFingerprint.method.stubReturnNullReference()
        KnoxControlCheckWarrantyBitFingerprint.method.stubReturnIntZero()

        // Health 6.32 ships multiple SamsungSakChecker impls (e.g. c6r, sl9).
        SamsungSakCheckerImplFingerprint.matchAllOrNull()?.forEach { match ->
            match.method.stubReturnBooleanFalse()
        }
    }
}
