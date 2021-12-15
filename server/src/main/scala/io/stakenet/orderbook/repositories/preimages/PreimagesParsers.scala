package io.stakenet.orderbook.repositories.preimages

import anorm.Column
import io.stakenet.orderbook.models.Preimage

private[preimages] object PreimagesParsers {

  implicit val preimageColumn: Column[Preimage] = Column.columnToByteArray.map(_.toVector).map(Preimage.apply)
}
