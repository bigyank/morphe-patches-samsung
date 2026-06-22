package app.bigyank.patches.shealth

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.bigyank.patches.shared.Constants.COMPATIBILITY_SHEALTH
import app.morphe.patcher.util.proxy.mutableTypes.encodedValue.MutableStringEncodedValue
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.value.StringEncodedValue
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference
import com.android.tools.smali.dexlib2.util.MethodUtil

private const val SAMSUNG_ACCOUNT_PACKAGE = "com.osp.app.signin"
private const val DUMMY_ACCOUNT_PACKAGE = "com.notsamsung.dummy"
private const val REAL_SAMSUNG_ACCOUNT_TYPE = "com.osp.app.signin"

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

private fun MutableMethodImplementation.clearExceptionHandlers() {
    val tryBlocksField = MutableMethodImplementation::class.java.getDeclaredField("tryBlocks")
    tryBlocksField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    (tryBlocksField.get(this) as java.util.ArrayList<*>).clear()
}

/**
 * Same workaround as [SamsungAppsPatcher wearable-patcher.sh](https://github.com/adil192/SamsungAppsPatcher),
 * adapted for on-device Morphe patching without decoding the ~300 MB resource table.
 *
 * Dex string replacement alone is not enough: Morphe builds still ship manifest/res entries for
 * com.osp.app.signin, so Health calls Samsung Account's AccountManagerProvider as app.shealth and
 * gets signature-blocked. Stubs route account lookups through Android AccountManager instead.
 */
@Suppress("unused")
val bypassSamsungAccountSignatureCheckPatch = bytecodePatch(
    name = "Bypass Samsung Account signature check",
    description = "Replaces com.osp.app.signin with com.notsamsung.dummy in dex and bypasses Samsung " +
        "Account provider signature checks. Use with the SamsungPatch keystore.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_SHEALTH)

    execute {
        fun stubReturnFalse(fingerprint: app.morphe.patcher.Fingerprint) {
            fingerprint.method.apply {
                val stubBody = "const/4 v0, 0x0\nreturn v0"
                val registerCount = maxOf(1, parameters.size + if (AccessFlags.STATIC.isSet(accessFlags)) 0 else 1)
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
                        removeInstructions(0, impl.instructions.count())
                        addInstructions(0, stubBody)
                    }
                }
            }
        }

        stubReturnFalse(SamsungAccountUtilsIsAccountProviderSupportedFingerprint)
        stubReturnFalse(UtilGetSupportAccountManagerProviderFingerprint)
        stubReturnFalse(UtilIsAccountSignedInFromAccountManagerProviderFingerprint)

        SamsungAccountUtilsGetSamsungAccountIdFingerprint.method.apply {
            implementation?.let { impl ->
                impl.clearExceptionHandlers()
                removeInstructions(0, impl.instructions.count())
                addInstructions(
                    0,
                    """
                    invoke-static {p1}, Landroid/accounts/AccountManager;->get(Landroid/content/Context;)Landroid/accounts/AccountManager;
                    move-result-object v0
                    const-string v1, "$REAL_SAMSUNG_ACCOUNT_TYPE"
                    invoke-virtual {v0, v1}, Landroid/accounts/AccountManager;->getAccountsByType(Ljava/lang/String;)[Landroid/accounts/Account;
                    move-result-object v0
                    array-length v1, v0
                    if-lez v1, :sa_no_account
                    const/4 v1, 0x0
                    aget-object v0, v0, v1
                    iget-object v0, v0, Landroid/accounts/Account;->name:Ljava/lang/String;
                    return-object v0
                    :sa_no_account
                    const/4 v0, 0x0
                    return-object v0
                    """.trimIndent(),
                )
            }
        }

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
