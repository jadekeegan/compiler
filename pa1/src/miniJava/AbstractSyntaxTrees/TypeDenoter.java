/**
 * miniJava Abstract Syntax Tree classes
 * @author prins
 * @version COMP 520 (v2.2)
 */
package miniJava.AbstractSyntaxTrees;

import miniJava.SyntacticAnalyzer.SourcePosition;

abstract public class TypeDenoter extends AST {
    
    public TypeDenoter(TypeKind type, SourcePosition posn){
        super(posn);
        typeKind = type;
    }

    public boolean compareType(TypeDenoter t) {
        if (this.typeKind == TypeKind.CLASS && t.typeKind == TypeKind.NULL
                || this.typeKind == TypeKind.NULL && t.typeKind == TypeKind.CLASS) {
            return true;
        }
        return this.typeKind == t.typeKind;
    }
    
    public TypeKind typeKind;
    
}

        