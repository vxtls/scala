/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
**                                                                      **
** $Id$
\*                                                                      */

package scalac.transformer.matching;

import ch.epfl.lamp.util.Position;
import scalac.*;
import scalac.ast.*;
import scalac.symtab.*;
import scalac.typechecker.*;


/** Intermediate data structure for algebraic + pattern matcher
 */
public class PatternNode {
    public int pos = Position.FIRSTPOS;
    public Type type;
    public PatternNode or;
    public PatternNode and;

    public static class Header extends PatternNode {
        public Tree selector;
        public Header next;

        public Header(Tree selector, Header next) {
            this.selector = selector;
            this.next = next;
        }
    }
    public static class Body extends PatternNode {
        public Tree.ValDef[][] bound;
        public Tree[] guard;
        public Tree[] body;

        public Body(Tree.ValDef[][] bound, Tree[] guard, Tree[] body) {
            this.bound = bound;
            this.guard = guard;
            this.body = body;
        }
    }
    public static class DefaultPat extends PatternNode {
        public DefaultPat() {
        }
    }
    public static class ConstrPat extends PatternNode {
        public Symbol casted;

        public ConstrPat(Symbol casted) {
            this.casted = casted;
        }
    }
    public static class ConstantPat extends PatternNode {
        public Object value;

        public ConstantPat(Object value) {
            this.value = value;
        }
    }
    public static class VariablePat extends PatternNode {
        public Tree tree;

        public VariablePat(Tree tree) {
            this.tree = tree;
        }
    }
    public static class AltPat extends PatternNode {
        public Header subheader;

        public AltPat(Header subheader) {
            this.subheader = subheader;
        }
    }
    public static class SequencePat extends PatternNode { // only used in PatternMatcher
        public Symbol casted;
        public int len;

        public SequencePat(Symbol casted, int len) {
            this.casted = casted;
            this.len = len;
        }
    }
    public static class SeqContainerPat extends PatternNode { //   in AlgebraicMatcher
        public Symbol casted;
        public Tree seqpat;

        public SeqContainerPat(Symbol casted, Tree seqpat) {
            this.casted = casted;
            this.seqpat = seqpat;
        }
    }

    public PatternNode dup() {
    	PatternNode res;
        if (this instanceof Header) {
            Header header = (Header)this;
            res = new Header(header.selector, header.next);
        } else if (this instanceof Body) {
            Body body = (Body)this;
            res = new Body(body.bound, body.guard, body.body);
        } else if (this instanceof DefaultPat) {
            res = new DefaultPat();
        } else if (this instanceof ConstrPat) {
            res = new ConstrPat(((ConstrPat)this).casted);
        } else if (this instanceof SequencePat) {
            SequencePat sequencePat = (SequencePat)this;
            res = new SequencePat(sequencePat.casted, sequencePat.len);
        } else if (this instanceof SeqContainerPat) {
            SeqContainerPat seqContainerPat = (SeqContainerPat)this;
            res = new SeqContainerPat(seqContainerPat.casted, seqContainerPat.seqpat);
        } else if (this instanceof ConstantPat) {
            res = new ConstantPat(((ConstantPat)this).value);
        } else if (this instanceof VariablePat) {
            res = new VariablePat(((VariablePat)this).tree);
        } else if (this instanceof AltPat) {
            res = new AltPat(((AltPat)this).subheader);
        } else {
            throw new ApplicationError();
        }
    	res.pos = pos;
   		res.type = type;
   		res.or = or;
   		res.and = and;
   		return res;
    }

    public Symbol symbol() {
        if (this instanceof ConstrPat) {
            return ((ConstrPat)this).casted;
        }
        if (this instanceof SequencePat) {
            return ((SequencePat)this).casted;
        }
        if (this instanceof SeqContainerPat) {
            return ((SeqContainerPat)this).casted;
        }
        return Symbol.NONE;
    }

    public PatternNode next() {
        if (this instanceof Header) {
            return ((Header)this).next;
        }
        return null;
    }

    public final boolean isDefaultPat() {
        return this instanceof DefaultPat;
    }

    /** returns true if
     *  p and q are equal (constructor | sequence) type tests, or
     *  "q matches" => "p matches"
     */
    public final boolean isSameAs( PatternNode q ) {
        if (this instanceof ConstrPat) {
            if (q instanceof ConstrPat) {
                return q.type.isSameAs( this.type );
            }
            return false;
        }
        if (this instanceof SequencePat) {
            int plen = ((SequencePat)this).len;
            if (q instanceof SequencePat) {
                int qlen = ((SequencePat)q).len;
                return (plen == qlen) && q.type.isSameAs( this.type );
            }
            return false;
        }
        return subsumes( q );
    }

    /** returns true if "q matches" => "p matches"
     */
    public final boolean subsumes( PatternNode q ) {
        if (this instanceof DefaultPat) {
            if (q instanceof DefaultPat) {
                return true;
            }
            return false;
        }
        if (this instanceof ConstrPat) {
            if (q instanceof ConstrPat) {
                return q.type.isSubType(this.type);
            }
            return false;
        }
        if (this instanceof SequencePat) {
            int plen = ((SequencePat)this).len;
            if (q instanceof SequencePat) {
                int qlen = ((SequencePat)q).len;
                return (plen == qlen) && q.type.isSubType(this.type);
            }
            return false;
        }
        if (this instanceof ConstantPat) {
            Object pval = ((ConstantPat)this).value;
            if (q instanceof ConstantPat) {
                Object qval = ((ConstantPat)q).value;
                return pval.equals(qval);
            }
            return false;
        }
        if (this instanceof VariablePat) {
            Tree tree = ((VariablePat)this).tree;
            if (q instanceof VariablePat) {
                Tree other = ((VariablePat)q).tree;
                return (tree.symbol() != null) &&
                    (tree.symbol().kind != Kinds.NONE) &&
                    (tree.symbol().kind != Kinds.ERROR) &&
                    (tree.symbol() == other.symbol());
            }
            return false;
        }
        return false;
    }

    public String toString() {
        if (this instanceof Header) {
            Tree selector = ((Header)this).selector;
                return "Header(" + selector + ")";
        }
        if (this instanceof Body) {
                return "Body";
        }
        if (this instanceof DefaultPat) {
                return "DefaultPat";
        }
        if (this instanceof ConstrPat) {
            Symbol casted = ((ConstrPat)this).casted;
                return "ConstrPat(" + casted + ")";
        }
        if (this instanceof SequencePat) {
            SequencePat sequencePat = (SequencePat)this;
            return "SequencePat(" + sequencePat.casted + ", " + sequencePat.len + "...)";
        }
        if (this instanceof SeqContainerPat) {
            SeqContainerPat seqContainerPat = (SeqContainerPat)this;
            return "SeqContainerPat(" + seqContainerPat.casted + ", " + seqContainerPat.seqpat + ")";
        }
        if (this instanceof ConstantPat) {
            Object value = ((ConstantPat)this).value;
                return "ConstantPat(" + value + ")";
        }
        if (this instanceof VariablePat) {
                return "VariablePat";
        }
        return "<unknown pat>";
    }
}
