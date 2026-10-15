package app.bigyank.patches.shealth

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.util.MethodUtil

/**
 * Stub the Discover+ promotion (ad) card display path, content-scanned.
 *
 * The load coroutine lives under `home/promotion/discoverplus/` with a stable
 * log tag but per-build obfuscated names (`j` on 6.32, `q` on 7.00.6) and an
 * obfuscated kotlin-stdlib (Continuation and Unit are renamed per build).
 * The stub replicates the app's own offline early-exit — return the Kotlin
 * Unit singleton the original body already references — so the card state
 * never reaches LOADING/SUCCESS and the panel stays in its pre-fetch state.
 */
internal fun BytecodePatchContext.stubPromotionDisplayPath() {
    classDefForEach { classDef ->
        if (!classDef.type.contains("promotion/discoverplus")) return@classDefForEach

        val stubs = classDef.methods.mapNotNull { method ->
            if (!isPromotionDisplayPath(method)) return@mapNotNull null
            val unitField = method.findUnitSingletonField() ?: return@mapNotNull null
            method to "sget-object v0, $unitField\nreturn-object v0"
        }
        if (stubs.isEmpty()) return@classDefForEach

        val mutableClass = mutableClassDefBy(classDef)
        stubs.forEach { (method, body) ->
            val mutableMethod = mutableClass.methods.firstOrNull { candidate ->
                MethodUtil.methodSignaturesMatch(candidate, method)
            } ?: return@forEach
            mutableMethod.replacePromotionBody(body)
        }
    }
}

private const val PROMOTION_LOG_TAG = "SHEALTH#PromotionDiscoverCardViewModel"

private fun isPromotionDisplayPath(method: Method): Boolean {
    if (method.returnType != "Ljava/lang/Object;") return false
    if (method.parameterTypes.size != 2) return false
    if (!AccessFlags.STATIC.isSet(method.accessFlags)) return false
    if (AccessFlags.ABSTRACT.isSet(method.accessFlags)) return false
    if (method.implementation == null) return false
    return method.stringConstants().contains(PROMOTION_LOG_TAG)
}

/** `sget-object vN, Lunit;->a:Lunit;` — the self-typed singleton field the
 *  original body returns from its no-network path; Unit is obfuscated per
 *  build, so the reference is lifted from the method itself. */
private fun Method.findUnitSingletonField(): String? {
    val impl = implementation ?: return null
    impl.instructions.forEach { instruction ->
        if (instruction.opcode != Opcode.SGET_OBJECT) return@forEach
        val ref = (instruction as? ReferenceInstruction)?.reference as? FieldReference ?: return@forEach
        if (ref.name == "a" && ref.definingClass == ref.type) {
            return "${ref.definingClass}->${ref.name}:${ref.type}"
        }
    }
    return null
}

private fun Method.stringConstants(): Set<String> {
    val impl = implementation ?: return emptySet()
    return impl.instructions.mapNotNull { instruction ->
        if (instruction.opcode != Opcode.CONST_STRING && instruction.opcode != Opcode.CONST_STRING_JUMBO) {
            return@mapNotNull null
        }
        ((instruction as ReferenceInstruction).reference as? StringReference)?.string
    }.toSet()
}

private fun MutableMethod.replacePromotionBody(stubBody: String) {
    val impl = implementation as? MutableMethodImplementation ?: return
    impl.clearExceptionHandlers()
    removeInstructions(0, impl.instructions.count())
    addInstructions(0, stubBody)
}

private fun MutableMethodImplementation.clearExceptionHandlers() {
    val tryBlocksField = MutableMethodImplementation::class.java.getDeclaredField("tryBlocks")
    tryBlocksField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    (tryBlocksField.get(this) as java.util.ArrayList<*>).clear()
}
