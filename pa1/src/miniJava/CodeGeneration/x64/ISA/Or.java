package miniJava.CodeGeneration.x64.ISA;

import miniJava.CodeGeneration.x64.R;

public class Or extends SimpleMathInstruction {
	@Override
	protected SimpleMathOp _thisOp() {
		return SimpleMathOp.OR;
	}
	
	public Or(R modrmsib) {
		super(modrmsib);
	}

	public Or(R modrmsib, int imm) {
		super(modrmsib,imm);
	}
}
