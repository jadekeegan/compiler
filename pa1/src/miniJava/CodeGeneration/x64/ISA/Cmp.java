package miniJava.CodeGeneration.x64.ISA;

import miniJava.CodeGeneration.x64.R;

public class Cmp extends SimpleMathInstruction {
	@Override
	protected SimpleMathOp _thisOp() {
		return SimpleMathOp.CMP;
	}

	public Cmp(R modrmsib) {
		super(modrmsib);
	}

	public Cmp(R modrmsib, int imm) {
		super(modrmsib,imm);
	}
}
