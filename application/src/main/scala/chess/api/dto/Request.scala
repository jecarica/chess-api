package chess.api.dto

import chess.domain.model.{PieceType, Position}
import sttp.tapir.Schema
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

object Request {

  case class AddPieceRequest(pieceType: PieceType, position: Position)

  object AddPieceRequest {
    import Schemas._
    implicit val jsonDecoder: JsonDecoder[AddPieceRequest] = DeriveJsonDecoder.gen[AddPieceRequest]
    implicit val jsonEncoder: JsonEncoder[AddPieceRequest] = DeriveJsonEncoder.gen[AddPieceRequest]
    implicit val schema: Schema[AddPieceRequest] = Schema.derived[AddPieceRequest]
      .description("Request to add a new piece to the board")
      .modify(_.pieceType)(_.description("Type of piece to add (Rook or Bishop)"))
      .modify(_.position)(_.description("Position where to add the piece"))
  }

  case class MovePieceRequest(to: Position)

  object MovePieceRequest {
    import Schemas._
    implicit val jsonDecoder: JsonDecoder[MovePieceRequest] = DeriveJsonDecoder.gen[MovePieceRequest]
    implicit val jsonEncoder: JsonEncoder[MovePieceRequest] = DeriveJsonEncoder.gen[MovePieceRequest]
    implicit val schema: Schema[MovePieceRequest] = Schema.derived[MovePieceRequest]
      .description("Request to move a piece to a new position")
      .modify(_.to)(_.description("Target position to move the piece to"))
  }

}
