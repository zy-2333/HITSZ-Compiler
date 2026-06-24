package cn.edu.hitsz.compiler.asm;

import cn.edu.hitsz.compiler.ir.IRImmediate;
import cn.edu.hitsz.compiler.ir.IRValue;
import cn.edu.hitsz.compiler.ir.IRVariable;
import cn.edu.hitsz.compiler.ir.Instruction;
import cn.edu.hitsz.compiler.ir.InstructionKind;
import cn.edu.hitsz.compiler.utils.FileUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * 实验四：目标代码生成，将 IR 转为 RISC-V 汇编并完成寄存器分配。
 */
public class AssemblyGenerator {

    private static final List<String> REGISTER_POOL = List.of("t0", "t1", "t2", "t3", "t4", "t5", "t6");
    private static final String RETURN_REG = "a0";

    private List<Instruction> instructions;
    private Map<IRVariable, Integer> lastUsage;               // 变量 → 最后使用位置
    private Map<IRVariable, String> regMap = new HashMap<>(); // 变量 → 寄存器
    private Map<String, IRVariable> varMap = new HashMap<>(); // 寄存器 → 变量
    private LinkedList<String> freeRegisters = new LinkedList<>(); // 空闲寄存器池
    private Map<IRVariable, Integer> spillMap = new HashMap<>();  // 溢出变量 → 内存偏移
    private int spillOffset = 0;
    private List<String> asmLines = new ArrayList<>();

    /** 加载 IR，完成规范化与活跃信息统计 */
    public void loadIR(List<Instruction> originInstructions) {
        var normalized = normalizeIR(originInstructions);
        this.instructions = normalized;
        computeLastUsage();
    }

    /** 执行寄存器分配与汇编生成 */
    public void run() {
        freeRegisters.clear();
        freeRegisters.addAll(REGISTER_POOL);
        regMap.clear();
        varMap.clear();
        spillMap.clear();
        spillOffset = 0;
        asmLines.clear();
        asmLines.add(".text");
        for (int i = 0; i < instructions.size(); i++) {
            processInstruction(instructions.get(i), i);
        }
    }

    /** 输出汇编到文件 */
    public void dump(String path) {
        FileUtils.writeLines(path, asmLines);
    }

    // ---- IR 规范化 ----

    /**
     * 规范化 IR 使之更贴近 RISC-V 指令格式：
     * 双立即数 → 常量折叠；ADD 左立即数 → 交换；SUB/MUL 左立即数 → 插入 MOV；RET 后截断。
     */
    private List<Instruction> normalizeIR(List<Instruction> orig) {
        List<Instruction> result = new ArrayList<>();
        boolean foundRet = false;

        for (Instruction inst : orig) {
            if (foundRet) break;

            switch (inst.getKind()) {
                case ADD -> normalizeAdd(result, inst);
                case SUB -> normalizeSub(result, inst);
                case MUL -> normalizeMul(result, inst);
                case DIV -> normalizeMul(result, inst);  // DIV 规范化与 MUL 相同
                case SLT -> normalizeSlt(result, inst);
                case SEQZ -> normalizeSeqz(result, inst);
                case MOV, BZ, JMP, LABEL -> result.add(inst);
                case RET -> {
                    result.add(inst);
                    foundRet = true;
                }
            }
        }
        return result;
    }

    private void normalizeAdd(List<Instruction> result, Instruction inst) {
        IRValue lhs = inst.getLHS();
        IRValue rhs = inst.getRHS();
        IRVariable res = inst.getResult();

        if (lhs instanceof IRImmediate lhsImm && rhs instanceof IRImmediate rhsImm) {
            result.add(Instruction.createMov(res, IRImmediate.of(lhsImm.getValue() + rhsImm.getValue())));
        } else if (lhs instanceof IRImmediate) {
            // 左立即数 ADD → 交换，变成 var + imm
            result.add(Instruction.createAdd(res, rhs, lhs));
        } else {
            result.add(inst);
        }
    }

    private void normalizeSub(List<Instruction> result, Instruction inst) {
        IRValue lhs = inst.getLHS();
        IRValue rhs = inst.getRHS();
        IRVariable res = inst.getResult();

        if (lhs instanceof IRImmediate lhsImm && rhs instanceof IRImmediate rhsImm) {
            result.add(Instruction.createMov(res, IRImmediate.of(lhsImm.getValue() - rhsImm.getValue())));
        } else if (lhs instanceof IRImmediate) {
            // 左立即数 SUB → 插入 MOV
            IRVariable temp = IRVariable.temp();
            result.add(Instruction.createMov(temp, lhs));
            result.add(Instruction.createSub(res, temp, rhs));
        } else {
            result.add(inst);
        }
    }

    private void normalizeSlt(List<Instruction> result, Instruction inst) {
        IRValue lhs = inst.getLHS();
        IRValue rhs = inst.getRHS();
        IRVariable res = inst.getResult();

        if (lhs instanceof IRImmediate lhsImm && rhs instanceof IRImmediate rhsImm) {
            result.add(Instruction.createMov(res, IRImmediate.of(lhsImm.getValue() < rhsImm.getValue() ? 1 : 0)));
        } else if (lhs instanceof IRImmediate) {
            IRVariable temp = IRVariable.temp();
            result.add(Instruction.createMov(temp, lhs));
            result.add(Instruction.createSlt(res, temp, rhs));
        } else if (rhs instanceof IRImmediate) {
            IRVariable temp = IRVariable.temp();
            result.add(Instruction.createMov(temp, rhs));
            result.add(Instruction.createSlt(res, lhs, temp));
        } else {
            result.add(inst);
        }
    }

    private void normalizeSeqz(List<Instruction> result, Instruction inst) {
        IRValue from = inst.getFrom();
        IRVariable res = inst.getResult();

        if (from instanceof IRImmediate imm) {
            result.add(Instruction.createMov(res, IRImmediate.of(imm.getValue() == 0 ? 1 : 0)));
        } else {
            result.add(inst);
        }
    }

    private void normalizeMul(List<Instruction> result, Instruction inst) {
        IRValue lhs = inst.getLHS();
        IRValue rhs = inst.getRHS();
        IRVariable res = inst.getResult();

        if (lhs instanceof IRImmediate lhsImm && rhs instanceof IRImmediate rhsImm) {
            result.add(Instruction.createMov(res, IRImmediate.of(lhsImm.getValue() * rhsImm.getValue())));
        } else if (lhs instanceof IRImmediate) {
            // 左立即数 MUL → 插入 MOV
            IRVariable temp = IRVariable.temp();
            result.add(Instruction.createMov(temp, lhs));
            result.add(Instruction.createMul(res, temp, rhs));
        } else if (rhs instanceof IRImmediate) {
            // 右立即数 MUL → 插入 MOV
            IRVariable temp = IRVariable.temp();
            result.add(Instruction.createMov(temp, rhs));
            result.add(Instruction.createMul(res, lhs, temp));
        } else {
            result.add(inst);
        }
    }


    // ---- 活跃信息统计 ----

    /** 倒序遍历指令，记录每个变量作为操作数的最后一次出现位置。 */
    private void computeLastUsage() {
        lastUsage = new HashMap<>();
        for (int i = instructions.size() - 1; i >= 0; i--) {
            for (IRValue operand : getOperands(instructions.get(i))) {
                if (operand instanceof IRVariable var) {
                    lastUsage.putIfAbsent(var, i);
                }
            }
        }
    }

    /** 获取指令中所有操作数（不含 result）。 */
    private List<IRValue> getOperands(Instruction inst) {
        return switch (inst.getKind()) {
            case ADD, SUB, MUL, DIV, SLT -> List.of(inst.getLHS(), inst.getRHS());
            case MOV, SEQZ -> List.of(inst.getFrom());
            case RET -> List.of(inst.getReturnValue());
            case BZ -> List.of(inst.getBranchCondition());
            case JMP, LABEL -> List.of();
        };
    }


    // ---- 指令分发 ----

    private void processInstruction(Instruction inst, int index) {
        switch (inst.getKind()) {
            case MOV, SEQZ -> genUnary(inst, index);
            case ADD, SUB, MUL, DIV, SLT -> genBinary(inst, index);
            case RET -> genReturn(inst, index);
            case BZ -> genBranch(inst, index);
            case JMP -> genJump(inst, index);
            case LABEL -> genLabel(inst);
        }
    }

    private void genUnary(Instruction inst, int index) {
        Set<String> reserved = new HashSet<>();
        IRValue from = inst.getFrom();
        String resultReg = allocRegister(inst.getResult(), index, reserved);

        if (inst.getKind() == InstructionKind.SEQZ) {
            String fromReg = getOperandRegister(from, index, reserved);
            emit("seqz " + resultReg + ", " + fromReg, inst);
        } else if (from instanceof IRImmediate imm) {
            emit("li " + resultReg + ", " + imm.getValue(), inst);
        } else {
            String fromReg = getOperandRegister(from, index, reserved);
            emit("mv " + resultReg + ", " + fromReg, inst);
        }
        freeUnusedRegisters(index);
    }

    private void genBinary(Instruction inst, int index) {
        Set<String> reserved = new HashSet<>();

        // 先加载所有变量操作数到不同寄存器（预留，防止互相抢占）
        String lhsReg = getOperandRegister(inst.getLHS(), index, reserved);
        String rhsReg = null;
        IRValue rhs = inst.getRHS();
        if (!(rhs instanceof IRImmediate)) {
            rhsReg = getOperandRegister(rhs, index, reserved);
        }

        // 再为结果分配寄存器
        String resultReg = allocRegister(inst.getResult(), index, reserved);

        if (rhs instanceof IRImmediate imm) {
            int val = inst.getKind() == InstructionKind.SUB ? -imm.getValue() : imm.getValue();
            emit("addi " + resultReg + ", " + lhsReg + ", " + val, inst);
        } else {
            String op = switch (inst.getKind()) {
                case ADD -> "add";
                case SUB -> "sub";
                case MUL -> "mul";
                case DIV -> "div";
                case SLT -> "slt";
                default -> throw new RuntimeException("Unexpected binary kind");
            };
            emit(op + " " + resultReg + ", " + lhsReg + ", " + rhsReg, inst);
        }
        freeUnusedRegisters(index);
    }

    private void genReturn(Instruction inst, int index) {
        Set<String> reserved = new HashSet<>();
        IRValue returnValue = inst.getReturnValue();
        String reg = getOperandRegister(returnValue, index, reserved);
        emit("mv " + RETURN_REG + ", " + reg, inst);
    }

    private void genBranch(Instruction inst, int index) {
        Set<String> reserved = new HashSet<>();
        IRValue cond = inst.getBranchCondition();
        String condReg = getOperandRegister(cond, index, reserved);
        emit("beq " + condReg + ", x0, " + inst.getBranchLabel(), inst);
    }

    private void genJump(Instruction inst, int index) {
        emit("j " + inst.getBranchLabel(), inst);
    }

    private void genLabel(Instruction inst) {
        asmLines.add(inst.getLabelName() + ":");
    }


    // ---- 寄存器分配 ----

    /** 获取操作数的物理寄存器：已在寄存器中 → 加载溢出 → 新分配 */
    private String getOperandRegister(IRValue operand, int index, Set<String> reserved) {
        if (operand instanceof IRImmediate) return null;
        IRVariable var = (IRVariable) operand;

        if (regMap.containsKey(var)) return regMap.get(var);

        if (spillMap.containsKey(var)) {
            int offset = spillMap.remove(var);
            String reg = allocRegister(var, index, reserved);
            asmLines.add("\tlw " + reg + ", " + offset + "(x0)\t\t# load spilled " + var.getName());
            reserved.add(reg);
            return reg;
        }

        String reg = allocRegister(var, index, reserved);
        reserved.add(reg);
        return reg;
    }

    /** 寄存器分配：已分配 > 空闲 > 抢占死亡变量 > 溢出（选做） */
    private String allocRegister(IRVariable var, int index, Set<String> reserved) {
        if (regMap.containsKey(var)) return regMap.get(var);

        if (!freeRegisters.isEmpty()) {
            String reg = freeRegisters.poll();
            regMap.put(var, reg);
            varMap.put(reg, var);
            return reg;
        }

        // 抢占不再使用的变量
        Iterator<Map.Entry<String, IRVariable>> it = varMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, IRVariable> entry = it.next();
            String reg = entry.getKey();
            IRVariable deadVar = entry.getValue();
            if (lastUsage.getOrDefault(deadVar, -1) < index && !reserved.contains(reg)) {
                it.remove();
                regMap.remove(deadVar);
                regMap.put(var, reg);
                varMap.put(reg, var);
                return reg;
            }
        }

        // 溢出（选做）
        return spillAndAllocate(var, reserved);
    }

    /** 释放本次指令处最后一次使用的变量所占用寄存器 */
    private void freeUnusedRegisters(int index) {
        Iterator<Map.Entry<String, IRVariable>> it = varMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, IRVariable> entry = it.next();
            IRVariable var = entry.getValue();
            if (lastUsage.getOrDefault(var, -1) == index) {
                it.remove();
                regMap.remove(var);
                freeRegisters.add(entry.getKey());
            }
        }
    }

    /** 寄存器溢出：选最远将来才使用的变量溢出到内存（Belady 策略） */
    private String spillAndAllocate(IRVariable var, Set<String> reserved) {
        String victimReg = null;
        IRVariable victimVar = null;
        int furthestLastUse = -1;

        for (Map.Entry<String, IRVariable> entry : varMap.entrySet()) {
            IRVariable v = entry.getValue();
            String reg = entry.getKey();
            if (reserved.contains(reg)) continue;
            int last = lastUsage.getOrDefault(v, Integer.MAX_VALUE);
            if (last > furthestLastUse) {
                furthestLastUse = last;
                victimReg = entry.getKey();
                victimVar = v;
            }
        }

        // 溢出到基于 x0 偏移的内存
        spillMap.put(victimVar, spillOffset);
        asmLines.add("\tsw " + victimReg + ", " + spillOffset + "(x0)\t\t# spill " + victimVar.getName());
        spillOffset += 4;

        varMap.remove(victimReg);
        regMap.remove(victimVar);

        regMap.put(var, victimReg);
        varMap.put(victimReg, var);
        return victimReg;
    }


    // ---- 输出 ----

    /** 输出一条汇编指令，带 IR 注释 */
    private void emit(String asm, Instruction ir) {
        asmLines.add("\t" + asm + "\t\t# " + ir.toString());
    }
}
