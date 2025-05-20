package chess.domain.error

import chess.domain.model.Position
import sttp.tapir.Schema
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

sealed trait ChessError extends Throwable {
  def message: String
}

object ChessError {
  import chess.api.dto.Schemas._
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

}