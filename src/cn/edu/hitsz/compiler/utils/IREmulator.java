package cn.edu.hitsz.compiler.utils;

import cn.edu.hitsz.compiler.ir.IRImmediate;
import cn.edu.hitsz.compiler.ir.IRValue;
import cn.edu.hitsz.compiler.ir.IRVariable;
import cn.edu.hitsz.compiler.ir.Instruction;
import cn.edu.hitsz.compiler.ir.InstructionKind;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 用来模拟执行 IR 的类
 */
public class IREmulator {
    public static IREmulator load(List<Instruction> instructions) {
        return new IREmulator(instructions);
    }

    public Optional<Integer> execute() {
        // 预计算标号 → 指令索引映射
        final Map<String, Integer> labelMap = new HashMap<>();
        for (int i = 0; i < instructions.size(); i++) {
            final var inst = instructions.get(i);
            if (inst.getKind() == InstructionKind.LABEL) {
                labelMap.put(inst.getLabelName(), i);
            }
        }

        int ip = 0;
        while (ip < instructions.size()) {
            final var instruction = instructions.get(ip);
            switch (instruction.getKind()) {
                case MOV -> environment.put(instruction.getResult(), eval(instruction.getFrom()));
                case ADD -> environment.put(instruction.getResult(),
                    eval(instruction.getLHS()) + eval(instruction.getRHS()));
                case SUB -> environment.put(instruction.getResult(),
                    eval(instruction.getLHS()) - eval(instruction.getRHS()));
                case MUL -> environment.put(instruction.getResult(),
                    eval(instruction.getLHS()) * eval(instruction.getRHS()));
                case DIV -> environment.put(instruction.getResult(),
                    eval(instruction.getLHS()) / eval(instruction.getRHS()));
                case SLT -> environment.put(instruction.getResult(),
                    eval(instruction.getLHS()) < eval(instruction.getRHS()) ? 1 : 0);
                case SEQZ -> environment.put(instruction.getResult(),
                    eval(instruction.getFrom()) == 0 ? 1 : 0);
                case RET -> this.returnValue = eval(instruction.getReturnValue());
                case BZ -> {
                    if (eval(instruction.getBranchCondition()) == 0) {
                        ip = labelMap.get(instruction.getBranchLabel());
                        continue;
                    }
                }
                case JMP -> {
                    ip = labelMap.get(instruction.getBranchLabel());
                    continue;
                }
                case LABEL -> { /* 标号占位，无操作 */ }
                default -> throw new RuntimeException("Unknown instruction kind: " + instruction.getKind());
            }
            ip++;
        }

        return Optional.ofNullable(this.returnValue);
    }

    public Integer eval(IRValue value) {
        if (value instanceof IRImmediate immediate) {
            return immediate.getValue();
        } else if (value instanceof IRVariable variable) {
            return environment.get(variable);
        } else {
            throw new RuntimeException("Unknown IR value type");
        }
    }

    private IREmulator(List<Instruction> instructions) {
        this.instructions = instructions;
        this.environment = new HashMap<>();
        this.returnValue = null;
    }

    private final List<Instruction> instructions;
    private final Map<IRVariable, Integer> environment;
    private Integer returnValue;
}
