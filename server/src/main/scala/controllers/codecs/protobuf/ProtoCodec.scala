package controllers.codecs.protobuf

trait ProtoCodec[Proto, Model] {
  def decode(proto: Proto): Model

  def encode(model: Model): Proto
}
