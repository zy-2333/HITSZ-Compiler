package cn.edu.hitsz.compiler.parser;

import cn.edu.hitsz.compiler.ir.IRImmediate;
import cn.edu.hitsz.compiler.ir.IRValue;
import cn.edu.hitsz.compiler.ir.IRVariable;
import cn.edu.hitsz.compiler.ir.Instruction;
import cn.edu.hitsz.compiler.lexer.Token;
import cn.edu.hitsz.compiler.parser.table.Production;
import cn.edu.hitsz.compiler.parser.table.Status;
import cn.edu.hitsz.compiler.symtab.SymbolTable;
import cn.edu.hitsz.compiler.utils.FileUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * IR 生成器（实验三），基于 SDT 将语义动作附着在归约产生式上生成三地址码。
 */
public class IRGenerator implements ActionObserver {

    private SymbolTable symbolTable;
    private final Stack<Object> valueStack = new Stack<>();
    private final List<Instruction> instructions = new ArrayList<>();
    private int labelCounter = 0;
    private boolean inIfCondition = false; // 标记当前在 if 条件中，用于回填

    @Override
    public void setSymbolTable(SymbolTable table) {
        this.symbolTable = table;
    }

    @Override
    public void whenShift(Status currentStatus, Token currentToken) {
        final var kindId = currentToken.getKind().getIdentifier();
        switch (kindId) {
            case "id" -> valueStack.push(IRVariable.named(currentToken.getText()));
            case "IntConst" -> valueStack.push(IRImmediate.of(Integer.parseInt(currentToken.getText())));
            case "if" -> {
                valueStack.push(instructions.size());
                inIfCondition = true;
            }
            case ")" -> {
                if (inIfCondition) {
                    valueStack.push(instructions.size());
                    inIfCondition = false;
                }
            }
            case "else" -> valueStack.push(instructions.size());
            default -> {}
        }
    }

    @Override
    public void whenReduce(Status currentStatus, Production production) {
        switch (production.index()) {
            // 传递型产生式：值自然传递，无需动作
            case 1 -> { /* P -> S_list */ }
            case 2 -> { /* S_list -> S Semicolon */ }
            case 3 -> { /* S_list -> S Semicolon S_list */ }
            case 12 -> { /* Block -> { S_list } */ }
            case 13 -> { /* E -> A */ }
            case 16 -> { /* A -> B */ }
            case 19 -> { /* B -> id */ }
            case 20 -> { /* B -> IntConst */ }
            case 21 -> { /* B -> ( E ) */ }
            case 22 -> { /* B -> id [ E ] */ }

            // 声明：清理栈，不生成 IR
            case 5 -> { /* D -> int */ }
            case 4 -> { valueStack.pop(); }        // S -> D id
            case 6 -> { valueStack.pop(); valueStack.pop(); } // S -> D id [ IntConst ]

            // 二元运算：弹两个操作数，生成指令，压入临时变量
            case 14 -> { // E -> E + A
                final var rhs = (IRValue) valueStack.pop();
                final var lhs = (IRValue) valueStack.pop();
                final var temp = IRVariable.temp();
                instructions.add(Instruction.createAdd(temp, lhs, rhs));
                valueStack.push(temp);
            }
            case 15 -> { // E -> E - A
                final var rhs = (IRValue) valueStack.pop();
                final var lhs = (IRValue) valueStack.pop();
                final var temp = IRVariable.temp();
                instructions.add(Instruction.createSub(temp, lhs, rhs));
                valueStack.push(temp);
            }
            case 17 -> { // A -> A * B
                final var rhs = (IRValue) valueStack.pop();
                final var lhs = (IRValue) valueStack.pop();
                final var temp = IRVariable.temp();
                instructions.add(Instruction.createMul(temp, lhs, rhs));
                valueStack.push(temp);
            }
            case 18 -> { // A -> A / B
                final var rhs = (IRValue) valueStack.pop();
                final var lhs = (IRValue) valueStack.pop();
                final var temp = IRVariable.temp();
                instructions.add(Instruction.createDiv(temp, lhs, rhs));
                valueStack.push(temp);
            }

            // 比较运算：用 SLT、SEQZ、SUB 合成
            case 23 -> { // C -> E < E
                final var rhs = (IRValue) valueStack.pop();
                final var lhs = (IRValue) valueStack.pop();
                final var temp = IRVariable.temp();
                instructions.add(Instruction.createSlt(temp, lhs, rhs));
                valueStack.push(temp);
            }
            case 24 -> { // C -> E > E
                final var rhs = (IRValue) valueStack.pop();
                final var lhs = (IRValue) valueStack.pop();
                final var temp = IRVariable.temp();
                instructions.add(Instruction.createSlt(temp, rhs, lhs));
                valueStack.push(temp);
            }
            case 25 -> { // C -> E <= E : not (E2 < E1)
                final var rhs = (IRValue) valueStack.pop();
                final var lhs = (IRValue) valueStack.pop();
                final var t1 = IRVariable.temp();
                instructions.add(Instruction.createSlt(t1, rhs, lhs));
                final var t2 = IRVariable.temp();
                instructions.add(Instruction.createSeqz(t2, t1));
                valueStack.push(t2);
            }
            case 26 -> { // C -> E >= E : not (E1 < E2)
                final var rhs = (IRValue) valueStack.pop();
                final var lhs = (IRValue) valueStack.pop();
                final var t1 = IRVariable.temp();
                instructions.add(Instruction.createSlt(t1, lhs, rhs));
                final var t2 = IRVariable.temp();
                instructions.add(Instruction.createSeqz(t2, t1));
                valueStack.push(t2);
            }
            case 27 -> { // C -> E != E : 1 - (E1 == E2 ? 1 : 0)
                final var rhs = (IRValue) valueStack.pop();
                final var lhs = (IRValue) valueStack.pop();
                final var t1 = IRVariable.temp();
                instructions.add(Instruction.createSub(t1, lhs, rhs));
                final var t2 = IRVariable.temp();
                instructions.add(Instruction.createSeqz(t2, t1));
                final var t3 = IRVariable.temp();
                instructions.add(Instruction.createSub(t3, IRImmediate.of(1), t2));
                valueStack.push(t3);
            }
            case 28 -> { // C -> E == E
                final var rhs = (IRValue) valueStack.pop();
                final var lhs = (IRValue) valueStack.pop();
                final var t1 = IRVariable.temp();
                instructions.add(Instruction.createSub(t1, lhs, rhs));
                final var t2 = IRVariable.temp();
                instructions.add(Instruction.createSeqz(t2, t1));
                valueStack.push(t2);
            }

            // 赋值与返回：不压结果
            case 7 -> { // S -> id = E
                final var rhs = (IRValue) valueStack.pop();
                final var lhs = (IRVariable) valueStack.pop();
                instructions.add(Instruction.createMov(lhs, rhs));
            }
            case 8 -> { // S -> return E
                instructions.add(Instruction.createRet((IRValue) valueStack.pop()));
            }
            case 9 -> { // S -> id [ E ] = E
                valueStack.pop(); valueStack.pop(); valueStack.pop();
            }

            // if-else（回填法插入控制流指令）
            case 10 -> { // S -> if ( C ) Block
                final var thenBlockStart = (Integer) valueStack.pop();
                final var cond = (IRValue) valueStack.pop();
                valueStack.pop();
                final var elseLabel = nextLabel();
                final var endLabel = nextLabel();
                instructions.add(thenBlockStart, Instruction.createBZ(cond, elseLabel));
                instructions.add(Instruction.createJMP(endLabel));
                instructions.add(Instruction.createLabel(elseLabel));
                instructions.add(Instruction.createLabel(endLabel));
            }
            case 11 -> { // S -> if ( C ) Block else Block
                final var elseBlockStart = (Integer) valueStack.pop();
                final var thenBlockStart = (Integer) valueStack.pop();
                final var cond = (IRValue) valueStack.pop();
                valueStack.pop();
                final var elseLabel = nextLabel();
                final var endLabel = nextLabel();
                instructions.add(thenBlockStart, Instruction.createBZ(cond, elseLabel));
                final var jmpPos = elseBlockStart + 1; // BZ 插入导致偏移 +1
                instructions.add(jmpPos, Instruction.createJMP(endLabel));
                instructions.add(jmpPos + 1, Instruction.createLabel(elseLabel));
                instructions.add(Instruction.createLabel(endLabel));
            }

            default -> {}
        }
    }

    @Override
    public void whenAccept(Status currentStatus) {
    }

    public List<Instruction> getIR() {
        return instructions;
    }

    public void dumpIR(String path) {
        FileUtils.writeLines(path, getIR().stream().map(Instruction::toString).toList());
    }

    private String nextLabel() {
        return "L_" + labelCounter++;
    }
}
