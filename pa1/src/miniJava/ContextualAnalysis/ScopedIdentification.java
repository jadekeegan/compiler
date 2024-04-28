package miniJava.ContextualAnalysis;

import miniJava.AbstractSyntaxTrees.*;
import miniJava.ErrorReporter;
import miniJava.SyntacticAnalyzer.SourcePosition;
import miniJava.SyntacticAnalyzer.Token;
import miniJava.SyntacticAnalyzer.TokenType;

import java.util.ArrayList;
import java.util.Stack;

public class ScopedIdentification {
    public Stack<IDTable> si = new Stack<>();
    private ErrorReporter _errors;

    ScopedIdentification(ErrorReporter _errors) {
        this._errors = _errors;
    }

    public void reportIdentificationError(SourcePosition posn, String e) {
        this._errors.reportError(posn, "(IdentificationError) " + e + ".");
    }

    public void openScope() {
        si.push(new IDTable());
    }

    public void closeScope() {
        si.pop();
    }

    public void addDeclaration(String id, Declaration decl) {
        // If at scope higher than level 2
        if (si.size() > 3) {
            Stack<IDTable> siCopy = new Stack<>();
            siCopy.addAll(si);

            // For all scopes higher than level 1 (2 tables on stack)
            while (siCopy.size() > 2) {
                IDTable curr = siCopy.pop();
                if (curr.containsKey(id)) {
                    this.reportIdentificationError(decl.posn,
                            "Variable '" + id + "' is already defined in scope");
                    break;
                }
            }

            si.peek().put(id, decl);
        } else {
            IDTable top = si.peek();

            // Ensure class exists when declaring new Var/Param
            if (decl instanceof LocalDecl) {
                LocalDecl ld = (LocalDecl) decl;

                if (decl.type.typeKind == TypeKind.CLASS) {
                    ClassType ct = (ClassType) ld.type;

                    if (!si.get(0).containsKey(ct.className.spelling)) {
                        this.reportIdentificationError(decl.posn,
                                "Cannot resolve symbol '" + ct.className.spelling + "' for identifier declaration");
                    }
                }
            }

            // if top IDTable contains ID already, throw IDError
            if (top.containsKey(id)) {
                ArrayList<Declaration> potentialDecls = top.get(id);
                if (potentialDecls.get(0) instanceof MethodDecl && decl instanceof MethodDecl) {
                    for (Declaration potentialDecl : potentialDecls) {
                        boolean matching = checkMethodMatch((MethodDecl) potentialDecl, (MethodDecl) decl);

                        if (matching) {
                            this.reportIdentificationError(decl.posn, "Method signature exists already");
                        } else {
                            top.put(id, decl);
                            return;
                        }
                    }
                } else if (potentialDecls.get(0)instanceof MemberDecl && decl instanceof MemberDecl) {
                    for (Declaration potentialDecl : potentialDecls) {
                        String existingIdClass = ((MemberDecl) potentialDecl).associatedClass.name;
                        String newIdClass = ((MemberDecl) decl).associatedClass.name;

                        if (!existingIdClass.equals(newIdClass)) {
                            top.put(id, decl);
                            return;
                        }
                    }
                }

                this.reportIdentificationError(decl.posn,
                        "Variable '" + id + "' is already defined in scope");
            } else {
                top.put(id, decl);
            }
        }

    }

    public void removeDeclaration(String id) {
        IDTable top = si.peek();
        top.remove(id);
    }

    public MethodDecl findMethodDeclaration(Identifier id, ArrayList<TypeDenoter> argListTypes, ClassDecl classContext) {
        Stack<IDTable> siCopy = new Stack<>();
        siCopy.addAll(si);

        MethodDecl result = null;
        while (!siCopy.isEmpty()) {
            IDTable curr = siCopy.pop();

            if (curr.containsKey(id.spelling)) {
                ArrayList<Declaration> potentialDecls = curr.get(id.spelling);

                if (curr.get(id.spelling).get(0) instanceof MethodDecl) {
                    for (Declaration potentialDecl : potentialDecls) {
                        boolean currentlyMatching = false;
                        if (argListTypes.size() == ((MethodDecl) potentialDecl).parameterDeclList.size()) {
                            currentlyMatching = true;

                            // Iterate through each parameter in both lists
                            for (int i = 0; i < ((MethodDecl) potentialDecl).parameterDeclList.size(); i++) {
                                // Retrieve the type of the element in the argument list at index i
                                TypeDenoter exprType = argListTypes.get(i);

                                // If TypeKind of arglist element at i and paramlist element at i are different, TypeCheckingError.
                                if (!exprType.compareType(((MethodDecl) potentialDecl).parameterDeclList.get(i).type)) {
                                    currentlyMatching = false;
                                }
                            }
                        }

                        if (currentlyMatching) {
                            result = (MethodDecl) potentialDecl;
                            break;
                        }
                    }
                }
            }
        }

        if (result == null) {
            this.reportIdentificationError(id.posn,
                    "(IdentificationError) Cannot resolve method symbol '" + id.spelling + "' with the provided argument list");
        }

        return result;
    }

    public MemberDecl findDeclarationInClass(Identifier id, ClassDecl cd, Declaration context, ArrayList<TypeDenoter> argListTypes) {
        MemberDecl result = null;
        for (FieldDecl f: cd.fieldDeclList) {
            if (f.name.equals(id.spelling)) {
                result = f;
                break;
            }
        }

        if (result == null) {
            for (MethodDecl m : cd.methodDeclList) {
                if (m.name.equals(id.spelling)) {
                    boolean currentlyMatching = false;
                    if (argListTypes.size() == m.parameterDeclList.size()) {
                        currentlyMatching = true;

                        // Iterate through each parameter in both lists
                        for (int i = 0; i < m.parameterDeclList.size(); i++) {
                            // Retrieve the type of the element in the argument list at index i
                            TypeDenoter exprType = argListTypes.get(i);

                            // If TypeKind of arglist element at i and paramlist element at i are different, TypeCheckingError.
                            if (!exprType.compareType(m.parameterDeclList.get(i).type)) {
                                currentlyMatching = false;
                            }
                        }
                    }

                    if (currentlyMatching) {
                        result = m;
                        break;
                    }
                }
            }
        }

        // Need to get position for these ! (or do error checking outside of this method)
        if (result == null) {
            this.reportIdentificationError(id.posn,
                    "(IdentificationError) Cannot resolve symbol '" + id.spelling + "' in class " + cd.name + ".");
        } else if (result.isPrivate) {
            if (((MethodDecl) context).associatedClass.name.equals(result.associatedClass.name)) {
                return result;
            }
            this.reportIdentificationError(id.posn,
                    "(IdentificationError) Invalid attempt to reference private identifier '" + id.spelling + "' in class " + cd.name);
        }

        return result;
    }

    public Declaration findDeclaration(Identifier id, Declaration context) {
        Stack<IDTable> siCopy = new Stack<>();
        siCopy.addAll(si);

        Declaration result = null;
        while (!siCopy.isEmpty()) {
            IDTable curr = siCopy.pop();
            if (curr.containsKey(id.spelling)) {
                result = curr.get(id.spelling).get(0);
                break;
            }
        }

        if (result == null) {
            this.reportIdentificationError(id.posn,
                    "(IdentificationError) Cannot resolve symbol '" + id.spelling + "'.");
        } else if (result instanceof MemberDecl && ((MemberDecl) result).isPrivate) {
            this.reportIdentificationError(id.posn,
                    "(IdentificationError) Cannot reference private identifier '" + result.name + "'.");
        } else if (result instanceof VarDecl && !((VarDecl) result).isInitialized) {
            this.reportIdentificationError(id.posn,
                    "Cannot reference uninitialized variable " + result.name);
        } else if (result instanceof MemberDecl && context instanceof MethodDecl && ((MethodDecl) context).isStatic && !((MemberDecl) result).isStatic) {
            MethodDecl md = (MethodDecl) context;
            if (result.type.typeKind == TypeKind.CLASS && md.associatedClass.name.equals(((MemberDecl) result).associatedClass.name)) {
                return result;
            }

            this.reportIdentificationError(id.posn,
                    "Cannot access non-static member " + result.name + " in static method " + context.name);
        }

        return result;
    }

    public boolean checkMethodMatch(MethodDecl m1, MethodDecl m2) {
        // If size of given argument list not the same as size of expected method parameter list, TypeCheckingError.
        ParameterDeclList existingPDL = m1.parameterDeclList;
        ParameterDeclList newPDL = m2.parameterDeclList;

        boolean currentlyMatching = false;
        if (existingPDL.size() == newPDL.size()) {
            currentlyMatching = true;

            // Iterate through each parameter in both lists
            for (int i=0; i < newPDL.size(); i++) {
                // Retrieve the type of the element in the argument list at index i
                TypeDenoter exprType = existingPDL.get(i).type;

                // If TypeKind of arglist element at i and paramlist element at i are different, TypeCheckingError.
                if (!exprType.compareType(newPDL.get(i).type)) {
                    currentlyMatching = false;
                }
            }
        }

        return currentlyMatching;
    }

    public void addPredefinedClassDeclarations() {
        // Add String Class
        ClassDecl String = new ClassDecl("String", new FieldDeclList(), new MethodDeclList(), null);
        String.type = new BaseType(TypeKind.UNSUPPORTED, null);
        addDeclaration(String.name, String);

        // Add _PrintStream Class
        MethodDeclList printMethods = new MethodDeclList();
        FieldDecl printMethodMember = new FieldDecl(false, false, new BaseType(TypeKind.VOID, null), "println", null);
        ParameterDeclList printParams = new ParameterDeclList();
        printParams.add(new ParameterDecl(new BaseType(TypeKind.INT, null), "n", null));
        MethodDecl printMethod = new MethodDecl(printMethodMember, printParams, new StatementList(), null);
        printMethods.add(printMethod);
        ClassDecl _PrintStream = new ClassDecl("_PrintStream", new FieldDeclList(), printMethods, null);
        addDeclaration(_PrintStream.name, _PrintStream);

        // Add System Class
        FieldDeclList systemFields = new FieldDeclList();
        TypeDenoter outFieldType = new ClassType(new Identifier(new Token(TokenType.Identifier, "_PrintStream", null)), null);
        FieldDecl outField = new FieldDecl(false, true, outFieldType, "out", null);
        systemFields.add(outField);
        ClassDecl System = new ClassDecl("System", systemFields, new MethodDeclList(), null);
        addDeclaration(System.name, System);
    }

    public void addPredefinedMemberDeclarations() {
        IDTable level0 = si.get(0);

        ClassDecl _PrintStream = (ClassDecl) level0.get("_PrintStream").get(0);
        addDeclaration(_PrintStream.methodDeclList.get(0).name, _PrintStream.methodDeclList.get(0));

        ClassDecl System = (ClassDecl) level0.get("System").get(0);
        addDeclaration(System.fieldDeclList.get(0).name, System.fieldDeclList.get(0));
    }
}
