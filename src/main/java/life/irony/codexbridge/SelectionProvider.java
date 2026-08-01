package life.irony.codexbridge;

import java.util.List;

/** 选区来源抽象：插件里由 IDE 实现，独立测试 harness 里用假数据实现。 */
public interface SelectionProvider {
    /** 返回各打开项目当前编辑器的选区快照，聚焦窗口排在最前。 */
    List<EditorSelection> currentSelections();
}
