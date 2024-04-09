package miniJava.CodeGeneration.x64.ISA;

import miniJava.CodeGeneration.x64.Instruction;
import miniJava.CodeGeneration.x64.R;
import miniJava.CodeGeneration.x64.x64;

public class Neg extends Instruction {
	public Neg(R modrmsib) {
		opcodeBytes.write(0xF7);
		modrmsib.SetRegR(x64.mod543ToReg(3));
		byte[] rmsib = modrmsib.getBytes();
		importREX(modrmsib);
		x64.writeBytes(immBytes,rmsib);
	}
}
