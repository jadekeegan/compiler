package miniJava.CodeGeneration.x64.ISA;

import miniJava.CodeGeneration.x64.R;

public class Add extends SimpleMathInstruction {
	@Override
	protected SimpleMathOp _thisOp() {
		return SimpleMathOp.ADD;
	}
	
	public Add(R modrmsib) {
		super(modrmsib);
	}

	public Add(R modrmsib, int imm) {
		super(modrmsib,imm);
	}
}
