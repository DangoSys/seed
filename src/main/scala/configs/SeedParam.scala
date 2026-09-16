package framework.seed.configs

case class SeedParam(
  xLen:      Int,
  vaddrBits: Int,
  pgIdxBits: Int) {
  require(xLen == 32)
  require(vaddrBits == 32)
  require(pgIdxBits == 12)
}

object SeedParam {

  def apply(): SeedParam = SeedParam(
    xLen = 32,
    vaddrBits = 32,
    pgIdxBits = 12
  )

}
