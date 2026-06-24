package cn.edu.hitsz.compiler.ir;

/**
 * IR 的种类
 */
public enum InstructionKind {
    ADD, SUB, MUL, DIV, MOV, RET,
    BZ, JMP, LABEL, SLT, SEQZ;

    public boolean isBinary() {
        return this == ADD || this == SUB || this == MUL || this == DIV || this == SLT;
    }

    public boolean isUnary() {
        return this == MOV || this == SEQZ;
    }

    public boolean isReturn() {
        return this == RET;
    }

    public boolean isBranch() {
        return this == BZ;
    }

    public boolean isJump() {
        return this == JMP;
    }

    public boolean isLabel() {
        return this == LABEL;
    }
}
