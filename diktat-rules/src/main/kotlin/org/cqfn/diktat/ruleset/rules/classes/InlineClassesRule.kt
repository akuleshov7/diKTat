package org.cqfn.diktat.ruleset.rules.classes

import org.cqfn.diktat.common.config.rules.RulesConfig
import org.cqfn.diktat.ruleset.constants.EmitType
import org.cqfn.diktat.ruleset.constants.Warnings.INLINE_CLASS_CAN_BE_USED
import org.cqfn.diktat.ruleset.utils.getFirstChildWithType

import com.pinterest.ktlint.core.Rule
import com.pinterest.ktlint.core.ast.ElementType.CLASS
import com.pinterest.ktlint.core.ast.ElementType.FINAL_KEYWORD
import com.pinterest.ktlint.core.ast.ElementType.INTERNAL_KEYWORD
import com.pinterest.ktlint.core.ast.ElementType.MODIFIER_LIST
import com.pinterest.ktlint.core.ast.ElementType.PRIVATE_KEYWORD
import com.pinterest.ktlint.core.ast.ElementType.PROTECTED_KEYWORD
import com.pinterest.ktlint.core.ast.ElementType.PUBLIC_KEYWORD
import com.pinterest.ktlint.core.ast.ElementType.SUPER_TYPE_LIST
import com.pinterest.ktlint.core.ast.ElementType.VAR_KEYWORD
import org.cqfn.diktat.ruleset.utils.hasChildOfType
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtClass

/**
 * This rule checks if inline class can be used.
 */
class InlineClassesRule(private val configRule: List<RulesConfig>) : Rule("inline-classes") {
    private var isFixMode: Boolean = false
    private lateinit var emitWarn: EmitType

    override fun visit(
        node: ASTNode,
        autoCorrect: Boolean,
        emit: EmitType
    ) {
        emitWarn = emit
        isFixMode = autoCorrect

        if (node.elementType == CLASS) {
            handleClasses(node.psi as KtClass)
        }
    }

    private fun handleClasses(classPsi: KtClass) {
        if (hasValidProperties(classPsi)
                && !classPsi.node.hasChildOfType(SUPER_TYPE_LIST) // Fixme: for now we can't understand whether it extends class or interface
                && classPsi.node.getFirstChildWithType(MODIFIER_LIST)?.getChildren(null)?.all { it.elementType in goodModifiers } != false) {
            INLINE_CLASS_CAN_BE_USED.warnAndFix(configRule, emitWarn, isFixMode, "class ${classPsi.name}", classPsi.node.startOffset, classPsi.node) {
                // Fixme: since it's an experimental feature we shouldn't do fixer
            }
        }
    }

    private fun hasValidProperties(classPsi: KtClass) : Boolean {
        if (classPsi.getProperties().size == 1 && !classPsi.hasExplicitPrimaryConstructor()) {
            return !classPsi.getProperties().first().isVar
        } else if (classPsi.getProperties().isEmpty() && classPsi.hasExplicitPrimaryConstructor()) {
            return classPsi.primaryConstructorParameters.size == 1 && !classPsi.primaryConstructorParameters.first().node.hasChildOfType(VAR_KEYWORD)
        }
        return false
    }

    companion object {
        val goodModifiers = listOf(PUBLIC_KEYWORD, PRIVATE_KEYWORD, FINAL_KEYWORD, PROTECTED_KEYWORD, INTERNAL_KEYWORD)
    }
}
