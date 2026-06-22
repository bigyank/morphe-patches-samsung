package app.bigyank.patches.shealth

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.bigyank.patches.shared.Constants.COMPATIBILITY_SHEALTH
import app.morphe.util.findMutableMethodOf
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.nio.file.Files
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.writeText

private const val SAMSUNG_ACCOUNT_PACKAGE = "com.osp.app.signin"
private const val DUMMY_ACCOUNT_PACKAGE = "com.notsamsung.dummy"

/**
 * Same workaround as [SamsungAppsPatcher wearable-patcher.sh](https://github.com/adil192/SamsungAppsPatcher):
 * Health must not call the system Samsung Account provider with the real package name, or
 * [SignatureInfoDbHelper] blocks patched APKs even when signed with the community keystore.
 */
@Suppress("unused")
val bypassSamsungAccountSignatureCheckPatch = resourcePatch(
    name = "Bypass Samsung Account signature check",
    description = "Routes Samsung Account SDK calls away from com.osp.app.signin so login works on patched APKs. " +
        "Matches the Mac/PC SamsungAppsPatcher build (required alongside the SamsungPatch keystore).",
    default = true,
) {
    compatibleWith(COMPATIBILITY_SHEALTH)

    dependsOn(
        bytecodePatch {
            execute {
                classDefForEach { classDef ->
                    classDef.methods.forEach { method ->
                        val implementation = method.implementation ?: return@forEach
                        val mutableClass = mutableClassDefBy(classDef)
                        val mutableMethod = mutableClass.findMutableMethodOf(method)

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
        },
    )

    finalize {
        fun replaceInXmlDocument(document: org.w3c.dom.Document) {
            fun walk(node: Node) {
                if (node is Element) {
                    for (i in 0 until node.attributes.length) {
                        val attribute = node.attributes.item(i)
                        val value = attribute.nodeValue
                        if (value.contains(SAMSUNG_ACCOUNT_PACKAGE)) {
                            attribute.nodeValue = value.replace(SAMSUNG_ACCOUNT_PACKAGE, DUMMY_ACCOUNT_PACKAGE)
                        }
                    }
                }
                for (i in 0 until node.childNodes.length) {
                    walk(node.childNodes.item(i))
                }
            }
            walk(document.documentElement)
        }

        document("AndroidManifest.xml").use(::replaceInXmlDocument)

        val resRoot = get("res").toPath()
        if (Files.isDirectory(resRoot)) {
            Files.walk(resRoot).use { paths ->
                paths.filter { it.isRegularFile() && it.fileName.toString().endsWith(".xml") }
                    .forEach { xmlPath ->
                        val text = xmlPath.readText()
                        if (SAMSUNG_ACCOUNT_PACKAGE in text) {
                            xmlPath.writeText(text.replace(SAMSUNG_ACCOUNT_PACKAGE, DUMMY_ACCOUNT_PACKAGE))
                        }
                    }
            }
        }
    }
}
