/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.symtab;

import scala.tools.util.AbstractFile;
import scalac.CompilationUnit;

/** Instances of this class designate the origin of a symbol. */
public class SymbolOrigin {

    /** Designates an unknown source. */
    public static final SymbolOrigin Unknown = new Unknown();

    public static SymbolOrigin Directory(AbstractFile file) {
        return new Directory(file);
    }

    public static SymbolOrigin ClassFile(AbstractFile file, String sourcefile) {
        return new ClassFile(file, sourcefile);
    }

    public static SymbolOrigin SymblFile(AbstractFile file) {
        return new SymblFile(file);
    }

    public static SymbolOrigin ScalaFile(AbstractFile file) {
        return new ScalaFile(file);
    }

    public static SymbolOrigin ScalaUnit(CompilationUnit unit) {
        return new ScalaUnit(unit);
    }

    /** Records the source file attribute. */
    public void setSourceFileAttribute(String sourcefile) {
    }

    private static final class Unknown extends SymbolOrigin {
        private Unknown() {
        }
    }

    public static final class Directory extends SymbolOrigin {
        public final AbstractFile file;

        private Directory(AbstractFile file) {
            this.file = file;
        }
    }

    public static final class ClassFile extends SymbolOrigin {
        public final AbstractFile file;
        public String sourcefile;

        private ClassFile(AbstractFile file, String sourcefile) {
            this.file = file;
            this.sourcefile = sourcefile;
        }

        public void setSourceFileAttribute(String sourcefile) {
            this.sourcefile = sourcefile;
        }
    }

    public static final class SymblFile extends SymbolOrigin {
        public final AbstractFile file;

        private SymblFile(AbstractFile file) {
            this.file = file;
        }
    }

    public static final class ScalaFile extends SymbolOrigin {
        public final AbstractFile file;

        private ScalaFile(AbstractFile file) {
            this.file = file;
        }
    }

    public static final class ScalaUnit extends SymbolOrigin {
        public final CompilationUnit unit;

        private ScalaUnit(CompilationUnit unit) {
            this.unit = unit;
        }
    }
}
