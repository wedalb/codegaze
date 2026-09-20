package io.codegaze.ide;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import io.codegaze.core.Model;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Paint and mapping are captured in one EDT/read-action snapshot, in Swing logical pixels. */
public final class EditorCapture {
    private final Project project;
    private final AtomicLong ids = new AtomicLong();
    private record Pixels(long id, long epoch, long mono, BufferedImage image, String title,
                          String status, List<Model.Target> targets) {}
    public EditorCapture(Project project) { this.project = project; }

    public Model.Frame capture() throws Exception {
        Pixels[] pixels = new Pixels[1];
        Runnable action = () -> {
            if (project.isDisposed()) return;
            // Commit before read action so PSI and document contents describe the same revision.
            PsiDocumentManager.getInstance(project).commitAllDocuments();
            ApplicationManager.getApplication().runReadAction(() -> pixels[0] = paint());
        };
        if (ApplicationManager.getApplication().isDispatchThread()) action.run();
        else ApplicationManager.getApplication().invokeAndWait(action);
        Pixels p = pixels[0];
        if (p == null) throw new IllegalStateException("Project is closing");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(p.image(), "jpg", bytes);
        return new Model.Frame(p.id(), p.epoch(), p.mono(), p.image().getWidth(), p.image().getHeight(),
                Base64.getEncoder().encodeToString(bytes.toByteArray()), p.title(), p.status(), p.targets());
    }
    private Pixels paint() {
        long id = ids.incrementAndGet(), epoch = System.currentTimeMillis(), mono = System.nanoTime();
        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor == null || !editor.getComponent().isShowing()) return placeholder(id, epoch, mono);
        JComponent component = editor.getComponent();
        int width = component.getWidth(), height = component.getHeight();
        if (width < 10 || height < 10 || width > 8192 || height > 8192) return placeholder(id, epoch, mono);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(component.getBackground()); g.fillRect(0, 0, width, height);
        RepaintManager manager = RepaintManager.currentManager(component);
        boolean doubleBuffering = manager.isDoubleBufferingEnabled();
        try { manager.setDoubleBufferingEnabled(false); component.printAll(g); }
        finally { manager.setDoubleBufferingEnabled(doubleBuffering); g.dispose(); }
        Document doc = editor.getDocument();
        PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(doc);
        List<Model.Target> targets = new ArrayList<>();
        if (file != null && editor instanceof EditorEx ex && doc.getTextLength() > 0) {
            Rectangle visible = editor.getContentComponent().getVisibleRect();
            Point translation = SwingUtilities.convertPoint(editor.getContentComponent(), 0, 0, component);
            int first = editor.logicalPositionToOffset(editor.xyToLogicalPosition(new Point(visible.x, visible.y)));
            int last = editor.logicalPositionToOffset(editor.xyToLogicalPosition(new Point(visible.x + visible.width, visible.y + visible.height)));
            // Include the whole first/last source lines; geometry clips horizontally and around folds.
            first = doc.getLineStartOffset(doc.getLineNumber(Math.min(first, doc.getTextLength())));
            last = doc.getLineEndOffset(doc.getLineNumber(Math.min(last, doc.getTextLength())));
            String revision = CodeTokens.revision(doc);
            HighlighterIterator iterator = ex.getHighlighter().createIterator(first);
            int budget = 6000;
            while (!iterator.atEnd() && iterator.getStart() <= last && budget-- > 0) {
                int start = iterator.getStart(), end = Math.min(iterator.getEnd(), doc.getTextLength());
                CharSequence text = doc.getCharsSequence().subSequence(start, end);
                if (!text.toString().isBlank() && end - start <= 4096) {
                    List<Model.Rect> bounds = bounds(editor, start, end, visible, translation);
                    if (!bounds.isEmpty()) {
                        try { targets.add(CodeTokens.describe(project, file, doc, start, end,
                                iterator.getTokenType().toString(), revision, bounds)); }
                        catch (com.intellij.openapi.progress.ProcessCanceledException canceled) { throw canceled; }
                        catch (RuntimeException unsupportedLanguage) {
                            int line = doc.getLineNumber(start);
                            targets.add(new Model.Target(iterator.getTokenType().toString(), text.toString(),
                                    CodeTokens.path(project, file.getVirtualFile()), revision, start, end,
                                    line + 1, start - doc.getLineStartOffset(line) + 1, null, null, bounds));
                        }
                    }
                }
                iterator.advance();
            }
        }
        return new Pixels(id, epoch, mono, image, file == null ? "Editor" : CodeTokens.path(project, file.getVirtualFile()),
                "ready", List.copyOf(targets));
    }
    private static List<Model.Rect> bounds(Editor editor, int start, int end, Rectangle visible, Point translation) {
        List<Model.Rect> out = new ArrayList<>();
        Rectangle current = null;
        for (int i = start; i < end; i++) {
            char c = editor.getDocument().getCharsSequence().charAt(i);
            if (c == '\n' || c == '\r' || editor.getFoldingModel().isOffsetCollapsed(i)) continue;
            Point a = editor.offsetToXY(i), b = editor.offsetToXY(i + 1);
            // A soft-wrap boundary moves b to the next row; measure the current glyph locally.
            int glyphWidth = b.y == a.y ? b.x - a.x : editor.getContentComponent().getFontMetrics(editor.getContentComponent().getFont()).charWidth(c);
            if (glyphWidth <= 0) continue;
            Rectangle r = new Rectangle(a.x, a.y, glyphWidth, editor.getLineHeight()).intersection(visible);
            if (r.isEmpty()) continue;
            r.translate(translation.x, translation.y);
            if (current != null && current.y == r.y && current.x + current.width == r.x && current.height == r.height) current.width += r.width;
            else { if (current != null) out.add(rect(current)); current = r; }
        }
        if (current != null) out.add(rect(current));
        return List.copyOf(out);
    }
    private static Model.Rect rect(Rectangle r) { return new Model.Rect(r.x, r.y, r.width, r.height); }
    private Pixels placeholder(long id, long epoch, long mono) {
        BufferedImage image = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics(); g.setColor(new Color(22, 33, 53)); g.fillRect(0,0,1280,720);
        g.setColor(Color.WHITE); g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 30));
        g.drawString("Open a source file in IntelliJ to share its editor.", 120, 350); g.dispose();
        return new Pixels(id, epoch, mono, image, "No active editor", "no_editor", List.of());
    }
}
