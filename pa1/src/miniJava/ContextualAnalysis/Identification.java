package miniJava.ContextualAnalysis;

import miniJava.ErrorReporter;
import miniJava.AbstractSyntaxTrees.Package;
import miniJava.AbstractSyntaxTrees.*;

public class Identification implements Visitor<Object,Object> {
    private ErrorReporter _errors;
    private ScopedIdentification si = new ScopedIdentification();

    public Identification(ErrorReporter errors) {
        this._errors = errors;
        // TODO: predefined names
    }

    public void parse( Package prog ) {
        try {
            visitPackage(prog,null);
        } catch( IdentificationError e ) {
            _errors.reportError(e.toString());
        }
    }

    public Object visitPackage(Package prog, Object arg) throws IdentificationError {
        throw new IdentificationError(prog, "Identification Error in VisitPackage");
    }

    class IdentificationError extends Error {
        private static final long serialVersionUID = -441346906191470192L;
        private String _errMsg;

        public IdentificationError(AST ast, String errMsg) {
            super();
            this._errMsg = ast.posn == null
                    ? "*** " + errMsg
                    : "*** " + ast.posn.toString() + ": " + errMsg;
        }

        @Override
        public String toString() {
            return _errMsg;
        }
    }

    /**
     * quote a string
     * @param text    string to quote
     */
    private String quote(String text) {
        return ("\"" + text + "\"");
    }

    /**
     * increase depth in AST
     * @param prefix  current spacing to indicate depth in AST
     * @return  new spacing
     */
    private String indent(String prefix) {
        return prefix + "  ";
    }

    ///////////////////////////////////////////////////////////////////////////////
    //
    // PACKAGE
    //
    ///////////////////////////////////////////////////////////////////////////////

    public Object visitPackage(Package prog, String arg){
        ClassDeclList cl = prog.classDeclList;
        String pfx = arg + "  . ";

        // Open Level 0 Scope
        IDTable level0 = new IDTable();
        si.openScope(level0);

        // Add Predefined Class Declarations
        si.addPredefinedDeclarations();

        // Add Class Declarations to Level 0
        for (ClassDecl c: cl) {
            si.addDeclaration(c.name, c);
        }

        // Open Level 1 Scope
        IDTable level1 = new IDTable();
        si.openScope(level1);

        // Public Fields/Methods for Classes
        for (ClassDecl c: cl) {
            // Add Public Fields for Class to Level 1 Scope
            for (FieldDecl f: c.fieldDeclList) {
                if (!f.isPrivate) { si.addDeclaration(f.name, f); }
            }

            // Add Public Methods for Class to Level 1 Scope
            for (MethodDecl m: c.methodDeclList) {
                if (!m.isPrivate) { si.addDeclaration(m.name, m); }
            }
        }

        for (ClassDecl c: cl) {
            // Store Private Methods/Fields for Removal
            FieldDeclList privateFields = new FieldDeclList();
            MethodDeclList privateMethods = new MethodDeclList();

            // Add Private Fields for Class to Level 1 Scope
            for (FieldDecl f: c.fieldDeclList) {
                if (f.isPrivate) {
                    privateFields.add(f);
                    si.addDeclaration(f.name, f);
                }
            }

            // Add Private Methods for Class to Level 1 Scope
            for (MethodDecl m: c.methodDeclList) {
                if (m.isPrivate) {
                    privateMethods.add(m);
                    si.addDeclaration(m.name, m);
                }
            }

            // Visit the Class
            c.visit(this, pfx);

            // Remove Private Fields for Class from Level 1 Scope
            for (FieldDecl f: privateFields) {
                si.removeDeclaration(f.name);
            }

            // Remove Private Methods for Class from Level 1 Scope
            for (MethodDecl m: privateMethods) {
                si.removeDeclaration(m.name);
            }
        }

        return null;
    }


    ///////////////////////////////////////////////////////////////////////////////
    //
    // DECLARATIONS
    //
    ///////////////////////////////////////////////////////////////////////////////

    public Object visitClassDecl(ClassDecl clas, String arg){
        String pfx = arg + "  . ";
        for (FieldDecl f: clas.fieldDeclList)
            f.visit(this, pfx);
        for (MethodDecl m: clas.methodDeclList)
            m.visit(this, pfx);
        return null;
    }

    public Object visitFieldDecl(FieldDecl f, String arg){
        f.type.visit(this, indent(arg));
        return null;
    }

    public Object visitMethodDecl(MethodDecl m, String arg){
        // Open Level 2+ Scope for Method
        IDTable level2 = new IDTable();
        si.openScope(level2);

        m.type.visit(this, indent(arg));
        ParameterDeclList pdl = m.parameterDeclList;
        String pfx = ((String) arg) + "  . ";
        for (ParameterDecl pd: pdl) {
            pd.visit(this, pfx);
        }
        StatementList sl = m.statementList;
        for (Statement s: sl) {
            s.visit(this, pfx);
        }

        // Close Level 2+ Scope for Method
        si.closeScope();
        return null;
    }

    public Object visitParameterDecl(ParameterDecl pd, String arg){
        // Add ParameterDecl to Level 2+ Scope
        si.addDeclaration(pd.name, pd);

        pd.type.visit(this, indent(arg));
        return null;
    }

    public Object visitVarDecl(VarDecl vd, String arg){
        // Add VarDecl to Level 2+ Scope
        si.addDeclaration(vd.name, vd);

        vd.type.visit(this, indent(arg));
        return null;
    }


    ///////////////////////////////////////////////////////////////////////////////
    //
    // TYPES
    //
    ///////////////////////////////////////////////////////////////////////////////

    public Object visitBaseType(BaseType type, String arg){
        return null;
    }

    public Object visitClassType(ClassType ct, String arg){
        ct.className.visit(this, indent(arg));
        return null;
    }

    public Object visitArrayType(ArrayType type, String arg){
        type.eltType.visit(this, indent(arg));
        return null;
    }


    ///////////////////////////////////////////////////////////////////////////////
    //
    // STATEMENTS
    //
    ///////////////////////////////////////////////////////////////////////////////

    public Object visitBlockStmt(BlockStmt stmt, String arg){
        // Open Level 2+ Scope for BlockStmt
        IDTable level2 = new IDTable();
        si.openScope(level2);

        StatementList sl = stmt.sl;
        String pfx = arg + "  . ";
        for (Statement s: sl) {
            s.visit(this, pfx);
        }

        // Close Level 2+ Scope for BlockStmt
        si.closeScope();

        return null;
    }

    public Object visitVardeclStmt(VarDeclStmt stmt, String arg){
        stmt.varDecl.visit(this, indent(arg));
        stmt.initExp.visit(this, indent(arg));
        return null;
    }

    public Object visitAssignStmt(AssignStmt stmt, String arg){
        stmt.ref.visit(this, indent(arg));
        stmt.val.visit(this, indent(arg));
        return null;
    }

    public Object visitIxAssignStmt(IxAssignStmt stmt, String arg){
        stmt.ref.visit(this, indent(arg));
        stmt.ix.visit(this, indent(arg));
        stmt.exp.visit(this, indent(arg));
        return null;
    }

    public Object visitCallStmt(CallStmt stmt, String arg){
        stmt.methodRef.visit(this, indent(arg));
        ExprList al = stmt.argList;
        String pfx = arg + "  . ";
        for (Expression e: al) {
            e.visit(this, pfx);
        }
        return null;
    }

    public Object visitReturnStmt(ReturnStmt stmt, String arg){
        if (stmt.returnExpr != null)
            stmt.returnExpr.visit(this, indent(arg));
        return null;
    }

    public Object visitIfStmt(IfStmt stmt, String arg){
        stmt.cond.visit(this, indent(arg));
        stmt.thenStmt.visit(this, indent(arg));
        if (stmt.elseStmt != null)
            stmt.elseStmt.visit(this, indent(arg));
        return null;
    }

    public Object visitWhileStmt(WhileStmt stmt, String arg){
        stmt.cond.visit(this, indent(arg));
        stmt.body.visit(this, indent(arg));
        return null;
    }


    ///////////////////////////////////////////////////////////////////////////////
    //
    // EXPRESSIONS
    //
    ///////////////////////////////////////////////////////////////////////////////

    public Object visitUnaryExpr(UnaryExpr expr, String arg){
        expr.operator.visit(this, indent(arg));
        expr.expr.visit(this, indent(indent(arg)));
        return null;
    }

    public Object visitBinaryExpr(BinaryExpr expr, String arg){
        expr.operator.visit(this, indent(arg));
        expr.left.visit(this, indent(indent(arg)));
        expr.right.visit(this, indent(indent(arg)));
        return null;
    }

    public Object visitRefExpr(RefExpr expr, String arg){
        expr.ref.visit(this, indent(arg));
        return null;
    }

    public Object visitIxExpr(IxExpr ie, String arg){
        ie.ref.visit(this, indent(arg));
        ie.ixExpr.visit(this, indent(arg));
        return null;
    }

    public Object visitCallExpr(CallExpr expr, String arg){
        expr.functionRef.visit(this, indent(arg));
        ExprList al = expr.argList;
        String pfx = arg + "  . ";
        for (Expression e: al) {
            e.visit(this, pfx);
        }
        return null;
    }

    public Object visitLiteralExpr(LiteralExpr expr, String arg){
        expr.lit.visit(this, indent(arg));
        return null;
    }

    public Object visitNewArrayExpr(NewArrayExpr expr, String arg){
        expr.eltType.visit(this, indent(arg));
        expr.sizeExpr.visit(this, indent(arg));
        return null;
    }

    public Object visitNewObjectExpr(NewObjectExpr expr, String arg){
        expr.classtype.visit(this, indent(arg));
        return null;
    }


    ///////////////////////////////////////////////////////////////////////////////
    //
    // REFERENCES
    //
    ///////////////////////////////////////////////////////////////////////////////

    public Object visitThisRef(ThisRef ref, String arg) {
        // TODO: CHECK SCOPE
        return null;
    }

    public Object visitIdRef(IdRef ref, String arg) {
        ref.id.visit(this, indent(arg));
        return null;
    }

    public Object visitQRef(QualRef qr, String arg) {
        // Determine LHS Context
        // TODO: CHECK SCOPE
        Declaration context = (Declaration) qr.id.visit(this, indent(arg));
        qr.ref.visit(this, indent(arg));
        return null;
    }

    public Object visitNullRef(NullRef nr, String arg) {
        return null;
    }


    ///////////////////////////////////////////////////////////////////////////////
    //
    // TERMINALS
    //
    ///////////////////////////////////////////////////////////////////////////////

    public Object visitIdentifier(Identifier id, String arg){
        Declaration decl = si.findDeclaration(id, null);

        // Report Error if Identifier Not Found
        if (decl == null) {
            _errors.reportError("IdentificationError: Identifier could not be resolved.");
        } else {
            // Assign the Declaration to the Identifier
            id.setContext(decl);
        }
        // Return the Context of the Identifier
        return decl;
    }

    public Object visitOperator(Operator op, String arg){
        return null;
    }

    public Object visitIntLiteral(IntLiteral num, String arg){
        return null;
    }

    public Object visitBooleanLiteral(BooleanLiteral bool, String arg){
        return null;
    }

    public Object visitNullLiteral(NullLiteral nl, String arg) {
        return null;
    }
}