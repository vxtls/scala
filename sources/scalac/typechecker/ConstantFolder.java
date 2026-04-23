/* ************************************************************************
 * Pizza constant folder
 * by Martin Odersky
 *
 * Copyright (C) 1996,97 Martin Odersky. All rights reserved.
 * Permission is hereby granted to modify and use this software for research
 * and teaching purposes. Modification for commercial purposes requires
 * prior written permission by the author.
 * The software, or modifications thereof, may be redistributed only
 * if this copyright notice stays attached.
 *************************************************************************/

package scalac.typechecker;

import scalac.Global;
import scalac.ast.Tree;
import scalac.ast.TreeGen;
import scalac.atree.AConstant;
import scalac.atree.ATypeKind;
import scalac.symtab.Definitions;
import scalac.symtab.Type;
import scalac.util.Name;
import scalac.util.Names;

public class ConstantFolder {

    //########################################################################
    // Private Fields

    /** The global definitions */
    private final Definitions definitions;

    /** The tree generator */
    private final TreeGen gen;

    //########################################################################
    // Public Constructors

    /** Initializes this instance. */
    public ConstantFolder(Global global) {
        this.definitions = global.definitions;
        this.gen = global.treeGen;
    }

    //########################################################################
    // Public Methods

    /**
     * Attempts to constant fold given tree. Returns the input tree if
     * constant folding fails.
     */
    public Tree tryToFold(Tree tree) {
        AConstant value = evaluate(tree);
        return value != null ? gen.Literal(tree.pos, value) : tree;
    }

    /**
     * Evaluates the expression represented by the tree and returns
     * the resulting value. Returns null if the evaluation can't be
     * performed.
     */
    public AConstant evaluate(Tree tree) {
        if (tree instanceof Tree.TypeApply &&
            ((Tree.TypeApply) tree).fun instanceof Tree.Select) {
            Tree qualifier = ((Tree.Select) ((Tree.TypeApply) tree).fun).qualifier;
            Name op = ((Tree.Select) ((Tree.TypeApply) tree).fun).selector;
            Tree[] args = ((Tree.TypeApply) tree).args;
            if (qualifier.type() instanceof Type.ConstantType) {
                AConstant lvalue = ((Type.ConstantType) qualifier.type()).value;
                if (args.length == 1 && op == Names.asInstanceOf) {
                    return cast(lvalue, args[0].type());
                }
            }
            return null;
        }
        if (tree instanceof Tree.Apply &&
            ((Tree.Apply) tree).fun instanceof Tree.Select) {
            Tree qualifier = ((Tree.Select) ((Tree.Apply) tree).fun).qualifier;
            Name op = ((Tree.Select) ((Tree.Apply) tree).fun).selector;
            Tree[] args = ((Tree.Apply) tree).args;
            if (qualifier.type() instanceof Type.ConstantType) {
                AConstant lvalue = ((Type.ConstantType) qualifier.type()).value;
                if (args.length == 0) {
                    return evaluate(op, lvalue);
                }
                if (args.length == 1 && args[0].type() instanceof Type.ConstantType) {
                    AConstant rvalue = ((Type.ConstantType) args[0].type()).value;
                    return evaluate(op, lvalue, rvalue);
                }
            }
        }
        return null;
    }

    /**
     * Evaluates the unary operation and returns the resulting value.
     * Returns null if the evaluation can't be performed.
     */
    public AConstant evaluate(Name op, AConstant value) {
        ATypeKind kind = kind(value);
        if (kind == ATypeKind.BOOL) {
            boolean v = value.booleanValue();
            if (op == Names.ZNOT) return AConstant.BOOLEAN(!v);
            return null;
        }
        if (kind == ATypeKind.I4) {
            int v = value.intValue();
            if (op == Names.ADD) return AConstant.INT(+v);
            if (op == Names.SUB) return AConstant.INT(-v);
            if (op == Names.NOT) return AConstant.INT(~v);
            return null;
        }
        if (kind == ATypeKind.I8) {
            long v = value.longValue();
            if (op == Names.ADD) return AConstant.LONG(+v);
            if (op == Names.SUB) return AConstant.LONG(-v);
            if (op == Names.NOT) return AConstant.LONG(~v);
            return null;
        }
        if (kind == ATypeKind.R4) {
            float v = value.floatValue();
            if (op == Names.ADD) return AConstant.FLOAT(+v);
            if (op == Names.SUB) return AConstant.FLOAT(-v);
            return null;
        }
        if (kind == ATypeKind.R8) {
            double v = value.doubleValue();
            if (op == Names.ADD) return AConstant.DOUBLE(+v);
            if (op == Names.SUB) return AConstant.DOUBLE(-v);
            return null;
        }
        return null;
    }

    /**
     * Evaluates the binary operation and returns the resulting value.
     * Returns null if the evaluation can't be performed.
     */
    public AConstant evaluate(Name op, AConstant lvalue, AConstant rvalue) {
        ATypeKind kind = kind(lvalue, rvalue);
        if (kind == null) return null;
        if (kind == ATypeKind.I4) {
            int l = lvalue.intValue();
            int r = rvalue.intValue();
            if (op == Names.OR ) return AConstant.INT(l | r);
            if (op == Names.AND) return AConstant.INT(l & r);
            if (op == Names.XOR) return AConstant.INT(l ^ r);
            if (op == Names.ADD) return AConstant.INT(l + r);
            if (op == Names.SUB) return AConstant.INT(l - r);
            if (op == Names.MUL) return AConstant.INT(l * r);
            if (op == Names.DIV) return r == 0 ? null : AConstant.INT(l / r);
            if (op == Names.MOD) return r == 0 ? null : AConstant.INT(l % r);
            if (op == Names.EQ ) return AConstant.BOOLEAN(l == r);
            if (op == Names.NE ) return AConstant.BOOLEAN(l != r);
            if (op == Names.LT ) return AConstant.BOOLEAN(l <  r);
            if (op == Names.GT ) return AConstant.BOOLEAN(l >  r);
            if (op == Names.LE ) return AConstant.BOOLEAN(l <= r);
            if (op == Names.GE ) return AConstant.BOOLEAN(l >= r);
            if (op == Names.LSL) return AConstant.INT(l <<  r);
            if (op == Names.LSR) return AConstant.INT(l >>> r);
            if (op == Names.ASR) return AConstant.INT(l >>  r);
            return null;
        }
        if (kind == ATypeKind.I8) {
            long l = lvalue.longValue();
            long r = rvalue.longValue();
            if (op == Names.OR ) return AConstant.LONG(l | r);
            if (op == Names.AND) return AConstant.LONG(l & r);
            if (op == Names.XOR) return AConstant.LONG(l ^ r);
            if (op == Names.ADD) return AConstant.LONG(l + r);
            if (op == Names.SUB) return AConstant.LONG(l - r);
            if (op == Names.MUL) return AConstant.LONG(l * r);
            if (op == Names.DIV) return r == 0 ? null : AConstant.LONG(l / r);
            if (op == Names.MOD) return r == 0 ? null : AConstant.LONG(l % r);
            if (op == Names.EQ ) return AConstant.BOOLEAN(l == r);
            if (op == Names.NE ) return AConstant.BOOLEAN(l != r);
            if (op == Names.LT ) return AConstant.BOOLEAN(l < r);
            if (op == Names.GT ) return AConstant.BOOLEAN(l > r);
            if (op == Names.LE ) return AConstant.BOOLEAN(l <= r);
            if (op == Names.GE ) return AConstant.BOOLEAN(l >= r);
            if (kind(lvalue) == ATypeKind.I4) {
                int li = lvalue.intValue();
                if (op == Names.LSL) return AConstant.INT(li <<  r);
                if (op == Names.LSR) return AConstant.INT(li >>> r);
                if (op == Names.ASR) return AConstant.INT(li >>  r);
            } else {
                if (op == Names.LSL) return AConstant.LONG(l <<  r);
                if (op == Names.LSR) return AConstant.LONG(l >>> r);
                if (op == Names.ASR) return AConstant.LONG(l >>  r);
            }
            return null;
        }
        if (kind == ATypeKind.R4) {
            float l = lvalue.floatValue();
            float r = rvalue.floatValue();
            if (op == Names.ADD) return AConstant.FLOAT(l + r);
            if (op == Names.SUB) return AConstant.FLOAT(l - r);
            if (op == Names.MUL) return AConstant.FLOAT(l * r);
            if (op == Names.DIV) return AConstant.FLOAT(l / r);
            if (op == Names.MOD) return AConstant.FLOAT(l % r);
            if (op == Names.EQ ) return AConstant.BOOLEAN(l == r);
            if (op == Names.NE ) return AConstant.BOOLEAN(l != r);
            if (op == Names.LT ) return AConstant.BOOLEAN(l < r);
            if (op == Names.GT ) return AConstant.BOOLEAN(l > r);
            if (op == Names.LE ) return AConstant.BOOLEAN(l <= r);
            if (op == Names.GE ) return AConstant.BOOLEAN(l >= r);
            return null;
        }
        if (kind == ATypeKind.R8) {
            double l = lvalue.doubleValue();
            double r = rvalue.doubleValue();
            if (op == Names.ADD) return AConstant.DOUBLE(l + r);
            if (op == Names.SUB) return AConstant.DOUBLE(l - r);
            if (op == Names.MUL) return AConstant.DOUBLE(l * r);
            if (op == Names.DIV) return AConstant.DOUBLE(l / r);
            if (op == Names.MOD) return AConstant.DOUBLE(l % r);
            if (op == Names.EQ ) return AConstant.BOOLEAN(l == r);
            if (op == Names.NE ) return AConstant.BOOLEAN(l != r);
            if (op == Names.LT ) return AConstant.BOOLEAN(l <  r);
            if (op == Names.GT ) return AConstant.BOOLEAN(l >  r);
            if (op == Names.LE ) return AConstant.BOOLEAN(l <= r);
            if (op == Names.GE ) return AConstant.BOOLEAN(l >= r);
            return null;
        }
        if (kind == ATypeKind.BOOL) {
            boolean l = lvalue.booleanValue();
            boolean r = rvalue.booleanValue();
            if (op == Names.ZOR ) return AConstant.BOOLEAN(l |  r);
            if (op == Names.OR  ) return AConstant.BOOLEAN(l |  r);
            if (op == Names.ZAND) return AConstant.BOOLEAN(l &  r);
            if (op == Names.AND ) return AConstant.BOOLEAN(l &  r);
            if (op == Names.XOR ) return AConstant.BOOLEAN(l ^  r);
            if (op == Names.EQ  ) return AConstant.BOOLEAN(l == r);
            if (op == Names.NE  ) return AConstant.BOOLEAN(l != r);
            return null;
        }
        if (kind == ATypeKind.STR) {
            if (op == Names.ADD)
                return AConstant.STRING(lvalue.stringValue()+rvalue.stringValue());
            return null;
        }
        return null;
    }

    /**
     * Casts the value to given type and returns the resulting value.
     * Returns null if the cast can't be performed.
     */
    public AConstant cast(AConstant value, Type type) {
        ATypeKind kind = value.kind();
        if (kind == ATypeKind.UNIT) {
            if (type.isSameAs(definitions.UNIT_CLASS.type()))
                return value;
            return null;
        }
        if (kind == ATypeKind.BOOL) {
            if (type.isSameAs(definitions.BOOLEAN_TYPE()))
                return value;
            return null;
        }
        if (kind == ATypeKind.U1 || kind == ATypeKind.U2 || kind == ATypeKind.U4
            || kind == ATypeKind.U8 || kind == ATypeKind.I1 || kind == ATypeKind.I2
            || kind == ATypeKind.I4 || kind == ATypeKind.I8
            || kind == ATypeKind.R4 || kind == ATypeKind.R8) {
            if (type.isSameAs(definitions.BYTE_TYPE()))
                return AConstant.BYTE(value.byteValue());
            if (type.isSameAs(definitions.SHORT_TYPE()))
                return AConstant.SHORT(value.shortValue());
            if (type.isSameAs(definitions.CHAR_TYPE()))
                return AConstant.CHAR(value.charValue());
            if (type.isSameAs(definitions.INT_TYPE()))
                return AConstant.INT(value.intValue());
            if (type.isSameAs(definitions.LONG_TYPE()))
                return AConstant.LONG(value.longValue());
            if (type.isSameAs(definitions.FLOAT_TYPE()))
                return AConstant.FLOAT(value.floatValue());
            if (type.isSameAs(definitions.DOUBLE_TYPE()))
                return AConstant.DOUBLE(value.doubleValue());
            return null;
        }
        if (kind == ATypeKind.STR) {
            if (type.isSameAs(definitions.JAVA_STRING_TYPE()))
                return value;
            return null;
        }
        return null;
    }

    //########################################################################
    // Private Methods

    /** Returns the kind of given value. */
    private ATypeKind kind(AConstant value) {
        ATypeKind kind = value.kind();
        if (kind == ATypeKind.I1 || kind == ATypeKind.I2 || kind == ATypeKind.U2) {
            return ATypeKind.I4;
        }
        return kind;
    }

    /**
     * Returns the combined kind of given values or null if the values
     * can't be combined.
     */
    private ATypeKind kind(AConstant lvalue, AConstant rvalue) {
        ATypeKind lkind = kind(lvalue);
        ATypeKind rkind = kind(rvalue);
        if (lkind == rkind) return lkind;
        if (lkind == ATypeKind.ZERO) return null;
        if (rkind == ATypeKind.ZERO) return null;
        if (lkind == ATypeKind.STR) return lkind;
        if (rkind == ATypeKind.STR) return rkind;
        if (lkind == ATypeKind.I4) {
            if (rkind == ATypeKind.I4) return lkind;
            if (rkind == ATypeKind.I8) return rkind;
            if (rkind == ATypeKind.R4) return rkind;
            if (rkind == ATypeKind.R8) return rkind;
            return null;
        }
        if (lkind == ATypeKind.I8) {
            if (rkind == ATypeKind.I4) return lkind;
            if (rkind == ATypeKind.I8) return lkind;
            if (rkind == ATypeKind.R4) return rkind;
            if (rkind == ATypeKind.R8) return rkind;
            return null;
        }
        if (lkind == ATypeKind.R4) {
            if (rkind == ATypeKind.I4) return lkind;
            if (rkind == ATypeKind.I8) return lkind;
            if (rkind == ATypeKind.R4) return lkind;
            if (rkind == ATypeKind.R8) return rkind;
            return null;
        }
        if (lkind == ATypeKind.R8) {
            if (rkind == ATypeKind.I4) return lkind;
            if (rkind == ATypeKind.I8) return lkind;
            if (rkind == ATypeKind.R4) return lkind;
            if (rkind == ATypeKind.R8) return lkind;
            return null;
        }
        return null;
    }

    //########################################################################
}
