package io.codegaze.ide;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.codegaze.core.Mapper;
import io.codegaze.core.Model;
import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.io.ByteArrayInputStream;
import java.util.Base64;

/** Exercises real IntelliJ editor painting and geometry under a virtual display in CI. */
public class EditorCaptureTest extends BasePlatformTestCase {
    public void testRenderedEditorAndTokenMappingSurviveUnsavedEdit() throws Exception {
        if (GraphicsEnvironment.isHeadless()) return; // CI explicitly runs this under Xvfb.
        myFixture.configureByText("Example.java", "class Example {\n  int total(int quantity) {\n    return quantity * 2;\n  }\n}\n");
        Editor editor = myFixture.getEditor();
        JFrame[] window = new JFrame[1];
        Runnable show = () -> {
            window[0] = new JFrame("CodeGaze capture test");
            window[0].setContentPane(editor.getComponent());
            window[0].setSize(1100, 650);
            window[0].setVisible(true);
            window[0].validate();
        };
        if (ApplicationManager.getApplication().isDispatchThread()) show.run();
        else ApplicationManager.getApplication().invokeAndWait(show);
        try {
            EditorCapture capture = new EditorCapture(getProject());
            Model.Frame first = capture.captureEditor(editor);
            assertEquals("ready", first.status());
            assertEquals(first.width(), ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(first.image()))).getWidth());
            Model.Target quantity = first.targets().stream().filter(t -> t.text().equals("quantity") && t.line() == 3).findFirst().orElseThrow();
            Model.Rect box = quantity.bounds().getFirst();
            Model.Sample observation = new Model.Sample("test",0,first.id(),0,0.0,"simulated",null,true,
                    (box.x()+box.width()/2)/first.width(),(box.y()+box.height()/2)/first.height(),null,null,null,"unavailable");
            assertEquals(quantity, Mapper.map(first, observation).target());
            WriteCommandAction.runWriteCommandAction(getProject(), () -> editor.getDocument().replaceString(quantity.startOffset(), quantity.endOffset(), "amount"));
            Model.Frame second = capture.captureEditor(editor);
            Model.Target amount = second.targets().stream().filter(t -> t.text().equals("amount")).findFirst().orElseThrow();
            assertFalse(quantity.revision().equals(amount.revision()));
            assertEquals("quantity", Mapper.map(first, observation).target().text());
        } finally {
            Runnable close = () -> { window[0].getContentPane().removeAll(); window[0].dispose(); };
            if (ApplicationManager.getApplication().isDispatchThread()) close.run();
            else ApplicationManager.getApplication().invokeAndWait(close);
        }
    }
}
