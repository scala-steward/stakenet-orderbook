package io.stakenet.orderbook.models

/** The package has models related to orders that depend on a trading pair.
  *
  * The goal is to not require runtime checks for details that are tied to the same trading pair, for example, an order
  * pair that are matched must belong to the same trading pair, and this can be verified by the compiler.
  *
  * While this approach works, it is complex to use, Scala doesn't support path-dependant types on the classes
  * constructor which requires the traits in the package.
  *
  * A [[io.stakenet.orderbook.models.trading.Trade]] holds two matched orders, which belongs to the same pair.
  *
  * Be careful when dealing with this package, and pay attention to the types and how it is used in the code that works.
  */
package object trading
