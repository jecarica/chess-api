package chess

import zio.json._

case class Position(x: Int, y: Int)

object Position {
  implicit val positionEncoder: JsonEncoder[Position] = DeriveJsonEncoder.gen[Position]
  implicit val positionDecoder: JsonDecoder[Position] = DeriveJsonDecoder.gen[Position]
}

object Encoders {
  // Encoder for (String, Position) tuple
  implicit val stringPositionEncoder: JsonEncoder[(String, Position)] =
    JsonEncoder.tuple2[String, Position]
}
