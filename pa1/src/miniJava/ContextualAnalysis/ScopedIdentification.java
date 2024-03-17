package miniJava.ContextualAnalysis;

import miniJava.AbstractSyntaxTrees.*;
import miniJava.SyntacticAnalyzer.Token;
import miniJava.SyntacticAnalyzer.TokenType;

import java.util.Stack;

public class ScopedIdentification {
    Stack<IDTable> si = new Stack<IDTable>();

    ScopedIdentification() {}

    public void openScope(IDTable idTable) {
        si.push(idTable);
    }

    public void closeScope() {
        si.pop();
    }

    public void addDeclaration(String id, Declaration decl) {
        IDTable top = si.peek();

        // if top IDTable contains ID already, throw IDError
        if (top.containsDecl(id)) {
//            throw new IdentificationError("Error: Identifier already exists at this level.");
        }

        top.put(id, decl);
    }

    public void removeDeclaration(String id) {
        IDTable top = si.peek();
        top.remove(id);
    }

    public Declaration findDeclaration(Identifier id, Declaration decl) {
        Stack<IDTable> siCopy = new Stack<IDTable>();
        siCopy.addAll(si);

        Declaration result = null;
        while (siCopy.peek() != null) {
            IDTable curr = siCopy.pop();
            if (curr.containsDecl(id.spelling)) {
                result = curr.get(id.spelling);
            }
        }
        return result;
    }

    public void addPredefinedDeclarations() {
        // Add String Class
        ClassDecl String = new ClassDecl("String", new FieldDeclList(), new MethodDeclList(), null);
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
        FieldDecl outField = new FieldDecl(false, true, _PrintStream.type, "out", null);
        systemFields.add(outField);
        ClassDecl System = new ClassDecl("System", systemFields, new MethodDeclList(), null);
        addDeclaration(System.name, System);
    }

}
