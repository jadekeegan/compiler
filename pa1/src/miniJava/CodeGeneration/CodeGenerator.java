package miniJava.CodeGeneration;

import com.sun.jdi.ArrayReference;
import miniJava.ErrorReporter;
import miniJava.AbstractSyntaxTrees.*;
import miniJava.AbstractSyntaxTrees.Package;
import miniJava.CodeGeneration.x64.*;
import miniJava.CodeGeneration.x64.ISA.*;
import miniJava.SyntacticAnalyzer.SourcePosition;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;

public class CodeGenerator implements Visitor<Object, Object> {
	private ErrorReporter _errors;
	private InstructionList _asm; // our list of instructions that are used to make the code section
	private int mainAddress = 0;
	private int stackSize = 0;
	private HashMap<Integer, MethodDecl> methodPatching = new HashMap<>();

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

		_asm.markOutputStart();
		prog.visit(this,null);
		_asm.outputFromMark();

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
			// Calculate size of Classes
			for (FieldDecl f: c.fieldDeclList) {
				// Add space for static fields to the stack.
				if (f.isStatic) {
					_asm.add(	new Push(0)	);
					f.offset = stackSize;	// push space to stack
					stackSize += 1;		// increment the size of the stack
				} else {
					f.offset = c.numBytes;	// set offset of field from start of class
					c.numBytes += 8;	// assume size of all types is 8 bytes
				}
			}

			// Find Main Method
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
					m.isMain = true;
				}
			}
		}

		for (ClassDecl c: cl) {
			c.visit(this, null);
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

		md.methodAddress = _asm.getSize();
		if (md.isMain) {
			mainAddress = md.methodAddress;
		} else if (methodPatching.containsValue(md)) {
			for (Integer idx : methodPatching.keySet()) {
				if (methodPatching.get(idx).equals(md)) {
					Instruction oldInstr = _asm.get(idx);
					_asm.patch(idx, new Call(oldInstr.startAddress, md.methodAddress));
				}
			}
		}

		_asm.add(new Push(Reg64.RBP));
		_asm.add(new Mov_rmr(new R(Reg64.RBP, Reg64.RSP)));

		ParameterDeclList pdl = md.parameterDeclList;
		for (ParameterDecl pd: pdl) {
			pd.visit(this, md);
		}

		StatementList sl = md.statementList;

		for (Statement s: sl) {
			s.visit(this, md);
		}

		if (md.type.typeKind != TypeKind.VOID
				&& !(md.statementList.get(md.statementList.size() - 1) instanceof ReturnStmt)) {
			this.reportCodeGenerationError(md.posn,
					"Last statement of a non-void method must be a return statement");
		}

		_asm.add(new Mov_rmr(new R(Reg64.RSP, Reg64.RBP)));
		_asm.add(new Pop(Reg64.RBP));

		// Pop Arguments from the Stack
//		int RSPoffset = md.parameterDeclList.size() * 8;
//		_asm.add(new Add(new R(Reg64.RSP, true), RSPoffset));

		if (md.isMain) {
			_asm.add( new Mov_rmi(new R(Reg64.RAX,true),0x3C) ); // exit
			_asm.add(new Xor(new R(Reg64.RDI,Reg64.RDI)) ); // addr=0
			_asm.add(new Syscall());
		} else {
			_asm.add(new Ret());
		}
		return null;
	}

	// int method(int x, int y, int z)
	public Object visitParameterDecl(ParameterDecl pd, Object arg){
		// Update the stack offset for each parameterDecl.
		// num_params * -1 + num_params_processed
		MethodDecl md = (MethodDecl) arg;
		pd.stackOffset = (md.parameterDeclList.size() * 8) + md.currentArgPosition;
		md.currentArgPosition += 8;

		pd.type.visit(this, arg);
		return null;
	}

	public Object visitVarDecl(VarDecl vd, Object arg){
		// Update the stack offset for the variable.
		MethodDecl md = (MethodDecl) arg;
		vd.stackOffset = md.currentOffset;
		md.currentOffset -= 8;

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
		StatementList sl = stmt.sl;
		for (Statement s: sl) {
			s.visit(this, arg);
		}
		return null;
	}

	public Object visitVardeclStmt(VarDeclStmt stmt, Object arg){
		stmt.varDecl.visit(this, arg);
		stmt.initExp.visit(this, arg);
		_asm.add(new Push(Reg64.RAX));
		return null;
	}

	public Object visitAssignStmt(AssignStmt stmt, Object arg){
		if (stmt.ref instanceof IdRef) {
			stmt.val.visit(this, arg);	// Push value to be assigned to RAX

			if (stmt.ref.declaration instanceof LocalDecl) {
				_asm.add(new Mov_rmr(new R(Reg64.RBP, ((LocalDecl) stmt.ref.declaration).stackOffset, Reg64.RAX)));
			}
//			} else if (stmt.ref.declaration instanceof FieldDecl) {
//				FieldDecl fd = (FieldDecl) stmt.ref.declaration;
//				if (fd.isStatic) {	// Access from Stack
//					_asm.add(	new Mov_rmr(new R(Reg64.RBP, fd.offset, Reg64.RAX)));
//				} else {	// Access Heap
//					if (arg instanceof VarDecl) {
//						// Move heap addr to RDX
//						_asm.add( new Mov_rrm(new R(Reg64.RBP, ((VarDecl) arg).stackOffset, Reg64.RDX)));
//						// Add field offset to heap addr
//						_asm.add(	new Add(new R(Reg64.RDX, fd.offset)));
//						// Update Field
//						_asm.add(	new Mov_rmr(new R(Reg64.RDX, Reg64.RAX)));
//					}
//				}
//			}
		} else if (stmt.ref instanceof QualRef) {
			stmt.ref.visit(this, arg);	// Push address of var associated with ref to RAX
			_asm.add(new Mov_rmr(new R(Reg64.RCX, Reg64.RAX)));	// Move destination address to RDX
			stmt.val.visit(this, arg);	// Push value to be assigned to RAX

			if (stmt.val instanceof NewObjectExpr || stmt.val instanceof NewArrayExpr) {
				_asm.add(new Push(Reg64.RAX));
			}
			_asm.add(new Mov_rrm(new R(Reg64.RCX, 0, Reg64.RAX)));	// stmt.val to address at RAX
		}

		return null;
	}

	public Object visitIxAssignStmt(IxAssignStmt stmt, Object arg){
		stmt.ix.visit(this, arg);	// pushes value to RAX
		_asm.add(new Imul(Reg64.RDI, new R(Reg64.RAX, true), 8));	// move index * 8 into RDI
		stmt.ref.visit(this, arg); // pushes addr of ref to RAX
		_asm.add(new Add(new R(Reg64.RAX, Reg64.RDI)));	// rax = addr + index * 8
		_asm.add(new Mov_rmr(new R(Reg64.RDI, Reg64.RAX)));	// rdi = rax
		stmt.exp.visit(this, arg);	// pushes result to RAX
		_asm.add(new Mov_rmr(new R(Reg64.RDI, 0, Reg64.RAX)));	// [rdi] = rax
		return null;
	}

	public Object visitCallStmt(CallStmt stmt, Object arg){
//		stmt.methodRef.visit(this, arg);
		ExprList al = stmt.argList;
		for (int i=al.size()-1; i >= 0; i--) {
			al.get(i).visit(this, arg);	// Push arguments to RAX
			_asm.add(new Push(Reg64.RAX));
		}

		// Call method
		if (stmt.methodRef instanceof QualRef) {
			if (((QualRef) stmt.methodRef).ref.declaration.name.equals("out") &&
					((QualRef) stmt.methodRef).id.spelling.equals("println")) {
				_asm.add(new Lea(new R(Reg64.RSP, 0, Reg64.RSI)));	// move addr of RSP into RSI
				makePrintln();
			} else {
				if (((MethodDecl) ((QualRef) stmt.methodRef).id.declaration).methodAddress == -1) {
					int callIdx = _asm.add(new Call(0));	// dummy call for instruction reference
					methodPatching.put(callIdx, ((MethodDecl) ((QualRef) stmt.methodRef).id.declaration));	// put method into hashmap
				} else {
					_asm.add(new Call(_asm.getSize(), ((MethodDecl) ((QualRef) stmt.methodRef).id.declaration).methodAddress));
				}
			}
		} else if (stmt.methodRef instanceof IdRef && ((MethodDecl) stmt.methodRef.declaration).isStatic) {
			// Implicit "this" case!
			if (((MethodDecl) ((IdRef) stmt.methodRef).id.declaration).methodAddress == -1) {
				int callIdx = _asm.add(new Call(0));	// dummy call for instruction reference
				methodPatching.put(callIdx, ((MethodDecl) ((IdRef) stmt.methodRef).id.declaration));	// put method into hashmap
			} else {
				_asm.add(new Call(_asm.getSize(), ((MethodDecl) ((IdRef) stmt.methodRef).id.declaration).methodAddress));
			}
		}
		return null;
	}

	public Object visitReturnStmt(ReturnStmt stmt, Object arg) {
		if (stmt.returnExpr != null) {
			stmt.returnExpr.visit(this, arg);    // Push return to RAX
		}
		return null;
	}

	public Object visitIfStmt(IfStmt stmt, Object arg){
		stmt.cond.visit(this, arg);	// Flag is currently set in RAX (Reg8.AL).

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
		expr.expr.visit(this, Reg64.RAX);
		switch (expr.operator.spelling) {
			case "-":
				_asm.add(new Neg(new R(Reg64.RAX, true)));
				break;
			case "!":
				_asm.add(new Not(new R(Reg64.RAX, true)));
				break;
		}
		return null;
	}

	public Object visitBinaryExpr(BinaryExpr expr, Object arg){
		expr.operator.visit(this, arg);	// TODO: remove later, not necessary
		expr.right.visit(this, arg);	// result pushed to RAX
		_asm.add(new Mov_rmr(new R(Reg64.RCX, Reg64.RAX)));	// move right expression into RCX
		expr.left.visit(this, arg);	// result pushed to RAX

		switch (expr.operator.spelling) {
			case "+":
				_asm.add(new Add(new R(Reg64.RAX, Reg64.RCX)));
				break;
			case "-":
				_asm.add(new Sub(new R(Reg64.RAX, Reg64.RCX)));
				break;
			case "*":
				_asm.add(new Imul(new R(Reg64.RAX, Reg64.RCX)));
				break;
			case "/":
				_asm.add(new Idiv(new R(Reg64.RAX, Reg64.RCX)));
				break;
			case "||":
				_asm.add(new Or(new R(Reg64.RAX, Reg64.RCX)));
				break;
			case "&&":
				_asm.add(new And(new R(Reg64.RAX, Reg64.RCX)));
				break;
			default:	// Comparison
				_asm.add(new Cmp(new R(Reg64.RAX, Reg64.RCX)));
				_asm.add(new Xor(new R(Reg64.RAX, Reg64.RAX)));	// Clear RAX to set AL
				_asm.add(new SetCond(Condition.getCond(expr.operator), Reg8.AL));
		}
		return null;
	}

	public Object visitRefExpr(RefExpr expr, Object arg){
		expr.ref.visit(this, arg);
		return null;
	}

	public Object visitIxExpr(IxExpr ie, Object arg){
		ie.ixExpr.visit(this, arg);	// pushes value to RAX
		_asm.add(new Imul(Reg64.RDI, new R(Reg64.RAX, true), 8));	// move index * 8 into RDI
		ie.ref.visit(this, arg); // pushes addr of ref to RAX
		_asm.add(new Add(new R(Reg64.RAX, Reg64.RDI)));	// rax = addr + index * 8
		_asm.add(new Mov_rrm(new R(Reg64.RAX, 0, Reg64.RAX)));	// rax = [rax]
		return null;
	}

	public Object visitCallExpr(CallExpr expr, Object arg){
		ExprList al = expr.argList;
		for (int i=al.size()-1; i >= 0; i--) {
			al.get(i).visit(this, arg);	// Push arguments to RAX
			_asm.add(new Push(Reg64.RAX));
		}

		// Call method
		if (expr.functionRef instanceof QualRef) {
			if (((MethodDecl) ((QualRef) expr.functionRef).id.declaration).methodAddress == -1) {
				int callIdx = _asm.add(new Call(0));	// dummy call for instruction reference
				methodPatching.put(callIdx, ((MethodDecl) ((QualRef) expr.functionRef).id.declaration));	// put method into hashmap
			} else {
				_asm.add(new Call(_asm.getSize(), ((MethodDecl) ((QualRef) expr.functionRef).id.declaration).methodAddress));
			}
		} else if (expr.functionRef instanceof IdRef && ((MethodDecl) expr.functionRef.declaration).isStatic) {
			// Implicit "this" case!
			if (((MethodDecl) ((IdRef) expr.functionRef).id.declaration).methodAddress == -1) {
				int callIdx = _asm.add(new Call(0));	// dummy call for instruction reference
				methodPatching.put(callIdx, ((MethodDecl) ((IdRef) expr.functionRef).id.declaration));	// put method into hashmap
			} else {
				_asm.add(new Call(_asm.getSize(), ((MethodDecl) ((IdRef) expr.functionRef).id.declaration).methodAddress));
			}
		}

		for (int i=0; i< al.size(); i++) {
			_asm.add(new Pop(Reg64.R15));
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

		// Need more nuance. How do we use the size?
		makeMalloc();
		return null;
	}

	public Object visitNewObjectExpr(NewObjectExpr expr, Object arg){
		expr.classtype.visit(this, arg);

		// Need more nuance. How do we use the size?
		makeMalloc();
		return null;
	}


	///////////////////////////////////////////////////////////////////////////////
	//
	// REFERENCES
	//
	///////////////////////////////////////////////////////////////////////////////

	public Object visitThisRef(ThisRef ref, Object arg) {
		// push associated class for ref?
		return null;
	}

	public Object visitIdRef(IdRef ref, Object arg) {
		ref.id.visit(this, arg);
		return null;
	}

	public Object visitQRef(QualRef qr, Object arg) {
		qr.ref.visit(this, arg);	// push the address of the last reference into RAX

		// Associated Declaration must be a MemberDecl!
		if (qr.id.declaration instanceof FieldDecl) {
			if (((FieldDecl) qr.id.declaration).isStatic) {
				_asm.add(new Add(new R(Reg64.RAX, true), ((FieldDecl) qr.id.declaration).offset));
			} else {
				// add offset to RAX (instanceAddr + offset) (HEAP)
				_asm.add(new Add(new R(Reg64.RAX, true), ((FieldDecl) qr.id.declaration).offset));
			}

		} else if (qr.id.declaration instanceof MethodDecl) {
			// Move the
		}
//		qr.id.visit(this, arg);
		return null;
	}

	///////////////////////////////////////////////////////////////////////////////
	//
	// TERMINALS
	//
	///////////////////////////////////////////////////////////////////////////////

	public Object visitIdentifier(Identifier id, Object arg){
		if (id.declaration instanceof LocalDecl) {
			_asm.add(new Mov_rrm(new R(Reg64.RBP, ((LocalDecl) id.declaration).stackOffset, Reg64.RAX)));
		} else if (id.declaration instanceof FieldDecl) {
			_asm.add(new Mov_rrm(new R(Reg64.RBP, ((FieldDecl) id.declaration).offset, Reg64.RAX)));	// assume instance address in RAX
		} else if (id.declaration instanceof ClassDecl) {
			// What to do here?
		}
		return null;
	}

	public Object visitOperator(Operator op, Object arg){
		return null;
	}

	public Object visitIntLiteral(IntLiteral num, Object arg){
		_asm.add( new Mov_ri64(Reg64.RAX, Integer.parseInt(num.spelling)));
		return null;
	}

	public Object visitBooleanLiteral(BooleanLiteral bool, Object arg){
		if (bool.spelling.equals("true")) {
			_asm.add( new Mov_ri64(Reg64.RAX, 1));
		} else { // false
			_asm.add( new Mov_ri64(Reg64.RAX, 0));
		}
		return null;
	}

	public Object visitNullLiteral(NullLiteral nl, Object arg) {
		_asm.add( new Mov_ri64(Reg64.RAX, 0));
		return null;
	}

	public void makeElf(String fname) {
		ELFMaker elf = new ELFMaker(_errors, _asm.getSize(), 8); // bss ignored until PA5, set to 8
		elf.outputELF(fname, _asm.getBytes(), mainAddress); // TODO: set the location of the main method
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
		int idxStart = _asm.add( new Mov_rmi(new R(Reg64.RAX,true),0x01) );
		_asm.add(new Mov_rmi(new R(Reg64.RDI, true), 0x01));
		_asm.add( new Mov_rmi(	new R(Reg64.RDX, true), 0x4)	);	// size

		_asm.add( new Syscall() );

		// pointer to newly allocated memory is in RAX
		// return the index of the first instruction in this method, if needed
		return idxStart;
	}
}
