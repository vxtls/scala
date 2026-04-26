/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.backend.jvm;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;

import ch.epfl.lamp.fjbg.*;
import ch.epfl.lamp.util.Pair;

import scala.tools.util.Position;

import scalac.*;
import scalac.ast.Tree;
import scalac.ast.TreeInfo;
import scalac.atree.AConstant;
import scalac.backend.*;
import scalac.symtab.*;
import scalac.symtab.classfile.ClassfileConstants;
import scalac.symtab.classfile.Pickle;
import scalac.transformer.*;
import scalac.util.*;

/* Several things which are done here should in fact be done in
 * previous phases, namely:
 *
 *   - code linearisation (i.e. representation of code as a list of
 *     instructions),
 *
 *   - removal of implicit "this" selection,
 *
 *   - removal of implicit "unit" values,
 *
 *   - removal of implicit conversion,
 *
 *   - removal of "if"s without explicit "else" part,
 *
 *   - expansion of "==",
 *
 *   - introduction of a special primitive for string concatenation,
 *
 *   - initialisation of module instance variable.
 */

/**
 * Backend generating JVM byte-codes.
 *
 * @version 2.0
 * @author Michel Schinz
 */

public class GenJVM {
    protected final static String JAVA_LANG_OBJECT = "java.lang.Object";
    protected final static String JAVA_LANG_STRING = "java.lang.String";
    protected final static String JAVA_LANG_STRINGBUFFER =
        "java.lang.StringBuffer";
    protected final static String JAVA_RMI_REMOTE = "java.rmi.Remote";
    protected final static String JAVA_RMI_REMOTEEXCEPTION =
        "java.rmi.RemoteException";

    protected final static String SCALA_RUNTIME_RUNTIME =
        "scala.runtime.RunTime";
    protected final static String SCALA_UNIT = "scala.Unit";

    protected final static String SCALA_ATTR =
        ClassfileConstants.SCALA_N.toString();

    protected final static String MODULE_INSTANCE_FIELD_NAME = "MODULE$";

    protected final static String CONSTRUCTOR_STRING =
        "<init>";               // TODO get it from FJBG

    protected final JObjectType JAVA_LANG_OBJECT_T =
        new JObjectType(JAVA_LANG_OBJECT);
    protected final JObjectType JAVA_LANG_STRING_T =
        new JObjectType(JAVA_LANG_STRING);
    protected final JObjectType JAVA_LANG_STRINGBUFFER_T =
        new JObjectType (JAVA_LANG_STRINGBUFFER);
    protected final JObjectType JAVA_LANG_THROWABLE_T =
        new JObjectType("java.lang.Throwable");
    protected final JObjectType SCALA_UNIT_T =
        new JObjectType(SCALA_UNIT);

    protected final Symbol JAVA_RMI_REMOTE_CLASS;
    protected final Symbol SCALA_SERIAL_VERSION_UID_CONSTR;
    protected final Symbol SCALA_TRANSIENT_CONSTR;
    protected final Symbol SCALA_VOLATILE_CONSTR;

    protected final Global global;
    protected final Definitions defs;
    protected final Primitives prims;
    protected final ArrayList pickles = new ArrayList();

    protected final Phase refCheckPhase;
    protected final AddInterfacesPhase addInterfacesPhase;

    protected final FJBGContext fjbgContext;

    public GenJVM(Global global) {
        this.global = global;
        this.defs = global.definitions;
        this.prims = global.primitives;

        this.refCheckPhase = global.PHASE.REFCHECK.phase();
        this.addInterfacesPhase =
            (AddInterfacesPhase)global.PHASE.ADDINTERFACES.phase();

        this.fjbgContext = new FJBGContext();

        initTypeMap();
        initArithPrimMap();

        JAVA_RMI_REMOTE_CLASS = defs.getClass(JAVA_RMI_REMOTE);
        SCALA_SERIAL_VERSION_UID_CONSTR =
            defs.getClass("scala.SerialVersionUID").primaryConstructor();
        SCALA_TRANSIENT_CONSTR =
            defs.getClass("scala.transient").primaryConstructor();
        SCALA_VOLATILE_CONSTR =
            defs.getClass("scala.volatile").primaryConstructor();
    }

    /// Code generation
    //////////////////////////////////////////////////////////////////////

    /** Generate code for the given units. */
    public static void translate(Global global, CompilationUnit[] units) {
        GenJVM translator = new GenJVM(global);
        for (int i = 0; i < units.length; i++) translator.translate(units[i]);
    }

    /**
     * Generate code for the given unit.
     */
    public void translate(CompilationUnit unit) {
        try {
            for (int i = 0; i < unit.body.length; ++i)
                gen(Context.EMPTY.withSourceFileName(unit.source.getFile().getName()),
                    unit.body[i]);
        } catch (JCode.OffsetTooBigException e) {
            if (global.debug) e.printStackTrace();
            global.error(e.getMessage());
        }
        for (int i = 0; i < pickles.size() / 2; i++) {
            JClass clazz = (JClass)pickles.get(2 * i + 0);
            Pickle pickle = (Pickle)pickles.get(2 * i + 1);
            writeSymblFile(clazz, pickle);
        }
        pickles.clear();
    }

    /**
     * Generate code to perform the side effects associated with the
     * given tree (i.e. no value should remain on the stack
     * afterwards).
     */
    protected void gen(Context ctx, Tree tree) throws JCode.OffsetTooBigException {
        startCodeForTree(ctx, tree);

        Symbol sym = tree.symbol();

        if (tree instanceof Tree.PackageDef) {
            gen(ctx, ((Tree.PackageDef)tree).impl);
        } else if (tree instanceof Tree.ClassDef) {
            Tree.ClassDef classDef = (Tree.ClassDef)tree;
            Context ctx1 = enterClass(ctx, sym);

            addValueClassMembers(ctx1, classDef);
            gen(ctx1, classDef.impl);
            AConstant[] aargs = global.getAttrArguments(
                sym, SCALA_SERIAL_VERSION_UID_CONSTR);
            if (ctx1.isModuleClass || aargs != null) {
                JMethod clinit = getClassConstructorMethod(ctx1);
                if (clinit.getCode().getSize() == 0) {
                    Context ctx2 =
                        ctx1.withMethod(clinit, Collections.EMPTY_MAP, false);
                    completeClassConstructor(ctx2, sym);
                    ctx2.code.emitRETURN();
                    genLocalVariableTable(ctx2);
                }
            }
            leaveClass(ctx1, sym);
        } else if (tree instanceof Tree.Template) {
            gen(ctx, ((Tree.Template)tree).body);
        } else if (tree instanceof Tree.ValDef) {
            Tree.ValDef valDef = (Tree.ValDef)tree;
            if (ctx.method != null) {
                JType valType = typeStoJ(sym.info());
                JLocalVariable var =
                    ctx.method.addNewLocalVariable(valType, valDef.name.toString());
                ctx.locals.put(sym, new Integer(var.getIndex()));

                assert (valDef.rhs != Tree.Empty) : Debug.show(sym);
                genLoad(ctx, valDef.rhs, valType);
                ctx.code.emitSTORE(var);
            }
        } else if (tree instanceof Tree.DefDef) {
            Tree.DefDef defDef = (Tree.DefDef)tree;
            boolean retry = false;
            do {
                Context ctx1 = enterMethod(ctx, defDef, retry);
                try {
                    if (! Modifiers.Helper.isAbstract(sym.flags)) {
                        JType retType = ctx1.method.getReturnType();
                        genLoad(ctx1, defDef.rhs, retType);
                        if (sym.name == Names.CLASS_CONSTRUCTOR)
                            completeClassConstructor(ctx1, sym.owner());
                        ctx1.code.emitRETURN(retType);
                        ctx1.method.freeze();
                    }
                    leaveMethod(ctx1);
                    break;
                } catch (JCode.OffsetTooBigException e) {
                    ctx1.clazz.removeMethod(ctx1.method);
                    assert !retry;
                    retry = true;
                }
            } while (retry);
        } else if (tree instanceof Tree.Return) {
            Tree.Return ret = (Tree.Return)tree;
            JType retType = ctx.method.getReturnType();
            genLoad(ctx, ret.expr, retType);
            ctx.code.emitRETURN(retType);
        } else if (tree instanceof Tree.Typed) {
            gen(ctx, ((Tree.Typed)tree).expr);
        } else if (tree == Tree.Empty
                   || tree instanceof Tree.AbsTypeDef
                   || tree instanceof Tree.AliasTypeDef
                   || tree instanceof Tree.TypeApply
                   || tree instanceof Tree.FunType
                   || tree instanceof Tree.CompoundType
                   || tree instanceof Tree.AppliedType) {
            // no-op
        } else {
            genLoad(ctx, tree, JType.VOID);
        }

        endCodeForTree(ctx, tree);
    }

    protected void gen(Context ctx, Tree[] trees)
        throws JCode.OffsetTooBigException {
        for (int i = 0; i < trees.length; ++i)
            gen(ctx, trees[i]);
    }

    /**
     * Generate code to load the value of the given tree on the
     * stack, and make sure it is of the given expected type.
     */
    protected JType genLoad(Context ctx, Tree tree, JType expectedType)
        throws JCode.OffsetTooBigException {
        startCodeForTree(ctx, tree);

        JType generatedType = null;
        Symbol sym = tree.symbol();

        if (tree instanceof Tree.LabelDef) {
            Tree.LabelDef labelDef = (Tree.LabelDef)tree;
            JCode.Label label = ctx.code.newLabel();
            label.anchorToNext();
            ctx.labels.put(sym, new Pair(label, labelDef.params));
            generatedType = genLoad(ctx, labelDef.rhs, expectedType);
            ctx.labels.remove(sym);
        } else if (tree instanceof Tree.Block) {
            Tree.Block block = (Tree.Block)tree;
            for (int i = 0; i < block.stats.length; ++i)
                gen(ctx, block.stats[i]);
            genLoad(ctx, block.expr, expectedType);
            generatedType = expectedType;
        } else if (tree instanceof Tree.Typed) {
            genLoad(ctx, ((Tree.Typed)tree).expr, expectedType);
            generatedType = expectedType;
        } else if (tree instanceof Tree.New) {
            Tree init = ((Tree.New)tree).init;
            String className = javaName(newClassSymbol((Tree.New)tree));
            ctx.code.emitNEW(className);
            ctx.code.emitDUP();
            genLoad(ctx, init, JType.VOID);
            generatedType = new JObjectType(className);
        } else if (tree instanceof Tree.Apply
                   && ((Tree.Apply)tree).fun instanceof Tree.TypeApply
                   && (((Tree.TypeApply)((Tree.Apply)tree).fun).fun.symbol() == defs.ANY_IS_ERASED
                       || ((Tree.TypeApply)((Tree.Apply)tree).fun).fun.symbol() == defs.ANY_AS_ERASED)) {
            Tree.Apply apply = (Tree.Apply)tree;
            Tree.TypeApply typeApply = (Tree.TypeApply)apply.fun;
            Tree fun = typeApply.fun;
            Tree[] args = typeApply.args;

            genLoadQualifier(ctx, fun, true);

            JType type = typeStoJ(args[0].type);
            if (fun.symbol() == defs.ANY_IS_ERASED) {
                ctx.code.emitINSTANCEOF((JReferenceType)type);
                generatedType = JType.BOOLEAN;
            } else if (fun.symbol() == defs.ANY_AS_ERASED) {
                ctx.code.emitCHECKCAST((JReferenceType)type);
                generatedType = type;
            } else {
                throw Debug.abort("unexpected type application", tree);
            }
        } else if (tree instanceof Tree.Apply) {
            Tree.Apply apply = (Tree.Apply)tree;
            Tree fun = apply.fun;
            if (fun instanceof Tree.TypeApply) fun = ((Tree.TypeApply)fun).fun;
            Tree[] args = apply.args;
            Symbol funSym = fun.symbol();

            if (funSym.isLabel()) {
                Pair/*<Label, Tree[]>*/ labelAndIdents =
                    (Pair)ctx.labels.get(funSym);
                assert labelAndIdents != null : Debug.show(funSym);
                JMethodType funType = (JMethodType)typeStoJ(funSym.info());

                JCode.Label label = (JCode.Label)labelAndIdents.fst;
                Tree[] idents = (Tree[])labelAndIdents.snd;
                assert idents.length == args.length;

                for (int i = 0; i < args.length; ++i)
                    genLoad(ctx, args[i], typeStoJ(args[i].type));
                for (int i = idents.length; i > 0; --i)
                    genStoreEpilogue(ctx, idents[i - 1]);
                ctx.code.emitGOTO_maybe_W(label, ctx.useWideJumps);
                generatedType = funType.getReturnType();
            } else if (isKnownPrimitive(funSym)) {
                Primitive prim = prims.getPrimitive(funSym);

                switch (prim) {
                case CONCAT:
                    genStringConcatenation(ctx, liftStringConcatenations(tree));
                    generatedType = JAVA_LANG_STRING_T;
                    break;

                case POS: case NEG:
                case ADD: case SUB: case MUL: case DIV: case MOD:
                case NOT: case OR : case XOR: case AND:
                case LSL: case LSR: case ASR:
                    Tree[] allArgs = extractPrimitiveArgs(apply);
                    allArgs[0] = unbox(allArgs[0]);
                    JType resType = typeStoJ(tree.type);
                    genArithPrim(ctx, prim, allArgs, resType, expectedType);
                    generatedType = resType;
                    break;

                case ID: case NI:
                case EQ: case NE: case LT: case LE: case GE: case GT:
                case ZNOT: case ZOR: case ZAND:
                    JCode.Label falseLabel = ctx.code.newLabel();
                    JCode.Label afterLabel = ctx.code.newLabel();
                    genCond(ctx, tree, falseLabel, false);
                    ctx.code.emitICONST_1();
                    ctx.code.emitGOTO_maybe_W(afterLabel, ctx.useWideJumps);
                    falseLabel.anchorToNext();
                    ctx.code.emitICONST_0();
                    afterLabel.anchorToNext();
                    generatedType = JType.BOOLEAN;
                    break;

                case THROW:
                    assert args.length == 0;
                    genThrow(ctx, ((Tree.Select)fun).qualifier);
                    genUnreachableValue(ctx, expectedType);
                    generatedType = expectedType;
                    break;

                case SYNCHRONIZED:
                    assert args.length == 1;
                    genSynchronized(ctx, ((Tree.Select)fun).qualifier, args[0], expectedType);
                    generatedType = expectedType;
                    break;

                case NEW_OARRAY:
                    assert args.length == 2;
                    genRefArrayCreate(ctx, args[0], args[1]);
                    generatedType = expectedType;
                    break;

                case NEW_ZARRAY : case NEW_BARRAY : case NEW_SARRAY :
                case NEW_CARRAY : case NEW_IARRAY : case NEW_LARRAY :
                case NEW_FARRAY : case NEW_DARRAY :
                    assert args.length == 1;
                    genArrayCreate(ctx, prim, args[0]);
                    generatedType = JAVA_LANG_OBJECT_T; // TODO refine
                    break;

                case ZARRAY_SET : case BARRAY_SET : case SARRAY_SET :
                case CARRAY_SET : case IARRAY_SET : case LARRAY_SET :
                case FARRAY_SET : case DARRAY_SET : case OARRAY_SET :
                    assert args.length == 3;
                    genArrayUpdate(ctx, args[0], args[1], args[2]);
                    generatedType = JType.VOID;
                    break;

                case ZARRAY_GET : case BARRAY_GET : case SARRAY_GET :
                case CARRAY_GET : case IARRAY_GET : case LARRAY_GET :
                case FARRAY_GET : case DARRAY_GET : case OARRAY_GET :
                    assert args.length == 2 : "get - " + args.length;
                    genArrayAccess(ctx, args[0], args[1]);
                    generatedType = getArrayElementType(args[0]);
                    break;

                case ZARRAY_LENGTH : case BARRAY_LENGTH : case SARRAY_LENGTH :
                case CARRAY_LENGTH : case IARRAY_LENGTH : case LARRAY_LENGTH :
                case FARRAY_LENGTH : case DARRAY_LENGTH : case OARRAY_LENGTH :
                    assert args.length == 1 : args.length;
                    genArrayLength(ctx, args[0]);
                    generatedType = JType.INT;
                    break;

                case B2B: case B2S: case B2C: case B2I: case B2L: case B2F: case B2D:
                case S2B: case S2S: case S2C: case S2I: case S2L: case S2F: case S2D:
                case C2B: case C2S: case C2C: case C2I: case C2L: case C2F: case C2D:
                case I2B: case I2S: case I2C: case I2I: case I2L: case I2F: case I2D:
                case L2B: case L2S: case L2C: case L2I: case L2L: case L2F: case L2D:
                case F2B: case F2S: case F2C: case F2I: case F2L: case F2F: case F2D:
                case D2B: case D2S: case D2C: case D2I: case D2L: case D2F: case D2D:
                    assert args.length == 1 : args.length;
                    JType fromType = typeStoJ(args[0].type);
                    genLoad(ctx, args[0], fromType);
                    ctx.code.emitT2T(fromType, expectedType);
                    generatedType = expectedType;
                    break;

                default:
                    throw Debug.abort("unknown primitive ", prim);
                }
            } else {
                JMethodType funType = (JMethodType)typeStoJ(funSym.info());
                JType[] argTypes = funType.getArgumentTypes();

                boolean isConstrCall = funSym.isInitializer() || funSym.isConstructor();
                boolean isSuperCall = fun instanceof Tree.Select
                    && ((Tree.Select)fun).qualifier instanceof Tree.Super;

                boolean isStatic = isStaticMember(funSym);
                if (!isStatic)
                    genLoadQualifier(ctx, fun);
                for (int i = 0; i < args.length; ++i)
                    genLoad(ctx, args[i], argTypes[i]);

                String clsName = isSuperCall
                    ? ctx.clazz.getSuperclassName()
                    : javaName(funSym.owner());
                boolean isModuleInstanceCall = !isStatic
                    && fun instanceof Tree.Select
                    && moduleInstanceClassName(((Tree.Select)fun).qualifier) != null;
                if (isModuleInstanceCall)
                    clsName = moduleInstanceClassName(((Tree.Select)fun).qualifier);
                String mthName = jvmName(funSym.name);

                funSym.owner().info(); // [HACK] ensure that flags are
                                       // transformed.

                if (!isModuleInstanceCall && funSym.owner().isInterface()) {
                    ctx.code.emitINVOKEINTERFACE(clsName, mthName, funType);
                } else {
                    if (isConstrCall || isSuperCall) {
                        ctx.code.emitINVOKESPECIAL(clsName, mthName, funType);
                        if (isConstrCall && isSuperCall && ctx.isModuleClass) {
                            ctx.code.emitALOAD_0();
                            ctx.code.emitPUTSTATIC(ctx.clazz.getName(),
                                                   MODULE_INSTANCE_FIELD_NAME,
                                                   ctx.clazz.getType());
                        }
                    } else if (isStatic) {
                        ctx.code.emitINVOKESTATIC(clsName, mthName, funType);
                    } else {
                        ctx.code.emitINVOKEVIRTUAL(clsName, mthName, funType);
                    }
                }
                generatedType = isConstrCall ? JType.VOID : funType.getReturnType();
            }
        } else if (tree instanceof Tree.Ident) {
            JType type = typeStoJ(sym.info());
            if (sym.isModule()) {
                String javaSymName = javaName(sym.moduleClass());
                ctx.code.emitGETSTATIC(javaSymName,
                                       MODULE_INSTANCE_FIELD_NAME,
                                       type);
                generatedType = type;
            } else if (sym.isStatic()) {
                JType fieldType = typeStoJ(sym.info());
                String className = javaName(sym.owner());
                String fieldName = sym.name.toString();
                ctx.code.emitGETSTATIC(className, fieldName, fieldType);
                generatedType = type;
            } else {
                assert ctx.locals.containsKey(sym)
                    : Debug.show(sym) + " not in " + ctx.locals;
                int index = ((Integer)(ctx.locals.get(sym))).intValue();
                ctx.code.emitLOAD(index, type);
                generatedType = type;
            }
        } else if (tree instanceof Tree.Select) {
            Tree.Select select = (Tree.Select)tree;
            sym.info();
            if (sym.isModule()) {
                String javaSymName = javaName(sym.moduleClass());
                JType moduleType = typeStoJ(sym.info());
                ctx.code.emitGETSTATIC(javaSymName,
                                       MODULE_INSTANCE_FIELD_NAME,
                                       moduleType);
                generatedType = moduleType;
            } else {
                JType fieldType = typeStoJ(sym.info());
                String className = javaName(sym.owner());
                String fieldName = sym.name.toString();
                if (sym.isStatic()) {
                    ctx.code.emitGETSTATIC(className, fieldName, fieldType);
                } else {
                    genLoadQualifier(ctx, tree);
                    ctx.code.emitGETFIELD(className, fieldName, fieldType);
                }
                generatedType = fieldType;
            }
        } else if (tree instanceof Tree.Assign) {
            Tree.Assign assign = (Tree.Assign)tree;
            genStorePrologue(ctx, assign.lhs);
            genLoad(ctx, assign.rhs, typeStoJ(assign.lhs.symbol().info()));
            genStoreEpilogue(ctx, assign.lhs);
            generatedType = JType.VOID;
        } else if (tree instanceof Tree.If) {
            Tree.If ifTree = (Tree.If)tree;
            JType finalType = typeStoJ(tree.type);

            JCode.Label elseLabel = ctx.code.newLabel();
            genCond(ctx, ifTree.cond, elseLabel, false);
            genLoad(ctx, ifTree.thenp, finalType);
            JCode.Label afterLabel = ctx.code.newLabel();
            if (!isThrowingTree(ifTree.thenp))
                ctx.code.emitGOTO_maybe_W(afterLabel, ctx.useWideJumps);
            elseLabel.anchorToNext();
            if (ifTree.elsep == Tree.Empty)
                maybeGenLoadUnit(ctx, finalType);
            else
                genLoad(ctx, ifTree.elsep, finalType);
            afterLabel.anchorToNext();
            generatedType = finalType;
        } else if (tree instanceof Tree.Switch) {
            Tree.Switch switchTree = (Tree.Switch)tree;
            JCode.Label[] labels = ctx.code.newLabels(switchTree.bodies.length);
            JCode.Label defaultLabel = ctx.code.newLabel();
            JCode.Label afterLabel = ctx.code.newLabel();

            genLoad(ctx, switchTree.test, JType.INT);
            ctx.code.emitSWITCH(switchTree.tags, labels, defaultLabel, 0.9);
            for (int i = 0; i < switchTree.bodies.length; ++i) {
                labels[i].anchorToNext();
                genLoad(ctx, switchTree.bodies[i], expectedType);
                ctx.code.emitGOTO_maybe_W(afterLabel, ctx.useWideJumps);
            }
            defaultLabel.anchorToNext();
            genLoad(ctx, switchTree.otherwise, expectedType);
            afterLabel.anchorToNext();
            generatedType = expectedType;
        } else if (tree instanceof Tree.This || tree instanceof Tree.Super) {
            ctx.code.emitALOAD_0();
            generatedType = JAVA_LANG_OBJECT_T;
        } else if (tree instanceof Tree.Literal) {
            AConstant value = ((Tree.Literal)tree).value;
            if (value == AConstant.UNIT) {
                maybeGenLoadUnit(ctx, expectedType);
                generatedType = expectedType;
            } else if (value instanceof AConstant.BooleanValue) {
                ctx.code.emitPUSH(((AConstant.BooleanValue)value).value);
                generatedType = JType.BOOLEAN;
            } else if (value instanceof AConstant.ByteValue) {
                ctx.code.emitPUSH(((AConstant.ByteValue)value).value);
                generatedType = JType.BYTE;
            } else if (value instanceof AConstant.ShortValue) {
                ctx.code.emitPUSH(((AConstant.ShortValue)value).value);
                generatedType = JType.SHORT;
            } else if (value instanceof AConstant.CharValue) {
                ctx.code.emitPUSH(((AConstant.CharValue)value).value);
                generatedType = JType.CHAR;
            } else if (value instanceof AConstant.IntValue) {
                ctx.code.emitPUSH(((AConstant.IntValue)value).value);
                generatedType = JType.INT;
            } else if (value instanceof AConstant.LongValue) {
                ctx.code.emitPUSH(((AConstant.LongValue)value).value);
                generatedType = JType.LONG;
            } else if (value instanceof AConstant.FloatValue) {
                ctx.code.emitPUSH(((AConstant.FloatValue)value).value);
                generatedType = JType.FLOAT;
            } else if (value instanceof AConstant.DoubleValue) {
                ctx.code.emitPUSH(((AConstant.DoubleValue)value).value);
                generatedType = JType.DOUBLE;
            } else if (value instanceof AConstant.StringValue) {
                ctx.code.emitPUSH(((AConstant.StringValue)value).value);
                generatedType = JAVA_LANG_STRING_T;
            } else if (value == AConstant.NULL) {
                if (expectedType != JType.VOID)
                    ctx.code.emitACONST_NULL();
                generatedType = expectedType;
            } else {
                throw Debug.abort("unknown literal", value);
            }
        } else if (tree == Tree.Empty
                   || tree instanceof Tree.AbsTypeDef
                   || tree instanceof Tree.AliasTypeDef
                   || tree instanceof Tree.TypeApply
                   || tree instanceof Tree.FunType
                   || tree instanceof Tree.CompoundType
                   || tree instanceof Tree.AppliedType) {
            generatedType = JType.VOID;
        } else if (tree instanceof Tree.Sequence
                   || tree instanceof Tree.ModuleDef
                   || tree instanceof Tree.PatDef
                   || tree instanceof Tree.Import
                   || tree instanceof Tree.CaseDef
                   || tree instanceof Tree.Visitor
                   || tree instanceof Tree.Function) {
            throw Debug.abort("unexpected node", tree);
        } else {
            throw Debug.abort("unknown node", tree);
        }

        // Pop unneeded result from stack, or widen it if needed.
        if (expectedType == JType.VOID && generatedType != JType.VOID) {
            if (generatedType == JType.LONG || generatedType == JType.DOUBLE)
                ctx.code.emitPOP2();
            else
                ctx.code.emitPOP();
        } else if (generatedType == JType.VOID && expectedType.isReferenceType()) {
            maybeGenLoadUnit(ctx, expectedType);
        } else if (expectedType.isReferenceType() && !generatedType.isReferenceType()) {
            genBoxValue(ctx, generatedType);
        } else if (! (expectedType == JType.VOID
                      || generatedType == expectedType
                      || generatedType.isReferenceType()))
            ctx.code.emitT2T(generatedType, expectedType);

        endCodeForTree(ctx, tree);
        return expectedType;
    }

    private boolean isThrowingTree(Tree tree) {
        if (tree.getType().symbol() == defs.ALL_CLASS)
            return true;
        if (tree instanceof Tree.Block) {
            Tree.Block block = (Tree.Block)tree;
            if (isThrowingTree(block.expr))
                return true;
            return block.stats.length != 0 &&
                isThrowingTree(block.stats[block.stats.length - 1]);
        }
        if (tree instanceof Tree.Typed)
            return isThrowingTree(((Tree.Typed)tree).expr);
        Symbol symbol = TreeInfo.methSymbol(tree);
        if (isKnownPrimitive(symbol) && prims.getPrimitive(symbol) == Primitive.THROW)
            return true;
        return false;
    }

    /**
     * Generate code to load the module represented by the given symbol.
     */
    protected JType genLoadModule(Context ctx, Symbol sym) {
        scalac.symtab.Type info = sym.info();
        Symbol moduleClass = info.symbol();
        if (!moduleClass.isModuleClass())
            moduleClass = sym.isModule() ? sym.moduleClass() : sym;
        String javaSymName = javaName(moduleClass);
        if ((moduleClass.isModuleClass() || sym.isModule()) && !javaSymName.endsWith("$"))
            javaSymName = javaSymName + "$";
        JType type = new JObjectType(javaSymName);
        if (javaSymName.equals(ctx.clazz.getName()))
            ctx.code.emitALOAD_0();
        else
            ctx.code.emitGETSTATIC(javaSymName,
                                   MODULE_INSTANCE_FIELD_NAME,
                                   type);

        return type;
    }

    /**
     * Generate code to load the qualifier of the given tree, which
     * can be implicitely "this".
     */
    protected void genLoadQualifier(Context ctx, Tree tree)
        throws JCode.OffsetTooBigException {
        genLoadQualifier(ctx, tree, false);
    }

    protected void genLoadQualifier(Context ctx, Tree tree, boolean implicitThis)
        throws JCode.OffsetTooBigException {
        if (tree instanceof Tree.Ident) {
            if (implicitThis)
                ctx.code.emitALOAD_0();
        } else if (tree instanceof Tree.Select) {
            Tree qualifier = ((Tree.Select)tree).qualifier;
            if (!(stripTypedAndErasedCast(qualifier) instanceof Tree.Create))
                genLoad(ctx, qualifier, JAVA_LANG_OBJECT_T);
        } else {
            throw Debug.abort("unknown qualifier", tree);
        }
    }

    private Symbol newClassSymbol(Tree.New tree) {
        Symbol symbol = tree.type.symbol();
        if (symbol.isClassType()) return symbol;
        Tree init = stripTypedAndErasedCast(tree.init);
        if (init instanceof Tree.Apply) init = ((Tree.Apply)init).fun;
        if (init instanceof Tree.TypeApply) init = ((Tree.TypeApply)init).fun;
        init = stripTypedAndErasedCast(init);
        if (init instanceof Tree.Select) {
            Symbol initSymbol = init.symbol();
            if (!initSymbol.isNone() && initSymbol.owner().isClassType())
                return initSymbol.owner();
            Tree qualifier = stripTypedAndErasedCast(((Tree.Select)init).qualifier);
            if (qualifier instanceof Tree.Create) return qualifier.symbol();
            Symbol qualifierSymbol = qualifier.type.symbol();
            if (qualifierSymbol.isClassType()) return qualifierSymbol;
        }
        throw Debug.abort("cannot find class for new", tree);
    }

    private String moduleInstanceClassName(Tree tree) {
        tree = stripTypedAndErasedCast(tree);
        Symbol sym = tree.symbol();
        if (sym == null)
            return null;
        if (sym.isModule())
            return ensureModuleSuffix(javaName(sym.moduleClass()));
        if (tree instanceof Tree.Select
            && MODULE_INSTANCE_FIELD_NAME.equals(((Tree.Select)tree).selector.toString())) {
            return ensureModuleSuffix(javaName(sym.owner()));
        }
        return null;
    }

    private String ensureModuleSuffix(String className) {
        return className.endsWith("$") ? className : className + "$";
    }

    private Tree stripTyped(Tree tree) {
        while (tree instanceof Tree.Typed)
            tree = ((Tree.Typed)tree).expr;
        return tree;
    }

    private Tree stripTypedAndErasedCast(Tree tree) {
        while (true) {
            tree = stripTyped(tree);
            if (tree instanceof Tree.Apply) {
                Tree.Apply apply = (Tree.Apply)tree;
                if (apply.args.length == 0 && apply.fun instanceof Tree.TypeApply) {
                    Tree fun = ((Tree.TypeApply)apply.fun).fun;
                    if (fun instanceof Tree.Select
                        && fun.symbol() == defs.ANY_AS_ERASED) {
                        tree = ((Tree.Select)fun).qualifier;
                        continue;
                    }
                }
            }
            return tree;
        }
    }

    protected JType genLoadLiteral(Context ctx, AConstant lit, JType type) {
        if (lit == AConstant.UNIT) {
            maybeGenLoadUnit(ctx, type);
            return type;
        } else if (lit instanceof AConstant.BOOLEAN) {
            ctx.code.emitPUSH(((AConstant.BOOLEAN)lit).value);
            return JType.BOOLEAN;
        } else if (lit instanceof AConstant.BYTE) {
            ctx.code.emitPUSH(((AConstant.BYTE)lit).value);
            return JType.BYTE;
        } else if (lit instanceof AConstant.SHORT) {
            ctx.code.emitPUSH(((AConstant.SHORT)lit).value);
            return JType.SHORT;
        } else if (lit instanceof AConstant.CHAR) {
            ctx.code.emitPUSH(((AConstant.CHAR)lit).value);
            return JType.CHAR;
        } else if (lit instanceof AConstant.INT) {
            ctx.code.emitPUSH(((AConstant.INT)lit).value);
            return JType.INT;
        } else if (lit instanceof AConstant.LONG) {
            ctx.code.emitPUSH(((AConstant.LONG)lit).value);
            return JType.LONG;
        } else if (lit instanceof AConstant.FLOAT) {
            ctx.code.emitPUSH(((AConstant.FLOAT)lit).value);
            return JType.FLOAT;
        } else if (lit instanceof AConstant.DOUBLE) {
            ctx.code.emitPUSH(((AConstant.DOUBLE)lit).value);
            return JType.DOUBLE;
        } else if (lit instanceof AConstant.STRING) {
            ctx.code.emitPUSH(((AConstant.STRING)lit).value);
            return JAVA_LANG_STRING_T;
        } else if (lit instanceof AConstant.SYMBOL_NAME) {
            ctx.code.emitPUSH(javaName(((AConstant.SYMBOL_NAME)lit).value));
            return JAVA_LANG_STRING_T;
        } else if (lit == AConstant.NULL) {
            if (type != JType.VOID) ctx.code.emitACONST_NULL();
            return type;
        }
        throw Debug.abort("unknown literal", lit);
    }

    /**
     * Generate code to load the Unit value, iff the given type is an
     * object type (i.e. something really has to be loaded on stack).
     */
    protected void maybeGenLoadUnit(Context ctx, JType type) {
        if (type != JType.VOID)
            ctx.code.emitINVOKESTATIC(SCALA_RUNTIME_RUNTIME,
                                      "box_uvalue",
                                      new JMethodType(SCALA_UNIT_T,
                                                      JType.EMPTY_ARRAY));
    }

    protected void genBoxValue(Context ctx, JType type) {
        switch (type.getTag()) {
        case JType.T_BOOLEAN:
            ctx.code.emitINVOKESTATIC(SCALA_RUNTIME_RUNTIME, "box_zvalue",
                                      new JMethodType(new JObjectType("scala.Boolean"), new JType[] {JType.BOOLEAN}));
            break;
        case JType.T_BYTE:
            ctx.code.emitINVOKESTATIC(SCALA_RUNTIME_RUNTIME, "box_bvalue",
                                      new JMethodType(new JObjectType("scala.Byte"), new JType[] {JType.BYTE}));
            break;
        case JType.T_SHORT:
            ctx.code.emitINVOKESTATIC(SCALA_RUNTIME_RUNTIME, "box_svalue",
                                      new JMethodType(new JObjectType("scala.Short"), new JType[] {JType.SHORT}));
            break;
        case JType.T_CHAR:
            ctx.code.emitINVOKESTATIC(SCALA_RUNTIME_RUNTIME, "box_cvalue",
                                      new JMethodType(new JObjectType("scala.Char"), new JType[] {JType.CHAR}));
            break;
        case JType.T_INT:
            ctx.code.emitINVOKESTATIC(SCALA_RUNTIME_RUNTIME, "box_ivalue",
                                      new JMethodType(new JObjectType("scala.Int"), new JType[] {JType.INT}));
            break;
        case JType.T_LONG:
            ctx.code.emitINVOKESTATIC(SCALA_RUNTIME_RUNTIME, "box_lvalue",
                                      new JMethodType(new JObjectType("scala.Long"), new JType[] {JType.LONG}));
            break;
        case JType.T_FLOAT:
            ctx.code.emitINVOKESTATIC(SCALA_RUNTIME_RUNTIME, "box_fvalue",
                                      new JMethodType(new JObjectType("scala.Float"), new JType[] {JType.FLOAT}));
            break;
        case JType.T_DOUBLE:
            ctx.code.emitINVOKESTATIC(SCALA_RUNTIME_RUNTIME, "box_dvalue",
                                      new JMethodType(new JObjectType("scala.Double"), new JType[] {JType.DOUBLE}));
            break;
        default:
            throw Debug.abort("cannot box JVM type " + type);
        }
    }

    /**
     * Generate code to prepare the storage of a value in the location
     * represented by the tree.
     */
    protected void genStorePrologue(Context ctx, Tree tree)
        throws JCode.OffsetTooBigException {
        Symbol sym = tree.symbol();
        if (tree instanceof Tree.Ident) {
            if (sym.owner().isClass() && !isStaticMember(sym))
                ctx.code.emitALOAD_0();
        } else if (tree instanceof Tree.Select) {
            if (!isStaticMember(sym))
                genLoadQualifier(ctx, tree, true);
        } else {
            throw Debug.abort("unexpected left-hand side", tree);
        }
    }

    /**
     * Generate code to perform the storage of the value on top of
     * stack in the location represented by the tree.
     */
    protected void genStoreEpilogue(Context ctx, Tree tree) {
        Symbol sym = tree.symbol();
        if (sym.owner().isClass()) {
            String ownerName = javaName(sym.owner());
            if (isStaticMember(sym))
                ctx.code.emitPUTSTATIC(ownerName,
                                       jvmName(sym.name),
                                       typeStoJ(sym.info()));
            else
                ctx.code.emitPUTFIELD(ownerName,
                                      jvmName(sym.name),
                                      typeStoJ(sym.info()));
        } else {
            assert ctx.locals.containsKey(sym)
                : Debug.show(sym) + " not in " + ctx.locals;
            int index = ((Integer)(ctx.locals.get(sym))).intValue();
            ctx.code.emitSTORE(index, typeStoJ(sym.info()));
        }
    }

    /**
     * Generate code to evaluate the condition associated with the
     * given tree and jump to the target when the condition is equal
     * to the given value.
     */
    protected void genCond(Context ctx,
                           Tree tree,
                           JCode.Label target,
                           boolean when)
        throws JCode.OffsetTooBigException {
        if (tree instanceof Tree.Apply) {
            Tree.Apply apply = (Tree.Apply)tree;
            Tree fun = apply.fun;
            if (isKnownPrimitive(fun.symbol())) {
                Primitive prim = prims.getPrimitive(fun.symbol());
                Tree[] allArgs = extractPrimitiveArgs(apply);

                switch (prim) {
                case ID: case NI: case EQ: case NE:
                    assert allArgs.length == 2;
                    Tree unbox1 = unbox(allArgs[0]);
                    Tree unbox2 = unbox(allArgs[1]);
                    if (!getMaxType(unbox1, unbox2).isReferenceType()) {
                        allArgs[0] = unbox1;
                        allArgs[1] = unbox2;
                        genCompPrim(ctx, prim, allArgs, target, when);
                    } else {
                        genEqPrim(ctx, prim, allArgs, target, when);
                    }
                    return;

                case LT: case LE: case GE: case GT:
                    assert allArgs.length == 2;
                    allArgs[0] = unbox(allArgs[0]);
                    genCompPrim(ctx, prim, allArgs, target, when);
                    return;

                case ZNOT:
                    assert allArgs.length == 1;
                    genCond(ctx, unbox(allArgs[0]), target, !when);
                    return;

                case ZOR:
                case ZAND:
                    if (when ^ (prim == Primitive.ZAND)) {
                        // x || y jump if true  -or-  x && y jump if false
                        genCond(ctx, unbox(allArgs[0]), target, when);
                        genCond(ctx, allArgs[1], target, when);
                    } else {
                        // x || y jump if false  -or-  x && y jump if true
                        JCode.Label afterLabel = ctx.code.newLabel();
                        genCond(ctx, unbox(allArgs[0]), afterLabel, !when);
                        genCond(ctx, allArgs[1], target, when);
                        afterLabel.anchorToNext();
                    }
                    return;
                }
            }
        }
        // Default case: the condition is not a comparison or logical
        // primitive.
        genLoad(ctx, tree, JType.BOOLEAN);
        if (when)
            ctx.code.emitIFNE(target);
        else
            ctx.code.emitIFEQ(target);
    }

    protected Map/*<Primitive, Instruction>*/ arithPrimMap;
    protected void addPrim(Primitive prim,
                           JOpcode z,
                           JOpcode i,
                           JOpcode l,
                           JOpcode f,
                           JOpcode d) {
        arithPrimMap.put(prim, new JOpcode[] { z, i, l, f, d });
    }

    protected void initArithPrimMap() {
        arithPrimMap = new HashMap();
        /* boolean, int & al., long, float, double */
        addPrim(Primitive.ADD,
                null, JOpcode.IADD, JOpcode.LADD, JOpcode.FADD, JOpcode.DADD);
        addPrim(Primitive.SUB,
                null, JOpcode.ISUB, JOpcode.LSUB, JOpcode.FSUB, JOpcode.DSUB);
        addPrim(Primitive.MUL,
                null, JOpcode.IMUL, JOpcode.LMUL, JOpcode.FMUL, JOpcode.DMUL);
        addPrim(Primitive.DIV,
                null, JOpcode.IDIV, JOpcode.LDIV, JOpcode.FDIV, JOpcode.DDIV);
        addPrim(Primitive.MOD,
                null, JOpcode.IREM, JOpcode.LREM, JOpcode.FREM, JOpcode.DREM);
        addPrim(Primitive.AND,
                JOpcode.IAND, JOpcode.IAND, JOpcode.LAND, null, null);
        addPrim(Primitive.OR,
                JOpcode.IOR, JOpcode.IOR, JOpcode.LOR, null, null);
        addPrim(Primitive.XOR,
                JOpcode.IXOR, JOpcode.IXOR, JOpcode.LXOR, null, null);
        addPrim(Primitive.LSL,
                null, JOpcode.ISHL, JOpcode.LSHL, null, null);
        addPrim(Primitive.LSR,
                null, JOpcode.IUSHR, JOpcode.LUSHR, null, null);
        addPrim(Primitive.ASR,
                null, JOpcode.ISHR, JOpcode.LSHR, null, null);
        addPrim(Primitive.POS,
                null, null, null, null, null);
        addPrim(Primitive.NEG,
                null, JOpcode.INEG, JOpcode.LNEG, JOpcode.FNEG, JOpcode.DNEG);
    }

    /**
     * Generate code for the given arithmetic primitive, applied on
     * the given arguments.
     */
    protected void genArithPrim(Context ctx,
                                Primitive prim,
                                Tree[] args,
                                JType resType,
                                JType expectedType)
        throws JCode.OffsetTooBigException {
        int arity = args.length;
        int resTypeIdx = getTypeIndex(resType);

        if ((prim == Primitive.LSL)
            || (prim == Primitive.LSR)
            || (prim == Primitive.ASR)) {
            genLoad(ctx, args[0], resType);
            genLoad(ctx, args[1], JType.INT);
        } else {
            for (int i = 0; i < arity; ++i)
            	genLoad(ctx, args[i], resType);
        }

        if (prim == Primitive.NOT) {
            if (resType == JType.LONG) {
                ctx.code.emitPUSH(-1L);
                ctx.code.emitLXOR();
            } else {
                assert resType == JType.INT;
                ctx.code.emitPUSH(-1);
                ctx.code.emitIXOR();
            }
        } else {
            assert arithPrimMap.containsKey(prim);
            JOpcode primInst = ((JOpcode[])arithPrimMap.get(prim))[resTypeIdx];
            if (primInst != null)
                ctx.code.emit(primInst);
        }
    }

    /** Variable in which temporary objects are stored by the
     * implementation of == */
    protected JLocalVariable eqEqTempVar;

    /**
     * Generate code for the given equality primitive, applied on the
     * given arguments.
     */
    protected void genEqPrim(Context ctx,
                             Primitive prim,
                             Tree[] args,
                             JCode.Label target,
                             boolean when)
        throws JCode.OffsetTooBigException {
        JType maxType = getMaxType(args);
        assert maxType.isReferenceType(): args[0]+" - "+args[1]+" : "+maxType;
        // Generate code for all arguments
        for (int i = 0; i < args.length; ++i) genLoad(ctx, args[i], maxType);

        if (prim == Primitive.ID) {
            if (when) ctx.code.emitIF_ACMPEQ(target);
            else ctx.code.emitIF_ACMPNE(target);
        } else if (prim == Primitive.NI) {
            if (!when) ctx.code.emitIF_ACMPEQ(target);
            else ctx.code.emitIF_ACMPNE(target);
        } else {
            // Comparison between two references. We inline the code
            // for the predefined (and final) "=="/"!=" operators,
            // which check for null values and then forward the call
            // to "equals". "==" could be defined as follows, if
            // "null" was an object:
            //   final def ==(other: Any): boolean =
            //     if (this == null) other == null else this.equals(other)
            assert prim == Primitive.EQ || prim == Primitive.NE;

            if (eqEqTempVar == null || eqEqTempVar.getOwner() != ctx.method)
                eqEqTempVar =
                    ctx.method.addNewLocalVariable(JObjectType.JAVA_LANG_OBJECT,
                                                   "eqEqTemp$");

            ctx.code.emitSTORE(eqEqTempVar);
            ctx.code.emitDUP();
            JCode.Label ifNonNullLabel = ctx.code.newLabel();
            ctx.code.emitIFNONNULL(ifNonNullLabel);
            ctx.code.emitPOP();
            ctx.code.emitLOAD(eqEqTempVar);
            if (when ^ (prim != Primitive.EQ))
                ctx.code.emitIFNULL(target);
            else
                ctx.code.emitIFNONNULL(target);
            JCode.Label afterLabel = ctx.code.newLabel();
            ctx.code.emitGOTO_maybe_W(afterLabel, ctx.useWideJumps);
            ifNonNullLabel.anchorToNext();
            ctx.code.emitLOAD(eqEqTempVar);
            JMethodType equalsType =
                new JMethodType(JType.BOOLEAN,
                                new JType[] { JObjectType.JAVA_LANG_OBJECT });
            ctx.code.emitINVOKEVIRTUAL(JAVA_LANG_OBJECT, "equals", equalsType);
            if (when ^ (prim != Primitive.EQ))
                ctx.code.emitIFNE(target);
            else
                ctx.code.emitIFEQ(target);
            afterLabel.anchorToNext();
        }
    }

    /**
     * Generate code for the given comparison primitive, applied on
     * the given arguments.
     */
    protected void genCompPrim(Context ctx,
                               Primitive prim,
                               Tree[] args,
                               JCode.Label target,
                               boolean when)
        throws JCode.OffsetTooBigException {
        JType maxType = getMaxType(args);
        assert !maxType.isReferenceType(): args[0]+" - "+args[1]+" : "+maxType;
        int maxTypeIdx = getTypeIndex(maxType);
        int intTypeIdx = getTypeIndex(JType.INT);
        boolean intCompareWithZero = false;
        // Generate code for all arguments, while detecting
        // comparisons with 0, which can be optimised.
        for (int i = 0; i < args.length; ++i) {
            boolean isIntZero = false;
            if (maxTypeIdx <= intTypeIdx) {
                if (args[i] instanceof Tree.Literal) {
                    AConstant constant = ((Tree.Literal)args[i]).value;
                    int intVal;
                    if (constant instanceof AConstant.BooleanValue)
                        intVal = ((AConstant.BooleanValue)constant).value ? 1 : 0;
                    else if (constant instanceof AConstant.ByteValue)
                        intVal = ((AConstant.ByteValue)constant).value;
                    else if (constant instanceof AConstant.ShortValue)
                        intVal = ((AConstant.ShortValue)constant).value;
                    else if (constant instanceof AConstant.CharValue)
                        intVal = ((AConstant.CharValue)constant).value;
                    else if (constant instanceof AConstant.IntValue)
                        intVal = ((AConstant.IntValue)constant).value;
                    else
                        throw Debug.abort("unknown literal", constant);
                    if (intVal == 0) {
                        isIntZero = true;
                        if (i == 0) prim = prim.swap();
                    }
                }
            }
            if (intCompareWithZero || !isIntZero)
                genLoad(ctx, args[i], maxType);
            intCompareWithZero |= isIntZero;
        }

        if (maxTypeIdx <= intTypeIdx && !intCompareWithZero) {
            // Comparison between ints, no zeros involved
            switch (maybeNegatedPrim(prim, !when)) {
            case LT: ctx.code.emitIF_ICMPLT(target); break;
            case LE: ctx.code.emitIF_ICMPLE(target); break;
            case EQ: ctx.code.emitIF_ICMPEQ(target); break;
            case NE: ctx.code.emitIF_ICMPNE(target); break;
            case GE: ctx.code.emitIF_ICMPGE(target); break;
            case GT: ctx.code.emitIF_ICMPGT(target); break;
            default: throw Debug.abort("unknown primitive", prim);
            }
        } else {
            // Comparison between longs, floats or double, or between
            // one int and zero.
            switch (maxType.getTag()) {
            case JType.T_LONG:   ctx.code.emitLCMP();  break;
            case JType.T_FLOAT:  ctx.code.emitFCMPG(); break;
            case JType.T_DOUBLE: ctx.code.emitDCMPG(); break;
            default:
                ;               // do nothing (int comparison with 0)
            }
            switch (maybeNegatedPrim(prim, !when)) {
            case LT: ctx.code.emitIFLT(target); break;
            case LE: ctx.code.emitIFLE(target); break;
            case EQ: ctx.code.emitIFEQ(target); break;
            case NE: ctx.code.emitIFNE(target); break;
            case GE: ctx.code.emitIFGE(target); break;
            case GT: ctx.code.emitIFGT(target); break;
            default: throw Debug.abort("unknown primitive", prim);
            }
        }
    }

    /**
     * Generate code to throw the value returned by the argument.
     */
    protected void genThrow(Context ctx, Tree arg)
        throws JCode.OffsetTooBigException {
        genLoad(ctx, arg, JAVA_LANG_OBJECT_T);
        ctx.code.emitCHECKCAST(JAVA_LANG_THROWABLE_T);
        ctx.code.emitATHROW();
    }

    protected void genUnreachableValue(Context ctx, JType type) {
        if (type == JType.VOID)
            return;
        if (type == JType.LONG)
            ctx.code.emitPUSH(0L);
        else if (type == JType.FLOAT)
            ctx.code.emitPUSH(0.0f);
        else if (type == JType.DOUBLE)
            ctx.code.emitPUSH(0.0d);
        else if (type.isReferenceType())
            ctx.code.emitACONST_NULL();
        else
            ctx.code.emitICONST_0();
    }

    /**
     * Generate code to synchronise on the object and evaluate the
     * code fragment.
     */
    protected void genSynchronized(Context ctx,
                                   Tree object,
                                   Tree code,
                                   JType expectedType)
        throws JCode.OffsetTooBigException {
        JLocalVariable monitorVar =
            ctx.method.addNewLocalVariable(JAVA_LANG_OBJECT_T, "monitor");
        genLoad(ctx, object, JAVA_LANG_OBJECT_T);
        ctx.code.emitSTORE(monitorVar);
        ctx.code.emitLOAD(monitorVar);
        ctx.code.emitMONITORENTER();
        int startPC = ctx.code.getPC();
        genLoad(ctx, code, expectedType);
        int endPC = ctx.code.getPC();
        ctx.code.emitLOAD(monitorVar);
        ctx.code.emitMONITOREXIT();

        JCode.Label afterLabel = ctx.code.newLabel();
        ctx.code.emitGOTO(afterLabel);
        int handlerPC = ctx.code.getPC();
        ctx.code.emitLOAD(monitorVar);
        ctx.code.emitMONITOREXIT();
        ctx.code.emitATHROW();
        afterLabel.anchorToNext();

        ctx.code.addFinallyHandler(startPC, endPC, handlerPC);
    }

    /// Arrays
    //////////////////////////////////////////////////////////////////////

    /**
     * Generate code to create an array of some basic type, whose size
     * will be dynamically computed.
     */
    protected void genArrayCreate(Context ctx, Primitive prim, Tree size)
        throws JCode.OffsetTooBigException {
        genLoad(ctx, size, JType.INT);
        JType type;
        switch (prim) {
        case NEW_ZARRAY : type = JType.BOOLEAN; break;
        case NEW_BARRAY : type = JType.BYTE;    break;
        case NEW_SARRAY : type = JType.SHORT;   break;
        case NEW_CARRAY : type = JType.CHAR;    break;
        case NEW_IARRAY : type = JType.INT;     break;
        case NEW_LARRAY : type = JType.LONG;    break;
        case NEW_FARRAY : type = JType.FLOAT;   break;
        case NEW_DARRAY : type = JType.DOUBLE;  break;
        default: throw Debug.abort("unexpected primitive", prim);
        }
        ctx.code.emitNEWARRAY(type);
    }

    /**
     * Generate code to create an array of references, whose size will
     * be dynamically computed.
     */
    protected void genRefArrayCreate(Context ctx, Tree size, Tree classNameLit)
        throws JCode.OffsetTooBigException {
        genLoad(ctx, size, JType.INT);

        String className;
        if (classNameLit instanceof Tree.Literal
            && ((Tree.Literal)classNameLit).value instanceof AConstant.StringValue) {
            className =
                ((AConstant.StringValue)((Tree.Literal)classNameLit).value).value;
        } else {
            throw Debug.abort("invalid argument for oarray", classNameLit);
        }

        JReferenceType elemType;
        switch (className.charAt(0)) {
        case '[': case 'L':
            elemType = (JReferenceType)JType.parseSignature(className); break;
        default:
            elemType = new JObjectType(className);
        }
        ctx.code.emitANEWARRAY(elemType);
    }

    /**
     * Generate code to update an array.
     */
    protected void genArrayUpdate(Context ctx, Tree array, Tree index, Tree value)
        throws JCode.OffsetTooBigException {
        genLoad(ctx, array, JAVA_LANG_OBJECT_T);
        genLoad(ctx, index, JType.INT);
        JType elemType = getArrayElementType(array);
        if (elemType.isValueType())
            value = unbox(value);
        genLoad(ctx, value, elemType);
        ctx.code.emitASTORE(elemType);
    }

    /**
     * Generate code to load an element of an array.
     */
    protected void genArrayAccess(Context ctx, Tree array, Tree index)
        throws JCode.OffsetTooBigException {
        genLoad(ctx, array, JAVA_LANG_OBJECT_T);
        genLoad(ctx, index, JType.INT);
        ctx.code.emitALOAD(getArrayElementType(array));
    }

    /**
     * Generate code to load the length of an array.
     */
    protected void genArrayLength(Context ctx, Tree array)
        throws JCode.OffsetTooBigException {
        genLoad(ctx, array, JAVA_LANG_OBJECT_T);
        ctx.code.emitARRAYLENGTH();
    }

    /**
     * Return the Java type of the elements of the array represented
     * by the given tree.
     */
    protected JType getArrayElementType(Tree array) {
        JArrayType arrayType = (JArrayType)typeStoJ(array.type);
        return arrayType.getElementType();
    }

    /// String concatenation
    //////////////////////////////////////////////////////////////////////

    protected Tree[] liftStringConcatenations(Tree tree) {
        LinkedList accu = new LinkedList();
        liftStringConcatenations(tree, accu);
        return (Tree[])accu.toArray(new Tree[accu.size()]);
    }

    protected void liftStringConcatenations(Tree tree, LinkedList accu) {
        if (tree instanceof Tree.Apply
            && ((Tree.Apply)tree).fun instanceof Tree.Select) {
            Tree.Apply apply = (Tree.Apply)tree;
            Tree.Select select = (Tree.Select)apply.fun;
            Symbol funSym = select.symbol();
            if  (prims.isPrimitive(funSym)
                 && prims.getPrimitive(funSym) == Primitive.CONCAT) {
                liftStringConcatenations(select.qualifier, accu);
                liftStringConcatenations(apply.args[0], accu);
            } else {
                accu.addLast(tree);
            }
        } else {
            accu.addLast(tree);
        }
    }

    /**
     * Generate code to concatenate a list of expressions which return
     * strings.
     */
    protected void genStringConcatenation(Context ctx, Tree[] elements)
        throws JCode.OffsetTooBigException {
        // Create string buffer
        ctx.code.emitNEW(JAVA_LANG_STRINGBUFFER);
        ctx.code.emitDUP();
        ctx.code.emitINVOKESPECIAL(JAVA_LANG_STRINGBUFFER,
                                   "<init>",
                                   JMethodType.ARGLESS_VOID_FUNCTION);

        // Append all strings
        for (int i = 0; i < elements.length; ++i) {
            JType elemType = typeStoJ(elements[i].type);
            if (!elemType.equals(JObjectType.JAVA_LANG_STRING)
                && elemType.isReferenceType())
                elemType = JObjectType.JAVA_LANG_OBJECT;
            genLoad(ctx, elements[i], elemType);
            ctx.code.emitINVOKEVIRTUAL(JAVA_LANG_STRINGBUFFER,
                                       "append",
                                       new JMethodType(JAVA_LANG_STRINGBUFFER_T,
                                                       new JType[] { elemType }));
        }

        // Get resulting string
        ctx.code.emitINVOKEVIRTUAL(JAVA_LANG_STRINGBUFFER,
                                   "toString",
                                   new JMethodType(JObjectType.JAVA_LANG_STRING,
                                                   JType.EMPTY_ARRAY));
    }

    /// Primitives
    //////////////////////////////////////////////////////////////////////

    /**
     * Return true iff the given symbol is a primitive, AND that
     * primitive is recognized by this back-end.
     */
    protected boolean isKnownPrimitive(Symbol sym) {
        if (prims.isPrimitive(sym)) {
            switch (prims.getPrimitive(sym)) {
            case POS : case NEG :
            case ADD : case SUB : case MUL : case DIV : case MOD :
            case NOT : case OR : case XOR : case AND :
            case LSL : case LSR : case ASR :
            case EQ : case NE : case LT : case LE : case GE : case GT :
            case ZNOT : case ZOR : case ZAND :
            case NEW_ZARRAY : case NEW_BARRAY : case NEW_SARRAY :
            case NEW_CARRAY : case NEW_IARRAY : case NEW_LARRAY :
            case NEW_FARRAY : case NEW_DARRAY : case NEW_OARRAY :
            case ZARRAY_GET : case BARRAY_GET : case SARRAY_GET :
            case CARRAY_GET : case IARRAY_GET : case LARRAY_GET :
            case FARRAY_GET : case DARRAY_GET : case OARRAY_GET :
            case ZARRAY_SET : case BARRAY_SET : case SARRAY_SET :
            case CARRAY_SET : case IARRAY_SET : case LARRAY_SET :
            case FARRAY_SET : case DARRAY_SET : case OARRAY_SET :
            case ZARRAY_LENGTH : case BARRAY_LENGTH : case SARRAY_LENGTH :
            case CARRAY_LENGTH : case IARRAY_LENGTH : case LARRAY_LENGTH :
            case FARRAY_LENGTH : case DARRAY_LENGTH : case OARRAY_LENGTH :
            case IS : case AS : case ID : case NI :
            case CONCAT : case THROW : case SYNCHRONIZED:
            case B2B: case B2S: case B2C: case B2I: case B2L: case B2F: case B2D:
            case S2B: case S2S: case S2C: case S2I: case S2L: case S2F: case S2D:
            case C2B: case C2S: case C2C: case C2I: case C2L: case C2F: case C2D:
            case I2B: case I2S: case I2C: case I2I: case I2L: case I2F: case I2D:
            case L2B: case L2S: case L2C: case L2I: case L2L: case L2F: case L2D:
            case F2B: case F2S: case F2C: case F2I: case F2L: case F2F: case F2D:
            case D2B: case D2S: case D2C: case D2I: case D2L: case D2F: case D2D:
                return true;

            case EQUALS  :
            case HASHCODE :
            case TOSTRING :
            case COERCE :
            case BOX :
            case UNBOX :
            case APPLY : case UPDATE : case LENGTH :
                return false;
            default:
                throw Debug.abort("unknown primitive", sym);
            }
        } else
            return false;
    }

    /**
     * Negate the given primitive only if the second argument is
     * true, otherwise return it as is.
     */
    protected Primitive maybeNegatedPrim(Primitive prim, boolean negate) {
        return negate ? prim.negate() : prim;
    }

    /**
     * Return all the arguments associated with the primitive
     * represented by the given function call.
     */
    protected Tree[] extractPrimitiveArgs(Tree.Apply call) {
        Tree[] allArgs = Tree.cloneArray(1, call.args);
        allArgs[0] = ((Tree.Select)(call.fun)).qualifier;
        return allArgs;
    }

    /**
     * Return the unboxed version of the given tree.
     */
    protected Tree unbox(Tree tree) {
        if (tree instanceof Tree.Apply) {
            Tree.Apply apply = (Tree.Apply)tree;
            if (prims.getPrimitive(apply.fun.symbol()) == Primitive.BOX) {
                assert apply.args.length == 1;
                return apply.args[0];
            }
        }
        return tree;
    }

    /// Modules
    //////////////////////////////////////////////////////////////////////

    /**
     * Add field containing module instance, and code to initialize
     * it, to current class.
     */
    protected void addModuleInstanceField(Context ctx) {
        ctx.clazz.addNewField(JAccessFlags.ACC_PUBLIC
                              | JAccessFlags.ACC_FINAL
                              | JAccessFlags.ACC_STATIC,
                              MODULE_INSTANCE_FIELD_NAME,
                              ctx.clazz.getType());

        ctx.code.emitNEW(ctx.clazz.getName());
        ctx.code.emitINVOKESPECIAL(ctx.clazz.getName(),
                                   CONSTRUCTOR_STRING,
                                   JMethodType.ARGLESS_VOID_FUNCTION);
        // The field is initialised by the constructor, so we don't
        // need to do it here, creating the instance is sufficient.
    }

    protected JMethod addNewScalaMethod(JClass clazz,
                                        Symbol method,
                                        int addFlags) {
        JMethodType methodType = (JMethodType)typeStoJ(method.type());

        Symbol[] params = method.valueParams();
        String[] argNames = new String[params.length];

        for (int i = 0; i < argNames.length; ++i)
            argNames[i] = params[i].name.toString();

        return clazz.addNewMethod(javaModifiers(method) | addFlags,
                                  jvmName(method.name),
                                  methodType.getReturnType(),
                                  methodType.getArgumentTypes(),
                                  argNames);
    }

    protected String jvmName(Name name) {
        return jvmName(name.toString());
    }

    protected String jvmName(String name) {
        if (name.startsWith("<"))
            return name;

        StringBuffer buffer = null;
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            boolean safe =
                (0x20 <= ch && ch <= 0x7e)
                && ch != '.'
                && ch != ';'
                && ch != '['
                && ch != '/';
            if (!safe) {
                if (buffer == null) {
                    buffer = new StringBuffer(name.length() + 6);
                    buffer.append(name.substring(0, i));
                }
                buffer.append("$u");
                String hex = Integer.toHexString(ch).toUpperCase();
                for (int j = hex.length(); j < 4; j++)
                    buffer.append('0');
                buffer.append(hex);
            } else if (buffer != null) {
                buffer.append(ch);
            }
        }
        return buffer == null ? name : buffer.toString();
    }

    /**
     * Create a class which mirrors all public methods of the given
     * module class as static methods, to enable the use of the module
     * from Java.
     */
    protected void dumpModuleMirrorClass(Context ctx, Symbol cSym, Pickle pickle) {
        String mainName = javaName(cSym);
        String mirrorName = mainName.substring(0, mainName.length() - 1);

        JClass mirrorClass = fjbgContext.JClass(JAccessFlags.ACC_SUPER
                                                | JAccessFlags.ACC_PUBLIC
                                                | JAccessFlags.ACC_FINAL,
                                                mirrorName,
                                                JAVA_LANG_OBJECT,
                                                JClass.NO_INTERFACES,
                                                ctx.sourceFileName);
        Scope.SymbolIterator memberIt =
            new Scope.UnloadIterator(cSym.members().iterator());
        while (memberIt.hasNext()) {
            Symbol member = memberIt.next();
            if (!member.isMethod()
                || member.isInitializer()
                || member.isStatic())
                continue;

            JMethod mirrorMeth =
                addNewScalaMethod(mirrorClass, member, JAccessFlags.ACC_STATIC);
            JExtendedCode mirrorCode = (JExtendedCode)mirrorMeth.getCode();

            mirrorCode.emitGETSTATIC(mainName,
                                     MODULE_INSTANCE_FIELD_NAME,
                                     new JObjectType(mainName));
            JType[] argTypes = mirrorMeth.getArgumentTypes();
            for (int index = 0, i = 0; i < argTypes.length; ++i) {
                mirrorCode.emitLOAD(index, argTypes[i]);
                index += argTypes[i].getSize();
            }
            mirrorCode.emitINVOKEVIRTUAL(mainName,
                                         mirrorMeth.getName(),
                                         (JMethodType)mirrorMeth.getType());
            mirrorCode.emitRETURN(mirrorMeth.getReturnType());

            mirrorCode.setLineNumber(0,
                                     mirrorCode.getPC(),
                                     Position.line(member.pos));
        }
        addScalaAttr(mirrorClass, pickle);
        writeClassFile(mirrorClass);
    }

    /**
     * Add the "Scala" attribute to the given class, in which the
     * symbol table is saved.
     */
    protected void addScalaAttr(JClass cls, Pickle pickle) {
        if (false) {
            pickles.add(cls);
            pickles.add(pickle);
        } else {
            JOtherAttribute scalaAttr =
                fjbgContext.JOtherAttribute(cls,
                                            cls,
                                            SCALA_ATTR,
                                            pickle.bytes,
                                            pickle.size());
            cls.addAttribute(scalaAttr);
        }
    }

    /// Names
    //////////////////////////////////////////////////////////////////////

    protected HashMap nameMap/*<Symbol,String>*/ = new HashMap();

    /**
     * Return a Java-compatible version of the name of the given
     * symbol. The returned name is mangled and includes the names of
     * the owners.
     */
    protected String javaName(Symbol sym) {
        Object value = nameMap.get(sym);
        if (value != null) return (String)value;
        String name = prims.getJREClassName(sym);
        nameMap.put(sym, name);
        return name;
    }

    /// Types
    //////////////////////////////////////////////////////////////////////

    /**
     * Return the Java modifiers for the given Scala symbol.
     */
    protected int javaModifiers(Symbol sym) {
        int flags = sym.flags;
        int jFlags = 0;

        if (Modifiers.Helper.isPrivate(flags))
            jFlags |= JAccessFlags.ACC_PRIVATE;
        else
            jFlags |= JAccessFlags.ACC_PUBLIC;

        if (Modifiers.Helper.isAbstract(flags))
            jFlags |= JAccessFlags.ACC_ABSTRACT;
        if (Modifiers.Helper.isInterface(flags))
            jFlags |= JAccessFlags.ACC_INTERFACE;

        if (Modifiers.Helper.isFinal(flags)
            && !(Modifiers.Helper.isAbstract(flags)
                 || Modifiers.Helper.isInterface(flags)))
            jFlags |= JAccessFlags.ACC_FINAL;

        if (sym.isStatic())
            jFlags |= JAccessFlags.ACC_STATIC;

        return jFlags;
    }

    protected HashMap typeMap/*<Symbol,Type>*/ = new HashMap();
    protected void initTypeMap() {
        typeMap.put(defs.ANY_CLASS,    JObjectType.JAVA_LANG_OBJECT);
        typeMap.put(defs.ANYREF_CLASS, JObjectType.JAVA_LANG_OBJECT);
    }

    /**
     * Return the Java type corresponding to the given Scala type.
     */
    protected JType typeStoJ(Type tp) {
        if (tp instanceof Type.UnboxedType) {
            switch (((Type.UnboxedType)tp).tag) {
            case TypeTags.BYTE:
                return JType.BYTE;
            case TypeTags.CHAR:
                return JType.CHAR;
            case TypeTags.SHORT:
                return JType.SHORT;
            case TypeTags.INT:
                return JType.INT;
            case TypeTags.LONG:
                return JType.LONG;
            case TypeTags.FLOAT:
                return JType.FLOAT;
            case TypeTags.DOUBLE:
                return JType.DOUBLE;
            case TypeTags.BOOLEAN:
                return JType.BOOLEAN;
            case TypeTags.UNIT:
                return JType.VOID;
            case TypeTags.STRING:
                return JObjectType.JAVA_LANG_STRING;
            default:
                break;
            }
        } else if (tp instanceof Type.UnboxedArrayType) {
            return new JArrayType(typeStoJ(((Type.UnboxedArrayType)tp).elemtp));
        } else if (tp instanceof Type.MethodType) {
            Type.MethodType methodType = (Type.MethodType)tp;
            JType[] argTypes = new JType[methodType.vparams.length];
            for (int i = 0; i < methodType.vparams.length; ++i)
                argTypes[i] = typeStoJ(methodType.vparams[i].info());
            return new JMethodType(typeStoJ(methodType.result), argTypes);
        }

        Symbol sym = tp.symbol();
        if (sym == Symbol.NONE)
            throw Debug.abort("invalid type", tp);
        else if (typeMap.containsKey(sym))
            return (JType)typeMap.get(sym);
        else {
            JType jTp = new JObjectType(javaName(sym));
            typeMap.put(sym, jTp);
            return jTp;
        }
    }

    /**
     * Return the "index" of the given type. This index encodes the
     * level in the hierarchy of basic types, with arrays and objects
     * on top of everything.
     */
    protected int getTypeIndex(JType tp) {
        return getTypeIndex(tp.getTag());
    }

    /**
     * Return the "index" of the given type.
     */
    protected int getTypeIndex(int tp) {
        switch (tp) {
        case JType.T_BOOLEAN: return 0;
        case JType.T_BYTE:
        case JType.T_CHAR:
        case JType.T_SHORT:
        case JType.T_INT:     return 1;
        case JType.T_LONG:    return 2;
        case JType.T_FLOAT:   return 3;
        case JType.T_DOUBLE:  return 4;
        case JType.T_ARRAY:
        case JType.T_OBJECT:  return 5;
        default: return -1;
        }
    }

    /**
     * Return the maximum of the types of all the given trees. All
     * reference types are considered to be equivalent, and if several
     * reference types are present in the trees, any one of them is
     * returned.
     */
    protected JType getMaxType(Tree[] trees) {
        JType maxType = JType.BOOLEAN;
        int maxTypeIdx = getTypeIndex(maxType);

        for (int i = 0; i < trees.length; ++i) {
            JType argType = typeStoJ(trees[i].type);
            if (getTypeIndex(argType) > maxTypeIdx) {
                maxType = argType;
                maxTypeIdx = getTypeIndex(maxType);
            }
        }
        return maxType;
    }
    protected JType getMaxType(Tree tree1, Tree tree2) {
        JType type1 = typeStoJ(tree1.type);
        JType type2 = typeStoJ(tree2.type);
        return getTypeIndex(type1) > getTypeIndex(type2) ? type1 : type2;
    }

    /// Line numbers
    //////////////////////////////////////////////////////////////////////

    int[] pcStack = new int[32];
    int pcStackDepth = 0;
    void startCodeForTree(Context ctx, Tree tree) {
        if (pcStackDepth == pcStack.length) {
            int[] newPCStack = new int[pcStack.length * 2];
            System.arraycopy(pcStack, 0, newPCStack, 0, pcStack.length);
            pcStack = newPCStack;
        }
        pcStack[pcStackDepth++] = (ctx.code == null ? 0 : ctx.code.getPC());
    }

    void endCodeForTree(Context ctx, Tree tree) {
        assert pcStackDepth > 0;
        int startPC = pcStack[--pcStackDepth];
        if (ctx.code != null)
            ctx.code.completeLineNumber(startPC,
                                        ctx.code.getPC(),
                                        Position.line(tree.pos));
    }

    /// Context
    //////////////////////////////////////////////////////////////////////

    /**
     * Record the entry into a class, and return the appropriate
     * context.
     */
    protected Context enterClass(Context ctx, Symbol cSym) {
        String javaName = javaName(cSym);

        boolean serializable =
            global.getAttrArguments(cSym, defs.SCALA_SERIALIZABLE_CONSTR) != null;

        scalac.symtab.Type[] baseTps = cSym.info().parents();
        assert baseTps.length > 0 : Debug.show(cSym);

        int offset;
        String superClassName;
        if (cSym.isInterface()) {
            offset = baseTps[0].symbol() == defs.ANY_CLASS ? 1 : 0;
            superClassName = JAVA_LANG_OBJECT;
        } else {
            offset = 1;
            superClassName = javaName(baseTps[0].symbol());
        }
        String[] interfaceNames =
            new String[baseTps.length - offset + (serializable ? 1 : 0)];
        for (int i = offset; i < baseTps.length; ++i) {
            Symbol baseSym = baseTps[i].symbol();
            assert baseSym.isInterface() : cSym + " implements " + baseSym;
            interfaceNames[i - offset] = javaName(baseSym);
        }
        if (serializable)
            interfaceNames[interfaceNames.length - 1] = "java.io.Serializable";

        JClass cls = fjbgContext.JClass(javaModifiers(cSym)
                                        & ~JAccessFlags.ACC_STATIC
                                        | JAccessFlags.ACC_SUPER,
                                        javaName,
                                        superClassName,
                                        interfaceNames,
                                        ctx.sourceFileName);

        return ctx.withClass(cls,
                             cSym.isModuleClass() && ctx.clazz == null,
                             cSym.isSubClass(JAVA_RMI_REMOTE_CLASS));
    }

    protected void leaveClass(Context ctx, Symbol cSym) {
        JClass clazz = ctx.clazz;

        Pickle pickle = (Pickle)global.symdata.get(cSym);
        if (pickle != null)
            if (ctx.isModuleClass)
                dumpModuleMirrorClass(ctx, cSym, pickle);
            else
                addScalaAttr(clazz, pickle);

        writeClassFile(clazz);
    }

    /**
     * Record the entry into a method, and return the appropriate
     * context.
     */
    protected Context enterMethod(Context ctx,
                                  Tree.DefDef mDef,
                                  boolean useWideJumps) {
        Symbol mSym = mDef.symbol();

        global.log("entering method " + Debug.toString(mSym)
                   + " (type: " + Debug.toString(mSym.info()) + ")"
                   + " wide jumps? " + useWideJumps);

        JMethod method = addNewScalaMethod(ctx.clazz, mSym, 0);

        Map locals = new HashMap();
        Symbol[] args = mSym.valueParams();
        JType[] argTypes = method.getArgumentTypes();

        int firstPos = mSym.isStatic() ? 0 : 1;
        for (int i = 0, pos = firstPos; i < argTypes.length; ++i) {
            locals.put(args[i], new Integer(pos));
            pos += argTypes[i].getSize();
        }

        if ((mSym.flags & Modifiers.BRIDGE) != 0)
            method.addAttribute(fjbgContext.JOtherAttribute(ctx.clazz,
                                                            method,
                                                            "Bridge",
                                                            new byte[]{}));

        if (ctx.isRemote && mSym.isPublic()) {
            JConstantPool cp = ctx.clazz.getConstantPool();
            int reInx = cp.addClass(JAVA_RMI_REMOTEEXCEPTION);
            ByteBuffer contents = ByteBuffer.allocate(4); // u2 + u2[1]
            contents.putShort((short)1);
            contents.putShort((short)reInx);
            global.log("adding 'Exceptions_attribute' " + contents.toString()
                       + " for remote method " + mSym.name.toString());
            method.addAttribute(fjbgContext.JOtherAttribute(ctx.clazz,
                                                            method,
                                                            "Exceptions",
                                                            contents.array()));
        }

        return ctx.withMethod(method, locals, useWideJumps);
    }

    protected void leaveMethod(Context ctx) {
        genLocalVariableTable(ctx);
        global.log("leaving method");
    }

    protected void genLocalVariableTable(Context ctx) {
        JLocalVariable[] vars = ctx.method.getLocalVariables();

        if (!global.debuginfo || vars.length == 0)
            return;

        JConstantPool pool = ctx.clazz.getConstantPool();
        int pc = ctx.code.getPC();
        int anonCounter = 1;

        ByteBuffer lvTab = ByteBuffer.allocate(2 + 10 * vars.length);
        lvTab.putShort((short)vars.length);
        for (int i = 0; i < vars.length; i++) {
            JLocalVariable lv = vars[i];
            String name = (lv.getName() == null)
                ? "<anon" + (anonCounter++) + ">" : lv.getName();
            lvTab.putShort((short)0);
            lvTab.putShort((short)pc);
            lvTab.putShort((short)pool.addUtf8(name));
            lvTab.putShort((short)pool.addUtf8(lv.getType().getSignature()));
            lvTab.putShort((short)lv.getIndex());
        }
        JAttribute attr =
            fjbgContext.JOtherAttribute(ctx.clazz,
                                        ctx.method,
                                        "LocalVariableTable",
                                        lvTab.array());
        ctx.code.addAttribute(attr);
    }

    /// I/O
    //////////////////////////////////////////////////////////////////////

    /** Writes the given class to a file. */
    protected void writeClassFile(JClass clazz) {
        File file = getFile(clazz, ".class");
        try {
            clazz.writeTo(file);
            global.operation("wrote " + file);
        } catch (IOException exception) {
            if (global.debug) exception.printStackTrace();
            global.error("could not write file " + file);
        }
    }

    /** Writes the given pickle for given class to a file. */
    protected void writeSymblFile(JClass clazz, Pickle pickle) {
        File file = getFile(clazz, ".symbl");
        try {
            pickle.writeTo(file);
            global.operation("wrote " + file);
        } catch (IOException exception) {
            if (global.debug) exception.printStackTrace();
            global.error("could not write file " + file);
        }
    }

    /** Returns the file with the given suffix for the given class. */
    protected File getFile(JClass clazz, String suffix) {
        String path = clazz.getName().replace('.', File.separatorChar);
        return new File(global.outpath + File.separatorChar + path + suffix);
    }

    /// Misc.
    //////////////////////////////////////////////////////////////////////

    protected void completeClassConstructor(Context ctx, Symbol cSym) {
        if (ctx.isModuleClass)
            addModuleInstanceField(ctx);
        AConstant[] aargs =
            global.getAttrArguments(cSym, SCALA_SERIAL_VERSION_UID_CONSTR);
        if (aargs == null)
            return;
        assert aargs.length == 1 : "Wrong attribute arguments: ";
        String fieldName = "serialVersionUID";
        JField field = ctx.clazz.addNewField(
            JAccessFlags.ACC_STATIC | JAccessFlags.ACC_PUBLIC,
            fieldName,
            JType.LONG);
        genLoadLiteral(ctx, aargs[0], JType.LONG);
        ctx.code.emitPUTSTATIC(ctx.clazz.getName(), fieldName, JType.LONG);
    }

    /**
     * Return the class constructor method of the given class.
     */
    private JMethod getClassConstructorMethod(Context ctx) {
        JMethod[] methods = ctx.clazz.getMethods();
        JMethod clinit = null;
        for (int i = 0; i < methods.length; ++i) {
            if (methods[i].getName().equals("<clinit>")) {
                clinit = methods[i];
                break;
            }
        }
        return clinit != null
            ? clinit
            : ctx.clazz.addNewMethod(JAccessFlags.ACC_PUBLIC
                                     | JAccessFlags.ACC_STATIC,
                                     "<clinit>",
                                     JType.VOID,
                                     JType.EMPTY_ARRAY,
                                     Strings.EMPTY_ARRAY);
    }

    /**
     * Add value members (i.e. fields) to current class.
     */
    protected void addValueClassMembers(Context ctx, Tree.ClassDef cDef) {
        Symbol cSym = cDef.symbol();
        Scope.SymbolIterator memberIt =
            new Scope.UnloadIterator(cSym.members().iterator());
        while (memberIt.hasNext()) {
            Symbol member = memberIt.next();
            if (member.isTerm() && !member.isMethod()) {
                int flags = javaModifiers(member);
                if (global.getAttrArguments(member, SCALA_TRANSIENT_CONSTR) != null)
                    flags |= JAccessFlags.ACC_TRANSIENT;
                if (global.getAttrArguments(member, SCALA_VOLATILE_CONSTR) != null)
                    flags |= JAccessFlags.ACC_VOLATILE;
                ctx.clazz.addNewField(flags,
                                      member.name.toString(),
                                      typeStoJ(member.info()));
            }
        }
    }

    /**
     * Return true iff the given symbol is a static (in the Java
     * sense) member of its owner.
     */
    protected boolean isStaticMember(Symbol sym) {
        return sym.isStatic();
    }
}

/**
 * A compilation context, which records information about the class
 * and the method currently being generated.
 */
class Context {
    public final String sourceFileName;
    public final JClass clazz;
    public final JMethod method;
    public final JExtendedCode code;
    public final Map/*<Symbol,JLocalVariable>*/ locals;
    public final Map/*<Symbol,Pair<JCode.Label,Tree[]>>*/ labels;
    public final boolean useWideJumps;
    public final boolean isModuleClass;
    public final boolean isRemote;

    public final static Context EMPTY =
        new Context(null, null, null, null, null, false, false, false);

    private Context(String sourceFileName,
                    JClass clazz,
                    JMethod method,
                    Map locals,
                    Map labels,
                    boolean useWideJumps,
                    boolean isModuleClass,
                    boolean isRemote) {
        this.sourceFileName = sourceFileName;
        this.clazz = clazz;
        this.method = method;
        if (method == null || method.isAbstract())
            this.code = null;
        else
            this.code = (JExtendedCode)method.getCode();
        this.locals = locals;
        this.labels = labels;
        this.useWideJumps = useWideJumps;
        this.isModuleClass = isModuleClass;
        this.isRemote = isRemote;
    }

    public Context withSourceFileName(String sourceFileName) {
        return new Context(sourceFileName,
                           null,
                           null,
                           null,
                           null,
                           false,
                           false,
                           false);
    }

    public Context withClass(JClass clazz, boolean isModuleClass, boolean isRemote) {
        return new Context(this.sourceFileName,
                           clazz,
                           null,
                           null,
                           null,
                           false,
                           isModuleClass,
                           isRemote);
    }

    public Context withMethod(JMethod method, Map locals, boolean useWideJumps) {
        assert this.clazz == method.getOwner();
        return new Context(this.sourceFileName,
                           this.clazz,
                           method,
                           locals,
                           new HashMap(),
                           useWideJumps,
                           this.isModuleClass,
                           this.isRemote);
    }
}
