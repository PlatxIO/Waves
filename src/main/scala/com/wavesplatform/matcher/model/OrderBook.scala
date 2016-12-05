package com.wavesplatform.matcher.model

import com.wavesplatform.matcher.model.MatcherModel.{Level, Price}

import scala.collection.+:
import scala.collection.immutable.TreeMap

case class OrderBook(bids: TreeMap[Price, Level[BuyLimitOrder]],
                     asks: TreeMap[Price, Level[SellLimitOrder]]) {
  def bestBid: Option[BuyLimitOrder] = bids.headOption.flatMap(_._2.headOption)
  def bestAsk: Option[SellLimitOrder] = asks.headOption.flatMap(_._2.headOption)

}



object OrderBook {
  val bidsOrdering: Ordering[Long] = new Ordering[Long] {
    def compare(x: Long, y: Long): Int = - Ordering.Long.compare(x, y)
  }

  val empty: OrderBook = OrderBook(TreeMap.empty[Price, Level[BuyLimitOrder]](bidsOrdering),
    TreeMap.empty[Price, Level[SellLimitOrder]])

  import Events._

  def matchOrder(ob: OrderBook, o: LimitOrder): Event = o match {
    case oo: BuyLimitOrder =>
      ob.bestAsk.exists(oo.price >= _.price) match {
        case true => OrderExecuted(o, ob.bestAsk.get)
        case false => OrderAdded(oo)
      }
    case oo: SellLimitOrder =>
      ob.bestBid.exists(oo.price <= _.price) match {
        case true => OrderExecuted(o, ob.bestBid.get)
        case false => OrderAdded(oo)
      }
  }

  def matchOrder1(ob: OrderBook, o: LimitOrder): (OrderBook, Option[LimitOrder], Long) = o match {
    case oo: BuyLimitOrder =>
      ob.bestAsk.exists(oo.price >= _.price) match {
        case true => executeBuy(ob, ob.asks.head, oo)
        case false =>
          val orders = ob.bids.getOrElse(oo.price, Vector.empty)
          (ob.copy(bids = ob.bids + (oo.price -> (orders :+ oo))), None, oo.amount)
      }
    case oo: SellLimitOrder =>
      ob.bestBid.exists(oo.price <= _.price) match {
        case true => executeSell(ob, ob.bids.head, oo)
        case false =>
          val orders = ob.asks.getOrElse(oo.price, Vector.empty)
          (ob.copy(asks = ob.asks + (oo.price -> (orders :+ oo))), None, oo.amount)
      }
  }

  def updateExecutedBuy(ob: OrderBook, o: BuyLimitOrder, r: Long) = ob.bids.get(o.price) match {
    case Some(l) =>
      val (l1, l2) = l.span(_ != o)
      val ll = if (r > 0) (l1 :+ o.copy(amount = r)) ++ l2.tail else l1 ++ l2.tail
      ob.copy(bids = if (ll.isEmpty) ob.bids - o.price else ob.bids + (o.price -> ll))
  }

  def updateExecutedSell(ob: OrderBook, o: SellLimitOrder, r: Long) = ob.asks.get(o.price) match {
    case Some(l) =>
      val (l1, l2) = l.span(_ != o)
      val ll = if (r > 0) (l1 :+ o.copy(amount = r)) ++ l2.tail else l1 ++ l2.tail
      ob.copy(asks = if (ll.isEmpty) ob.asks - o.price else ob.asks + (o.price -> ll))
  }

  def updateState(ob: OrderBook, event: Event): OrderBook = event match {
    case OrderAdded(o: BuyLimitOrder) =>
      val orders = ob.bids.getOrElse(o.price, Vector.empty)
      ob.copy(bids = ob.bids + (o.price -> (orders :+ o)))
    case OrderAdded(o: SellLimitOrder) =>
      val orders = ob.asks.getOrElse(o.price, Vector.empty)
      ob.copy(asks = ob.asks + (o.price -> (orders :+ o)))
    case e@OrderExecuted(_, c: BuyLimitOrder) => updateExecutedBuy(ob, c, e.counterRemaining)
    case e@OrderExecuted(_, c: SellLimitOrder) => updateExecutedSell(ob, c, e.counterRemaining)

  }


  def executeBuy(ob: OrderBook, level: (Price, Level[SellLimitOrder]),
                 o: BuyLimitOrder): (OrderBook, Option[SellLimitOrder], Long) = level._2 match {
    case h +: t if h.amount > o.amount =>
      (ob.copy(asks = ob.asks + (level._1 -> (h.copy(amount = h.amount - o.amount) +: t))),
        Some(h.copy(amount = o.amount)), 0L)
    case h +: t if t.nonEmpty =>
      (ob.copy(asks = ob.asks + (level._1 -> t)), Some(h), o.amount - h.amount)
    case h +: _ =>
      (ob.copy(asks = ob.asks - level._1), Some(h), o.amount - h.amount)
    }


  def executeSell(ob: OrderBook, level: (Price, Level[BuyLimitOrder]),
                 o: SellLimitOrder): (OrderBook, Option[BuyLimitOrder], Long) = level._2 match {
    case h +: t if h.amount > o.amount =>
      (ob.copy(bids = ob.bids + (level._1 -> (h.copy(amount = h.amount - o.amount) +: t))),
        Some(h.copy(amount = o.amount)), 0L)
    case h +: t if t.nonEmpty =>
      (ob.copy(bids = ob.bids + (level._1 -> t)), Some(h), o.amount - h.amount)
    case h +: _ =>
      (ob.copy(bids = ob.bids - level._1), Some(h), o.amount - h.amount)
  }

}

/*case class OrderBook(assetPair: AssetPair, priceOrders: TreeMap[Long, Level]) {

  def add(order: OrderItem) {
    val prevLevel = priceOrders.getOrElse(order.price, Level(order.price))
    copy(priceOrders = priceOrders + (order.price -> prevLevel.copy(orders = prevLevel.orders :+ order)))
  }


  def matchOrder(order: OrderItem): Option[(OrderBook, OrderItem, OrderItem)] = {
    priceOrders.headOption.flatMap { case (bestPrice, bestLevel) =>
      if (priceOrders.ordering.compare(bestPrice, order.price) <= 0) {
        val (level, executed, remaining) = bestLevel.executeOrder(order)
        val newTree = if (level.isEmpty) priceOrders - bestPrice else priceOrders + (bestPrice -> level)
        val ob = copy(priceOrders = newTree)
        Some(this.copy(priceOrders = newTree), executed, order.copy(amount = remaining))
      } else None
    }
  }

  /*def runMatching(order: OrderItem, n: Int = 1): (OrderBook, Seq[OrderItem], Long) = {
    priceOrders.headOption.map { bestOrders =>
      val bestLevel = priceOrders(bestOrders.last)

      if (priceOrders.ordering.compare(bestLevel.price, order.price) <= 0) {
        val (level, executed, remaining) = bestLevel.execute(order)

        if (remaining > 0) {
          val (remainingExecuted, nextRemaining) = runMatching(order.copy(amount = remaining), n + 1)
          (executed ++ remainingExecuted, nextRemaining)
        } else (executed, remaining)
      } else (Seq.empty, order.amount)
    } else
    {
      (Seq.empty, order.amount)
    }
  }*/


  /*def removeOrder(order: OrderItem): Unit = {
    val prevLevel = priceOrders.getOrDefault(order.price, Level(order.price))

    val (before, after) = prevLevel.orders.span(_.order != order.order)
    if (after.nonEmpty) {
      val prevOrder = after.head
      val newOrders = if (order.amount < prevOrder.amount) {
        (before :+ prevOrder.copy(amount = prevOrder.amount - order.amount)) ++ after.tail
      } else before ++ after.tail

      if (newOrders.isEmpty) priceOrders.remove(order.price)
      else priceOrders.put(order.price, prevLevel.copy(orders = newOrders))
    }
  }*/

  private def delete(orders: Level) {
    priceOrders.remove(orders.price)
  }

  def flattenOrders: Seq[OrderItem] = {
    priceOrders.flatMap(_._2.orders).toSeq
  }

  def take(depth: Int): Seq[LevelAgg] = {
    priceOrders.take(depth).values.map(_.getAgg).toList
  }

}

object OrderBook {
  def empty(assetPair: AssetPair, ordering: Ordering[Long]): OrderBook = {
    OrderBook(assetPair, TreeMap.empty[Long, Level](ordering))
  }
}*/