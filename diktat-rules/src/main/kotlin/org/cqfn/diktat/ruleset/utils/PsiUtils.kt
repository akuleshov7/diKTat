/**
 * Utility methods to work with PSI code representation
 */

package org.cqfn.diktat.ruleset.utils

import com.pinterest.ktlint.core.ast.ElementType
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtTryExpression
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isAncestor
import org.jetbrains.kotlin.psi.psiUtil.parents

/**
 * Checks if this [KtExpression] contains only constant literals, strings with possibly constant expressions in templates,
 * method calls on literals.
 *
 * @return boolean result
 */
@Suppress("UnsafeCallOnNullableType", "FUNCTION_BOOLEAN_PREFIX")
fun KtExpression.containsOnlyConstants(): Boolean =
        when (this) {
            is KtConstantExpression -> true
            is KtStringTemplateExpression -> entries.all { it.expression?.containsOnlyConstants() ?: true }
            // left and right are marked @Nullable @IfNotParsed, so it should be safe to `!!`
            is KtBinaryExpression -> left!!.containsOnlyConstants() && right!!.containsOnlyConstants()
            is KtDotQualifiedExpression -> receiverExpression.containsOnlyConstants() &&
                    (selectorExpression is KtReferenceExpression ||
                            ((selectorExpression as? KtCallExpression)
                                ?.valueArgumentList
                                ?.arguments
                                ?.all { it.getArgumentExpression()!!.containsOnlyConstants() }
                                ?: false)
                    )
            else -> false
        }

/**
 * Get block inside which the property is declared.
 * Here we assume that property can be declared only in block, since declaration is not an expression in kotlin
 * and compiler prohibits things like `if (condition) val x = 0`.
 */
@Suppress("UnsafeCallOnNullableType")
fun KtProperty.getDeclarationScope() =
        // FixMe: class body is missing here
        getParentOfType<KtBlockExpression>(true)
            .let { if (it is KtIfExpression) it.then!! else it }
            .let { if (it is KtTryExpression) it.tryBlock else it }
            as KtBlockExpression?

/**
 * Checks if this [PsiElement] is an ancestor of [block].
 * Nodes like `IF`, `TRY` are parents of `ELSE`, `CATCH`, but their scopes are not intersecting, and false is returned in this case.
 *
 * @param block
 * @return boolean result
 */
fun PsiElement.isContainingScope(block: KtBlockExpression): Boolean {
    when (block.parent.node.elementType) {
        ElementType.ELSE -> getParentOfType<KtIfExpression>(true)
        ElementType.CATCH -> getParentOfType<KtTryExpression>(true)
        else -> null
    }.let {
        if (this == it) {
            return false
        }
    }
    return isAncestor(block, false)
}

/**
 * Method that tries to find a local property declaration with the same name as current [KtNameReferenceExpression] element
 *
 * @return [KtProperty] if it is found, null otherwise
 */
fun KtNameReferenceExpression.findLocalDeclaration(): KtProperty? = parents
    .mapNotNull { it as? KtBlockExpression }
    .mapNotNull { blockExpression ->
        blockExpression
            .statements
            .takeWhile { !it.isAncestor(this, true) }
            .mapNotNull { it as? KtProperty }
            .find {
                it.isLocal &&
                        it.hasInitializer() &&
                        it.name?.equals(getReferencedName())
                            ?: false
            }
    }
    .firstOrNull()

/**
 * @return name of a function which is called in a [KtCallExpression] or null if it can't be found
 */
fun KtCallExpression.getFunctionName() = (calleeExpression as? KtNameReferenceExpression)?.getReferencedName()
