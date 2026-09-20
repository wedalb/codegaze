package io.codegaze.ide;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import io.codegaze.core.Model;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

public final class CodeTokens {
    private CodeTokens() {}
    public static String revision(Document document) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(document.getText().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    public static String path(Project project, VirtualFile file) {
        if (file == null) return "unknown";
        String base = project.getBasePath(), path = file.getPath();
        return base != null && path.startsWith(base + "/") ? path.substring(base.length() + 1) : file.getName();
    }
    public static Model.Target describe(Project project, PsiFile file, Document doc,
                                        int start, int end, String type, String revision, List<Model.Rect> bounds) {
        String symbol = null, symbolKind = null;
        if (!DumbService.isDumb(project)) {
            PsiElement element = file.findElementAt(start);
            PsiReference reference = file.findReferenceAt(start);
            PsiElement declaration = reference == null ? null : reference.resolve();
            if (declaration == null && element != null && element.getParent() instanceof PsiNameIdentifierOwner owner
                    && owner.getNameIdentifier() == element) declaration = owner;
            if (declaration instanceof PsiNamedElement named && declaration.getContainingFile() != null) {
                symbol = path(project, declaration.getContainingFile().getVirtualFile()) + ":"
                        + declaration.getTextOffset() + ":" + named.getName();
                symbolKind = declaration instanceof PsiParameter ? "parameter" : declaration instanceof PsiField ? "field" :
                        declaration instanceof PsiLocalVariable ? "local_variable" : declaration instanceof PsiMethod ? "method" :
                        declaration instanceof PsiClass ? "class" : "symbol";
            }
        }
        int line = doc.getLineNumber(start);
        return new Model.Target(type, doc.getCharsSequence().subSequence(start, end).toString(),
                path(project, file.getVirtualFile()), revision, start, end, line + 1,
                start - doc.getLineStartOffset(line) + 1, symbol, symbolKind, bounds);
    }
}
