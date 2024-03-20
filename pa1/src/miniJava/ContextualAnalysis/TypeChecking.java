package miniJava.ContextualAnalysis;

import miniJava.ErrorReporter;
import miniJava.AbstractSyntaxTrees.*;
import miniJava.AbstractSyntaxTrees.Package;

public class TypeChecking implements Visitor<Object, TypeDenoter> {
    private ErrorReporter _errors;

    public TypeChecking(ErrorReporter errors) {
        this._errors = errors;
    }

    public void parse(Package prog) {
        prog.visit(this, null);
    }

    private void reportTypeError(AST ast, String errMsg) {
        _errors.reportError( ast.posn == null
                ? "*** " + errMsg
                : "*** " + ast.posn.toString() + ": " + errMsg );
    }
    
    ///////////////////////////////////////////////////////////////////////////////
    //
    // PACKAGE
    //
    ///////////////////////////////////////////////////////////////////////////////

    public TypeDenoter visitPackage(Package prog, Object arg){
        ClassDeclList cl = prog.classDeclList;

        for (ClassDecl c: prog.classDeclList){
            c.visit(this, arg);
        }
        return null;
    }


    ///////////////////////////////////////////////////////////////////////////////
    //
    // DECLARATIONS
    //
    ///////////////////////////////////////////////////////////////////////////////

    public TypeDenoter visitClassDecl(ClassDecl clas, Object arg){
        for (FieldDecl f: clas.fieldDeclList)
            f.visit(this, arg);
        for (MethodDecl m: clas.methodDeclList)
            m.visit(this, arg);
        return null;
    }

    public TypeDenoter visitFieldDecl(FieldDecl f, Object arg){
        f.type.visit(this, arg);
        return null;
    }

    public TypeDenoter visitMethodDecl(MethodDecl m, Object arg){
        m.type.visit(this, arg);
        ParameterDeclList pdl = m.parameterDeclList;
        for (ParameterDecl pd: pdl) {
            pd.visit(this, arg);
        }
        StatementList sl = m.statementList;
        for (Statement s: sl) {
            s.visit(this, arg);
        }
        return null;
    }

    public TypeDenoter visitParameterDecl(ParameterDecl pd, Object arg){
        pd.type.visit(this, arg);
        return null;
    }

    public TypeDenoter visitVarDecl(VarDecl vd, Object arg){
        vd.type.visit(this, arg);
        return null;
    }


    ///////////////////////////////////////////////////////////////////////////////
    //
    // TYPES
    //
    ///////////////////////////////////////////////////////////////////////////////

    public TypeDenoter visitBaseType(BaseType type, Object arg){
        return null;
    }

    public TypeDenoter visitClassType(ClassType ct, Object arg){
        ct.className.visit(this, arg);
        return null;
    }

    public TypeDenoter visitArrayType(ArrayType type, Object arg){
        type.eltType.visit(this, arg);
        return null;
    }


    ///////////////////////////////////////////////////////////////////////////////
    //
    // STATEMENTS
    //
    ///////////////////////////////////////////////////////////////////////////////

    public TypeDenoter visitBlockStmt(BlockStmt stmt, Object arg){
        StatementList sl = stmt.sl;
        for (Statement s: sl) {
            s.visit(this, arg);
        }
        return null;
    }

    public TypeDenoter visitVardeclStmt(VarDeclStmt stmt, Object arg){
        stmt.varDecl.visit(this, arg);
        stmt.initExp.visit(this, arg);
        return null;
    }

    public TypeDenoter visitAssignStmt(AssignStmt stmt, Object arg){
        stmt.ref.visit(this, arg);
        stmt.val.visit(this, arg);
        return null;
    }

    public TypeDenoter visitIxAssignStmt(IxAssignStmt stmt, Object arg){
        stmt.ref.visit(this, arg);
        stmt.ix.visit(this, arg);
        stmt.exp.visit(this, arg);
        return null;
    }

    public TypeDenoter visitCallStmt(CallStmt stmt, Object arg){
        stmt.methodRef.visit(this, arg);
        ExprList al = stmt.argList;
        for (Expression e: al) {
            e.visit(this, arg);
        }
        return null;
    }

    public TypeDenoter visitReturnStmt(ReturnStmt stmt, Object arg){

        if (stmt.returnExpr != null)
            stmt.returnExpr.visit(this, arg);
        return null;
    }

    public TypeDenoter visitIfStmt(IfStmt stmt, Object arg){

        stmt.cond.visit(this, arg);
        stmt.thenStmt.visit(this, arg);
        if (stmt.elseStmt != null)
            stmt.elseStmt.visit(this, arg);
        return null;
    }

    public TypeDenoter visitWhileStmt(WhileStmt stmt, Object arg){
        stmt.cond.visit(this, arg);
        stmt.body.visit(this, arg);
        return null;
    }


    ///////////////////////////////////////////////////////////////////////////////
    //
    // EXPRESSIONS
    //
    ///////////////////////////////////////////////////////////////////////////////

    public TypeDenoter visitUnaryExpr(UnaryExpr expr, Object arg){
        expr.operator.visit(this, arg);
        expr.expr.visit(this, arg);
        return null;
    }

    public TypeDenoter visitBinaryExpr(BinaryExpr expr, Object arg){
        expr.operator.visit(this, arg);
        expr.left.visit(this, arg);
        expr.right.visit(this, arg);
        return null;
    }

    public TypeDenoter visitRefExpr(RefExpr expr, Object arg){
        expr.ref.visit(this, arg);
        return null;
    }

    public TypeDenoter visitIxExpr(IxExpr ie, Object arg){
        ie.ref.visit(this, arg);
        ie.ixExpr.visit(this, arg);
        return null;
    }

    public TypeDenoter visitCallExpr(CallExpr expr, Object arg){
        expr.functionRef.visit(this, arg);
        ExprList al = expr.argList;
        for (Expression e: al) {
            e.visit(this, arg);
        }
        return null;
    }

    public TypeDenoter visitLiteralExpr(LiteralExpr expr, Object arg){
        expr.lit.visit(this, arg);
        return null;
    }

    public TypeDenoter visitNewArrayExpr(NewArrayExpr expr, Object arg){
        expr.eltType.visit(this, arg);
        expr.sizeExpr.visit(this, arg);
        return null;
    }

    public TypeDenoter visitNewObjectExpr(NewObjectExpr expr, Object arg){
        expr.classtype.visit(this, arg);
        return null;
    }


    ///////////////////////////////////////////////////////////////////////////////
    //
    // REFERENCES
    //
    ///////////////////////////////////////////////////////////////////////////////

    public TypeDenoter visitThisRef(ThisRef ref, Object arg) {
        return null;
    }

    public TypeDenoter visitIdRef(IdRef ref, Object arg) {
        ref.id.visit(this, arg);
        return null;
    }

    public TypeDenoter visitQRef(QualRef qr, Object arg) {
        qr.id.visit(this, arg);
        qr.ref.visit(this, arg);
        return null;
    }

    public TypeDenoter visitNullRef(NullRef nr, Object arg) {
        return null;
    }


    ///////////////////////////////////////////////////////////////////////////////
    //
    // TERMINALS
    //
    ///////////////////////////////////////////////////////////////////////////////

    public TypeDenoter visitIdentifier(Identifier id, Object arg){
        return null;
    }

    public TypeDenoter visitOperator(Operator op, Object arg){
        return null;
    }

    public TypeDenoter visitIntLiteral(IntLiteral num, Object arg){
        return null;
    }

    public TypeDenoter visitBooleanLiteral(BooleanLiteral bool, Object arg){
        return null;
    }

    public TypeDenoter visitNullLiteral(NullLiteral nl, Object arg) {
        return null;
    }
}