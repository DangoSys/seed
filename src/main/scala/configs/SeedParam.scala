package framework.seed.configs

case class SeedParam(
  xLen:      Int,
  vaddrBits: Int,
  pgIdxBits: Int) {
  require(xLen == 64)
  require(vaddrBits == 64)
  require(pgIdxBits == 12)
}

object SeedParam {

  def apply(): SeedParam = SeedParam(
    xLen = 64,
    vaddrBits = 64,
    pgIdxBits = 12
  )

}
