package javax.swing;

import javax.swing.event.ListDataListener;

public abstract class AbstractListModel implements ListModel {
    public AbstractListModel() {}
    public void addListDataListener(ListDataListener listener) {}
    public void removeListDataListener(ListDataListener listener) {}
    protected void fireContentsChanged(Object source, int index0, int index1) {}
    protected void fireIntervalAdded(Object source, int index0, int index1) {}
}
