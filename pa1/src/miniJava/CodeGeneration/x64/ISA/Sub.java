package miniJava.CodeGeneration.x64.ISA;

import miniJava.CodeGeneration.x64.R;

public class Sub extends SimpleMathInstruction {
	@Override
	protected SimpleMathOp _thisOp() {
		return SimpleMathOp.SUB;
	}
	
	public Sub(R modrmsib) {
		super(modrmsib);
	}

	public Sub(R modrmsib, int imm) {
		super(modrmsib,imm);
	}
}
