package app.bigyank.patches.shealth

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.bigyank.patches.shared.Constants.COMPATIBILITY_SHEALTH
import com.android.tools.smali.dexlib2.AccessFlags

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
        fun returnRegisterName(accessFlags: Int, parameterCount: Int, registerCount: Int): String {
            if (AccessFlags.STATIC.isSet(accessFlags)) {
                return "v$parameterCount"
            }
            return if (registerCount <= 1) "p0" else "v0"
        }

        KnoxAdapterCheckKnoxCompromisedExternalFingerprint.method.apply {
            implementation?.let { impl ->
                removeInstructions(0, impl.instructions.count())
                val reg = returnRegisterName(accessFlags, parameters.size, impl.registerCount)
                addInstructions(0, "const/4 $reg, 0x0\nreturn-object $reg")
            }
        }
        KnoxAdapterCheckKnoxCompromisedInternalFingerprint.method.apply {
            implementation?.let { impl ->
                removeInstructions(0, impl.instructions.count())
                val reg = returnRegisterName(accessFlags, parameters.size, impl.registerCount)
                addInstructions(0, "const/4 $reg, 0x0\nreturn $reg")
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
                    val reg = returnRegisterName(accessFlags, parameters.size, impl.registerCount)
                    addInstructions(0, "const/4 $reg, 0x0\nreturn $reg")
                }
            }
        }
        IcccAdapterCheckKnoxCompromisedFingerprint.method.apply {
            implementation?.let { impl ->
                removeInstructions(0, impl.instructions.count())
                val reg = returnRegisterName(accessFlags, parameters.size, impl.registerCount)
                addInstructions(0, "const/4 $reg, 0x0\nreturn $reg")
            }
        }
        KnoxControlCheckKnoxCompromisedFingerprint.method.apply {
            implementation?.let { impl ->
                removeInstructions(0, impl.instructions.count())
                val reg = returnRegisterName(accessFlags, parameters.size, impl.registerCount)
                addInstructions(0, "const/4 $reg, 0x0\nreturn-object $reg")
            }
        }
        KnoxControlCheckWarrantyBitFingerprint.method.apply {
            implementation?.let { impl ->
                removeInstructions(0, impl.instructions.count())
                val reg = returnRegisterName(accessFlags, parameters.size, impl.registerCount)
                addInstructions(0, "const/4 $reg, 0x0\nreturn $reg")
            }
        }

        // Only patch c6r — sl9 also implements SamsungSakChecker but is a shared
        // synthetic delegate with .locals 0; rewriting it with v0 crashes at startup.
        SamsungSakCheckerC6rFingerprint.method.apply {
            implementation?.let { impl ->
                removeInstructions(0, impl.instructions.count())
                val reg = returnRegisterName(accessFlags, parameters.size, impl.registerCount)
                addInstructions(0, "const/4 $reg, 0x0\nreturn $reg")
            }
        }
    }
}
