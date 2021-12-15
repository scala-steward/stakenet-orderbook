package io.stakenet.orderbook.models.clients

class ClientVersion private (val components: List[Int]) extends Ordered[ClientVersion] {
  require(components.size == 4, "ClientVersion should have 4 components")

  override def compare(that: ClientVersion): Int = {
    components
      .zip(that.components)
      .find {
        case (component1, component2) => component1 != component2
      }
      .map {
        case (component1, component2) => component1.compare(component2)
      }
      .getOrElse(0)
  }

  override def toString: String = components.mkString(".")
}

object ClientVersion {

  def apply(version: String): Option[ClientVersion] = {
    val validVersionFormat = "[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+"

    if (version.matches(validVersionFormat)) {
      val versionComponents = version.split('.').map(_.toInt).toList

      Some(new ClientVersion(versionComponents))
    } else {
      None
    }
  }
}
