package app.bigyank.patches.shealth

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.bigyank.patches.shared.Constants.COMPATIBILITY_SHEALTH
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val SAMSUNG_ACCOUNT_PACKAGE = "com.osp.app.signin"
private const val DUMMY_ACCOUNT_PACKAGE = "com.notsamsung.dummy"

/**
 * Same smali workaround as [SamsungAppsPatcher wearable-patcher.sh](https://github.com/adil192/SamsungAppsPatcher):
 * replace `com.osp.app.signin` string constants so Health does not hit Samsung Account signature blocking.
 *
 * Bytecode-only (no resource decode) — Samsung Health is ~300 MB and OOMs Morphe Manager during
 * `Decoding all resources` if this were a resourcePatch.
 */
@Suppress("unused")
val bypassSamsungAccountSignatureCheckPatch = bytecodePatch(
    name = "Bypass Samsung Account signature check",
    description = "Replaces com.osp.app.signin with com.notsamsung.dummy in dex (same as PC patcher). " +
        "Dex-only to avoid OOM on large APKs. Use with the SamsungPatch keystore.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_SHEALTH)

    execute {
        classDefForEach { classDef ->
            val mutableClass = mutableClassDefBy(classDef)
            mutableClass.methods.forEach { mutableMethod ->
                val implementation = mutableMethod.implementation ?: return@forEach

                implementation.instructions.forEachIndexed { index, instruction ->
                    if (instruction.opcode != Opcode.CONST_STRING) return@forEachIndexed

                    val referenceInstruction = instruction as ReferenceInstruction
                    val string = (referenceInstruction.reference as StringReference).string
                    if (string != SAMSUNG_ACCOUNT_PACKAGE) return@forEachIndexed

                    val register = (instruction as OneRegisterInstruction).registerA
                    mutableMethod.replaceInstruction(
                        index,
                        "const-string v$register, \"$DUMMY_ACCOUNT_PACKAGE\"",
                    )
                }
            }
        }
    }
}
