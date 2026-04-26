/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $OldId: ScalaTemplate.java,v 1.8 2002/07/11 11:46:53 paltherr Exp $
// $Id$

package scala.tools.scalai;

import java.lang.reflect.Method;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import scalac.util.Name;
import scalac.symtab.Symbol;
import scalac.util.Debug;

public class ScalaTemplate {

    //########################################################################
    // Private Fields

    private final Evaluator evaluator;
    private final Symbol symbol;
    private final Class proxy;
    private final Function constructor;
    private final Map/*<Method|Symbol,CodePromise>*/ vtable;
    private final Object[] fields;

    //########################################################################
    // Public Constructors

    public ScalaTemplate(Evaluator evaluator, Symbol symbol, Class proxy, Function constructor, Map vtable, Object[] fields) {
        this.evaluator = evaluator;
        this.symbol = symbol;
        this.proxy = proxy;
        this.constructor = constructor;
        this.vtable = vtable;
        this.fields = fields;
    }

    //########################################################################
    // Public Methods - ScalaTemplate interface

    public String getName() {
        return evaluator.getClassName(symbol);
    }

    public Symbol getSymbol() {
        return symbol;
    }

    public Class getProxy() {
        return proxy;
    }

    public Function getConstructor() {
        return constructor;
    }

    public Map getMethods() {
        return new HashMap(vtable);
    }

    public List getFields() {
        List list = new ArrayList(fields.length);
        for (int i = 0; i < fields.length; i++) list.add(fields[i]);
        return list;
    }

    public CodePromise getMethod(Symbol symbol) {
        return (CodePromise)vtable.get(symbol);
    }

    public ScalaObject getHandler() {
        Object[] fields = new Object[this.fields.length];
        System.arraycopy(this.fields, 0, fields, 0, fields.length);
        return new ScalaObject(this, fields);
    }

    public Object invoke(Object self, Symbol method, Object[] args) {
        CodePromise code = (CodePromise)vtable.get(method);
        if (code == null) {
            code = (CodePromise)vtable.get(method.overridingSymbol(symbol.thisType(), true));
        }
        if (code == null) {
            code = (CodePromise)vtable.get(method.overridingSymbol(symbol.thisType()));
        }
        if (code == null) {
            code = findBySignature(method);
        }
        assert code != null : Debug.show(symbol) + "->" + Debug.show(method);
        return evaluator.evaluate(code, self, args);
    }

    private CodePromise findBySignature(Symbol method) {
        java.util.Iterator entries = vtable.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry entry = (Map.Entry)entries.next();
            Object key = entry.getKey();
            if (!(key instanceof Symbol)) continue;
            Symbol candidate = (Symbol)key;
            if (sameMethodSignature(candidate, method))
                return (CodePromise)entry.getValue();
        }
        return null;
    }

    private boolean sameMethodSignature(Symbol candidate, Symbol method) {
        if (!candidate.isMethod() || !method.isMethod()) return false;
        if (candidate.name != method.name) return false;
        if (!candidate.owner().isSubClass(method.owner())) return false;
        Symbol[] candidateParams = candidate.valueParams();
        Symbol[] methodParams = method.valueParams();
        if (candidateParams.length != methodParams.length) return false;
        for (int i = 0; i < candidateParams.length; i++)
            if (!candidateParams[i].type().isSameAs(methodParams[i].type()))
                return false;
        return true;
    }

    public Object invoke(Object self, Method method, Object[] args) {
        CodePromise code = (CodePromise)vtable.get(method);
        assert code != null : Debug.show(symbol) + "->" + Debug.show(method);
        return evaluator.adaptResult(method, evaluator.evaluate(code, self, args));
    }

    //########################################################################
    // Public Methods - Object interface

    public String toString() {
        return "template " + getName();
    }

    //########################################################################
}
