package chess.api

import chess.domain.error.ChessError
import chess.domain.model._
import chess.service.game.GameService
import zio.{IO, Ref, UIO, ZIO}

class ChessGameServiceMock(
  gameStateRef: Ref[Map[Position, Piece]],
  removedPiecesRef: Ref[Map[String, Position]]
) extends GameService {
  def addPiece(pieceType: PieceType, position: Position, pieceId: Option[String] = None): IO[ChessError, Piece] = {
    for {
      board <- gameStateRef.get
      removedPieces <- removedPiecesRef.get
      _ <- ZIO.fail(ChessError.PositionOccupied(position)).when(board.contains(position))
      id = pieceId.getOrElse("1")
      _ <- ZIO.fail(ChessError.InvalidPieceType("This piece has been removed and cannot be added back!"))
        .when(removedPieces.contains(id))
      piece <- pieceType match {
        case PieceType.Rook => ZIO.succeed(Rook(id))
        case PieceType.Bishop => ZIO.succeed(Bishop(id))
      }
      _ <- gameStateRef.update(_ + (position -> piece))
    } yield piece
  }

  def movePiece(from: Position, to: Position): IO[ChessError, Unit] = {
    for {
      gameState <- gameStateRef.get
      piece <- ZIO.fromOption(gameState.get(from))
        .orElseFail(ChessError.PieceNotFound(s"No piece at position $from"))
      _ <- ZIO.fail(ChessError.PositionOccupied(to)).when(gameState.contains(to))
      _ <- ZIO.fail(ChessError.InvalidMove(from, to, "Invalid move for this piece type"))
        .when(!piece.moveIsValid(from, to, gameState))
      _ <- gameStateRef.update(state => state - from + (to -> piece))
    } yield ()
  }

  def movePieceById(id: String, to: Position): IO[ChessError, Unit] = {
    for {
      board <- gameStateRef.get
      foundPiece <- findPieceById(id, board)
      (from, piece) = foundPiece
      _ <- ZIO.fail(ChessError.PositionOccupied(to)).when(board.contains(to))
      _ <- ZIO.fail(ChessError.InvalidMove(from, to, "Invalid move for this piece type"))
        .when(!piece.moveIsValid(from, to, board))
      _ <- gameStateRef.update(_ - from + (to -> piece))
    } yield ()
  }

  def removePieceById(id: String): IO[ChessError, Position] = {
    for {
      board <- gameStateRef.get
      foundPiece <- findPieceById(id, board)
      (position, piece) = foundPiece
      _ <- gameStateRef.update(_ - position)
      _ <- removedPiecesRef.update(_ + (id -> position))
    } yield position
  }

  private def findPieceById(id: String, board: Map[Position, Piece]): IO[ChessError, (Position, Piece)] =
    board.collectFirst { case (pos, piece) if piece.id == id => (pos, piece) } match {
      case Some(result) => ZIO.succeed(result)
      case None         => ZIO.fail(ChessError.PieceNotFound(id))
    }

  def getBoard = gameStateRef.get

  def getLastPositionOfRemovedPiece(id: String): IO[ChessError, Position] = {
    for {
      removedPieces <- removedPiecesRef.get
      position <- ZIO.fromOption(removedPieces.get(id))
        .orElseFail(ChessError.PieceNotFound(id))
    } yield position
  }
}

object ChessGameServiceMock {
  def make: UIO[ChessGameServiceMock] = {
    for {
      gameStateRef <- Ref.make(Map.empty[Position, Piece])
      removedPiecesRef <- Ref.make(Map.empty[String, Position])
    } yield new ChessGameServiceMock(gameStateRef, removedPiecesRef)
  }
}
