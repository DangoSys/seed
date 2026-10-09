package framework.seed.pipeline

import chisel3.util.BitPat

/** Named 32-bit instruction encodings. Question marks match operand fields. */
object Instruction {
  private def opcode(op: String): BitPat = BitPat("b" + ("?" * 25) + op)
  private def funct3(op: String, f3: String): BitPat =
    BitPat("b" + ("?" * 17) + f3 + ("?" * 5) + op)
  private def funct7(op: String, f3: String, f7: String): BitPat =
    BitPat("b" + f7 + ("?" * 10) + f3 + ("?" * 5) + op)
  private def shift6(op: String, f3: String, f6: String): BitPat =
    BitPat("b" + f6 + ("?" * 11) + f3 + ("?" * 5) + op)

  val INST_LUI   = opcode("0110111")
  val INST_AUIPC = opcode("0010111")
  val INST_JAL   = opcode("1101111")
  val INST_JALR  = funct3("1100111", "000")

  val INST_BEQ  = funct3("1100011", "000")
  val INST_BNE  = funct3("1100011", "001")
  val INST_BLT  = funct3("1100011", "100")
  val INST_BGE  = funct3("1100011", "101")
  val INST_BLTU = funct3("1100011", "110")
  val INST_BGEU = funct3("1100011", "111")

  val INST_LB  = funct3("0000011", "000")
  val INST_LH  = funct3("0000011", "001")
  val INST_LW  = funct3("0000011", "010")
  val INST_LD  = funct3("0000011", "011")
  val INST_LBU = funct3("0000011", "100")
  val INST_LHU = funct3("0000011", "101")
  val INST_LWU = funct3("0000011", "110")

  val INST_SB = funct3("0100011", "000")
  val INST_SH = funct3("0100011", "001")
  val INST_SW = funct3("0100011", "010")
  val INST_SD = funct3("0100011", "011")

  val INST_ADDI  = funct3("0010011", "000")
  val INST_SLTI  = funct3("0010011", "010")
  val INST_SLTIU = funct3("0010011", "011")
  val INST_XORI  = funct3("0010011", "100")
  val INST_ORI   = funct3("0010011", "110")
  val INST_ANDI  = funct3("0010011", "111")
  val INST_SLLI  = shift6("0010011", "001", "000000")
  val INST_SRLI  = shift6("0010011", "101", "000000")
  val INST_SRAI  = shift6("0010011", "101", "010000")

  val INST_ADD  = funct7("0110011", "000", "0000000")
  val INST_SUB  = funct7("0110011", "000", "0100000")
  val INST_SLL  = funct7("0110011", "001", "0000000")
  val INST_SLT  = funct7("0110011", "010", "0000000")
  val INST_SLTU = funct7("0110011", "011", "0000000")
  val INST_XOR  = funct7("0110011", "100", "0000000")
  val INST_SRL  = funct7("0110011", "101", "0000000")
  val INST_SRA  = funct7("0110011", "101", "0100000")
  val INST_OR   = funct7("0110011", "110", "0000000")
  val INST_AND  = funct7("0110011", "111", "0000000")

  val INST_MUL    = funct7("0110011", "000", "0000001")
  val INST_MULH   = funct7("0110011", "001", "0000001")
  val INST_MULHSU = funct7("0110011", "010", "0000001")
  val INST_MULHU  = funct7("0110011", "011", "0000001")
  val INST_DIV    = funct7("0110011", "100", "0000001")
  val INST_DIVU   = funct7("0110011", "101", "0000001")
  val INST_REM    = funct7("0110011", "110", "0000001")
  val INST_REMU   = funct7("0110011", "111", "0000001")

  val INST_ADDIW = funct3("0011011", "000")
  val INST_SLLIW = funct7("0011011", "001", "0000000")
  val INST_SRLIW = funct7("0011011", "101", "0000000")
  val INST_SRAIW = funct7("0011011", "101", "0100000")

  val INST_ADDW  = funct7("0111011", "000", "0000000")
  val INST_SUBW  = funct7("0111011", "000", "0100000")
  val INST_SLLW  = funct7("0111011", "001", "0000000")
  val INST_SRLW  = funct7("0111011", "101", "0000000")
  val INST_SRAW  = funct7("0111011", "101", "0100000")
  val INST_MULW  = funct7("0111011", "000", "0000001")
  val INST_DIVW  = funct7("0111011", "100", "0000001")
  val INST_DIVUW = funct7("0111011", "101", "0000001")
  val INST_REMW  = funct7("0111011", "110", "0000001")
  val INST_REMUW = funct7("0111011", "111", "0000001")
}
