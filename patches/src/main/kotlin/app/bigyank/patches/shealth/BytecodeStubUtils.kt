package app.bigyank.patches.shealth

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction11n
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction11x
import com.android.tools.smali.dexlib2.iface.Method

/**
 * Replace a method body with minimal smali while clearing stale try/catch tables.
 *
 * Uses in-place wipes on the Morphe [MutableMethod] proxy so register counts stay correct.
 * Avoids VerifyError from orphaned exception handlers after instruction replacement.
 */
internal fun stubReturnFalse(fingerprint: Fingerprint) {
    fingerprint.method.stubZeroReturn(returnObject = false)
}

internal fun stubZeroReturn(fingerprint: Fingerprint, returnObject: Boolean) {
    fingerprint.method.stubZeroReturn(returnObject = returnObject)
}

internal fun Fingerprint.replaceMethodBody(stubBody: String) {
    method.replaceBodyWithSmali(stubBody, name)
}

private fun Method.stubZeroReturn(returnObject: Boolean) {
    val registerCount = if (AccessFlags.STATIC.isSet(accessFlags)) {
        maxOf(1, parameters.size)
    } else {
        maxOf(1, parameters.size + 1)
    }
    val returnInsn = if (returnObject) "return-object v0" else "return v0"
    val stubBody = "const/4 v0, 0x0\n$returnInsn"
    val freshImpl = MutableMethodImplementation(registerCount).apply {
        addInstruction(BuilderInstruction11n(Opcode.CONST_4, 0, 0))
        addInstruction(
            BuilderInstruction11x(
                if (returnObject) Opcode.RETURN_OBJECT else Opcode.RETURN,
                0,
            ),
        )
    }

    if (!replaceImplementationViaReflection(freshImpl)) {
        replaceBodyWithSmali(stubBody, name)
    }
}

private fun Method.replaceBodyWithSmali(stubBody: String, label: String) {
    val impl = implementation as? MutableMethodImplementation
        ?: throw PatchException("Failed to replace $label: no method implementation")
    impl.clearExceptionHandlers()
    removeInstructions(0, impl.instructions.count())
    addInstructions(0, stubBody)
}

private fun Method.replaceImplementationViaReflection(freshImpl: MutableMethodImplementation): Boolean =
    runCatching {
        val field = javaClass.getDeclaredField("implementation")
        field.isAccessible = true
        field.set(this, freshImpl)
        true
    }.getOrDefault(false)

private fun MutableMethodImplementation.clearExceptionHandlers() {
    val tryBlocksField = MutableMethodImplementation::class.java.getDeclaredField("tryBlocks")
    tryBlocksField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    (tryBlocksField.get(this) as java.util.ArrayList<*>).clear()
}
