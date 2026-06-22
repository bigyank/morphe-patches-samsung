package app.bigyank.patches.shealth

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.bigyank.patches.shared.Constants.COMPATIBILITY_SHEALTH
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction11n
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction11x

private fun MutableMethodImplementation.clearExceptionHandlers() {
    val tryBlocksField = MutableMethodImplementation::class.java.getDeclaredField("tryBlocks")
    tryBlocksField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    (tryBlocksField.get(this) as java.util.ArrayList<*>).clear()
}

private fun macStyleStubImplementation(registerCount: Int, returnObject: Boolean): MutableMethodImplementation {
    return MutableMethodImplementation(registerCount).apply {
        addInstruction(BuilderInstruction11n(Opcode.CONST_4, 0, 0))
        addInstruction(
            BuilderInstruction11x(
                if (returnObject) Opcode.RETURN_OBJECT else Opcode.RETURN,
                0,
            ),
        )
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
        /**
         * Match macOS apktool / apply_shealth_knox_bypass.py: minimal frame, v0 return,
         * no try/catch. In-place wipes left stale exception tables on shouldUseKnox and
         * isSupportedTimaVersion, breaking KeyInitializer in the :remote process.
         */
        fun stubMacStyle(fingerprint: Fingerprint, returnObject: Boolean) {
            fingerprint.method.apply {
                val isStatic = AccessFlags.STATIC.isSet(accessFlags)
                val registerCount = if (isStatic) {
                    maxOf(1, parameters.size)
                } else {
                    maxOf(1, parameters.size + 1)
                }
                val returnInsn = if (returnObject) "return-object v0" else "return v0"
                val stubBody = "const/4 v0, 0x0\n$returnInsn"
                val freshImpl = macStyleStubImplementation(registerCount, returnObject)

                val replaced = runCatching {
                    val field = javaClass.getDeclaredField("implementation")
                    field.isAccessible = true
                    field.set(this@apply, freshImpl)
                    true
                }.getOrDefault(false)

                if (!replaced) {
                    implementation?.let { impl ->
                        impl.clearExceptionHandlers()
                        removeInstructions(0, impl.instructions.count())
                        addInstructions(0, stubBody)
                    }
                }
            }
        }

        stubMacStyle(KnoxAdapterCheckKnoxCompromisedExternalFingerprint, returnObject = true)
        stubMacStyle(KnoxAdapterCheckKnoxCompromisedInternalFingerprint, returnObject = false)
        stubMacStyle(KnoxAdapterIsKnoxAvailableFingerprint, returnObject = false)
        stubMacStyle(KnoxAdapterIsKnoxAvailableCoreFingerprint, returnObject = false)
        stubMacStyle(KnoxAdapterIsAksSakMandatoryFingerprint, returnObject = false)
        stubMacStyle(KnoxAdapterShouldUseKnoxFingerprint, returnObject = false)
        stubMacStyle(KnoxAdapterIsSupportedTimaVersionFingerprint, returnObject = false)
        stubMacStyle(IcccAdapterCheckKnoxCompromisedFingerprint, returnObject = false)
        stubMacStyle(KnoxControlIsKnoxAvailableFingerprint, returnObject = false)
        stubMacStyle(KnoxControlCheckKnoxCompromisedFingerprint, returnObject = true)
        stubMacStyle(KnoxControlCheckWarrantyBitFingerprint, returnObject = false)
        stubMacStyle(IKnoxControlProxyIsKnoxAvailableFingerprint, returnObject = false)
        stubMacStyle(SakCheckerIsSupportedFingerprint, returnObject = false)
        stubMacStyle(SamsungSakCheckerC6rFingerprint, returnObject = false)
    }
}
