package framework.seed.configs

case class SeedParam(
  xLen:      Int,
  vaddrBits: Int,
  pgIdxBits: Int,
  resetPc:   BigInt) {
  require(xLen == 64)
  require(vaddrBits == 64)
  require(pgIdxBits == 12)
  require(resetPc >= 0 && resetPc.bitLength <= vaddrBits)
}

object SeedParam {

  def apply(): SeedParam = SeedParam(
    xLen = 64,
    vaddrBits = 64,
    pgIdxBits = 12,
    resetPc = BigInt("80000000", 16)
  )

}
