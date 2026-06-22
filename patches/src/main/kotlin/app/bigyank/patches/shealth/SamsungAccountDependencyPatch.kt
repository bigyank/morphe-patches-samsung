package app.bigyank.patches.shealth

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.bigyank.patches.shared.Constants.COMPATIBILITY_SHEALTH
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val SAMSUNG_ACCOUNT_PACKAGE = "com.osp.app.signin"
private const val DUMMY_ACCOUNT_PACKAGE = "com.notsamsung.dummy"

private fun Instruction.isSamsungAccountPackageString(): Boolean {
    if (opcode != Opcode.CONST_STRING) return false
    val reference = (this as ReferenceInstruction).reference
    return reference is StringReference && reference.string == SAMSUNG_ACCOUNT_PACKAGE
}

private fun Method.collectSigninStringReplacements(): List<Pair<Int, Int>> {
    val implementation = implementation ?: return emptyList()
    return buildList {
        implementation.instructions.forEachIndexed { index, instruction ->
            if (!instruction.isSamsungAccountPackageString()) return@forEachIndexed
            add(index to (instruction as OneRegisterInstruction).registerA)
        }
    }
}

/**
 * Same smali workaround as [SamsungAppsPatcher wearable-patcher.sh](https://github.com/adil192/SamsungAppsPatcher).
 *
 * Scans dex read-only first and only opens mutable classes that contain the target string, so Morphe
 * Manager does not duplicate every class in memory on this ~300 MB APK.
 */
@Suppress("unused")
val bypassSamsungAccountSignatureCheckPatch = bytecodePatch(
    name = "Bypass Samsung Account signature check",
    description = "Replaces com.osp.app.signin with com.notsamsung.dummy in dex (same as PC patcher). " +
        "Use with the SamsungPatch keystore. If on-device patching loops or OOMs, patch on a PC instead.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_SHEALTH)

    execute {
        classDefForEach { classDef ->
            val replacementsByMethod = classDef.methods.mapNotNull { method ->
                val replacements = method.collectSigninStringReplacements()
                if (replacements.isEmpty()) null else method to replacements
            }
            if (replacementsByMethod.isEmpty()) return@classDefForEach

            val mutableClass = mutableClassDefBy(classDef)
            replacementsByMethod.forEach { (method, replacements) ->
                val methodIndex = classDef.methods.indexOf(method)
                if (methodIndex < 0) return@forEach
                val mutableMethod = mutableClass.methods.elementAt(methodIndex)
                replacements.sortedByDescending { it.first }.forEach { (index, register) ->
                    mutableMethod.replaceInstruction(
                        index,
                        "const-string v$register, \"$DUMMY_ACCOUNT_PACKAGE\"",
                    )
                }
            }
        }
    }
}
