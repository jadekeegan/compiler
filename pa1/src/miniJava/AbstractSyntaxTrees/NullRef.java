package miniJava.AbstractSyntaxTrees;

import miniJava.SyntacticAnalyzer.SourcePosition;

public class NullRef extends Reference {

    public NullRef(SourcePosition posn) {
        super(posn);
    }

    public <A, R> R visit(Visitor<A, R> v, A o) {
        return v.visitNullRef(this, o);
    }
}
