package miniJava.ContextualAnalysis;

import miniJava.ErrorReporter;
import miniJava.AbstractSyntaxTrees.Package;
import miniJava.AbstractSyntaxTrees.*;

import java.util.Stack;

public class Identification implements Visitor<Object,Object> {
    private ErrorReporter _errors;
    private ScopedIdentification si;

    public Identification(ErrorReporter errors) {
        this._errors = errors;
        this.si = new ScopedIdentification(_errors);
    }

    public void parse( Package prog ) {
        try {
            visitPackage(prog,null);
        } catch( IdentificationError e ) {
            _errors.reportError(e.toString());
        }
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

    ///////////////////////////////////////////////////////////////////////////////
    //
    // PACKAGE
    //
    ///////////////////////////////////////////////////////////////////////////////

    public Object visitPackage(Package prog, Object arg){
        ClassDeclList cl = prog.classDeclList;
        // Open Level 0 Scope
        si.openScope();

        // Add Predefined Class Declarations
        si.addPredefinedDeclarations();

        // Add Class Declarations to Level 0
        for (ClassDecl c: cl) {
            si.addDeclaration(c.name, c);
        }

        // Open Level 1 Scope
        si.openScope();

        // Public Fields/Methods for Classes
        for (ClassDecl c: cl) {
            // Add Fields for Class to Level 1 Scope
            for (FieldDecl f: c.fieldDeclList) {
                si.addDeclaration(f.name, f);
                f.associatedClass = c;
            }

            // Add Methods for Class to Level 1 Scope
            for (MethodDecl m: c.methodDeclList) {
                si.addDeclaration(m.name, m);
                m.associatedClass = c;
            }

            // Visit the Class
            c.visit(this, arg);
        }

        return null;
    }


    ///////////////////////////////////////////////////////////////////////////////
    //
    // DECLARATIONS
    //
    ///////////////////////////////////////////////////////////////////////////////

    public Object visitClassDecl(ClassDecl clas, Object arg){
        for (FieldDecl f: clas.fieldDeclList)
            // When visiting fields, you now have the context of the class the field is in.
            f.visit(this, clas);
        for (MethodDecl m: clas.methodDeclList)
            // When visiting methods, you now have the context of the class the method is in.
            m.visit(this, clas);
        return null;
    }

    public Object visitFieldDecl(FieldDecl f, Object arg){
        f.type.visit(this, arg);
        return null;
    }

    public Object visitMethodDecl(MethodDecl m, Object arg){
        // Open Level 2+ Scope for Method
        si.openScope();

        m.type.visit(this, arg);
        ParameterDeclList pdl = m.parameterDeclList;
        for (ParameterDecl pd: pdl) {
            // When visiting parameters, you now have the context of the method the parameters are in.
            pd.visit(this, m);
        }
        StatementList sl = m.statementList;
        for (Statement s: sl) {
            // When visiting statements, you now have the context of the method the statements are in.
            s.visit(this, m);
        }

        // Close Level 2+ Scope for Method
        si.closeScope();
        return null;
    }

    public Object visitParameterDecl(ParameterDecl pd, Object arg){
        // Add ParameterDecl to Level 2+ Scope
        si.addDeclaration(pd.name, pd);
        pd.type.visit(this, arg);
        return null;
    }

    public Object visitVarDecl(VarDecl vd, Object arg){
        // Add VarDecl to Level 2+ Scope
        si.addDeclaration(vd.name, vd);
        vd.type.visit(this, arg);
        return null;
    }


    ///////////////////////////////////////////////////////////////////////////////
    //
    // TYPES
    //
    ///////////////////////////////////////////////////////////////////////////////

    public Object visitBaseType(BaseType type, Object arg){
        return null;
    }

    public Object visitClassType(ClassType ct, Object arg){
        ct.className.visit(this, arg);
        return null;
    }

    public Object visitArrayType(ArrayType type, Object arg){
        type.eltType.visit(this, arg);
        return null;
    }


    ///////////////////////////////////////////////////////////////////////////////
    //
    // STATEMENTS
    //
    ///////////////////////////////////////////////////////////////////////////////

    public Object visitBlockStmt(BlockStmt stmt, Object arg){
        // Open Level 2+ Scope for BlockStmt
        si.openScope();

        StatementList sl = stmt.sl;
        for (Statement s: sl) {
            s.visit(this, arg);
        }

        // Close Level 2+ Scope for BlockStmt
        si.closeScope();

        return null;
    }

    public Object visitVardeclStmt(VarDeclStmt stmt, Object arg){
        stmt.varDecl.visit(this, arg);
        stmt.initExp.visit(this, arg);

        // After varDecl and associated Expression visited, varDecl has been initialized.
        stmt.varDecl.isInitialized = true;
        return null;
    }

    public Object visitAssignStmt(AssignStmt stmt, Object arg){
        stmt.ref.visit(this, arg);
        stmt.val.visit(this, arg);
        return null;
    }

    public Object visitIxAssignStmt(IxAssignStmt stmt, Object arg){
        stmt.ref.visit(this, arg);
        stmt.ix.visit(this, arg);
        stmt.exp.visit(this, arg);
        return null;
    }

    public Object visitCallStmt(CallStmt stmt, Object arg){
        stmt.methodRef.visit(this, arg);
        ExprList al = stmt.argList;
        for (Expression e: al) {
            e.visit(this, arg);
        }
        return null;
    }

    public Object visitReturnStmt(ReturnStmt stmt, Object arg){
        if (stmt.returnExpr != null)
            stmt.returnExpr.visit(this, arg);
        return null;
    }

    public Object visitIfStmt(IfStmt stmt, Object arg){
        stmt.cond.visit(this, arg);

        if (stmt.thenStmt instanceof VarDeclStmt) {
            this._errors.reportError("IdentificationError: Solitary variable declaration not allowed in scope to itself.");
        }

        stmt.thenStmt.visit(this, arg);
        if (stmt.elseStmt != null)
            stmt.elseStmt.visit(this, arg);
        return null;
    }

    public Object visitWhileStmt(WhileStmt stmt, Object arg){
        stmt.cond.visit(this, arg);
        if (stmt.body instanceof VarDeclStmt) {
            this._errors.reportError("IdentificationError: Solitary variable declaration not allowed in scope to itself.");
        }
        stmt.body.visit(this, arg);
        return null;
    }


    ///////////////////////////////////////////////////////////////////////////////
    //
    // EXPRESSIONS
    //
    ///////////////////////////////////////////////////////////////////////////////

    public Object visitUnaryExpr(UnaryExpr expr, Object arg){
        expr.operator.visit(this, arg);
        expr.expr.visit(this, arg);
        return null;
    }

    public Object visitBinaryExpr(BinaryExpr expr, Object arg){
        expr.operator.visit(this, arg);
        expr.left.visit(this, arg);
        expr.right.visit(this, arg);
        return null;
    }

    public Object visitRefExpr(RefExpr expr, Object arg){
        expr.ref.visit(this, arg);
        return null;
    }

    public Object visitIxExpr(IxExpr ie, Object arg){
        ie.ref.visit(this, arg);
        ie.ixExpr.visit(this, arg);
        return null;
    }

    public Object visitCallExpr(CallExpr expr, Object arg){
        expr.functionRef.visit(this, arg);
        ExprList al = expr.argList;
        for (Expression e: al) {
            e.visit(this, arg);
        }
        return null;
    }

    public Object visitLiteralExpr(LiteralExpr expr, Object arg){
        expr.lit.visit(this, arg);
        return null;
    }

    public Object visitNewArrayExpr(NewArrayExpr expr, Object arg){
        expr.eltType.visit(this, arg);
        expr.sizeExpr.visit(this, arg);
        return null;
    }

    public Object visitNewObjectExpr(NewObjectExpr expr, Object arg){
        expr.classtype.visit(this, arg);
        return null;
    }


    ///////////////////////////////////////////////////////////////////////////////
    //
    // REFERENCES
    //
    ///////////////////////////////////////////////////////////////////////////////

    public Object visitThisRef(ThisRef ref, Object arg) {
        MemberDecl md = (MemberDecl) arg;

        if (md.isStatic) {
            this._errors.reportError("IdentificationError: Invalid reference to `this` in a static method.");
        }

        ref.associatedClass = md.associatedClass;
        return ref.associatedClass;
    }

    public Object visitIdRef(IdRef ref, Object arg) {
        ref.id.visit(this, arg);
        return null;
    }

    public Object visitQRef(QualRef qr, Object arg) {
        MemberDecl context = (MemberDecl) arg;

        // Determine LHS Context
        Stack<Reference> refStack = new Stack<>();

        Reference currRef = qr;
        // Load refs into refStack
        while (currRef instanceof QualRef) {
            refStack.push(currRef);
            currRef = ((QualRef) currRef).ref;
        }

        // Parse stack
        while (!refStack.isEmpty()) {
            Declaration decl = null;
            if (currRef instanceof ThisRef) {
                // Visit to ensure ThisRef has an associated declaration!
                currRef.visit(this, arg);
                decl = ((ThisRef) currRef).associatedClass;
            } else if (currRef instanceof IdRef) {
                // Visit to ensure id has an associated declaration!
                ((IdRef) currRef).id.visit(this, arg);
                decl = ((IdRef) currRef).id.declaration;
            }

            // get next ref from stack
            currRef = refStack.pop();

            QualRef currQRef = (QualRef) currRef;
            if (decl instanceof LocalDecl) {
                // Handling for case A a = new A(); \ a.b = ... (using a)
                LocalDecl ld = (LocalDecl) decl;

                if (ld.type.typeKind == TypeKind.CLASS) {
                    // get associated class of LocalDecl
                    ClassType ct = (ClassType) ld.type;
                    ClassDecl cd = (ClassDecl) si.findDeclaration(ct.className, context);

                    // Find the id in the class
                    // Set id declaration to found declaration
                    currQRef.id.declaration = si.findDeclarationInClass(currQRef.id, cd, context);
                } else {
                    this._errors.reportError("IdentificationError: Invalid attempt to reference a non-class type identifier.");
                }
            } else if (decl instanceof ClassDecl) {
                // Handling for A.x where A is a class
                ClassDecl cd = (ClassDecl) decl;

                // Find the id in the class
                // Set id declaration to found declaration
                currQRef.id.declaration = si.findDeclarationInClass(currQRef.id, cd, context);
            } else if (decl instanceof MemberDecl) {
                MemberDecl md = (MemberDecl) decl;

                if (md.type.typeKind == TypeKind.CLASS) {
                    // get associated class of MemberDecl
                    ClassType ct = (ClassType) md.type;
                    ClassDecl cd = (ClassDecl) si.findDeclaration(ct.className, context);

                    // Find the id in the class
                    // Set id declaration to found declaration
                    currQRef.id.declaration = si.findDeclarationInClass(currQRef.id, cd, context);
                } else {
                    this._errors.reportError("IdentificationError: Invalid attempt to reference a non-class type identifier.");
                }
            }
        }
        return null;
    }

    public Object visitNullRef(NullRef nr, Object arg) {
        return null;
    }


    ///////////////////////////////////////////////////////////////////////////////
    //
    // TERMINALS
    //
    ///////////////////////////////////////////////////////////////////////////////

    public Object visitIdentifier(Identifier id, Object arg){
        Declaration context = (Declaration) arg;
        id.declaration = si.findDeclaration(id, context);
        return null;
    }

    public Object visitOperator(Operator op, Object arg){
        return null;
    }

    public Object visitIntLiteral(IntLiteral num, Object arg){
        return null;
    }

    public Object visitBooleanLiteral(BooleanLiteral bool, Object arg){
        return null;
    }

    public Object visitNullLiteral(NullLiteral nl, Object arg) {
        return null;
    }
}