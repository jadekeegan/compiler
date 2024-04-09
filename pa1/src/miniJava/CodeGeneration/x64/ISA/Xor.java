package miniJava.CodeGeneration.x64.ISA;

import miniJava.CodeGeneration.x64.R;

public class Xor extends SimpleMathInstruction {
	@Override
	protected SimpleMathOp _thisOp() {
		return SimpleMathOp.XOR;
	}
	
	public Xor(R modrmsib) {
		super(modrmsib);
	}

	public Xor(R modrmsib, int imm) {
		super(modrmsib,imm);
	}
}
