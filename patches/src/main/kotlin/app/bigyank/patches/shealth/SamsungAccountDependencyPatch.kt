package app.bigyank.patches.shealth

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.bigyank.patches.shared.Constants.COMPATIBILITY_SHEALTH
import app.morphe.patcher.util.proxy.mutableTypes.encodedValue.MutableStringEncodedValue
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.value.StringEncodedValue
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference
import com.android.tools.smali.dexlib2.util.MethodUtil

private const val SAMSUNG_ACCOUNT_PACKAGE = "com.osp.app.signin"
private const val DUMMY_ACCOUNT_PACKAGE = "com.notsamsung.dummy"

private fun String.replaceSigninPackage(): String =
    replace(SAMSUNG_ACCOUNT_PACKAGE, DUMMY_ACCOUNT_PACKAGE)

private fun isStringConstantOpcode(opcode: Opcode): Boolean =
    opcode == Opcode.CONST_STRING || opcode == Opcode.CONST_STRING_JUMBO

private data class Replacement(
    val index: Int,
    val register: Int,
    val opcode: Opcode,
    val value: String,
)

/**
 * Same smali workaround as [SamsungAppsPatcher wearable-patcher.sh](https://github.com/adil192/SamsungAppsPatcher).
 *
 * Mac/PC sed replaces every occurrence of com.osp.app.signin (including field defaults and
 * com.osp.app.signin.service.* strings), not just exact package-name const-strings. Dex-only
 * so Morphe Manager does not decode the ~300 MB resource table and OOM on device.
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
            val methodReplacements = classDef.methods.mapNotNull { method ->
                val implementation = method.implementation ?: return@mapNotNull null
                val replacements = buildList {
                    implementation.instructions.forEachIndexed { index, instruction ->
                        if (!isStringConstantOpcode(instruction.opcode)) return@forEachIndexed
                        val string = ((instruction as ReferenceInstruction).reference as? StringReference)?.string
                            ?: return@forEachIndexed
                        if (SAMSUNG_ACCOUNT_PACKAGE !in string) return@forEachIndexed
                        add(
                            Replacement(
                                index = index,
                                register = (instruction as OneRegisterInstruction).registerA,
                                opcode = instruction.opcode,
                                value = string.replaceSigninPackage(),
                            ),
                        )
                    }
                }
                if (replacements.isEmpty()) null else method to replacements
            }

            val fieldReplacements = classDef.fields.mapNotNull { field ->
                val initial = field.initialValue as? StringEncodedValue ?: return@mapNotNull null
                if (SAMSUNG_ACCOUNT_PACKAGE !in initial.value) return@mapNotNull null
                field to initial.value.replaceSigninPackage()
            }

            if (methodReplacements.isEmpty() && fieldReplacements.isEmpty()) return@classDefForEach

            val mutableClass = mutableClassDefBy(classDef)

            methodReplacements.forEach { (method, replacements) ->
                val mutableMethod = mutableClass.methods.first { candidate ->
                    MethodUtil.methodSignaturesMatch(candidate, method)
                }
                replacements.sortedByDescending { it.index }.forEach { replacement ->
                    mutableMethod.replaceInstruction(
                        replacement.index,
                        BuilderInstruction21c(
                            replacement.opcode,
                            replacement.register,
                            ImmutableStringReference(replacement.value),
                        ),
                    )
                }
            }

            fieldReplacements.forEach { (field, newValue) ->
                val mutableField = mutableClass.fields.first { candidate ->
                    candidate.name == field.name && candidate.type == field.type
                }
                (mutableField.getInitialValue() as? MutableStringEncodedValue)?.setValue(newValue)
            }
        }
    }
}
