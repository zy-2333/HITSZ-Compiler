package cn.edu.hitsz.compiler.ir;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 中间表示的指令.
 * <br>
 * 本项目的中间表示采用三地址代码/四元组形式, 指令与 IR 变量分离.
 * 实现上采用同型 IR + 辅助用 getter, 用枚举确定类型, 用 createXXX 方法模拟子类构造函数.
 * <br>
 * 实验三选做扩展: 新增 DIV/SLT/SEQZ/BZ/JMP/LABEL 指令以支持除法和 if-else 控制流.
 */
public class Instruction {
    //============================== 不同种类 IR 的构造函数 ==============================
    public static Instruction createAdd(IRVariable result, IRValue lhs, IRValue rhs) {
        return new Instruction(InstructionKind.ADD, result, List.of(lhs, rhs), null);
    }

    public static Instruction createSub(IRVariable result, IRValue lhs, IRValue rhs) {
        return new Instruction(InstructionKind.SUB, result, List.of(lhs, rhs), null);
    }

    public static Instruction createMul(IRVariable result, IRValue lhs, IRValue rhs) {
        return new Instruction(InstructionKind.MUL, result, List.of(lhs, rhs), null);
    }

    public static Instruction createDiv(IRVariable result, IRValue lhs, IRValue rhs) {
        return new Instruction(InstructionKind.DIV, result, List.of(lhs, rhs), null);
    }

    public static Instruction createMov(IRVariable result, IRValue from) {
        return new Instruction(InstructionKind.MOV, result, List.of(from), null);
    }

    public static Instruction createRet(IRValue returnValue) {
        return new Instruction(InstructionKind.RET, null, List.of(returnValue), null);
    }

    public static Instruction createSlt(IRVariable result, IRValue lhs, IRValue rhs) {
        return new Instruction(InstructionKind.SLT, result, List.of(lhs, rhs), null);
    }

    public static Instruction createSeqz(IRVariable result, IRValue operand) {
        return new Instruction(InstructionKind.SEQZ, result, List.of(operand), null);
    }

    public static Instruction createBZ(IRValue cond, String label) {
        return new Instruction(InstructionKind.BZ, null, List.of(cond), label);
    }

    public static Instruction createJMP(String label) {
        return new Instruction(InstructionKind.JMP, null, List.of(), label);
    }

    public static Instruction createLabel(String name) {
        return new Instruction(InstructionKind.LABEL, null, List.of(), name);
    }


    //============================== 不同种类 IR 的参数 getter ==============================
    public InstructionKind getKind() {
        return kind;
    }

    public IRVariable getResult() {
        ensureKindMatch(Set.of(InstructionKind.ADD, InstructionKind.SUB, InstructionKind.MUL,
            InstructionKind.MOV, InstructionKind.DIV, InstructionKind.SLT, InstructionKind.SEQZ));
        return result;
    }

    public IRValue getLHS() {
        ensureKindMatch(Set.of(InstructionKind.ADD, InstructionKind.SUB, InstructionKind.MUL,
            InstructionKind.DIV, InstructionKind.SLT));
        return operands.get(0);
    }

    public IRValue getRHS() {
        ensureKindMatch(Set.of(InstructionKind.ADD, InstructionKind.SUB, InstructionKind.MUL,
            InstructionKind.DIV, InstructionKind.SLT));
        return operands.get(1);
    }

    public IRValue getFrom() {
        ensureKindMatch(Set.of(InstructionKind.MOV, InstructionKind.SEQZ));
        return operands.get(0);
    }

    public IRValue getReturnValue() {
        ensureKindMatch(Set.of(InstructionKind.RET));
        return operands.get(0);
    }

    public IRValue getBranchCondition() {
        ensureKindMatch(Set.of(InstructionKind.BZ));
        return operands.get(0);
    }

    public String getBranchLabel() {
        ensureKindMatch(Set.of(InstructionKind.BZ, InstructionKind.JMP));
        return label;
    }

    public String getLabelName() {
        ensureKindMatch(Set.of(InstructionKind.LABEL));
        return label;
    }

    public void setBranchLabel(String label) {
        ensureKindMatch(Set.of(InstructionKind.BZ, InstructionKind.JMP));
        this.label = label;
    }


    //============================== 基础设施 ==============================
    @Override
    public String toString() {
        final var kindString = kind.toString();
        final var resultString = result == null ? "" : result.toString();
        final var operandsString = operands.stream().map(Objects::toString).collect(Collectors.joining(", "));
        if (label != null) {
            return "(%s, %s, %s, %s)".formatted(kindString, resultString, operandsString, label);
        }
        return "(%s, %s, %s)".formatted(kindString, resultString, operandsString);
    }

    public List<IRValue> getOperands() {
        return Collections.unmodifiableList(operands);
    }

    private Instruction(InstructionKind kind, IRVariable result, List<IRValue> operands, String label) {
        this.kind = kind;
        this.result = result;
        this.operands = operands;
        this.label = label;
    }

    private final InstructionKind kind;
    private final IRVariable result;
    private final List<IRValue> operands;
    private String label;

    private void ensureKindMatch(Set<InstructionKind> targetKinds) {
        final var kind = getKind();
        if (!targetKinds.contains(kind)) {
            final var acceptKindsString = targetKinds.stream()
                .map(InstructionKind::toString)
                .collect(Collectors.joining(","));

            throw new RuntimeException(
                "Illegal operand access, except %s, but given %s".formatted(acceptKindsString, kind));
        }
    }
}
