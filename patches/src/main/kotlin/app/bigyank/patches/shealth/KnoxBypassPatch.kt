package app.bigyank.patches.shealth

import app.morphe.patcher.patch.bytecodePatch
import app.bigyank.patches.shared.Constants.COMPATIBILITY_SHEALTH
import app.morphe.util.returnEarly

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

    finalize {
        // KnoxAdapter
        KnoxAdapterCheckKnoxCompromisedExternalFingerprint.method.returnEarly(null)
        KnoxAdapterCheckKnoxCompromisedInternalFingerprint.method.returnEarly(0)
        KnoxAdapterIsKnoxAvailableFingerprint.method.returnEarly(false)
        KnoxAdapterIsKnoxAvailableCoreFingerprint.method.returnEarly(false)
        KnoxAdapterIsAksSakMandatoryFingerprint.method.returnEarly(false)
        KnoxAdapterShouldUseKnoxFingerprint.method.returnEarly(false)
        KnoxAdapterIsSupportedTimaVersionFingerprint.method.returnEarly(false)

        // IcccAdapter
        IcccAdapterCheckKnoxCompromisedFingerprint.method.returnEarly(0)

        // KnoxControl + binder proxy
        KnoxControlIsKnoxAvailableFingerprint.method.returnEarly(false)
        KnoxControlCheckKnoxCompromisedFingerprint.method.returnEarly(null)
        KnoxControlCheckWarrantyBitFingerprint.method.returnEarly(0)
        IKnoxControlProxyIsKnoxAvailableFingerprint.method.returnEarly(false)

        // Samsung Attestation Key (SAK)
        SakCheckerIsSupportedFingerprint.method.returnEarly(false)
        SamsungSakCheckerImplFingerprint.method.returnEarly(false)
    }
}
