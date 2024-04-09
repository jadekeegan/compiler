package miniJava.CodeGeneration.x64.ISA;

import miniJava.CodeGeneration.x64.R;

public class And extends SimpleMathInstruction {
	@Override
	protected SimpleMathOp _thisOp() {
		return SimpleMathOp.AND;
	}

	public And(R modrmsib) {
		super(modrmsib);
	}

	public And(R modrmsib, int imm) {
		super(modrmsib,imm);
	}
	
	public And(R modrmsib, int imm, boolean signExtend) {
		super(modrmsib,imm,signExtend);
	}
}
