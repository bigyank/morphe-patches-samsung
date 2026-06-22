package app.bigyank.patches.shealth

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.bigyank.patches.shared.Constants.COMPATIBILITY_SHEALTH
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation

private fun MutableMethodImplementation.clearExceptionHandlers() {
    val tryBlocksField = MutableMethodImplementation::class.java.getDeclaredField("tryBlocks")
    tryBlocksField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    (tryBlocksField.get(this) as java.util.ArrayList<*>).clear()
}

/**
 * Replace a method body the same way apply_shealth_knox_bypass.py / apktool does on macOS:
 * minimal register frame (.locals 1), return via v0, no try/catch tables.
 *
 * Morphe's prior approach (wipe instructions in-place, keep old register count + exception
 * handlers) produced VerifyError on shouldUseKnox / isSupportedTimaVersion and broke the
 * :remote KeyInitializer path — account sign-in never appeared.
 */
private fun Fingerprint.replaceWithMacStyleStub(returnObject: Boolean) {
    method.apply {
        val isStatic = AccessFlags.STATIC.isSet(accessFlags)
        val registerCount = if (isStatic) {
            maxOf(1, parameters.size)
        } else {
            maxOf(1, parameters.size + 1)
        }
        val returnInsn = if (returnObject) "return-object v0" else "return v0"
        val stubBody = "const/4 v0, 0x0\n$returnInsn"
        val freshImpl = MutableMethodImplementation(registerCount).apply {
            addInstructions(0, stubBody)
        }

        val replaced = runCatching {
            val field = javaClass.getDeclaredField("implementation")
            field.isAccessible = true
            field.set(this@apply, freshImpl)
            true
        }.getOrDefault(false)

        if (!replaced) {
            implementation?.let { impl ->
                impl.clearExceptionHandlers()
                impl.removeInstructions(0, impl.instructions.count())
                impl.registerCount = registerCount
                impl.addInstructions(0, stubBody)
            }
        }
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
        KnoxAdapterCheckKnoxCompromisedExternalFingerprint.replaceWithMacStyleStub(returnObject = true)
        KnoxAdapterCheckKnoxCompromisedInternalFingerprint.replaceWithMacStyleStub(returnObject = false)
        KnoxAdapterIsKnoxAvailableFingerprint.replaceWithMacStyleStub(returnObject = false)
        KnoxAdapterIsKnoxAvailableCoreFingerprint.replaceWithMacStyleStub(returnObject = false)
        KnoxAdapterIsAksSakMandatoryFingerprint.replaceWithMacStyleStub(returnObject = false)
        KnoxAdapterShouldUseKnoxFingerprint.replaceWithMacStyleStub(returnObject = false)
        KnoxAdapterIsSupportedTimaVersionFingerprint.replaceWithMacStyleStub(returnObject = false)
        IcccAdapterCheckKnoxCompromisedFingerprint.replaceWithMacStyleStub(returnObject = false)
        KnoxControlIsKnoxAvailableFingerprint.replaceWithMacStyleStub(returnObject = false)
        KnoxControlCheckKnoxCompromisedFingerprint.replaceWithMacStyleStub(returnObject = true)
        KnoxControlCheckWarrantyBitFingerprint.replaceWithMacStyleStub(returnObject = false)
        IKnoxControlProxyIsKnoxAvailableFingerprint.replaceWithMacStyleStub(returnObject = false)
        SakCheckerIsSupportedFingerprint.replaceWithMacStyleStub(returnObject = false)
        SamsungSakCheckerC6rFingerprint.replaceWithMacStyleStub(returnObject = false)
    }
}
