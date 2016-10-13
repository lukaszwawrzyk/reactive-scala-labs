package auction.actors.common

object Auction {
  case object Start
  case object Relist
  case class Bid(value: BigDecimal)
}