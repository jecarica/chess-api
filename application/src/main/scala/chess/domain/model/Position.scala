package chess.domain.model

import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

case class Position(x: Int, y: Int)

object Position {
  implicit val jsonDecoder: JsonDecoder[Position] = DeriveJsonDecoder.gen[Position]
  implicit val jsonEncoder: JsonEncoder[Position] = DeriveJsonEncoder.gen[Position]
}

