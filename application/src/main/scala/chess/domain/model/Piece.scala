package chess.domain.model

import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

sealed trait Piece {
  def moveIsValid(from: Position, to: Position, gameState: Map[Position, Piece]): Boolean
  def id: String
}

// Rook piece, moves horizontally or vertically
case class Rook(id: String) extends Piece {
  override def moveIsValid(from: Position, to: Position, gameState: Map[Position, Piece]): Boolean = {
    val isStraightMove = from.x == to.x || from.y == to.y

    val isPathClear = if (from.x == to.x) {
      // Moving along the same file (vertical move)
      val yRange = if (from.y < to.y) (from.y + 1) until to.y else (to.y + 1) until from.y
      yRange.forall(y => !gameState.contains(Position(from.x, y)))
    } else {
      // Moving along the same rank (horizontal move)
      val xRange = if (from.x < to.x) (from.x + 1) until to.x else (to.x + 1) until from.x
      xRange.forall(x => !gameState.contains(Position(x, from.y)))
    }

    isStraightMove && isPathClear && !gameState.contains(to)
  }
}

// Bishop piece, moves diagonally
case class Bishop(id: String) extends Piece {
  override def moveIsValid(from: Position, to: Position, gameState: Map[Position, Piece]): Boolean = {
    val isDiagonalMove = Math.abs(from.x - to.x) == Math.abs(from.y - to.y)

    val isPathClear = if (isDiagonalMove) {
      val xStep = if (from.x < to.x) 1 else -1
      val yStep = if (from.y < to.y) 1 else -1
      val pathPositions = (1 until Math.abs(from.x - to.x)).map { step =>
        Position(from.x + step * xStep, from.y + step * yStep)
      }
      pathPositions.forall(pos => !gameState.contains(pos))
    } else {
      false
    }

    isDiagonalMove && isPathClear && !gameState.contains(to)
  }
}
object Piece {
  implicit val pieceEncoder: JsonEncoder[Piece] = DeriveJsonEncoder.gen[Piece]
  implicit val pieceDecoder: JsonDecoder[Piece] = DeriveJsonDecoder.gen[Piece]
}

object Rook {
  implicit val rookEncoder: JsonEncoder[Rook] = DeriveJsonEncoder.gen[Rook]
  implicit val rookDecoder: JsonDecoder[Rook] = DeriveJsonDecoder.gen[Rook]
}

object Bishop {
  implicit val bishopEncoder: JsonEncoder[Bishop] = DeriveJsonEncoder.gen[Bishop]
  implicit val bishopDecoder: JsonDecoder[Bishop] = DeriveJsonDecoder.gen[Bishop]
}

