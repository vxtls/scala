package scala.tools.nsc.backend.jvm;

import ch.epfl.lamp.fjbg.FJBGContext;
import ch.epfl.lamp.fjbg.JConstantPool;

public class ScalaFJBGContext extends FJBGContext {
    public JConstantPool JConstantPool() {
        return new ScalaJConstantPool(this);
    }

    private static final class ScalaJConstantPool extends JConstantPool {
        ScalaJConstantPool(FJBGContext context) {
            super(context);
        }

        protected int addEntry(EntryValue e) {
            assert !frozen;
            Integer idx = (Integer)entryToIndex.get(e);
            if (idx != null) return idx.intValue();

            e.addChildren();

            int index = currIndex;
            currIndex += e.getSize();

            entryToIndex.put(e, new Integer(index));
            while (index + e.getSize() > indexToEntry.length) {
                Entry[] newI2E = new Entry[indexToEntry.length * 2];
                System.arraycopy(indexToEntry, 0, newI2E, 0, indexToEntry.length);
                indexToEntry = newI2E;
            }
            indexToEntry[index] = e;
            return index;
        }
    }
}
