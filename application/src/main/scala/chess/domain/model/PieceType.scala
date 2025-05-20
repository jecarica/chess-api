package chess.domain.model

import enumeratum.{Enum, EnumEntry}
import sttp.tapir.Schema
import zio.json.{JsonDecoder, JsonEncoder}

sealed trait PieceType extends EnumEntry

object PieceType extends Enum[PieceType] {
  case object Rook extends PieceType
  case object Bishop extends PieceType

  val values = findValues

  implicit val schema: Schema[PieceType] = Schema.string[PieceType]
  implicit val jsonEncoder: JsonEncoder[PieceType] = JsonEncoder[String].contramap(_.entryName)
  implicit val jsonDecoder: JsonDecoder[PieceType] = JsonDecoder[String].mapOrFail { str =>
    values.find(_.entryName == str)
      .toRight(s"Invalid piece type: $str")
  }
}