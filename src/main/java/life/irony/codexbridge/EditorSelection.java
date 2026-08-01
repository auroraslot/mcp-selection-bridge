package life.irony.codexbridge;

/** 单个编辑器窗口的选区快照。字段直接被 Gson 序列化进 /selection 调试端点。 */
public class EditorSelection {
    public String project;
    public String projectPath;
    public String file;
    public int startLine;
    public int endLine;
    public boolean focused;
    public String selectedText;

    public EditorSelection(String project, String projectPath, String file,
                           int startLine, int endLine, boolean focused, String selectedText) {
        this.project = project;
        this.projectPath = projectPath;
        this.file = file;
        this.startLine = startLine;
        this.endLine = endLine;
        this.focused = focused;
        this.selectedText = selectedText;
    }
}
