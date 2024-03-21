package miniJava.ContextualAnalysis;

import miniJava.ErrorReporter;
import miniJava.AbstractSyntaxTrees.Package;
import miniJava.AbstractSyntaxTrees.*;

import java.sql.Ref;
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
        si.addPredefinedClassDeclarations();

        // Add Class Declarations to Level 0
        for (ClassDecl c: cl) {
            si.addDeclaration(c.name, c);
        }

        // Open Level 1 Scope
        si.openScope();

        si.addPredefinedMemberDeclarations();

        // Fields/Methods for Classes
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
        }

        for (ClassDecl c: cl) {
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
        return clas.type;
    }

    public Object visitFieldDecl(FieldDecl f, Object arg){
        f.type.visit(this, arg);
        return f.type;
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
        return m.type;
    }

    public Object visitParameterDecl(ParameterDecl pd, Object arg){
        // Add ParameterDecl to Level 2+ Scope
        si.addDeclaration(pd.name, pd);
        pd.type.visit(this, arg);
        return pd.type;
    }

    public Object visitVarDecl(VarDecl vd, Object arg){
        // Add VarDecl to Level 2+ Scope
        si.addDeclaration(vd.name, vd);
        vd.type.visit(this, arg);
        return vd.type;
    }


    ///////////////////////////////////////////////////////////////////////////////
    //
    // TYPES
    //
    ///////////////////////////////////////////////////////////////////////////////

    public Object visitBaseType(BaseType type, Object arg){
        return type;
    }

    public Object visitClassType(ClassType ct, Object arg){
        return ct.className.visit(this, arg);
    }

    public Object visitArrayType(ArrayType type, Object arg){
        return type.eltType.visit(this, arg);
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
        TypeDenoter varType = (TypeDenoter) stmt.varDecl.visit(this, arg);
        TypeDenoter stmtType = (TypeDenoter) stmt.initExp.visit(this, arg);

        if (stmtType.typeKind == TypeKind.NULL) {
            stmt.varDecl.isInitialized = true;
            return null;
        }

        if ((stmt.initExp instanceof CallExpr && !(((CallExpr) stmt.initExp).functionRef instanceof ThisRef) && ((CallExpr) stmt.initExp).functionRef.declaration instanceof ClassDecl)
                || (stmt.initExp instanceof RefExpr && !(((RefExpr) stmt.initExp).ref instanceof ThisRef) && ((RefExpr) stmt.initExp).ref.declaration instanceof ClassDecl)
                || (stmt.initExp instanceof IxExpr && !(((IxExpr) stmt.initExp).ref instanceof ThisRef) && ((IxExpr) stmt.initExp).ref.declaration instanceof ClassDecl)) {
            this._errors.reportError("Identification Error: Invalid class reference (expected field/var).");
        }

        if (!varType.compareType(stmtType)) {
            // want to compare actual types?
            this._errors.reportError("Type Checking Error: Type mismatch between variable declaration of type " + varType.typeKind + " and expression type " + stmtType.typeKind);
        } else if (varType instanceof ClassType && !((ClassType) varType).className.spelling.equals(((ClassType) stmtType).className.spelling)) {
            this._errors.reportError("Type Checking Error: Class type mismatch for equality comparison.");
        }

        // After varDecl and associated Expression visited, varDecl has been initialized.
        stmt.varDecl.isInitialized = true;
        return null;
    }

    public Object visitAssignStmt(AssignStmt stmt, Object arg){
        TypeDenoter refType = (TypeDenoter) stmt.ref.visit(this, arg);
        TypeDenoter stmtType = (TypeDenoter) stmt.val.visit(this, arg);

        if (stmtType.typeKind == TypeKind.NULL) {
            return null;
        }

        if ((stmt.val instanceof CallExpr && !(((CallExpr) stmt.val).functionRef instanceof ThisRef) && ((CallExpr) stmt.val).functionRef.declaration instanceof ClassDecl)
                || (stmt.val instanceof RefExpr && !(((RefExpr) stmt.val).ref instanceof ThisRef) && ((RefExpr) stmt.val).ref.declaration instanceof ClassDecl)
                || (stmt.val instanceof IxExpr && !(((IxExpr) stmt.val).ref instanceof ThisRef) && ((IxExpr) stmt.val).ref.declaration instanceof ClassDecl)) {
            this._errors.reportError("Identification Error: Invalid class reference (expected field/var).");
        }

        if (!refType.compareType(stmtType)) {
            this._errors.reportError("Type Checking Error: Type mismatch between variable assignment of type " + refType.typeKind + " and expression type " + stmtType.typeKind);
        }
        return null;
    }

    public Object visitIxAssignStmt(IxAssignStmt stmt, Object arg){
        stmt.ref.visit(this, arg);

        if (stmt.ref.declaration.type instanceof ArrayType) {
            TypeDenoter ixExprType = (TypeDenoter) stmt.ix.visit(this, arg);

            if (ixExprType.typeKind != TypeKind.INT) {
                this._errors.reportError("Type Checking Error: Index is not of type INT.");
            }

            TypeDenoter eltType = ((ArrayType) stmt.ref.declaration.type).eltType;
            TypeDenoter stmtType = (TypeDenoter) stmt.exp.visit(this, arg);

            if (stmtType.typeKind == TypeKind.NULL) {
                return null;
            }

            if ((stmt.exp instanceof CallExpr && !(((CallExpr) stmt.exp).functionRef instanceof ThisRef) && ((CallExpr) stmt.exp).functionRef.declaration instanceof ClassDecl)
                    || (stmt.exp instanceof RefExpr && !(((RefExpr) stmt.exp).ref instanceof ThisRef) && ((RefExpr) stmt.exp).ref.declaration instanceof ClassDecl)
                    || (stmt.exp instanceof IxExpr && !(((IxExpr) stmt.exp).ref instanceof ThisRef) && ((IxExpr) stmt.exp).ref.declaration instanceof ClassDecl)) {
                this._errors.reportError("Identification Error: Invalid class reference (expected field/var).");
            }

            if (!eltType.compareType(stmtType)) {
                this._errors.reportError("Type Checking Error: Type mismatch between array element type " + eltType.typeKind + " and expression type " + stmtType.typeKind);
            }
        } else {
            this._errors.reportError("Type Checking Error: Invalid attempt to index a non-array type identifier.");
        }
        return null;
    }

    public Object visitCallStmt(CallStmt stmt, Object arg){
        stmt.methodRef.visit(this, arg);

        if (stmt.methodRef.declaration instanceof MethodDecl) {
            MethodDecl md = (MethodDecl) stmt.methodRef.declaration;

            ExprList al = stmt.argList;

            if (al.size() != md.parameterDeclList.size()) {
                this._errors.reportError("Type Checking Error: Parameter list length mismatch.");
            }

            for (int i=0; i < md.parameterDeclList.size(); i++) {
                TypeDenoter exprType = (TypeDenoter) al.get(i).visit(this, arg);
                if (!exprType.compareType(md.parameterDeclList.get(i).type)) {
                    this._errors.reportError("Type Checking Error: Parameter type mismatch for parameter at index " + i);
                }
            }
        } else {
            this._errors.reportError("Type Checking Error: Attempt to call a non-method declaration.");
        }

        return null;
    }

    public Object visitReturnStmt(ReturnStmt stmt, Object arg){
        if (stmt.returnExpr != null) {
            TypeDenoter returnType = (TypeDenoter) stmt.returnExpr.visit(this, arg);
            MethodDecl md = (MethodDecl) arg;
            if (!md.type.compareType(returnType)) {
                this._errors.reportError("Type Checking Error: Return type " + returnType.typeKind + " does not match method's expected return type " + md.type.typeKind);
            }
        }

        return null;
    }

    public Object visitIfStmt(IfStmt stmt, Object arg){
        TypeDenoter type = (TypeDenoter) stmt.cond.visit(this, arg);

        if (type.typeKind != TypeKind.BOOLEAN) {
            this._errors.reportError("Type Checking Error: Invalid type " + type.typeKind + " for if loop condition (expected BOOLEAN).");
        }

        if (stmt.thenStmt instanceof VarDeclStmt) {
            this._errors.reportError("IdentificationError: Solitary variable declaration not allowed in scope to itself.");
        }

        stmt.thenStmt.visit(this, arg);

        if (stmt.elseStmt != null) {
            if (stmt.elseStmt instanceof VarDeclStmt) {
                this._errors.reportError("IdentificationError: Solitary variable declaration not allowed in scope to itself.");
            }
            stmt.elseStmt.visit(this, arg);
        }
        return null;
    }

    public Object visitWhileStmt(WhileStmt stmt, Object arg){
        TypeDenoter type = (TypeDenoter) stmt.cond.visit(this, arg);

        if (type.typeKind != TypeKind.BOOLEAN) {
            this._errors.reportError("Type Checking Error: Invalid type " + type.typeKind + " for while loop condition (expected BOOLEAN).");
        }

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
        TypeDenoter type = (TypeDenoter) expr.expr.visit(this, arg);

        switch (expr.operator.spelling) {
            case "-":
                if (type.typeKind == TypeKind.INT) {
                    return new BaseType(TypeKind.INT, expr.posn);
                } else {
                    this._errors.reportError("Type Checking Error: Invalid type " + type.typeKind + " for unary operator '-' (expected INT).");
                }
                break;
            case "!":
                if (type.typeKind == TypeKind.BOOLEAN) {
                    return new BaseType(TypeKind.BOOLEAN, expr.posn);
                } else {
                    this._errors.reportError("Type Checking Error: Invalid type " + type.typeKind + " for unary operator '!' (expected BOOLEAN).");
                }
                break;

        }
        return new BaseType(TypeKind.ERROR, expr.posn);
    }

    public Object visitBinaryExpr(BinaryExpr expr, Object arg){
        expr.operator.visit(this, arg);

        TypeDenoter lType = (TypeDenoter) expr.left.visit(this, arg);
        TypeDenoter rType = (TypeDenoter) expr.right.visit(this, arg);

        switch (expr.operator.spelling) {
            case "&&":
            case "||":
                if (lType.typeKind == TypeKind.BOOLEAN && lType.compareType(rType)) {
                    return new BaseType(TypeKind.BOOLEAN, expr.posn);
                } else {
                    this._errors.reportError("Type Checking Error: Invalid types for conjunction/disjunction.");
                }
                break;
            case ">":
            case ">=":
            case "<":
            case "<=":
                if (lType.typeKind == TypeKind.INT && lType.compareType(rType)) {
                    return new BaseType(TypeKind.BOOLEAN, expr.posn);
                } else {
                    this._errors.reportError("Type Checking Error: Invalid types for integer comparison (excluding ==/!=).");
                }
                break;
            case "+":
            case "-":
            case "*":
            case "/":
                if (lType.typeKind == TypeKind.INT && lType.compareType(rType)) {
                    return new BaseType(TypeKind.INT, expr.posn);
                } else {
                    this._errors.reportError("Type Checking Error: Invalid types for arithmetic expression.");
                }
                break;
            case "==":
            case "!=":
                if (!lType.compareType(rType)) {
                    this._errors.reportError("Type Checking Error: Type mismatch for equality comparison.");
                    break;
                } else if (lType instanceof ClassType && !((ClassType) lType).className.spelling.equals(((ClassType) rType).className.spelling)) {
                    this._errors.reportError("Type Checking Error: Class type mismatch for equality comparison.");
                    break;
                } else if (lType.typeKind == TypeKind.UNSUPPORTED || lType.typeKind == TypeKind.ERROR) {
                    return new BaseType(TypeKind.ERROR, expr.posn);
                } else if (lType.typeKind == TypeKind.ARRAY && ((ArrayType) lType).eltType.compareType(((ArrayType) rType).eltType)) {
                    return new BaseType(TypeKind.BOOLEAN, expr.posn);
                }
                return new BaseType(TypeKind.BOOLEAN, expr.posn);
            default:
        }

        return new BaseType(TypeKind.ERROR, expr.posn);
    }

    public Object visitRefExpr(RefExpr expr, Object arg){
        TypeDenoter refType = (TypeDenoter) expr.ref.visit(this, arg);
        if (expr.ref.declaration instanceof MethodDecl) {
            this._errors.reportError("Type Checking Error: Invalid attempt to reference a method when field/var expected.");
            return new BaseType(TypeKind.ERROR, expr.posn);
        }
        return refType;
    }

    public Object visitIxExpr(IxExpr ie, Object arg){
        TypeDenoter refType = (TypeDenoter) ie.ref.visit(this, arg);

        if (refType instanceof ArrayType) {
            TypeDenoter ixExprType = (TypeDenoter) ie.ixExpr.visit(this, arg);

            if (ixExprType.typeKind != TypeKind.INT) {
                this._errors.reportError("Type Checking Error: Index is not of type INT.");
                return new BaseType(TypeKind.ERROR, ie.ref.posn);
            }

            return ((ArrayType) ie.ref.declaration.type).eltType;
        } else {
            this._errors.reportError("Type Checking Error: Invalid attempt to index a non-array type identifier.");
        }

        return new BaseType(TypeKind.ERROR, ie.ref.posn);
    }

    public Object visitCallExpr(CallExpr expr, Object arg){
        expr.functionRef.visit(this, arg);

        if (expr.functionRef.declaration instanceof MethodDecl) {
            MethodDecl md = (MethodDecl) expr.functionRef.declaration;

            ExprList al = expr.argList;

            if (al.size() != md.parameterDeclList.size()) {
                this._errors.reportError("Type Checking Error: Parameter list length mismatch.");
                return new BaseType(TypeKind.ERROR, expr.posn);
            }

            for (int i=0; i < md.parameterDeclList.size(); i++) {
                TypeDenoter exprType = (TypeDenoter) al.get(i).visit(this, arg);
                if (!exprType.compareType(md.parameterDeclList.get(i).type)) {
                    this._errors.reportError("Type Checking Error: Parameter type mismatch for parameter at index " + i);
                    return new BaseType(TypeKind.ERROR, expr.posn);
                }
            }

            return expr.functionRef.declaration.type;
        } else {
            this._errors.reportError("Type Checking Error: Attempt to call a non-method declaration.");
        }

        return new BaseType(TypeKind.ERROR, expr.posn);
    }

    public Object visitLiteralExpr(LiteralExpr expr, Object arg){
        return expr.lit.visit(this, arg);
    }

    public Object visitNewArrayExpr(NewArrayExpr expr, Object arg){
        expr.eltType.visit(this, arg);
        expr.sizeExpr.visit(this, arg);
        return new ArrayType(expr.eltType, expr.posn);
    }

    public Object visitNewObjectExpr(NewObjectExpr expr, Object arg){
        return expr.classtype.visit(this, arg);
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

        ref.declaration = md.associatedClass;
        return ref.declaration.type;
    }

    public Object visitIdRef(IdRef ref, Object arg) {
        TypeDenoter type = (TypeDenoter) ref.id.visit(this, arg);
        ref.declaration = ref.id.declaration;
        return type;
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
                decl = ((ThisRef) currRef).declaration;
            } else if (currRef instanceof IdRef) {
                // Visit to ensure id has an associated declaration!
                ((IdRef) currRef).id.visit(this, arg);
                decl = ((IdRef) currRef).id.declaration;
                currRef.declaration = decl;
            } else if (currRef instanceof QualRef) {
                ((QualRef) currRef).id.visit(this, arg);
                decl = ((QualRef) currRef).id.declaration;
                currRef.declaration = decl;
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
                    currQRef.declaration = currQRef.id.declaration;
                } else {
                    this._errors.reportError("IdentificationError: Invalid attempt to reference a non-class type identifier.");
                    return new BaseType(TypeKind.ERROR, currQRef.posn);
                }
            } else if (decl instanceof ClassDecl) {
                // Handling for A.x where A is a class
                ClassDecl cd = (ClassDecl) decl;

                // Find the id in the class
                // Set id declaration to found declaration
                currQRef.id.declaration = si.findDeclarationInClass(currQRef.id, cd, context);
                currQRef.declaration = currQRef.id.declaration;

                if (context instanceof MethodDecl && context.isStatic && !((MemberDecl) currQRef.declaration).isStatic) {
                    _errors.reportError("IdentificationError: Invalid attempt to access a non-static member in a static method.");
                }
            } else if (decl instanceof MemberDecl) {
                MemberDecl md = (MemberDecl) decl;

                if (md.type.typeKind == TypeKind.CLASS) {
                    // get associated class of MemberDecl
                    ClassType ct = (ClassType) md.type;
                    ClassDecl cd = (ClassDecl) si.findDeclaration(ct.className, context);

                    // Find the id in the class
                    // Set id declaration to found declaration
                    currQRef.id.declaration = si.findDeclarationInClass(currQRef.id, cd, context);
                    currQRef.declaration = currQRef.id.declaration;
                } else {
                    this._errors.reportError("IdentificationError: Invalid attempt to reference a non-class type identifier.");
                    return new BaseType(TypeKind.ERROR, currQRef.posn);
                }
            } else {
                this._errors.reportError("IdentificationError: Unable to resolve reference.");
                return new BaseType(TypeKind.ERROR, currRef.posn);
            }
        }

        return qr.id.declaration.type;
    }

    /// uyhhhhhhjlkjfks
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

        if (id.declaration == null) {
            return new BaseType(TypeKind.ERROR, id.posn);
        }

        return id.declaration.type;
    }

    public Object visitOperator(Operator op, Object arg){
        return null;
    }

    public Object visitIntLiteral(IntLiteral num, Object arg){
        return new BaseType(TypeKind.INT, num.posn);
    }

    public Object visitBooleanLiteral(BooleanLiteral bool, Object arg){
        return new BaseType(TypeKind.BOOLEAN, bool.posn);
    }

    public Object visitNullLiteral(NullLiteral nl, Object arg) {
        return new BaseType(TypeKind.NULL, nl.posn);
    }
}