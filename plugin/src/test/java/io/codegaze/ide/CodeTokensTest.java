package io.codegaze.ide;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.codegaze.core.Model;
import java.util.List;

public class CodeTokensTest extends BasePlatformTestCase {
    public void testDistinguishesIdenticallyNamedLocalVariables() {
        String code="class Example { void first(){ int value=1; System.out.println(value); } void second(){ int value=2; System.out.println(value); } }";
        myFixture.configureByText("Example.java",code);
        var doc=myFixture.getEditor().getDocument();
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        int a=code.indexOf("println(value)")+8,b=code.lastIndexOf("println(value)")+8;
        Model.Target first=ReadAction.compute(()->CodeTokens.describe(getProject(),myFixture.getFile(),doc,a,a+5,"IDENTIFIER",CodeTokens.revision(doc),List.of()));
        Model.Target second=ReadAction.compute(()->CodeTokens.describe(getProject(),myFixture.getFile(),doc,b,b+5,"IDENTIFIER",CodeTokens.revision(doc),List.of()));
        assertEquals("value",first.text());assertEquals("local_variable",first.symbolKind());
        assertNotNull(first.symbol());assertNotNull(second.symbol());assertFalse(first.symbol().equals(second.symbol()));
    }
    public void testRevisionChangesWithUnsavedEdits() {
        myFixture.configureByText("Example.java","class Example {}");
        String before=CodeTokens.revision(myFixture.getEditor().getDocument());
        myFixture.type(" ");
        assertFalse(before.equals(CodeTokens.revision(myFixture.getEditor().getDocument())));
    }
}
