package miniJava.ContextualAnalysis;

import miniJava.ErrorReporter;
import miniJava.AbstractSyntaxTrees.Package;
import miniJava.AbstractSyntaxTrees.*;
import miniJava.SyntacticAnalyzer.SourcePosition;

import javax.xml.transform.Source;
import java.util.Stack;

public class ContextualAnalysis implements Visitor<Object,Object> {
    private ErrorReporter _errors;
    private ScopedIdentification si;

    public ContextualAnalysis(ErrorReporter errors) {
        this._errors = errors;
        this.si = new ScopedIdentification(_errors);
    }

    public void parse( Package prog ) {
        visitPackage(prog,null);
    }

    public void reportIdentificationError(SourcePosition posn, String e) {
        this._errors.reportError(posn, "(IdentificationError) " + e + ".");
    }

    public void reportTypeCheckingError(SourcePosition posn, String e) {
        this._errors.reportError(posn, "(TypeCheckingError) " + e + ".");
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

        // Add Predefined Member Declarations (from Predefined Classes)
        si.addPredefinedMemberDeclarations();

        // Fields/Methods for Classes
        for (ClassDecl c: cl) {
            // Add Fields for Class to Level 1 Scope
            for (FieldDecl f: c.fieldDeclList) {
                f.associatedClass = c;
                si.addDeclaration(f.name, f);
            }

            // Add Methods for Class to Level 1 Scope
            for (MethodDecl m: c.methodDeclList) {
                m.associatedClass = c;
                si.addDeclaration(m.name, m);
            }
        }

        // Visit the Classes
        for (ClassDecl c: cl) {
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

        // Visit each statement in the BlockStmt
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

        // If assigning variable to NULL, declaration is valid.
        if (stmtType.typeKind == TypeKind.NULL) {
            stmt.varDecl.isInitialized = true;
            return null;
        }

        // If expression results in a class reference and is NOT a ThisRef, IdentificationError.
        if ((stmt.initExp instanceof CallExpr && !(((CallExpr) stmt.initExp).functionRef instanceof ThisRef) && ((CallExpr) stmt.initExp).functionRef.declaration instanceof ClassDecl)
                || (stmt.initExp instanceof RefExpr && !(((RefExpr) stmt.initExp).ref instanceof ThisRef) && ((RefExpr) stmt.initExp).ref.declaration instanceof ClassDecl)
                || (stmt.initExp instanceof IxExpr && !(((IxExpr) stmt.initExp).ref instanceof ThisRef) && ((IxExpr) stmt.initExp).ref.declaration instanceof ClassDecl)) {
            this.reportIdentificationError(stmt.initExp.posn,
                    "Expected field/var but provided class reference");
        }

        if (!varType.compareType(stmtType)) {
            // If TypeKinds do not match, TypeCheckingError.
            this.reportTypeCheckingError(stmt.initExp.posn,
                    "Type mismatch between variable declaration of type " + varType.typeKind
                            + " and expression type " + stmtType.typeKind);
        } else if (varType instanceof ClassType && !((ClassType) varType).className.spelling.equals(((ClassType) stmtType).className.spelling)) {
            // If TypeKind Class but Class names don't match, TypeCheckingError.
            this.reportTypeCheckingError(stmt.initExp.posn,
                    "Type mismatch between variable declaration of class " + ((ClassType) varType).className.spelling
                            + " and reference of class " + ((ClassType) stmtType).className.spelling);
        }

        // After varDecl and associated Expression visited, varDecl has been initialized.
        stmt.varDecl.isInitialized = true;
        return null;
    }

    public Object visitAssignStmt(AssignStmt stmt, Object arg){
        TypeDenoter refType = (TypeDenoter) stmt.ref.visit(this, arg);
        TypeDenoter stmtType = (TypeDenoter) stmt.val.visit(this, arg);

        // If assigning variable to NULL, declaration is valid.
        if (stmtType.typeKind == TypeKind.NULL) {
            return null;
        }

        // If expression results in a class reference and is NOT a ThisRef, IdentificationError.
        if ((stmt.val instanceof CallExpr && !(((CallExpr) stmt.val).functionRef instanceof ThisRef) && ((CallExpr) stmt.val).functionRef.declaration instanceof ClassDecl)
                || (stmt.val instanceof RefExpr && !(((RefExpr) stmt.val).ref instanceof ThisRef) && ((RefExpr) stmt.val).ref.declaration instanceof ClassDecl)
                || (stmt.val instanceof IxExpr && !(((IxExpr) stmt.val).ref instanceof ThisRef) && ((IxExpr) stmt.val).ref.declaration instanceof ClassDecl)) {
            this.reportIdentificationError(stmt.val.posn,
                    "Expected field/var but received class reference");
        }

        if (!refType.compareType(stmtType)) {
            // If TypeKinds do not match, TypeCheckingError.
            this.reportTypeCheckingError(stmt.val.posn,
                    "Type mismatch between variable assignment of type " + refType.typeKind
                            + " and expression type " + stmtType.typeKind);
        } else if (refType instanceof ClassType && !((ClassType) refType).className.spelling.equals(((ClassType) stmtType).className.spelling)) {
                // If TypeKind Class but Class names don't match, TypeCheckingError.
                this.reportTypeCheckingError(stmt.val.posn,
                        "Type mismatch between variable declaration of class " + ((ClassType) refType).className.spelling
                                + " and reference of class " + ((ClassType) stmtType).className.spelling);
            }
        return null;
    }

    public Object visitIxAssignStmt(IxAssignStmt stmt, Object arg){
        stmt.ref.visit(this, arg);

        if (stmt.ref.declaration.type instanceof ArrayType) {
            // If indexing an ArrayType reference
            // Get type of the index expression
            TypeDenoter ixExprType = (TypeDenoter) stmt.ix.visit(this, arg);

            if (ixExprType.typeKind != TypeKind.INT) {
                // If type of the index expression is not INT
                this.reportTypeCheckingError(stmt.ix.posn,
                        "Array index expects type INT but provided type " + ixExprType.typeKind);
            }

            TypeDenoter eltType = ((ArrayType) stmt.ref.declaration.type).eltType;
            TypeDenoter stmtType = (TypeDenoter) stmt.exp.visit(this, arg);

            // If assigning variable to NULL, declaration is valid.
            if (stmtType.typeKind == TypeKind.NULL) {
                return null;
            }

            // If expression results in a class reference and is NOT a ThisRef, IdentificationError.
            if ((stmt.exp instanceof CallExpr && !(((CallExpr) stmt.exp).functionRef instanceof ThisRef) && ((CallExpr) stmt.exp).functionRef.declaration instanceof ClassDecl)
                    || (stmt.exp instanceof RefExpr && !(((RefExpr) stmt.exp).ref instanceof ThisRef) && ((RefExpr) stmt.exp).ref.declaration instanceof ClassDecl)
                    || (stmt.exp instanceof IxExpr && !(((IxExpr) stmt.exp).ref instanceof ThisRef) && ((IxExpr) stmt.exp).ref.declaration instanceof ClassDecl)) {
                this.reportIdentificationError(stmt.exp.posn,
                        "Expected field/var but received class reference");
            }

            // If TypeKinds of elements do not match, TypeCheckingError.
            if (!eltType.compareType(stmtType)) {
                this.reportTypeCheckingError(stmt.exp.posn,
                        "Type mismatch between array element type " + eltType.typeKind
                                + " and expression type " + stmtType.typeKind);
            }
        } else {
            // Error if attempting to index an array
            this.reportTypeCheckingError(stmt.posn,
                    "Cannot index non-array type identifier " + stmt.ref.declaration.type.typeKind);
        }
        return null;
    }

    public Object visitCallStmt(CallStmt stmt, Object arg){
        stmt.methodRef.visit(this, arg);

        // Ensure reference is a MethodDecl
        if (stmt.methodRef.declaration instanceof MethodDecl) {
            MethodDecl md = (MethodDecl) stmt.methodRef.declaration;

            ExprList al = stmt.argList;

            // If size of given argument list not the same as size of expected method parameter list, TypeCheckingError.
            if (al.size() != md.parameterDeclList.size()) {
                this.reportTypeCheckingError(stmt.methodRef.posn,
                        "Method call expects " + md.parameterDeclList.size() + " parameters but provided " + al.size() + " parameters.");
            }

            // Iterate through each parameter in both lists
            for (int i=0; i < md.parameterDeclList.size(); i++) {
                // Retrieve the type of the element in the argument list at index i
                TypeDenoter exprType = (TypeDenoter) al.get(i).visit(this, arg);

                // If TypeKind of arglist element at i and paramlist element at i are different, TypeCheckingError.
                if (!exprType.compareType(md.parameterDeclList.get(i).type)) {
                    this.reportTypeCheckingError(al.get(i).posn,
                            "Expected parameter of type " + md.parameterDeclList.get(i).type.typeKind
                                    + " for method parameter " + i
                                    + " but provided parameter of type " + exprType.typeKind);
                }
            }
        } else {
            // Error if attempting to call a reference that isn't a method.
            this.reportIdentificationError(stmt.methodRef.posn,
                    "Cannot call non-method declaration " + stmt.methodRef.declaration.type.typeKind);
        }

        return null;
    }

    public Object visitReturnStmt(ReturnStmt stmt, Object arg){
        // If return statement has a result
        if (stmt.returnExpr != null) {
            // Retrieve return type
            TypeDenoter returnType = (TypeDenoter) stmt.returnExpr.visit(this, arg);

            // If given return type is not the same as the expected method return type, TypeCheckingError.
            MethodDecl md = (MethodDecl) arg;
            if (!md.type.compareType(returnType)) {
                this.reportTypeCheckingError(stmt.returnExpr.posn,
                        "Method expects return type " + md.type.typeKind
                                + " but provided return type " + returnType.typeKind);
            }
        }

        return null;
    }

    public Object visitIfStmt(IfStmt stmt, Object arg){
        // Retrieve type of IfStmt condition
        TypeDenoter condType = (TypeDenoter) stmt.cond.visit(this, arg);

        // If condition not BOOLEAN, TypeCheckingError.
        if (condType.typeKind != TypeKind.BOOLEAN) {
            this.reportTypeCheckingError(stmt.cond.posn,
                    "If statement expects condition of type BOOLEAN but provided type " + condType.typeKind);
        }

        // If thenStmt is just a VarDeclStmt, IdentificationError.
        if (stmt.thenStmt instanceof VarDeclStmt) {
            this.reportIdentificationError(((VarDeclStmt) stmt.thenStmt).varDecl.posn,
                    "Solitary variable declaration not allowed in scope to itself" );
        }

        stmt.thenStmt.visit(this, arg);

        // If elseStmt exists
        if (stmt.elseStmt != null) {
            // If body of elseStmt is just a VarDeclStmt, IdentificationError.
            if (stmt.elseStmt instanceof VarDeclStmt) {
                this.reportIdentificationError(((VarDeclStmt) stmt.elseStmt).varDecl.posn,
                        "Solitary variable declaration not allowed in scope to itself" );
            }
            stmt.elseStmt.visit(this, arg);
        }
        return null;
    }

    public Object visitWhileStmt(WhileStmt stmt, Object arg){
        // Retrieve type of WhileStmt condition
        TypeDenoter condType = (TypeDenoter) stmt.cond.visit(this, arg);

        // If condition not BOOLEAN, TypeCheckingError.
        if (condType.typeKind != TypeKind.BOOLEAN) {
            this.reportTypeCheckingError(stmt.cond.posn,
                    "While loop expects condition of type BOOLEAN but provided type " + condType.typeKind);
        }

        // If WhileStmt body is just a VarDeclStmt, IdentificationError.
        if (stmt.body instanceof VarDeclStmt) {
            this.reportIdentificationError(stmt.body.posn,
                    "Solitary variable declaration not allowed in scope to itself" );
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
            case "-":   // If operator "-"
                // If type is INT, valid, else TypeCheckingError
                if (type.typeKind == TypeKind.INT) {
                    return type;
                } else {
                    this.reportTypeCheckingError(expr.posn,
                            "Unary operator '-' expects type INT but provided type " + type.typeKind);
                }
                break;
            case "!":   // If operator "!"
                // If type is BOOLEAN, valid, else TypeCheckingError
                if (type.typeKind == TypeKind.BOOLEAN) {
                    return type;
                } else {
                    this.reportTypeCheckingError(expr.posn,
                            "Unary operator '!' expects type BOOLEAN but provided type " + type.typeKind);
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
            case "||":  // If operator "&&" or "||"
                // If types are the same and BOOLEAN, valid.
                if (lType.typeKind == TypeKind.BOOLEAN && lType.compareType(rType)) {
                    return new BaseType(TypeKind.BOOLEAN, expr.posn);
                }
                break;
            case ">":
            case ">=":
            case "<":
            case "<=":  // If operator ">", ">=", "<", or "<="
                // If types are same and INT, valid.
                if (lType.typeKind == TypeKind.INT && lType.compareType(rType)) {
                    return new BaseType(TypeKind.BOOLEAN, expr.posn);
                }
                break;
            case "+":
            case "-":
            case "*":
            case "/":   // If operator "+", "-", "*", or "/"
                // If types are same and INT, valid.
                if (lType.typeKind == TypeKind.INT && lType.compareType(rType)) {
                    return new BaseType(TypeKind.INT, expr.posn);
                }
                break;
            case "==":
            case "!=":  // If operator "==" or "!="
                if (!lType.compareType(rType)) {
                    // If TypeKinds of elements do not match, TypeCheckingError (reported at end of switch/case).
                    break;
                } else if (lType instanceof ClassType && !((ClassType) lType).className.spelling.equals(((ClassType) rType).className.spelling)) {
                    // If TypeKind Class but Class names don't match, TypeCheckingError.
                    this.reportTypeCheckingError(expr.posn,
                            "Operator '" + expr.operator.spelling + "' cannot be applied to class types '" +
                                    ((ClassType) lType).className.spelling + "', " + ((ClassType) rType).className.spelling);
                    return new BaseType(TypeKind.ERROR, expr.posn);
                } else if (lType.typeKind == TypeKind.UNSUPPORTED || lType.typeKind == TypeKind.ERROR) {
                    // If TypeKind UNSUPPORTED/ERROR, return an ERROR type without reporting an ERROR.
                    return new BaseType(TypeKind.ERROR, expr.posn);
                } else if (lType instanceof ArrayType && !((ArrayType) lType).eltType.compareType(((ArrayType) rType).eltType)) {
                    // If TypeKinds of elements do not match, TypeCheckingError (reported at end of switch/case).
                    break;
                }
                return new BaseType(TypeKind.BOOLEAN, expr.posn);
            default:
        }

        // Report TypeCheckingError and return Error Type.
        this.reportTypeCheckingError(expr.posn,
                "Operator '" + expr.operator.spelling + "' cannot be applied to types '"
                        + lType.typeKind + "', '" + rType.typeKind + "'");
        return new BaseType(TypeKind.ERROR, expr.posn);
    }

    public Object visitRefExpr(RefExpr expr, Object arg){
        TypeDenoter refType = (TypeDenoter) expr.ref.visit(this, arg);

        // If RefExpr returns a MethodDecl, invalid (only CallExpr should return MethodDecl)
        if (expr.ref.declaration instanceof MethodDecl) {
            this.reportTypeCheckingError(expr.ref.posn,
                    "Expected field/var but provided method reference");
            return new BaseType(TypeKind.ERROR, expr.posn);
        }
        return refType;
    }

    public Object visitIxExpr(IxExpr ie, Object arg){
        TypeDenoter refType = (TypeDenoter) ie.ref.visit(this, arg);

        if (refType instanceof ArrayType) {
            // If indexing an ArrayType reference
            // Get type of the index expression
            TypeDenoter ixExprType = (TypeDenoter) ie.ixExpr.visit(this, arg);

            if (ixExprType.typeKind != TypeKind.INT) {
                // If type of the index expression is not INT
                this.reportTypeCheckingError(ie.ixExpr.posn,
                        "Array index expects type INT but provided type " + ixExprType.typeKind);
                return new BaseType(TypeKind.ERROR, ie.ixExpr.posn);
            }

            // Return type of element
            return ((ArrayType) ie.ref.declaration.type).eltType;
        } else {
            // Error if attempting to index non-array reference.
            this.reportTypeCheckingError(ie.posn,
                    "Cannot index non-array type identifier " + ie.ref.declaration.type.typeKind);
        }

        return new BaseType(TypeKind.ERROR, ie.ref.posn);
    }

    public Object visitCallExpr(CallExpr expr, Object arg){
        expr.functionRef.visit(this, arg);

        // Ensure reference is a MethodDecl
        if (expr.functionRef.declaration instanceof MethodDecl) {
            MethodDecl md = (MethodDecl) expr.functionRef.declaration;

            ExprList al = expr.argList;

            // If size of given argument list not the same as size of expected method parameter list, TypeCheckingError.
            if (al.size() != md.parameterDeclList.size()) {
                this.reportTypeCheckingError(expr.functionRef.posn,
                        "Method call expects " + md.parameterDeclList.size() + " parameters but provided " + al.size() + " parameters.");
                return new BaseType(TypeKind.ERROR, expr.posn);
            }

            // Iterate through each parameter in both lists
            for (int i=0; i < md.parameterDeclList.size(); i++) {
                // Retrieve the type of the element in the argument list at index i
                TypeDenoter exprType = (TypeDenoter) al.get(i).visit(this, arg);
                if (!exprType.compareType(md.parameterDeclList.get(i).type)) {
                    // If TypeKind of arglist element at i and paramlist element at i are different, TypeCheckingError.
                    this.reportTypeCheckingError(al.get(i).posn,
                            "Expected parameter of type " + md.parameterDeclList.get(i).type.typeKind
                                    + " for method parameter " + i
                                    + " but provided parameter of type " + exprType.typeKind);
                    return new BaseType(TypeKind.ERROR, al.get(i).posn);
                }
            }

            return expr.functionRef.declaration.type;
        } else {
            // Error if attempting to call a reference that isn't a method.
            this.reportIdentificationError(expr.functionRef.posn,
                    "Cannot call non-method declaration " + expr.functionRef.declaration.type.typeKind);
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

        // If ThisRef in a static method, IdentificationError.
        if (md.isStatic) {
            this.reportIdentificationError(ref.posn, "Cannot reference `this` in static method " + md.name);
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
        MemberDecl inClassContext = (MemberDecl) arg;

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
                currRef.visit(this, context);
                decl = ((ThisRef) currRef).declaration;
            } else if (currRef instanceof IdRef) {
                // Visit to ensure id has an associated declaration!
                ((IdRef) currRef).id.visit(this, context);
                decl = ((IdRef) currRef).id.declaration;
                currRef.declaration = decl;
            } else if (currRef instanceof QualRef) {
                // Visit to ensure id has an associated declaration!
                ((QualRef) currRef).id.visit(this, context);
                decl = ((QualRef) currRef).id.declaration;
                currRef.declaration = decl;
            }

            // get next ref from stack
            currRef = refStack.pop();

            QualRef currQRef = (QualRef) currRef;
            if (decl instanceof LocalDecl) {
                context = null;

                // Handling for case A a = new A(); \ a.b = ... (using a)
                LocalDecl ld = (LocalDecl) decl;

                if (ld.type.typeKind == TypeKind.CLASS) {
                    // get associated class of LocalDecl
                    ClassType ct = (ClassType) ld.type;
                    ClassDecl cd = (ClassDecl) si.findDeclaration(ct.className, context);

                    // Find the id in the class
                    // Set id declaration to found declaration
                    currQRef.id.declaration = si.findDeclarationInClass(currQRef.id, cd, inClassContext);
                    currQRef.declaration = currQRef.id.declaration;
                } else {
                    // Should only be able to reference objects of type class
                    this.reportIdentificationError(currQRef.posn,
                            "Cannot reference non-class type " + currQRef.id.spelling);
                    return new BaseType(TypeKind.ERROR, currQRef.posn);
                }
            } else if (decl instanceof ClassDecl) {
                // Handling for A.x where A is a class
                ClassDecl cd = (ClassDecl) decl;

                // Find the id in the class
                // Set id declaration to found declaration
                currQRef.id.declaration = si.findDeclarationInClass(currQRef.id, cd, inClassContext);
                currQRef.declaration = currQRef.id.declaration;

                // Handles trying to access class with a static ref in itself
                if (context instanceof MethodDecl && context.isStatic && !((MemberDecl) currQRef.declaration).isStatic) {
                    this.reportIdentificationError(currQRef.posn,
                            "Cannot access non-static member " + currQRef.id.spelling + " in static method " + context.name);
                }
            } else if (decl instanceof FieldDecl) {
                FieldDecl md = (FieldDecl) decl;

                if (md.type.typeKind == TypeKind.CLASS) {
                    // get associated class of MemberDecl
                    ClassType ct = (ClassType) md.type;
                    ClassDecl cd = (ClassDecl) si.findDeclaration(ct.className, context);

                    // Find the id in the class
                    // Set id declaration to found declaration
                    currQRef.id.declaration = si.findDeclarationInClass(currQRef.id, cd, inClassContext);
                    currQRef.declaration = currQRef.id.declaration;
                } else {
                    // Should only be able to reference objects of type class
                    this.reportIdentificationError(currQRef.posn,
                            "Cannot reference non-class type " + currQRef.id.spelling);
                    return new BaseType(TypeKind.ERROR, currQRef.posn);
                }
            } else {
                // General Error if found Declaration not valid/null
                this.reportIdentificationError(currQRef.posn,
                        "Cannot resolve symbol '" + currQRef.id.spelling + "' in qualified reference");
                return new BaseType(TypeKind.ERROR, currQRef.posn);
            }
        }

        // Return declaration for QualRef
        return qr.id.declaration.type;
    }

    ///////////////////////////////////////////////////////////////////////////////
    //
    // TERMINALS
    //
    ///////////////////////////////////////////////////////////////////////////////

    public Object visitIdentifier(Identifier id, Object arg){
        Declaration context = (Declaration) arg;

        // Find declaration corresponding to Id
        id.declaration = si.findDeclaration(id, context);

        // If id is null, not found, return ErrorType.
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