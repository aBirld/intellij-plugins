package org.jetbrains.vuejs.codeInsight

import com.intellij.lang.javascript.JSStubElementTypes
import com.intellij.lang.javascript.library.JSLibraryUtil
import com.intellij.lang.javascript.psi.*
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.lang.javascript.psi.resolve.ES6QualifiedNameResolver
import com.intellij.lang.javascript.psi.stubs.JSImplicitElement
import com.intellij.lang.javascript.psi.util.JSProjectUtil
import com.intellij.lang.javascript.psi.util.JSStubBasedPsiTreeUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

/**
 * @author Irina.Chernushina on 9/26/2017.
 */
class VueComponents {
  companion object {
    fun selectComponent(elements : Collection<JSImplicitElement>?, ignoreLibraries: Boolean) : JSImplicitElement? {
      elements ?: return null
      var filtered : Collection<JSImplicitElement> = onlyLocal(elements)
      if (filtered.isEmpty()) {
        if (ignoreLibraries) return null
        filtered = elements
      }

      return filtered.firstOrNull { it.typeString != null } ?: elements.firstOrNull()
    }

    fun onlyLocal(elements: Collection<JSImplicitElement>): List<JSImplicitElement> {
      return elements.filter {
        val file = it.containingFile.viewProvider.virtualFile
        !JSProjectUtil.isInLibrary(file, it.project) && !JSLibraryUtil.isProbableLibraryFile(file)
      }
    }

    fun findComponentDescriptor(element: JSImplicitElement): JSObjectLiteralExpression? {
      val parent = element.parent

      if (parent is JSCallExpression) {
        val reference = element.typeString ?: return null

        return resolveReferenceToObjectLiteral(element, reference)
      }
      return (parent as? JSProperty)?.context as? JSObjectLiteralExpression
    }

    fun resolveReferenceToObjectLiteral(element: JSImplicitElement, reference: String): JSObjectLiteralExpression? {
      val scope = PsiTreeUtil.getContextOfType(element, JSCatchBlock::class.java, JSClass::class.java, JSExecutionScope::class.java)
                  ?: element.containingFile

      val resolvedLocally = JSStubBasedPsiTreeUtil.resolveLocally(reference, scope)
      if (resolvedLocally != null) {
        return getLiteralFromResolve(listOf(resolvedLocally))
      }

      val elements = ES6QualifiedNameResolver(scope).resolveQualifiedName(reference)
      return getLiteralFromResolve(elements)
    }

    private fun getLiteralFromResolve(result : Collection<PsiElement>): JSObjectLiteralExpression? {
      return result.mapNotNull(fun(it : PsiElement) : JSObjectLiteralExpression? {
        val element : PsiElement? = (it as? JSVariable)?.initializerOrStub ?: it
        if (element is JSObjectLiteralExpression) return element
        return JSStubBasedPsiTreeUtil.calculateMeaningfulElement(element!!) as? JSObjectLiteralExpression
      }).firstOrNull()
    }

    fun isGlobal(it: JSImplicitElement) = it.typeString != null

    fun vueMixinDescriptorFinder(implicitElement: JSImplicitElement) : JSObjectLiteralExpression? {
      if (!StringUtil.isEmptyOrSpaces(implicitElement.typeString)) {
        val expression = VueComponents.resolveReferenceToObjectLiteral(implicitElement, implicitElement.typeString!!)
        if (expression != null) {
          return expression
        }
      }
      val mixinObj = (implicitElement.parent as? JSProperty)?.parent as? JSObjectLiteralExpression
      if (mixinObj != null) return mixinObj

      val call = implicitElement.parent as? JSCallExpression
      if (call != null) {
        return JSStubBasedPsiTreeUtil.findDescendants(call, JSStubElementTypes.OBJECT_LITERAL_EXPRESSION)
          .firstOrNull { (it.context as? JSArgumentList)?.context == call || (it.context == call) }
      }
      return null
    }
  }
}