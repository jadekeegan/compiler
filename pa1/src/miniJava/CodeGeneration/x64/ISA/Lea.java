package miniJava.CodeGeneration.x64.ISA;

import miniJava.CodeGeneration.x64.Instruction;
import miniJava.CodeGeneration.x64.R;
import miniJava.CodeGeneration.x64.x64;

public class Lea extends Instruction {
	public Lea(R modrmsib) {
		opcodeBytes.write(0x8D);
		byte[] rmsib = modrmsib.getBytes();
		importREX(modrmsib);
		x64.writeBytes(immBytes,rmsib);
	}
}
