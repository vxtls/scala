/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
**                                                                      **
** $Id$
\*                                                                      */

package scalac.transformer.matching;

import scala.tools.util.Position;
import scalac.*;
import scalac.ast.*;
import scalac.atree.AConstant;
import scalac.util.*;
import scalac.symtab.*;
import scalac.transformer.matching.PatternNode.*;
import scalac.ast.Tree.*;


public class PatternMatcher extends PatternTool {

    protected boolean optimize = true;
    protected boolean delegateSequenceMatching = false;
    protected boolean doBinding = true;

    /** the owner of the pattern matching expression
     */
    protected Symbol owner;

    /** the selector expression
     */
    protected Tree selector;

    /** the root of the pattern node structure
     */
    protected PatternNode root;

    /** the symbol of the result variable
     */
    protected Symbol resultVar;

    /** methods to generate scala code
     */
    protected CodeFactory cf;

    /** methods to create pattern nodes
     */
    protected PatternNodeCreator mk;

    /** constructor
     */
    public PatternMatcher(Unit unit, Tree selector,
                          Symbol owner, Type resultType) {
        super(unit);
        initialize(selector, owner, resultType, true);
    }

    /** constructor, used in subclass AlgebraicMatcher
     */
    protected PatternMatcher(Unit unit) {
        super(unit);
    }

    /** init method, also needed in subclass AlgebraicMatcher
     */
    protected void initialize(Tree selector, Symbol owner, Type resultType,  boolean doBinding) {
        this.mk = new PatternNodeCreator(unit, owner);
        this.cf = new CodeFactory(unit, selector.pos, owner);
        this.root = mk.ConstrPat(selector.pos, selector.type.widen());
        this.root.and = mk.Header(selector.pos,
                                  selector.type.widen(),
                                  gen.Ident(selector.pos, root.symbol()));
        this.resultVar = owner.newVariable(selector.pos,
                                           Modifiers.MUTABLE,
                                           fresh.newName(RESULT_N));
        this.resultVar.setType(resultType);
        this.owner = owner;
        this.selector = selector;
        this.optimize &= (unit.global.target == Global.TARGET_JVM);
        this.doBinding = doBinding;
    }

    /** pretty printer
     */
    public void print() {
        print(root.and, "");
    }

    public void print(PatternNode patNode, String indent) {
        if (patNode == null)
            System.out.println(indent + "NULL");
        else if (patNode instanceof Header) {
                Tree selector = ((Header)patNode).selector;
                Header next = ((Header)patNode).next;
                System.out.println(indent + "HEADER(" + patNode.type +
                                   ", " + selector + ")");
                print(patNode.or, indent + "|");
                if (next != null)
                    print(next, indent);
        } else if (patNode instanceof ConstrPat) {
                Symbol casted = ((ConstrPat)patNode).casted;
                String s = "-- " + patNode.type.symbol().name +
                    "(" + patNode.type + ", " + casted + ") -> ";
                String ind = indent;
                indent = (patNode.or != null) ?
                    indent :
                    indent.substring(0, indent.length() - 1) + " ";
                for (int i = 0; i < s.length(); i++)
                    indent += " ";
                System.out.println(ind + s);
                print(patNode.and, indent);
                if (patNode.or != null)
                    print(patNode.or, ind);
        } else if (patNode instanceof SequencePat) {
                Symbol casted = ((SequencePat)patNode).casted;
                int plen = ((SequencePat)patNode).len;
                String s = "-- " + patNode.type.symbol().name + "(" + patNode.type +
                    ", " + casted + ", " + plen + ") -> ";
                String ind = indent;
                indent = (patNode.or != null) ?
                    indent :
                    indent.substring(0, indent.length() - 1) + " ";
                for (int i = 0; i < s.length(); i++)
                    indent += " ";
                System.out.println(ind + s);
                print(patNode.and, indent);
                if (patNode.or != null)
                    print(patNode.or, ind);
        } else if (patNode instanceof DefaultPat) {
                System.out.println(indent + "-- _ -> ");
                print(patNode.and, indent.substring(0, indent.length() - 1) +
                      "         ");
                if (patNode.or != null)
                    print(patNode.or, indent);
        } else if (patNode instanceof ConstantPat) {
                AConstant value = ((ConstantPat)patNode).value;
                String  s = "-- CONST(" + value + ") -> ";
                String  ind = indent;
                indent = (patNode.or != null) ?
                    indent :
                    indent.substring(0, indent.length() - 1) + " ";
                for (int i = 0; i < s.length(); i++)
                    indent += " ";
                System.out.println(ind + s);
                print(patNode.and, indent);
                if (patNode.or != null)
                    print(patNode.or, ind);
        } else if (patNode instanceof VariablePat) {
                Tree tree = ((VariablePat)patNode).tree;
                String  s = "-- STABLEID(" + tree + ": " + patNode.type + ") -> ";
                String  ind = indent;
                indent = (patNode.or != null) ?
                    indent :
                    indent.substring(0, indent.length() - 1) + " ";
                for (int i = 0; i < s.length(); i++)
                    indent += " ";
                System.out.println(ind + s);
                print(patNode.and, indent);
                if (patNode.or != null)
                    print(patNode.or, ind);
        } else if (patNode instanceof AltPat) {
                Header header = ((AltPat)patNode).subheader;
                System.out.println(indent + "-- ALTERNATIVES:");
                print(header, indent + "   * ");
                print(patNode.and, indent + "   * -> ");
                if (patNode.or != null)
                    print(patNode.or, indent);
        } else if (patNode instanceof Body) {
                Tree[] guards = ((Body)patNode).guard;
                Tree[] stats = ((Body)patNode).body;
                if ((guards.length == 0) && (stats.length == 0))
                    System.out.println(indent + "true");
                else
                    System.out.println(indent + "BODY(" + stats.length + ")");
        }
    }

    /** enters a sequence of cases into the pattern matcher
     */
    public void construct(Tree[] cases) {
        for( int i = 0; i < cases.length; i++ )
            enter(cases[i]);
    }

    /** enter a single case into the pattern matcher
     */
    protected void enter(Tree caseDef) {
        if (caseDef instanceof CaseDef) {
            Tree pat = ((CaseDef)caseDef).pat;
            Tree guard = ((CaseDef)caseDef).guard;
            Tree body = ((CaseDef)caseDef).body;
            CaseEnv env = new CaseEnv(owner, unit);
            // PatternNode matched = match(pat, root);
            PatternNode target = enter1(pat, -1, root, root.symbol(), env);
            // if (target.and != null)
            //     unit.error(pat.pos, "duplicate case");
            if (target.and == null)
                target.and = mk.Body(caseDef.pos, env.boundVars(), guard, body);
            else if (target.and instanceof Body)
                updateBody((Body)target.and, env.boundVars(), guard, body);
            else
                unit.error(pat.pos, "duplicate case");
        }
    }

    protected void updateBody(Body tree, ValDef[] bound, Tree guard, Tree body) {
        if (tree.guard[tree.guard.length - 1] == Tree.Empty) {
            //unit.error(body.pos, "unreachable code");
        } else {
            ValDef[][] bd = new ValDef[tree.bound.length + 1][];
            Tree[] ng = new Tree[tree.guard.length + 1];
            Tree[] nb = new Tree[tree.body.length + 1];
            System.arraycopy(tree.bound, 0, bd, 0, tree.bound.length);
            System.arraycopy(tree.guard, 0, ng, 0, tree.guard.length);
            System.arraycopy(tree.body, 0, nb, 0, tree.body.length);
            bd[bd.length - 1] = bound;
            ng[ng.length - 1] = guard;
            nb[nb.length - 1] = body;
            tree.bound = bd;
            tree.guard = ng;
            tree.body = nb;
        }
    }

    protected Tree[] patternArgs(Tree tree) {
        if (tree instanceof Bind) {
            Tree pat = ((Bind)tree).rhs;
            return patternArgs(pat);
        }
        if (tree instanceof Apply) {
            Tree[] args = ((Apply)tree).args;
            if ( isSeqApply((Apply) tree)  && !delegateSequenceMatching)
                if (args[0] instanceof Sequence) {
                    Tree[] ts = ((Sequence)args[0]).trees;
                    return ts;
                }
            return args;
        }
        if (tree instanceof Sequence) {
            Tree[] ts = ((Sequence)tree).trees;
            if (!delegateSequenceMatching)
                return ts;
            return Tree.EMPTY_ARRAY;
        }
        return Tree.EMPTY_ARRAY;
    }

    protected boolean isSeqApply( Tree.Apply tree ) {
        return (tree.args.length == 1 &&
                (tree.type.symbol().flags & Modifiers.CASE) == 0 &&
                tree.args[0] instanceof Sequence);
    }

    protected PatternNode patternNode(Tree tree, Header header, CaseEnv env) {
        if (tree instanceof Bind) {
            Bind bind = (Bind)tree;
            Name name = bind.name;
            Tree pat = bind.rhs;
            if (pat instanceof Typed &&
                ((Typed)pat).expr instanceof Ident &&
                ((Ident)((Typed)pat).expr).name == Names.PATTERN_WILDCARD) { // little opt. for x@_:Type
                Tree tpe = ((Typed)pat).tpe;
                if(header.type.isSubType(tpe.type)) {
                PatternNode node = mk.DefaultPat(tree.pos, tpe.type);
                env.newBoundVar( tree.symbol(), tree.type, header.selector );
                return node;
                } else {
                ConstrPat node = mk.ConstrPat(tree.pos, tpe.type);
                env.newBoundVar( tree.symbol(), tree.type, gen.Ident(tree.pos, node.casted));
                return node;
                }
            }
            if (pat instanceof Ident &&
                ((Ident)pat).name == Names.PATTERN_WILDCARD) { // little opt. for x@_
            PatternNode node = mk.DefaultPat(tree.pos, header.type);
            if ((env != null) && (tree.symbol() != defs.PATTERN_WILDCARD))
                env.newBoundVar( tree.symbol(), tree.type, header.selector);
            return node;
            }
            PatternNode node = patternNode(pat, header, env);
            if ((env != null) && (tree.symbol() != defs.PATTERN_WILDCARD)) {
                Symbol casted = node.symbol();
                Tree theValue =  (casted == Symbol.NONE) ? header.selector : gen.Ident(tree.pos, casted);
                env.newBoundVar( tree.symbol(), tree.type, theValue );
            }
            return node;
        }
        if (tree instanceof Apply) {             // pattern with args
            Tree[] args = ((Apply)tree).args;
            Tree fn = ((Apply)tree).fun;
            if(isSeqApply((Apply)tree)) {
                if (!delegateSequenceMatching) {
                    if (args[0] instanceof Sequence) {
                        Tree[] ts = ((Sequence)args[0]).trees;
                        return mk.SequencePat( tree.pos, tree.type, ts.length );
                    }
                } else {
                    PatternNode res = mk.ConstrPat(tree.pos, tree.type);
                    res.and = mk.Header(tree.pos, header.type, header.selector);
                    res.and.and = mk.SeqContainerPat( tree.pos, tree.type, args[ 0 ] );
                    return res;
                }
            } else if ((fn.symbol() != null) &&
                       fn.symbol().isStable() &&
                       !(fn.symbol().isModule() &&
                         ((fn.symbol().flags & Modifiers.CASE) != 0)))
                return mk.VariablePat(tree.pos, tree);
            return mk.ConstrPat(tree.pos, tree.type);
        }
        if (tree instanceof Typed && ((Typed)tree).expr instanceof Ident) {       // variable pattern
            Ident ident = (Ident)((Typed)tree).expr;
            Tree tpe = ((Typed)tree).tpe;
            boolean doTest = header.type.isSubType(tpe.type);
            PatternNode node = doTest ?
                mk.DefaultPat(tree.pos, tpe.type)
                : mk.ConstrPat(tree.pos, tpe.type);
            if ((env != null) && (ident.symbol() != defs.PATTERN_WILDCARD)) {
                if (node instanceof ConstrPat) {
                    Symbol casted = ((ConstrPat)node).casted;
                    env.newBoundVar(
                                    ((Tree.Typed)tree).expr.symbol(),
                                    tpe.type,
                                    gen.Ident(tree.pos, casted));
                } else {
                    env.newBoundVar(
                                    ((Tree.Typed)tree).expr.symbol(),
                                    tpe.type,
                                    doTest ? header.selector : gen.Ident(tree.pos, ((ConstrPat) node).casted));
                }
            }
            return node;
        }
        if (tree instanceof Ident) {                  // pattern without args or variable
            Name name = ((Ident)tree).name;
            if (tree.symbol() == defs.PATTERN_WILDCARD)
                return mk.DefaultPat(tree.pos, header.type);
            else if (tree.symbol().isPrimaryConstructor()) {
                assert false; // this may not happen ??  ----------------- Burak
                return mk.ConstrPat(tree.pos, tree.type);
            } else if (name.isVariable()) {// should be Bind  ------------ Burak
                assert false : "encountered ident:"+name;
                if (env != null)
                    env.newBoundVar(tree.symbol(),
                                    tree.type,
                                    header.selector);
                return mk.DefaultPat(tree.pos, header.type);
            } else
                return mk.VariablePat(tree.pos, tree);
        }
        if (tree instanceof Select) {                                    // variable
            if (tree.symbol().isPrimaryConstructor())
                return mk.ConstrPat(tree.pos, tree.type);
            else
                return mk.VariablePat(tree.pos, tree);
        }
        if (tree instanceof Literal) {
            AConstant value = ((Literal)tree).value;
            return mk.ConstantPat(tree.pos, tree.type, value);
        }
        if (tree instanceof Sequence) {
            Tree[] ts = ((Sequence)tree).trees;
            if ( !delegateSequenceMatching ) {
                return mk.SequencePat(tree.pos, tree.type, ts.length);
            } else {
                return mk.SeqContainerPat(tree.pos, tree.type, tree);
            }
        }
        if (tree instanceof Alternative) {
            Tree[] ts = ((Alternative)tree).trees;
            assert ts.length > 1;
            PatternNode subroot = mk.ConstrPat(header.pos, header.type);
            subroot.and = mk.Header(header.pos, header.type, header.selector.duplicate());
            CaseEnv subenv = new CaseEnv(owner, unit);
            for (int i = 0; i < ts.length; i++) {
                PatternNode target = enter1(ts[i], -1, subroot, subroot.symbol(), subenv);
                target.and = mk.Body(tree.pos);
            }
            return mk.AltPat(tree.pos, (Header)subroot.and);
        }
        unit.global.newTextTreePrinter(new java.io.PrintWriter(System.out)).print(tree).flush();
        throw new ApplicationError("unit = " + unit + "; tree = "+tree);
    }

    protected PatternNode enter(Tree pat,
                             int index,
                             PatternNode target,
                             Symbol casted,
                             CaseEnv env) {
        if (target instanceof ConstrPat) {
            Symbol newCasted = ((ConstrPat)target).casted;
            return enter1(pat, index, target, newCasted, env);
        }
        if (target instanceof SequencePat) {
            Symbol newCasted = ((SequencePat)target).casted;
            return enter1(pat, index, target, newCasted, env);
        }
        return enter1(pat, index, target, casted, env);
    }

    private Header newHeader( int pos, Symbol casted, int index ) {
        Tree ident = gen.Ident( pos, casted );
        if (casted.pos == Position.FIRSTPOS) {
            Tree t =
                gen.mkApply_V(
                              gen.Select( ident, defs.FUNCTION_APPLY( 1 )),
                              new Tree[]{ gen.mkIntLit( pos, index ) });
            Type seqType = t.type;
            return mk.Header( pos, seqType, t );
        } else {
            Symbol ts = casted.getType().symbol().caseFieldAccessor(index);
            Type accType = casted.getType().memberType(ts);
            Tree accTree = gen.Select( ident, ts);
            if (accType instanceof Type.MethodType) {
                // scala case accessor
                return mk.Header(
                                 pos,
                                 accType.resultType(),
                                 gen.mkApply__(accTree));
            }
            // jaco case accessor
            return mk.Header(pos, accType, accTree);
        }
    }

    /** main enter function
     *
     *  invariant: ( curHeader == (Header)target.and ) holds
     */
    protected PatternNode enter1(Tree pat,
                              int index,
                              PatternNode target,
                              Symbol casted,
                              CaseEnv env) {
        //System.err.println("enter(" + pat + ", " + index + ", " + target + ", " + casted + ")");

        Tree[] patArgs = patternArgs(pat);        // get pattern arguments
        Header curHeader = (Header)target.and;    // advance one step in intermediate representation
        if (curHeader == null) {                  // check if we have to add a new header
            assert index >= 0 : casted;
            target.and = curHeader = newHeader(pat.pos, casted, index);
            curHeader.or = patternNode(pat, curHeader, env);
            return enter(patArgs, curHeader.or, casted, env);
        }
        // find most recent header
        while (curHeader.next != null)
            curHeader = curHeader.next;
        // create node
        PatternNode patNode = patternNode(pat, curHeader, env);
        PatternNode next = curHeader;
        // add branch to curHeader, but reuse tests if possible
        while (true)
            if ( next.isSameAs( patNode ) ) {           // test for patNode already present --> reuse
                if (patNode instanceof ConstrPat) {
                    Symbol ocasted = ((ConstrPat)patNode).casted;
                    env.substitute( ocasted, gen.Ident(patNode.pos,
                                                       ((ConstrPat) next).casted));
                }
                return enter(patArgs, next, casted, env);
            }
            else if ( next.isDefaultPat() ||         // default case reached, or
                      ((next.or == null) &&          //  no more alternatives and
                       ( patNode.isDefaultPat() || next.subsumes( patNode )))) // new node is default or subsumed
                return enter(                        // create independent new header , because cannot use this one
                             patArgs,
                             (curHeader = (curHeader.next =
                                           mk.Header(patNode.pos, curHeader.type, curHeader.selector))).or
                             = patNode,
                             casted,
                             env);
            else if (next.or == null)
                return enter(patArgs, next.or = patNode, casted, env); // add new branch
            else
                next = next.or;
    }

    /** calls enter for an array of patterns, see enter
     */
    protected PatternNode enter(Tree[] pats, PatternNode target, Symbol casted, CaseEnv env) {
        if (target instanceof ConstrPat) {
            Symbol newCasted = ((ConstrPat)target).casted;
            casted = newCasted;
        } else if (target instanceof SequencePat) {
            Symbol newCasted = ((SequencePat)target).casted;
            casted = newCasted;
        }
        for (int i = 0; i < pats.length; i++)
            target = enter1(pats[i], i, target, casted, env);
        return target;
    }

    protected int nCaseComponents(Tree tree) {
        if (tree instanceof Apply) {
            Type tpe = tree.type.symbol().primaryConstructor().type();
            //System.out.println("~~~ " + tree.type() + ", " + tree.type().symbol().primaryConstructor());
            if (tpe == Type.NoType) {
                // I'm not sure if this is a good idea, but obviously, currently all case classes
                // without constructor arguments have type NoType
                assert false;
                return 0;
            }
            if (tpe instanceof Type.MethodType) {
                Symbol[] args = ((Type.MethodType)tpe).vparams;
                return args.length;
            }
            if (tpe instanceof Type.PolyType && ((Type.PolyType)tpe).result instanceof Type.MethodType) {
                Symbol[] args = ((Type.MethodType)((Type.PolyType)tpe).result).vparams;
                return args.length;
            }
            if (tpe instanceof Type.PolyType) {
                return 0;
            }
            throw new ApplicationError("not yet implemented;" +
                                       "pattern matching for " + tree + ": " + tpe);
        }
        return 0;
    }


    //////////// generator methods

    public Tree toTree() {
        if (optimize && isSimpleIntSwitch())
            return intSwitchToTree();
        else if (false && optimize && isSimpleSwitch())
            return switchToTree();
        else
            return generalSwitchToTree();
    }

    protected boolean isSimpleIntSwitch() {
        if (selector.type.widen().isSameAs(defs.int_TYPE())) {
            PatternNode patNode = root.and;
            while (patNode != null) {
                PatternNode node = patNode;
                while ((node = node.or) != null) {
                    if (!(node instanceof ConstantPat)) {
                        return false;
                    }
                    if (!(node.and instanceof Body)) {
                        return false;
                    }
                    Body body = (Body)node.and;
                    if ((body.guard.length > 1) ||
                        (body.guard[0] != Tree.Empty) ||
                        (body.bound[0].length > 0)) {
                        return false;
                    }
                }
                patNode = patNode.next();
            }
            return true;
        } else
            return false;
    }

    protected boolean isSimpleSwitch() {
        print();
        PatternNode patNode = root.and;
        while (patNode != null) {
            PatternNode node = patNode;
            while ((node = node.or) != null) {
                if (node instanceof VariablePat) {
                    Tree tree = ((VariablePat)node).tree;
                    System.out.println(((tree.symbol().flags & Modifiers.CASE) != 0));
                } else if (node instanceof ConstrPat) {
                    System.out.println(node.type + " / " + ((node.type.symbol().flags & Modifiers.CASE) != 0));
                    PatternNode inner = node.and;
                    outer: while (true) {
                        if (inner instanceof Header) {
                            Header innerHeader = (Header)inner;
                            if (innerHeader.next != null) {
                                return false;
                            }
                            inner = inner.or;
                        } else if (inner instanceof DefaultPat) {
                            inner = inner.and;
                        } else if (inner instanceof Body) {
                            Body innerBody = (Body)inner;
                            if ((innerBody.guard.length > 1) ||
                                (innerBody.guard[0] != Tree.Empty)) {
                                return false;
                            }
                            break outer;
                        } else {
                            System.out.println(inner);
                            return false;
                        }
                    }
                } else {
                    return false;
                }
            }
            patNode = patNode.next();
        }
        return true;
    }

    static class TagBodyPair {
        int tag;
        Tree body;
        TagBodyPair next;

        TagBodyPair(int tag, Tree body, TagBodyPair next) {
            this.tag = tag;
            this.body = body;
            this.next = next;
        }

        int length() {
            return (next == null) ? 1 : (next.length() + 1);
        }
    }

    static TagBodyPair insert(int tag, Tree body, TagBodyPair current) {
        if (current == null)
            return new TagBodyPair(tag, body, null);
        else if (tag > current.tag)
            return new TagBodyPair(current.tag, current.body, insert(tag, body, current.next));
        else
            return new TagBodyPair(tag, body, current);
    }

    protected int numCases(PatternNode patNode) {
        int n = 0;
        while ((patNode = patNode.or) != null) {
            if (!(patNode instanceof DefaultPat)) {
                n++;
            }
        }
        return n;
    }

    protected Tree defaultBody(PatternNode patNode, Tree otherwise) {
        while (patNode != null) {
            PatternNode node = patNode;
            while ((node = node.or) != null) {
                if (node instanceof DefaultPat) {
                    return bodyToTree(node.and);
                }
            }
            patNode = patNode.next();
        }
        return otherwise;
    }

    /** This method translates pattern matching expressions that match
     *  on integers on the top level.
     */
    public Tree intSwitchToTree() {
        //print();
        int ncases = numCases(root.and);
        Tree matchError = cf.ThrowMatchError(selector.pos, resultVar.getType());
        // without a case, we return a match error if there is no default case
        if (ncases == 0)
            return defaultBody(root.and, matchError);
        // for one case we use a normal if-then-else instruction
        else if (ncases == 1) {
            if (root.and.or instanceof ConstantPat) {
                AConstant value = ((ConstantPat)root.and.or).value;
                return gen.If(
                              cf.Equals(selector,
                                        gen.Literal(root.and.or.pos, value)),
                              bodyToTree(root.and.or.and),
                              defaultBody(root.and, matchError));
            }
            return generalSwitchToTree();
        }
        //
        // if we have more than 2 cases than use a switch statement
        if (root.and instanceof Header) {
            TagBodyPair mappings = null;
            Tree defaultBody = matchError;
            PatternNode patNode = root.and;
            while (patNode != null) {
                PatternNode node = patNode.or;
                while (node != null) {
                    if (node instanceof DefaultPat) {
                        if (defaultBody != null)
                            throw new ApplicationError();
                        defaultBody = bodyToTree(node.and);
                        node = node.or;
                    } else if (node instanceof ConstantPat &&
                               ((ConstantPat)node).value instanceof AConstant.INT) {
                        int value = ((AConstant.INT)((ConstantPat)node).value).value;
                        mappings = insert(
                                          value,
                                          bodyToTree(node.and),
                                          mappings);
                        node = node.or;
                    } else {
                        throw new ApplicationError(node.toString());
                    }
                }
                patNode = patNode.next();
            }
            if (mappings == null) {
                return gen.Switch(selector, new int[0], new Tree[0], defaultBody, resultVar.getType());
            } else {
                int n = mappings.length();
                int[] tags = new int[n];
                Tree[] bodies = new Tree[n];
                n = 0;
                while (mappings != null) {
                    tags[n] = mappings.tag;
                    bodies[n++] = mappings.body;
                    mappings = mappings.next;
                }
                return gen.Switch(selector, tags, bodies, defaultBody, resultVar.getType());
            }
        }
        throw new ApplicationError();
    }

    protected Tree bodyToTree(PatternNode node) {
        if (node instanceof Body) {
            return ((Body)node).body[0];
        }
        throw new ApplicationError();
    }

    public Tree switchToTree() {
        throw new Error();
    }

    public Tree generalSwitchToTree() {
        Tree[] ts = {
            gen.ValDef(root.symbol(), selector),
            gen.ValDef(resultVar, gen.mkDefaultValue(selector.pos, resultVar.getType()))};
        Tree res = gen.If(
                          selector.pos,
                          toTree(root.and),
                          gen.Ident(selector.pos, resultVar),
                          cf.ThrowMatchError(selector.pos, resultVar.getType(), gen.Ident(selector.pos, root.symbol())));
        return gen.mkBlock(selector.pos, ts, res);
    }

    protected Tree toTree(PatternNode node) {
        Tree res = gen.mkBooleanLit(node.pos, false);
        while (node != null) {
            if (node instanceof Header) {
                Header header = (Header)node;
                Tree headerSelector = header.selector;
                //res = cf.And(mkNegate(res), toTree(node.or, selector));
                //System.out.println("HEADER TYPE = " + selector.type);
                if (optimize(node.type, node.or))
                    res = cf.Or(res, toOptTree(node.or, headerSelector));
                else
                    res = cf.Or(res, toTree(node.or, headerSelector));
                node = header.next;
            } else if (node instanceof Body) {
                Body bodyNode = (Body)node;
                ValDef[][] bound = bodyNode.bound;
                Tree[] guard = bodyNode.guard;
                Tree[] body = bodyNode.body;
                if ((bound.length == 0) &&
                    (guard.length == 0) &&
                    (body.length == 0)) {
                    return gen.mkBooleanLit(node.pos, true); // cf.Or(res, gen.mkBooleanLit(node.pos, true));
                } else if (!doBinding)
                    bound = new ValDef[][]{new ValDef[]{}};
                for (int i = guard.length - 1; i >= 0; i--) {
                    Tree[] ts = bound[i];
                    Tree res0 = gen.mkBlock(
                                            gen.Assign(
                                                       gen.Ident(body[i].pos, resultVar),
                                                       body[i]),
                                            gen.mkBooleanLit(body[i].pos, true));
                    if (guard[i] != Tree.Empty)
                        res0 = cf.And(guard[i], res0);
                    res = cf.Or(gen.mkBlock(body[i].pos, ts, res0), res);
                }
                return res;
            } else {
                throw new ApplicationError();
            }
        }
        return res;
    }

    protected boolean optimize(Type selType, PatternNode alternatives) {
        if (!optimize || !selType.isSubType(defs.SCALAOBJECT_TYPE()))
            return false;
        int cases = 0;
        while (alternatives != null) {
            if (alternatives instanceof ConstrPat) {
                if (alternatives.type.symbol().isCaseClass())
                    cases++;
                else
                    return false;
            } else if (!(alternatives instanceof DefaultPat)) {
                return false;
            }
            alternatives = alternatives.or;
        }
        return cases > 2;
    }

    static class TagNodePair {
        int tag;
        PatternNode node;
        TagNodePair next;

        TagNodePair(int tag, PatternNode node, TagNodePair next) {
            this.tag = tag;
            this.node = node;
            this.next = next;
        }

        int length() {
            return (next == null) ? 1 : (next.length() + 1);
        }
    }

    static TagNodePair insert(int tag, PatternNode node, TagNodePair current) {
        if (current == null)
            return new TagNodePair(tag, node, null);
        else if (tag > current.tag)
            return new TagNodePair(current.tag, current.node, insert(tag, node, current.next));
        else if (tag == current.tag) {
            PatternNode tail = current.node;
            while (tail.or != null)
                tail = tail.or;
            tail.or = node;
            return current;
        } else
            return new TagNodePair(tag, node, current);
    }

    static TagNodePair insertNode(int tag, PatternNode node, TagNodePair current) {
        PatternNode newnode = node.dup();
        newnode.or = null;
        return insert(tag, newnode, current);
    }

    protected Tree toOptTree(PatternNode node, Tree selector) {
        //System.err.println("pm.toOptTree called"+node);
        TagNodePair cases = null;
        PatternNode defaultCase = null;
        while (node != null) {
            if (node instanceof ConstrPat) {
                cases = insertNode(node.type.symbol().tag(), node, cases);
                node = node.or;
            } else if (node instanceof DefaultPat) {
                defaultCase = node;
                node = node.or;
            } else {
                throw new ApplicationError();
            }
        }
        int n = cases.length();
        int[] tags = new int[n];
        Tree[] bodies = new Tree[n];
        n = 0;
        while (cases != null) {
            tags[n] = cases.tag;
            bodies[n++] = toTree(cases.node, selector);
            cases = cases.next;
        }
        return gen.Switch(gen.mkApply__(gen.Select(selector.duplicate(), defs.SCALAOBJECT_TAG())),
                          tags,
                          bodies,
                          (defaultCase == null) ? gen.mkBooleanLit(selector.pos, false)
                          : toTree(defaultCase.and),
                          defs.boolean_TYPE());
    }

    protected Tree toTree(PatternNode node, Tree selector) {
        //System.err.println("pm.toTree("+node+","+selector+")");
        if (node == null)
            return gen.mkBooleanLit(selector.pos, false);
        if (node instanceof DefaultPat) {
            return toTree(node.and);
        }
        if (node instanceof ConstrPat) {
            Symbol casted = ((ConstrPat)node).casted;
            return gen.If(gen.mkIsInstanceOf(selector.duplicate(), node.type),
                          gen.mkBlock(gen.ValDef(casted,
                                                 gen.mkAsInstanceOf(selector.duplicate(), node.type)),
                                      toTree(node.and)),
                          toTree(node.or, selector.duplicate()));
        }
        if (node instanceof SequencePat) {
            SequencePat sequencePat = (SequencePat)node;
            Symbol casted = sequencePat.casted;
            int len = sequencePat.len;
            return gen.If(cf.And(gen.mkIsInstanceOf(selector.duplicate(), node.type),
                                 cf.Equals(gen.mkApply__(gen.Select(gen.mkAsInstanceOf(selector.duplicate(), node.type),
                                                                    defs.SEQ_LENGTH())),
                                           gen.mkIntLit(selector.pos, len))),
                          gen.mkBlock(gen.ValDef(casted,
                                                 gen.mkAsInstanceOf(selector.duplicate(), node.type)),
                                      toTree(node.and)),
                          toTree(node.or, selector.duplicate()));
        }
        if (node instanceof ConstantPat) {
            AConstant value = ((ConstantPat)node).value;
            return gen.If(cf.Equals(selector.duplicate(),
                                    gen.Literal(selector.pos, value)),
                          toTree(node.and),
                          toTree(node.or, selector.duplicate()));
        }
        if (node instanceof VariablePat) {
            Tree tree = ((VariablePat)node).tree;
            return gen.If(cf.Equals(selector.duplicate(), tree),
                          toTree(node.and),
                          toTree(node.or, selector.duplicate()));
        }
        if (node instanceof AltPat) {
            Header header = ((AltPat)node).subheader;
            return gen.If(toTree(header),
                          toTree(node.and),
                          toTree(node.or, selector.duplicate()));
        }
        throw new ApplicationError();
    }
}
