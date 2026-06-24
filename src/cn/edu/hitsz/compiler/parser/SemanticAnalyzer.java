package cn.edu.hitsz.compiler.parser;

import cn.edu.hitsz.compiler.lexer.Token;
import cn.edu.hitsz.compiler.parser.table.Production;
import cn.edu.hitsz.compiler.parser.table.Status;
import cn.edu.hitsz.compiler.symtab.SourceCodeType;
import cn.edu.hitsz.compiler.symtab.SymbolTable;

import java.util.Stack;

/**
 * 语义分析器（实验三），在变量声明时更新符号表中的类型信息。
 */
public class SemanticAnalyzer implements ActionObserver {

    private SymbolTable symbolTable;

    // 类型栈：存放 id 名字（String）和类型（SourceCodeType）
    private final Stack<Object> typeStack = new Stack<>();

    @Override
    public void setSymbolTable(SymbolTable table) {
        // 保存符号表引用，后续在声明产生式规约时更新符号表
        this.symbolTable = table;
    }

    @Override
    public void whenShift(Status currentStatus, Token currentToken) {
        // 只有标识符 "id" 有语义值，将其名字字符串压入类型栈
        // 其他终结符（int, return, =, +, -, *, ;, (, ), {, }, [, ] 等）无语义值，不压栈
        if (currentToken.getKind().getIdentifier().equals("id")) {
            typeStack.push(currentToken.getText());
        }
    }

    @Override
    public void whenReduce(Status currentStatus, Production production) {
        switch (production.index()) {
            case 5 -> { // D -> int
                // 将 Int 类型压入类型栈，供后续 S -> D id 规约时使用
                typeStack.push(SourceCodeType.Int);
            }
            case 4 -> { // S -> D id
                // 弹出 id 的名字（String）—— 先入后出，id 在 D 之后入栈，所以 id 先出
                final var idName = (String) typeStack.pop();
                // 弹出 D 的类型（SourceCodeType）
                final var type = (SourceCodeType) typeStack.pop();
                // 将变量的类型信息写入符号表
                symbolTable.get(idName).setType(type);
            }
            // 其他产生式无需语义动作，默认不处理
            default -> {
                // 传递型产生式或其他无需语义动作的产生式，不做任何操作
            }
        }
    }

    @Override
    public void whenAccept(Status currentStatus) {
        // 接受时无需额外动作，符号表已在各声明产生式规约时更新完毕
    }
}
