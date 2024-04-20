package miniJava.CodeGeneration;

import miniJava.ErrorReporter;
import miniJava.AbstractSyntaxTrees.*;
import miniJava.AbstractSyntaxTrees.Package;
import miniJava.CodeGeneration.x64.*;
import miniJava.CodeGeneration.x64.ISA.*;
import miniJava.SyntacticAnalyzer.SourcePosition;

public class CodeGenerator implements Visitor<Object, Object> {
	private ErrorReporter _errors;
	private InstructionList _asm; // our list of instructions that are used to make the code section
	
	public CodeGenerator(ErrorReporter errors) {
		this._errors = errors;
	}
	
	public void parse(Package prog) {
		_asm = new InstructionList();
		
		// If you haven't refactored the name "ModRMSIB" to something like "R",
		//  go ahead and do that now. You'll be needing that object a lot.
		// Here is some example code.
		
		// Simple operations:
		// _asm.add( new Push(0) ); // push the value zero onto the stack
		// _asm.add( new Pop(Reg64.RCX) ); // pop the top of the stack into RCX
		
		// Fancier operations:
		// _asm.add( new Cmp(new ModRMSIB(Reg64.RCX,Reg64.RDI)) ); // cmp rcx,rdi
		// _asm.add( new Cmp(new ModRMSIB(Reg64.RCX,0x10,Reg64.RDI)) ); // cmp [rcx+0x10],rdi
		// _asm.add( new Add(new ModRMSIB(Reg64.RSI,Reg64.RCX,4,0x1000,Reg64.RDX)) ); // add [rsi+rcx*4+0x1000],rdx
		
		// Thus:
		// new ModRMSIB( ... ) where the "..." can be:
		//  RegRM, RegR						== rm, r
		//  RegRM, int, RegR				== [rm+int], r
		//  RegRD, RegRI, intM, intD, RegR	== [rd+ ri*intM + intD], r
		// Where RegRM/RD/RI are just Reg64 or Reg32 or even Reg8
		//
		// Note there are constructors for ModRMSIB where RegR is skipped.
		// This is usually used by instructions that only need one register operand, and often have an immediate
		//   So they actually will set RegR for us when we create the instruction. An example is:
		// _asm.add( new Mov_rmi(new ModRMSIB(Reg64.RDX,true), 3) ); // mov rdx,3
		//   In that last example, we had to pass in a "true" to indicate whether the passed register
		//    is the operand RM or R, in this case, true means RM
		//  Similarly:
		// _asm.add( new Push(new ModRMSIB(Reg64.RBP,16)) );
		//   This one doesn't specify RegR because it is: push [rbp+16] and there is no second operand register needed
		
		// Patching example:
		// Instruction someJump = new Jmp((int)0); // 32-bit offset jump to nowhere
		// _asm.add( someJump ); // populate listIdx and startAddress for the instruction
		// ...
		// ... visit some code that probably uses _asm.add
		// ...
		// patch method 1: calculate the offset yourself
		//     _asm.patch( someJump.listIdx, new Jmp(asm.size() - someJump.startAddress - 5) );
		// -=-=-=-
		// patch method 2: let the jmp calculate the offset
		//  Note the false means that it is a 32-bit immediate for jumping (an int)
		//     _asm.patch( someJump.listIdx, new Jmp(asm.size(), someJump.startAddress, false) );
		
		prog.visit(this,null);
		
		// Output the file "a.out" if no errors
		if( !_errors.hasErrors() )
			makeElf("a.out");
	}
	public void reportCodeGenerationError(SourcePosition posn, String e) {
		this._errors.reportError(posn, "(CodeGenerationError) " + e + ".");
	}
	@Override
	public Object visitPackage(Package prog, Object arg) {
		// TODO: visit relevant parts of our AST
		ClassDeclList cl = prog.classDeclList;

		// Check for Main Method
		boolean hasOneMain = false;
		for (ClassDecl c: cl) {
			// Add Methods for Class to Level 1 Scope
			for (MethodDecl m: c.methodDeclList) {
				boolean isMain = !m.isPrivate		// public
					&& m.isStatic					// static
					&& (m.type.typeKind == TypeKind.VOID)	// void
					&& m.name.equals("main")				// main
					&& (m.parameterDeclList.size() == 1		// paramList size 1
					&& m.parameterDeclList.get(0).type instanceof ArrayType // []
					&& ((ArrayType) m.parameterDeclList.get(0).type).eltType instanceof ClassType // Class
					&& ((ClassType) ((ArrayType) m.parameterDeclList.get(0).type).eltType).className.spelling.equals("String")); // String[]

				if (isMain && hasOneMain) {
					this.reportCodeGenerationError(m.posn,
							"Main function already exists in program");
				} else if (isMain) {
					hasOneMain = true;
				}
			}
		}

		if (!hasOneMain) {
			this.reportCodeGenerationError(prog.posn,
					"No main function exists in program.");
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
			f.visit(this, arg);
		for (MethodDecl m: clas.methodDeclList)
			m.visit(this, arg);
		return null;
	}

	public Object visitFieldDecl(FieldDecl f, Object arg){
		f.type.visit(this, arg);
		return null;
	}

	public Object visitMethodDecl(MethodDecl md, Object arg) {
		md.type.visit(this, arg);

		ParameterDeclList pdl = md.parameterDeclList;
		for (ParameterDecl pd: pdl) {
			pd.visit(this, arg);
		}

		StatementList sl = md.statementList;
		for (Statement s: sl) {
			s.visit(this, arg);
		}

		if (md.type.typeKind != TypeKind.VOID
				&& !(md.statementList.get(md.statementList.size() - 1) instanceof ReturnStmt)) {
			this.reportCodeGenerationError(md.posn,
					"Last statement of a non-void method must be a return statement");
		}
		return null;
	}

	public Object visitParameterDecl(ParameterDecl pd, Object arg){
		pd.type.visit(this, arg);
		return null;
	}

	public Object visitVarDecl(VarDecl vd, Object arg){
		vd.type.visit(this, arg);

		// Make space for variable on the stack (8 bytes).
		_asm.add(new Push(0));
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
		StatementList sl = stmt.sl;
		for (Statement s: sl) {
			s.visit(this, arg);
		}
		return null;
	}

	public Object visitVardeclStmt(VarDeclStmt stmt, Object arg){
		stmt.varDecl.visit(this, arg);
		stmt.initExp.visit(this, arg);

		// TODO: Check if initExp evaluates to 'null'.

		if (stmt.varDecl.type instanceof ArrayType | stmt.varDecl.type instanceof ClassType) {

		} else {

		}
		
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
		stmt.thenStmt.visit(this, arg);
		if (stmt.elseStmt != null)
			stmt.elseStmt.visit(this, arg);
		return null;
	}

	public Object visitWhileStmt(WhileStmt stmt, Object arg){
		stmt.cond.visit(this, arg);
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
		return null;
	}

	public Object visitIdRef(IdRef ref, Object arg) {
		ref.id.visit(this, arg);
		return null;
	}

	public Object visitQRef(QualRef qr, Object arg) {
		qr.id.visit(this, arg);
		qr.ref.visit(this, arg);
		return null;
	}

	///////////////////////////////////////////////////////////////////////////////
	//
	// TERMINALS
	//
	///////////////////////////////////////////////////////////////////////////////

	public Object visitIdentifier(Identifier id, Object arg){
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

	public void makeElf(String fname) {
		ELFMaker elf = new ELFMaker(_errors, _asm.getSize(), 8); // bss ignored until PA5, set to 8
		elf.outputELF(fname, _asm.getBytes(), ??); // TODO: set the location of the main method
	}
	
	private int makeMalloc() {
		int idxStart = _asm.add( new Mov_rmi(new R(Reg64.RAX,true),0x09) ); // mmap
		
		_asm.add( new Xor(		new R(Reg64.RDI,Reg64.RDI)) 	); // addr=0
		_asm.add( new Mov_rmi(	new R(Reg64.RSI,true),0x1000) ); // 4kb alloc
		_asm.add( new Mov_rmi(	new R(Reg64.RDX,true),0x03) 	); // prot read|write
		_asm.add( new Mov_rmi(	new R(Reg64.R10,true),0x22) 	); // flags= private, anonymous
		_asm.add( new Mov_rmi(	new R(Reg64.R8, true),-1) 	); // fd= -1
		_asm.add( new Xor(		new R(Reg64.R9,Reg64.R9)) 	); // offset=0
		_asm.add( new Syscall() );
		
		// pointer to newly allocated memory is in RAX
		// return the index of the first instruction in this method, if needed
		return idxStart;
	}
	
	private int makePrintln() {
		// TODO: how can we generate the assembly to println?
		return -1;
	}
}
