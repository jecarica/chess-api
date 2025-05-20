package chess.domain.error

import chess.api.Schemas
import chess.domain.model.Position
import sttp.tapir.Schema
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

sealed trait ChessError extends Throwable {
  def message: String
}

object ChessError {
  import Schemas._
  case class InvalidPosition(position: Position, reason: String) extends ChessError {
    override def message: String = s"Invalid position $position: $reason"
  }

  case class InvalidPieceType(pieceType: String) extends ChessError {
    override def message: String = s"Invalid piece type: $pieceType"
  }

  case class PositionOccupied(position: Position) extends ChessError {
    override def message: String = s"Position $position is already occupied"
  }

  case class PieceNotFound(id: String) extends ChessError {
    override def message: String = s"Piece with id $id not found"
  }

  case class InvalidMove(from: Position, to: Position, reason: String) extends ChessError {
    override def message: String = s"Invalid move from $from to $to: $reason"
  }

  // JSON encoders/decoders for error types
  implicit val invalidPositionEncoder: JsonEncoder[InvalidPosition] = DeriveJsonEncoder.gen[InvalidPosition]
  implicit val invalidPositionDecoder: JsonDecoder[InvalidPosition] = DeriveJsonDecoder.gen[InvalidPosition]

  implicit val invalidPieceTypeEncoder: JsonEncoder[InvalidPieceType] = DeriveJsonEncoder.gen[InvalidPieceType]
  implicit val invalidPieceTypeDecoder: JsonDecoder[InvalidPieceType] = DeriveJsonDecoder.gen[InvalidPieceType]

  implicit val positionOccupiedEncoder: JsonEncoder[PositionOccupied] = DeriveJsonEncoder.gen[PositionOccupied]
  implicit val positionOccupiedDecoder: JsonDecoder[PositionOccupied] = DeriveJsonDecoder.gen[PositionOccupied]

  implicit val pieceNotFoundEncoder: JsonEncoder[PieceNotFound] = DeriveJsonEncoder.gen[PieceNotFound]
  implicit val pieceNotFoundDecoder: JsonDecoder[PieceNotFound] = DeriveJsonDecoder.gen[PieceNotFound]

  implicit val invalidMoveEncoder: JsonEncoder[InvalidMove] = DeriveJsonEncoder.gen[InvalidMove]
  implicit val invalidMoveDecoder: JsonDecoder[InvalidMove] = DeriveJsonDecoder.gen[InvalidMove]

  // Schema for error types
  implicit val invalidPositionSchema: Schema[InvalidPosition] = Schema.derived[InvalidPosition]
  implicit val invalidPieceTypeSchema: Schema[InvalidPieceType] = Schema.derived[InvalidPieceType]
  implicit val positionOccupiedSchema: Schema[PositionOccupied] = Schema.derived[PositionOccupied]
  implicit val pieceNotFoundSchema: Schema[PieceNotFound] = Schema.derived[PieceNotFound]
  implicit val invalidMoveSchema: Schema[InvalidMove] = Schema.derived[InvalidMove]
}