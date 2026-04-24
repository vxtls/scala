package scalac.transformer.matching ;


import scalac.ast.Tree;
import scalac.util.Name;
import scalac.symtab.Symbol ;
import scalac.ast.Traverser ;

import scalac.ast.Tree.Ident;
import scalac.ast.Tree.Bind;


abstract class VariableTraverser extends Traverser {

      boolean isVariableName( Name name ) {
            return ( name.isVariable() ) && ( name != Name.fromString("_") ) ;

      }

      boolean isVariableSymbol( Symbol sym ) {
            return ( sym != null )&&( !sym.isPrimaryConstructor() );
      }

      abstract void handleVariableSymbol( Symbol sym ) ;

      public VariableTraverser() {
            super();
      }


      public void traverse(Tree tree) {
            if (tree instanceof Ident) {
                  Name name = ((Ident)tree).name;
                  Symbol sym;

                  if( isVariableName( name )
                      && isVariableSymbol( sym = tree.symbol() ) )
                        handleVariableSymbol( sym );

                  return;
            }
            if (tree instanceof Bind) {
                  Name name = ((Bind)tree).name;
                  Tree subtree = ((Bind)tree).rhs;
                  Symbol sym;

                  if( isVariableName( name )
                      && isVariableSymbol( sym = tree.symbol() ))
                        handleVariableSymbol( sym );

                  traverse( subtree );

                  return;
            }
            if (tree instanceof Tree.Select) {
                  return;
            }
            super.traverse( tree );
      }


}
