package scalac.transformer.matching ;

import scalac.ast.Tree ;
import scalac.ast.TreeInfo ;
import scalac.symtab.Symbol ;
import scalac.symtab.Type ;
import scalac.ast.Tree.* ;
import scalac.ast.Tree.Literal ;

/** this class represents the label that a transition in an automaton may carry.
 *  these get translated to specific (boolean) tests
 */

public class Label {


      public static class DefaultLabel extends Label {
      }
      public static final DefaultLabel DefaultLabel = new DefaultLabel();

      public static class SimpleLabel extends Label {
            public Literal lit;

            public SimpleLabel(Literal lit) {
                  this.lit = lit;
            }
      }
      public static class TreeLabel extends Label { // Apply, Sequence
            public Tree pat;

            public TreeLabel(Tree pat) {
                  this.pat = pat;
            }
      }

      public static class TypeLabel extends Label { // Apply, Sequence
            public Type tpe;

            public TypeLabel(Type tpe) {
                  this.tpe = tpe;
            }
      }

      public static class Pair extends Label {
            public Integer state;
            public Label lab;

            public Pair(Integer state, Label lab) {
                  this.state = state;
                  this.lab = lab;
            }
      }

      //public case RLabel( Object rstate, Label lab, Symbol vars[] );

      public int hashCode() {
            if (this instanceof DefaultLabel) {
                  return 0;
            }
            if (this instanceof SimpleLabel) {
                  Literal lit = ((SimpleLabel)this).lit;
                  return lit.value.hashCode();
            }
            if (this instanceof TreeLabel) {
                Tree pat = ((TreeLabel)this).pat;
                // if pat is an  Apply, than this case can only be correctly
                // handled if it has no arguments...or there are no collisions
                return pat.getType().hashCode();
            }
            if (this instanceof TypeLabel) {
                  Type type = ((TypeLabel)this).tpe;
                  return type.hashCode();
            }
            return super.hashCode();
      }

      public boolean equals( Object o ) {
            if( !(o instanceof Label ))
                  return false;
            Label oL = (Label) o;
            //System.out.print(this + " equals " + oL);
            if (this instanceof DefaultLabel) {
                  return oL instanceof DefaultLabel;
            }
            if (this instanceof SimpleLabel) {
                  Literal lit = ((SimpleLabel)this).lit;
                  if (oL instanceof SimpleLabel) {
                        Literal lit2 = ((SimpleLabel)oL).lit;
                        return /*(lit.kind == lit2.kind)
				 && */lit.value.equals( lit2.value );
                  }
            } else if (this instanceof TreeLabel) {
                  Tree pat = ((TreeLabel)this).pat;
                  if (oL instanceof TreeLabel) {
                      Tree pat2 = ((TreeLabel)oL).pat;
		      if (pat instanceof Apply) {
			  if (pat2 instanceof Apply) {
			      return TreeInfo.methSymbol( pat ) == TreeInfo.methSymbol( pat2 );
			  }
		      }
		      return pat == pat2;
                  }
            } else if (this instanceof TypeLabel) {
                  Type tpe = ((TypeLabel)this).tpe;
                  if (oL instanceof TypeLabel) {
                        Type tpe2 = ((TypeLabel)oL).tpe;
                        return tpe.equals( tpe2 );
                  }
            } else if (this instanceof Pair) {
                  Pair pair = (Pair)this;
                  Integer state = pair.state;
                  Label lab = pair.lab;
                  if (oL instanceof Pair) {
                        Integer state2 = ((Pair)oL).state;
                        Label lab2 = ((Pair)oL).lab;
                        return  state.equals( state2 )
                                 && lab.equals( lab2 ) ;
                  }
            }
            return false;
      }


      public String toString2() {
            String ext = System.getProperty("extendedMatching");
            if(( ext != null )
               && ext.equals( "true" )) {
                  if (this instanceof DefaultLabel) {
                        return "<>:p"+p;
                  }
                  if (this instanceof SimpleLabel) {
                        Literal lit = ((SimpleLabel)this).lit;
                        return lit.toString()+":p"+p;
                  }
                  if (this instanceof TreeLabel) {
                        Tree pat = ((TreeLabel)this).pat;
                        return pat.getType()+":p"+p;

                  }
            }
            throw new scalac.ApplicationError
                  ("this never happens");
      }

      public String toString() {

            if (this instanceof DefaultLabel) {
                  return "<>";
            }
            if (this instanceof SimpleLabel) {
                  Literal lit = ((SimpleLabel)this).lit;
                  return lit.toString();
            }
            if (this instanceof TreeLabel) {
                  Tree pat = ((TreeLabel)this).pat;
                  return pat.toString();
            }
            if (this instanceof TypeLabel) {
                  Type tpe = ((TypeLabel)this).tpe;
                  return tpe.toString();
            }
            if (this instanceof Pair) {
                  Integer state = ((Pair)this).state;
                  Label lab = ((Pair)this).lab;
                  return "("+state.toString()+","+lab.toString()+")";
            }
            throw new scalac.ApplicationError("this never happens");
      }

      int p = -1; // tree state - only needed for extended matching


}
