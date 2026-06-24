# 编译原理课程实验

哈尔滨工业大学（威海）2026 秋季学期编译原理课程实验。

本项目实现了一个简易编译器，能够将自定义的类 C 语言编译为 RISC-V 汇编代码。编译器采用经典的多阶段架构，完整覆盖了从源代码到目标代码的完整编译流程。

## 项目结构

```
.
├── src/                              # 源代码
│   └── cn/edu/hitsz/compiler/
│       ├── Main.java                 # 主程序入口
│       ├── MainLab2Advanced.java     # 实验二选做入口
│       ├── lexer/                    # 词法分析器
│       │   ├── LexicalAnalyzer.java
│       │   ├── Token.java
│       │   └── TokenKind.java
│       ├── parser/                   # 语法分析器
│       │   ├── SyntaxAnalyzer.java
│       │   ├── IRGenerator.java      # 中间代码生成
│       │   ├── SemanticAnalyzer.java # 语义分析
│       │   ├── ProductionCollector.java
│       │   ├── ActionObserver.java
│       │   └── table/               # LR 分析表
│       │       ├── LRTable.java
│       │       ├── TableGenerator.java
│       │       ├── TableLoader.java
│       │       ├── GrammarInfo.java
│       │       ├── Production.java
│       │       ├── NonTerminal.java
│       │       ├── Status.java
│       │       └── Action.java
│       ├── ir/                       # 中间表示
│       │   ├── Instruction.java
│       │   ├── InstructionKind.java
│       │   ├── IRValue.java
│       │   ├── IRVariable.java
│       │   └── IRImmediate.java
│       ├── asm/                      # 目标代码生成
│       │   └── AssemblyGenerator.java
│       ├── symtab/                   # 符号表
│       └── utils/                    # 工具类
├── data/
│   ├── in/                           # 输入文件
│   │   ├── grammar.txt               # 文法定义
│   │   ├── input_code.txt            # 基础测试用例
│   │   ├── input_code_advanced.txt   # 进阶测试用例（if-else）
│   │   └── reg-alloc.txt             # 寄存器分配测试用例
│   ├── out/                          # 输出结果
│   └── std/                          # 标准答案
├── scripts/                          # 辅助脚本
│   ├── check-result.py               # 结果检查
│   ├── diff.py                       # 差异比较
│   └── make-template.py              # 模板生成
└── rars.jar                          # RISC-V 模拟器
```

## 编译流程

本编译器采用经典的多阶段编译架构：

```
源代码 → [词法分析] → Token 流
           ↓
       [语法分析] → 语法树 / 规约序列
           ↓
       [语义分析] → 符号表 + 类型检查
           ↓
     [中间代码生成] → 三地址码 IR
           ↓
     [目标代码生成] → RISC-V 汇编
```

### 1. 词法分析 (`lexer/`)

将源代码字符流转换为 Token 序列。支持的 Token 类型包括：

- 关键字：`int`、`return`、`if`、`else`
- 标识符：变量名
- 常量：整数常量
- 运算符：`+`、`-`、`*`、`/`、`=`、`<`、`>`、`<=`、`>=`、`==`、`!=`
- 分隔符：`(`、`)`、`[`、`]`、`{`、`}`、`;`

### 2. 语法分析 (`parser/`)

采用 LR(1) 分析法，从文法定义自动生成分析表。支持的语法结构：

- 变量声明：`int id;`
- 赋值语句：`id = E;`
- 算术表达式：`+`、`-`、`*`、`/`
- 返回语句：`return E;`
- 分支语句：`if (C) Block`、`if (C) Block else Block`
- 比较表达式：`<`、`>`、`<=`、`>=`、`==`、`!=`
- 数组操作：`int id[N]`、`id[E] = E`

### 3. 语义分析

在语法分析过程中同步进行：
- 符号表管理：记录变量名、类型、作用域
- 类型检查：确保表达式类型正确
- 语义错误报告

### 4. 中间代码生成 (`IRGenerator`)

生成三地址码的中间表示，支持的 IR 指令：

| 指令 | 格式 | 说明 |
|------|------|------|
| MOV | `MOV, x, y` | 赋值 |
| ADD | `ADD, x, y, z` | 加法 |
| SUB | `SUB, x, y, z` | 减法 |
| MUL | `MUL, x, y, z` | 乘法 |
| DIV | `DIV, x, y, z` | 除法 |
| RET | `RET, , x` | 返回 |
| SLT | `SLT, x, y, z` | 小于比较 |
| SEQZ | `SEQZ, x, y` | 判零 |
| BZ | `BZ, x, label` | 条件跳转 |
| JMP | `JMP, , label` | 无条件跳转 |
| LABEL | `LABEL, , label` | 标号定义 |

### 5. 目标代码生成 (`AssemblyGenerator`)

将 IR 转换为 RISC-V 汇编，包括：

- **IR 规范化**：常量折叠、立即数位置调整
- **寄存器分配**：基于活跃变量分析的寄存器分配策略
- **寄存器溢出**：当寄存器不足时，采用 Belady 最优策略将变量溢出到内存

使用的寄存器：
- 临时寄存器：`t0` - `t6`（7 个）
- 返回值寄存器：`a0`

## 实验内容

### 实验一：词法分析

实现词法分析器，将源代码转换为 Token 序列。

**测试用例** (`input_code.txt`)：
```c
int result;
int a;
int b;
int c;
a = 8;
b = 5;
c = 3 - a;
result = a * b - ( 3 + b ) * ( c - a );
return result;
```

### 实验二：语法分析

实现 LR(1) 语法分析器，支持基础文法和扩展文法。

**扩展文法** (`grammar.txt`)：
- if / if-else 分支语句
- 比较运算：`<`、`>`、`<=`、`>=`、`==`、`!=`
- 除法运算：`/`
- 数组声明与访问

### 实验三：语义分析与中间代码生成

实现语义检查和中间代码生成。

**选做功能**：支持 if-else 的中间代码生成，使用回填法翻译条件跳转。

**测试用例** (`input_code_advanced.txt`)：
```c
int x;
int y;
int z;
int w;
x = 10;
y = 2;
z = 4;
w = x / y;
if (w > z) {
    w = z;
};
if (w <= x) {
    w = x - w;
} else {
    w = w + x;
};
return w;
```
预期结果：`a0 = 6`

### 实验四：目标代码生成

实现 RISC-V 汇编代码生成和寄存器分配。

**选做功能**：完备寄存器分配，当寄存器不足时采用 Belady 最优策略进行寄存器溢出。

**测试用例** (`reg-alloc.txt`)：使用 40 个变量（20 个 f 类 + 20 个 s 类）计算类斐波那契数列。
预期结果：`a0 = 10945`

## 构建与运行

### 环境要求

- JDK 17+
- Python 3.x（用于检查脚本）
- RARS（RISC-V 模拟器，已包含在项目中）

### 编译运行

```bash
# 编译
javac -d out src/cn/edu/hitsz/compiler/**/*.java

# 运行
java -cp out cn.edu.hitsz.compiler.Main
```

### 检查结果

```bash
# 检查基础功能（实验 1-3）
python scripts/check-result.py 3 data/std data/out

# 检查实验四（使用 RARS 模拟执行汇编）
python scripts/check-result.py 4 data/std data/out
```

## 输出说明

编译器会在 `data/out/` 目录下生成以下文件：

| 文件 | 说明 |
|------|------|
| `token.txt` | 词法分析结果 |
| `old_symbol_table.txt` | 词法分析后的符号表 |
| `parser_list.txt` | 规约序列 |
| `new_symbol_table.txt` | 语法分析后的符号表 |
| `intermediate_code.txt` | 中间代码（IR） |
| `ir_emulate_result.txt` | IR 模拟执行结果 |
| `assembly_language.asm` | RISC-V 汇编代码 |

## 核心算法

### 寄存器分配策略

1. **空闲寄存器分配**：优先使用空闲寄存器
2. **死亡变量回收**：回收不再使用的变量所占寄存器
3. **Belady 溢出策略**：当寄存器全满时，选择最远将来才使用的变量溢出到内存

### IR 规范化

在生成汇编前对 IR 进行规范化处理：
- 双立即数运算 → 常量折叠
- 左立即数 ADD → 交换操作数
- 左立即数 SUB/MUL → 插入临时变量

## 参考资料

- RISC-V 指令集手册
- 龙书（Compilers: Principles, Techniques, and Tools）
- RARS 模拟器：[GitHub](https://github.com/TheThirdOne/rars)

## 许可证

本项目为课程实验代码，仅供学习参考。
