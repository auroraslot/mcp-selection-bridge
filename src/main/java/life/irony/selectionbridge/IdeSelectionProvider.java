package life.irony.selectionbridge;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;

import java.util.ArrayList;
import java.util.List;

/** 从正在运行的 IDE 里读取各项目当前编辑器的选区（EDT 上执行，聚焦窗口排最前）。 */
public class IdeSelectionProvider implements SelectionProvider {

    @Override
    public List<EditorSelection> currentSelections() {
        List<EditorSelection> entries = new ArrayList<>();
        ApplicationManager.getApplication().invokeAndWait(() -> {
            Project focused = null;
            IdeFrame frame = IdeFocusManager.getGlobalInstance().getLastFocusedFrame();
            if (frame != null) focused = frame.getProject();
            for (Project project : ProjectManager.getInstance().getOpenProjects()) {
                if (project.isDisposed()) continue;
                Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
                if (editor == null) continue;
                Document doc = editor.getDocument();
                VirtualFile file = FileDocumentManager.getInstance().getFile(doc);
                SelectionModel sel = editor.getSelectionModel();
                String text = sel.getSelectedText();
                int startLine;
                int endLine;
                if (text != null && !text.isEmpty()) {
                    startLine = doc.getLineNumber(sel.getSelectionStart()) + 1;
                    int endOffset = sel.getSelectionEnd();
                    // 选区在行首结束时不把下一行算进去
                    endLine = doc.getLineNumber(Math.max(endOffset - 1, sel.getSelectionStart())) + 1;
                } else {
                    startLine = endLine = doc.getLineNumber(editor.getCaretModel().getOffset()) + 1;
                }
                boolean isFocused = project.equals(focused);
                EditorSelection entry = new EditorSelection(
                        project.getName(), project.getBasePath(),
                        file != null ? file.getPath() : null,
                        startLine, endLine, isFocused, text);
                if (isFocused) {
                    entries.add(0, entry);
                } else {
                    entries.add(entry);
                }
            }
        }, ModalityState.any());
        return entries;
    }
}
