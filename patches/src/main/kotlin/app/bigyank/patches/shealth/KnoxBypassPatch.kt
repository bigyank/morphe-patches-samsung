package app.bigyank.patches.shealth

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.bigyank.patches.shared.Constants.COMPATIBILITY_SHEALTH
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation

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
        fun stubRegisterCount(accessFlags: Int, parameterCount: Int, originalRegisterCount: Int): Int {
            if (AccessFlags.STATIC.isSet(accessFlags)) {
                return parameterCount + 1
            }
            return if (originalRegisterCount <= 1) 1 else 2
        }

        fun returnRegisterName(accessFlags: Int, parameterCount: Int, registerCount: Int): String {
            if (AccessFlags.STATIC.isSet(accessFlags)) {
                return "v$parameterCount"
            }
            return if (registerCount <= 1) "p0" else "v0"
        }

        KnoxAdapterCheckKnoxCompromisedExternalFingerprint.method.apply {
            val registerCount = stubRegisterCount(accessFlags, parameters.size, implementation?.registerCount ?: 1)
            val reg = returnRegisterName(accessFlags, parameters.size, registerCount)
            implementation = MutableMethodImplementation(registerCount).apply {
                addInstructions(0, "const/4 $reg, 0x0\nreturn-object $reg")
            }
        }
        KnoxAdapterCheckKnoxCompromisedInternalFingerprint.method.apply {
            val registerCount = stubRegisterCount(accessFlags, parameters.size, implementation?.registerCount ?: 1)
            val reg = returnRegisterName(accessFlags, parameters.size, registerCount)
            implementation = MutableMethodImplementation(registerCount).apply {
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
                val registerCount = stubRegisterCount(accessFlags, parameters.size, implementation?.registerCount ?: 1)
                val reg = returnRegisterName(accessFlags, parameters.size, registerCount)
                implementation = MutableMethodImplementation(registerCount).apply {
                    addInstructions(0, "const/4 $reg, 0x0\nreturn $reg")
                }
            }
        }
        IcccAdapterCheckKnoxCompromisedFingerprint.method.apply {
            val registerCount = stubRegisterCount(accessFlags, parameters.size, implementation?.registerCount ?: 1)
            val reg = returnRegisterName(accessFlags, parameters.size, registerCount)
            implementation = MutableMethodImplementation(registerCount).apply {
                addInstructions(0, "const/4 $reg, 0x0\nreturn $reg")
            }
        }
        KnoxControlCheckKnoxCompromisedFingerprint.method.apply {
            val registerCount = stubRegisterCount(accessFlags, parameters.size, implementation?.registerCount ?: 1)
            val reg = returnRegisterName(accessFlags, parameters.size, registerCount)
            implementation = MutableMethodImplementation(registerCount).apply {
                addInstructions(0, "const/4 $reg, 0x0\nreturn-object $reg")
            }
        }
        KnoxControlCheckWarrantyBitFingerprint.method.apply {
            val registerCount = stubRegisterCount(accessFlags, parameters.size, implementation?.registerCount ?: 1)
            val reg = returnRegisterName(accessFlags, parameters.size, registerCount)
            implementation = MutableMethodImplementation(registerCount).apply {
                addInstructions(0, "const/4 $reg, 0x0\nreturn $reg")
            }
        }
        SamsungSakCheckerC6rFingerprint.method.apply {
            val registerCount = stubRegisterCount(accessFlags, parameters.size, implementation?.registerCount ?: 1)
            val reg = returnRegisterName(accessFlags, parameters.size, registerCount)
            implementation = MutableMethodImplementation(registerCount).apply {
                addInstructions(0, "const/4 $reg, 0x0\nreturn $reg")
            }
        }
    }
}
