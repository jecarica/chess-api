package chess.api.dto

import chess.domain.error.ChessError.{InvalidMove, InvalidPieceType, InvalidPosition, PieceNotFound, PositionOccupied}
import chess.domain.model.{Piece, Position}
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder, JsonFieldDecoder, JsonFieldEncoder}

object JsonCodecs {
  implicit val positionFieldDecoder: JsonFieldDecoder[Position] = JsonFieldDecoder[String].map { str =>
    val parts = str.split(",")
    Position(parts(0).toInt, parts(1).toInt)
  }

  implicit val positionFieldEncoder: JsonFieldEncoder[Position] = JsonFieldEncoder[String].contramap { pos =>
    s"${pos.x},${pos.y}"
  }

  implicit val mapJsonDecoder: JsonDecoder[Map[Position, Piece]] = JsonDecoder.map[Position, Piece]
  implicit val mapJsonEncoder: JsonEncoder[Map[Position, Piece]] = JsonEncoder.map[Position, Piece]

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
}