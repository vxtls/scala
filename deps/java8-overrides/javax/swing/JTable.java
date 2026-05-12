package javax.swing;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;

public class JTable extends JComponent implements Scrollable {
    public static final int AUTO_RESIZE_OFF = 0;
    public static final int AUTO_RESIZE_NEXT_COLUMN = 1;
    public static final int AUTO_RESIZE_SUBSEQUENT_COLUMNS = 2;
    public static final int AUTO_RESIZE_LAST_COLUMN = 3;
    public static final int AUTO_RESIZE_ALL_COLUMNS = 4;

    public JTable() {}
    public JTable(int rows, int columns) {}

    public TableCellRenderer getCellRenderer(int row, int column) { return null; }
    public TableCellEditor getCellEditor(int row, int column) { return null; }
    public Object getValueAt(int row, int column) { return null; }

    public int getRowHeight() { return 0; }
    public void setRowHeight(int rowHeight) {}
    public int getRowCount() { return 0; }
    public TableModel getModel() { return null; }
    public void setModel(TableModel model) {}
    public int getAutoResizeMode() { return 0; }
    public void setAutoResizeMode(int mode) {}
    public boolean getShowHorizontalLines() { return false; }
    public boolean getShowVerticalLines() { return false; }
    public void setShowGrid(boolean showGrid) {}
    public Color getGridColor() { return null; }
    public void setGridColor(Color gridColor) {}
    public void setPreferredScrollableViewportSize(Dimension size) {}

    public int[] getSelectedRows() { return null; }
    public int[] getSelectedColumns() { return null; }
    public void removeRowSelectionInterval(int index0, int index1) {}
    public void addRowSelectionInterval(int index0, int index1) {}
    public void removeColumnSelectionInterval(int index0, int index1) {}
    public void addColumnSelectionInterval(int index0, int index1) {}
    public int getSelectedRowCount() { return 0; }
    public int getSelectedColumnCount() { return 0; }

    public ListSelectionModel getSelectionModel() { return null; }
    public TableColumnModel getColumnModel() { return null; }
    public void setSelectionMode(int selectionMode) {}
    public boolean getColumnSelectionAllowed() { return false; }
    public boolean getRowSelectionAllowed() { return false; }
    public void setCellSelectionEnabled(boolean cellSelectionEnabled) {}
    public void setRowSelectionAllowed(boolean rowSelectionAllowed) {}
    public void setColumnSelectionAllowed(boolean columnSelectionAllowed) {}

    public TableCellRenderer getDefaultRenderer(Class columnClass) { return null; }
    public TableCellEditor getDefaultEditor(Class columnClass) { return null; }
    public int convertColumnIndexToModel(int viewColumnIndex) { return viewColumnIndex; }
    public int convertColumnIndexToView(int modelColumnIndex) { return modelColumnIndex; }
    public Color getSelectionForeground() { return null; }
    public void setSelectionForeground(Color selectionForeground) {}
    public Color getSelectionBackground() { return null; }
    public void setSelectionBackground(Color selectionBackground) {}

    public Dimension getPreferredScrollableViewportSize() { return null; }
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return 0; }
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) { return 0; }
    public boolean getScrollableTracksViewportWidth() { return false; }
    public boolean getScrollableTracksViewportHeight() { return false; }
}
