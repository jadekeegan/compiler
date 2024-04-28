package miniJava.Optimization;

import miniJava.CodeGeneration.x64.ISA.Mov_rmi;
import miniJava.CodeGeneration.x64.ISA.Push;
import miniJava.CodeGeneration.x64.Instruction;
import miniJava.CodeGeneration.x64.InstructionList;
import miniJava.CodeGeneration.x64.*;

public class Optimization {
    private InstructionList _asm; // our list of instructions that are used to make the code section

    public Optimization(InstructionList _asm) {
        this._asm = _asm;
    }

    public InstructionList optimize() {
        InstructionList optimized_asm = new InstructionList();

        Instruction lastOp = null;
        for (Instruction currOp : this._asm.getInstructions()) {
            if (lastOp != null && lastOp.getOpCodeBytes()[0] == -72 && currOp.getOpCodeBytes()[0] == 80) {    // movabs (Mov_ri64)
                // movabs rax, imm -> push rax = push imm
                optimized_asm.patch(lastOp.listIdx, new Push(lastOp.getImmBytes()[0]));
                lastOp = optimized_asm.get(lastOp.listIdx);
            } else if (lastOp != null && lastOp.getOpCodeBytes()[0] == -117 && currOp.getOpCodeBytes()[0] == 80 && lastOp.getImmBytes()[0] == 69) {
                // mov rax, [rbp-disp] -> push rax = push [rbp-disp]
                optimized_asm.patch(lastOp.listIdx, new Push(new R(Reg64.RBP, lastOp.getImmBytes()[1])));
            } else if (lastOp != null && lastOp.getOpCodeBytes()[0] == -72 && currOp.getOpCodeBytes()[0] == -119 && currOp.getImmBytes()[0] == 69) {
                // movabs rax, imm -> mov [rbp-disp], rax = mov [rbp-disp], imm
                optimized_asm.patch(lastOp.listIdx, new Mov_rmi(new R(Reg64.RBP, currOp.getImmBytes()[1]), lastOp.getImmBytes()[0]));
            } else if (lastOp != null && lastOp.getOpCodeBytes()[0] == 80 && currOp.getOpCodeBytes()[0] == 88) {
                // push rax -> pop rax = nothing. remove previous push rax
                lastOp = optimized_asm.get(optimized_asm.pop(lastOp));
            } else {
                optimized_asm.add(currOp);
                lastOp = currOp;
            }
        }

        return optimized_asm;
    }
}
