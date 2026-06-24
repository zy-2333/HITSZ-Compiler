package cn.edu.hitsz.compiler.lexer;

import cn.edu.hitsz.compiler.symtab.SymbolTable;
import cn.edu.hitsz.compiler.utils.FileUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

/**
 * TODO: 实验一: 实现词法分析
 * <br>
 * 你可能需要参考的框架代码如下:
 *
 * @see Token 词法单元的实现
 * @see TokenKind 词法单元类型的实现
 */
public class LexicalAnalyzer {

    private final SymbolTable symbolTable;
    private String sourceCode = "";
    private List<Token> tokens = List.of();

    public LexicalAnalyzer(SymbolTable symbolTable) {
        this.symbolTable = symbolTable;
    }

    /**
     * 从给予的路径中读取并加载文件内容
     *
     * @param path 路径
     */
    public void loadFile(String path) {
        sourceCode = FileUtils.readFile(path);
    }

    /**
     * 执行词法分析, 准备好用于返回的 token 列表 <br>
     * 需要维护实验一所需的符号表条目, 而得在语法分析中才能确定的符号表条目的成员可以先设置为 null
     */
    public void run() {
        final var result = new ArrayList<Token>();
        final var n = sourceCode.length();
        int i = 0;

        while (i < n) {
            final var ch = sourceCode.charAt(i);

            if (Character.isWhitespace(ch)) {
                i++;
                continue;
            }

            if (Character.isLetter(ch) || ch == '_') {
                int j = i + 1;
                while (j < n) {
                    final var c = sourceCode.charAt(j);
                    if (Character.isLetter(c) || Character.isDigit(c) || c == '_') {
                        j++;
                    } else {
                        break;
                    }
                }

                final var word = sourceCode.substring(i, j);
                if ("int".equals(word) || "return".equals(word) || "if".equals(word) || "else".equals(word)) {
                    result.add(Token.simple(word));
                } else {
                    result.add(Token.normal("id", word));
                    if (!symbolTable.has(word)) {
                        symbolTable.add(word);
                    }
                }
                i = j;
                continue;
            }

            if (Character.isDigit(ch)) {
                int j = i + 1;
                while (j < n && Character.isDigit(sourceCode.charAt(j))) {
                    j++;
                }

                result.add(Token.normal("IntConst", sourceCode.substring(i, j)));
                i = j;
                continue;
            }

            // 处理可能的双字符运算符
            if (i + 1 < n) {
                final var nextCh = sourceCode.charAt(i + 1);
                final var twoChar = String.valueOf(new char[]{ch, nextCh});
                
                switch (twoChar) {
                    case ">=" -> {
                        result.add(Token.simple(">="));
                        i += 2;
                        continue;
                    }
                    case "<=" -> {
                        result.add(Token.simple("<="));
                        i += 2;
                        continue;
                    }
                    case "==" -> {
                        result.add(Token.simple("=="));
                        i += 2;
                        continue;
                    }
                    case "!=" -> {
                        result.add(Token.simple("!="));
                        i += 2;
                        continue;
                    }
                    case "&&" -> {
                        result.add(Token.simple("&&"));
                        i += 2;
                        continue;
                    }
                    case "||" -> {
                        result.add(Token.simple("||"));
                        i += 2;
                        continue;
                    }
                }
            }

            switch (ch) {
                case '=' ->
                    result.add(Token.simple("="));
                case ',' ->
                    result.add(Token.simple(","));
                case ';' ->
                    result.add(Token.simple("Semicolon"));
                case '+' ->
                    result.add(Token.simple("+"));
                case '-' ->
                    result.add(Token.simple("-"));
                case '*' ->
                    result.add(Token.simple("*"));
                case '/' ->
                    result.add(Token.simple("/"));
                case '(' ->
                    result.add(Token.simple("("));
                case ')' ->
                    result.add(Token.simple(")"));
                case '[' ->
                    result.add(Token.simple("["));
                case ']' ->
                    result.add(Token.simple("]"));
                case '!' ->
                    result.add(Token.simple("!"));
                case '>' ->
                    result.add(Token.simple(">"));
                case '<' ->
                    result.add(Token.simple("<"));
                case '{' ->
                    result.add(Token.simple("{"));
                case '}' ->
                    result.add(Token.simple("}"));
                default ->
                    throw new RuntimeException("Illegal character in source: '" + ch + "'");
            }

            i++;
        }

        result.add(Token.eof());
        tokens = result;
    }

    /**
     * 获得词法分析的结果, 保证在调用了 run 方法之后调用
     *
     * @return Token 列表
     */
    public Iterable<Token> getTokens() {
        return tokens;
    }

    public void dumpTokens(String path) {
        FileUtils.writeLines(
                path,
                StreamSupport.stream(getTokens().spliterator(), false).map(Token::toString).toList()
        );
    }

}