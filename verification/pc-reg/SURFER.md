# 用 Surfer 查看 PCReg 波形

本页对应 `PCRegChiselSimSpec` 生成的波形。文件扩展名是 **`.vcd`**
（Value Change Dump），不是 `.cvd`。Surfer 负责查看波形；测试通过与否以
ChiselSim 的检查结果为准。

## 1. 找到并打开文件

从 Seed 仓库根目录运行：

```bash
find build/chiselsim/PCRegChiselSimSpec -name trace.vcd -print
surfer "$(find build/chiselsim/PCRegChiselSimSpec -name trace.vcd -print -quit)"
```

如果 `find` 没有输出，先运行一次测试生成 VCD：

```bash
mill pcRegVerification.test.testOnly \
  framework.seed.verification.PCRegChiselSimSpec -- -DemitVcd=1
```

也可以在 Surfer 中用 **File → Open** 选取 `trace.vcd`。当前测试的波形位于
`build/chiselsim/PCRegChiselSimSpec/PCReg/<测试名称>/workdir-verilator/trace.vcd`；
`<测试名称>` 是 ChiselSim 按测试描述生成的目录名。

## 2. 把信号加入波形区

打开文件后，中央区域仍可能是空白的；这表示尚未选择显示信号。

1. 在左侧 **Scopes** 展开 `TOP`，选择 `svsimTestbench`。
2. 在左下 **Variables** 清空过滤框，找到下面的信号。选中信号后点击
   Variables 标题右侧的 **`+`**，或将信号拖到中央波形区。
3. 按下表顺序加入信号，便于对照时钟沿。

| 信号 | 作用 |
| --- | --- |
| `clock` | 找到每次上升沿 |
| `reset` | 复位是否有效 |
| `io_redirect_valid` | 是否跳转 |
| `io_redirect_bits` | 跳转目标 |
| `io_advance` | 是否顺序递增 |
| `io_pc` | PCReg 的输出 |

`TOP → svsimTestbench → dut` 下也有 DUT 信号和内部寄存器 `pcReg`。
初次查看建议先用 `svsimTestbench` 的接口信号，避免把同名信号重复加入。

## 3. 看清前几个测试周期

本测试后面还有 1,000 个随机周期。若 Surfer 一次显示完整文件，前面的
定向场景会挤在左边，看起来像没有变化。按 **空格**打开命令栏，输入
`zoom_to 0ns 20ns` 后回车，即可放大前 20 ns；输入 `zoom_fit` 可回到全程。
若地址以二进制显示，选中 `io_pc` 波形行，按空格输入
`item_set_format hex` 后回车。

在上升沿后检查：

- 复位后 `io_pc = 0x80000000`；
- `io_advance = 1` 且没有跳转时，`io_pc` 加 4；
- `io_redirect_valid = 1` 时，`io_pc` 变为 `io_redirect_bits`，即使
  `io_advance` 同时为 1；
- 定向测试后段会从 `0xfffffffffffffffc` 加 4 回绕到 0。

## 导入后仍看不到波形

- 确认打开的是 `trace.vcd`，不是 `compilation-log.txt` 或其他生成文件。
- 确认已在 **Scopes** 选中 `svsimTestbench`，且 **Variables** 过滤框为空。
- 确认已经用 **`+`** 将信号加入中央区域；仅选中 scope 不会自动显示波形。
- 如果波形行出现但变化很密，执行 `zoom_to 0ns 20ns`。

另一个 `build/verification/<运行目录>/baseline/wave.vcd` 是**断言仿真**
生成的文件；本页使用的是 **ChiselSim** 的 `trace.vcd`。Surfer 的命令和
缩放操作可查阅[官方命令文档](https://docs.surfer-project.org/book/commands/index.html)。
