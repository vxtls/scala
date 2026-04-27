/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.ast;

import scalac.Global;
import scalac.ast.Tree;
import scalac.symtab.Symbol;
import scalac.symtab.SymbolCloner;
import scalac.symtab.Type;

/**
 * A {@link GenTransformer} variant that clones locally defined symbols while
 * rebuilding the tree.
 */
public class GenTreeCloner extends GenTransformer {

    public final SymbolCloner cloner;

    public GenTreeCloner(Global global, Type.Map map, SymbolCloner cloner) {
        super(global, map);
        this.cloner = cloner;
    }

    public Symbol getSymbolFor(Tree tree) {
        if (tree instanceof Tree.ValDef) {
            if (!tree.symbol().owner().isClass()) {
                Symbol symbol = cloner.cloneSymbol(tree.symbol());
                symbol.setType(transform(symbol.type()));
                return symbol;
            }
        } else if (tree instanceof Tree.LabelDef) {
            Symbol symbol = cloner.cloneSymbol(tree.symbol());
            symbol.setType(transform(symbol.type()));
            return symbol;
        }
        Symbol symbol = (Symbol)cloner.clones.get(tree.symbol());
        assert !tree.definesSymbol() || symbol != null: tree;
        return symbol != null ? symbol : tree.symbol();
    }
}
