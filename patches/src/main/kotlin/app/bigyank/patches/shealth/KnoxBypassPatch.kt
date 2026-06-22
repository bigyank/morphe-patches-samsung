package app.bigyank.patches.shealth

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.bigyank.patches.shared.Constants.COMPATIBILITY_SHEALTH
import com.android.tools.smali.dexlib2.iface.Method

private fun Method.stubWith(smaliBody: String) {
    implementation?.let { impl ->
        removeInstructions(0, impl.instructions.count())
        addInstructions(0, smaliBody)
    }
}

private fun Method.stubReturnBoolean(value: Boolean) {
    stubWith(if (value) "const/4 v0, 0x1\nreturn v0" else "const/4 v0, 0x0\nreturn v0")
}

private fun Method.stubReturnInt(value: Int) {
    stubWith("const/4 v0, 0x0\nreturn v0")
}

private fun Method.stubReturnNullReference() {
    stubWith("const/4 v0, 0x0\nreturn-object v0")
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
        KnoxAdapterCheckKnoxCompromisedInternalFingerprint.method.stubReturnInt(0)
        KnoxAdapterIsKnoxAvailableFingerprint.method.stubReturnBoolean(false)
        KnoxAdapterIsKnoxAvailableCoreFingerprint.method.stubReturnBoolean(false)
        KnoxAdapterIsAksSakMandatoryFingerprint.method.stubReturnBoolean(false)
        KnoxAdapterShouldUseKnoxFingerprint.method.stubReturnBoolean(false)
        KnoxAdapterIsSupportedTimaVersionFingerprint.method.stubReturnBoolean(false)

        IcccAdapterCheckKnoxCompromisedFingerprint.method.stubReturnInt(0)

        KnoxControlIsKnoxAvailableFingerprint.method.stubReturnBoolean(false)
        KnoxControlCheckKnoxCompromisedFingerprint.method.stubReturnNullReference()
        KnoxControlCheckWarrantyBitFingerprint.method.stubReturnInt(0)
        IKnoxControlProxyIsKnoxAvailableFingerprint.method.stubReturnBoolean(false)

        SakCheckerIsSupportedFingerprint.method.stubReturnBoolean(false)

        val sakImplClass = classDefBy(SamsungSakCheckerImplFingerprint.definingClass!!)
        val sakImpl = SamsungSakCheckerImplFingerprint.match(sakImplClass)
        sakImpl.method.stubReturnBoolean(false)
    }
}
