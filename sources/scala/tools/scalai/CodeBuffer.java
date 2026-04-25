/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $OldId: CodeBuffer.java,v 1.3 2002/06/13 15:24:14 paltherr Exp $
// $Id$

package scala.tools.scalai;

public class CodeBuffer {

    //########################################################################
    // Private Fields

    private Code[] buffer;
    private int size;

    //########################################################################
    // Public Constructors

    public CodeBuffer() {
        this.buffer = null;
        this.size = 0;
    }

    //########################################################################
    // Public Methods

    public CodeBuffer append(Code code) {
        if (code instanceof Code.Block) {
            Code.Block block = (Code.Block)code;
            insure(block.stats.length + 1);
            for (int i = 0; i < block.stats.length; i++) buffer[size++] = block.stats[i];
            buffer[size++] = block.value;
            return this;
        }
        insure(1);
        buffer[size++] = code;
        return this;
    }

    public Code code(Code value) {
        if (size == 0) return value;
        Code[] stats = buffer;
        if (size != stats.length) {
            Code[] array = new Code[size];
            System.arraycopy(stats, 0, array, 0, size);
            stats = array;
        }
        return Code.Block(stats, value);
    }

    //########################################################################
    // Private Methods

    private void insure(int count) {
        if (buffer == null) {
            buffer = new Code[count];
        } else {
            if (size + count < buffer.length) return;
            Code[] array = new Code[size + Math.max(count, size)];
            System.arraycopy(buffer, 0, array, 0, size);
            buffer = array;
        }
    }

    //########################################################################
}
