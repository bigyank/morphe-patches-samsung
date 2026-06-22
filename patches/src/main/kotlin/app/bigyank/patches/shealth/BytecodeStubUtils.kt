package app.bigyank.patches.shealth

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.Method

/**
 * Replace a method body with minimal smali while clearing stale try/catch tables.
 *
 * Avoids VerifyError from orphaned exception handlers after in-place instruction wipes.
 *
 * @return true if the stub was applied, false if the method has no implementation and reflection failed
 */
internal fun Method.replaceWithSmali(stubBody: String): Boolean {
    val regCount = implementation?.registerCount ?: registerCount()
    val freshImpl = MutableMethodImplementation(regCount).apply {
        addInstructions(0, stubBody)
    }

    val replaced = runCatching {
        val field = javaClass.getDeclaredField("implementation")
        field.isAccessible = true
        field.set(this@replaceWithSmali, freshImpl)
        true
    }.getOrDefault(false)

    if (replaced) return true

    val impl = implementation ?: return false
    impl.clearExceptionHandlers()
    removeInstructions(0, impl.instructions.count())
    addInstructions(0, stubBody)
    return true
}

internal fun stubReturnFalse(fingerprint: Fingerprint) {
    fingerprint.requireStubbed("return false") {
        replaceWithSmali("const/4 v0, 0x0\nreturn v0")
    }
}

internal fun stubZeroReturn(fingerprint: Fingerprint, returnObject: Boolean) {
    val returnInsn = if (returnObject) "return-object v0" else "return v0"
    fingerprint.requireStubbed("return zero/null") {
        replaceWithSmali("const/4 v0, 0x0\n$returnInsn")
    }
}

internal fun Fingerprint.replaceMethodBody(stubBody: String) {
    requireStubbed("replace method body") {
        replaceWithSmali(stubBody)
    }
}

private inline fun Fingerprint.requireStubbed(action: String, stub: Method.() -> Boolean) {
    if (!method.run(stub)) {
        throw patchException("Failed to $action for ${method.describe()}")
    }
}

private fun Method.describe(): String =
    "$name(${parameters.joinToString("") { it.type }})$returnType"

private fun Method.registerCount(): Int =
    maxOf(1, parameters.size + if (AccessFlags.STATIC.isSet(accessFlags)) 0 else 1)

private fun MutableMethodImplementation.clearExceptionHandlers() {
    val tryBlocksField = MutableMethodImplementation::class.java.getDeclaredField("tryBlocks")
    tryBlocksField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    (tryBlocksField.get(this) as java.util.ArrayList<*>).clear()
}
