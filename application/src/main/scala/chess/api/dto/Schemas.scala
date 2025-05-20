package chess.api.dto

import chess.domain.error.ChessError.{InvalidMove, InvalidPieceType, InvalidPosition, PieceNotFound, PositionOccupied}
import chess.domain.model.{Piece, Position}
import sttp.tapir.Schema

object Schemas {
  // Define Position schema manually since it's a case class with primitive types
  implicit val positionSchema: Schema[Position] = Schema.derived[Position]
    .description("Chess board position")
    .modify(_.x)(_.description("X coordinate (1-8)"))
    .modify(_.y)(_.description("Y coordinate (1-8)"))

  // Define Piece schema
  implicit val pieceSchema: Schema[Piece] = Schema.derived[Piece]
    .description("Chess piece")
    .modify(_.id)(_.description("Unique identifier of the piece"))

  // Define Map schema
  implicit val mapPositionPieceSchema: Schema[Map[Position, Piece]] =
    Schema.schemaForMap[Position, Piece](pos => s"${pos.x},${pos.y}")
      .description("Map of positions to pieces on the board")

  // Schemas for error types
  implicit val invalidPositionSchema: Schema[InvalidPosition] = Schema.derived[InvalidPosition]
  implicit val invalidPieceTypeSchema: Schema[InvalidPieceType] = Schema.derived[InvalidPieceType]
  implicit val positionOccupiedSchema: Schema[PositionOccupied] = Schema.derived[PositionOccupied]
  implicit val pieceNotFoundSchema: Schema[PieceNotFound] = Schema.derived[PieceNotFound]
  implicit val invalidMoveSchema: Schema[InvalidMove] = Schema.derived[InvalidMove]
}
