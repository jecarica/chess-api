package chess.api

import chess.{GameService, Piece, Position}
import zio._
import zio.mock._
import zio.mock.Mock


object ChessGameServiceMock extends Mock[GameService] {
  object AddPiece extends Effect[(String, Position, Option[String]), String, Piece]
  object MovePiece extends Effect[(Position, Position), String, Unit]
  object MovePieceById extends Effect[(String, Position), String, Unit]
  object RemovePieceById extends Effect[String, String, Position]


  val compose: URLayer[Proxy, GameService] = ZLayer.fromFunction { (proxy: Proxy) =>
    new GameService {
      def addPiece(pieceType: String, position: Position, pieceId: Option[String]): IO[String, Piece] =
        proxy(AddPiece, pieceType, position, pieceId)

      def movePiece(from: Position, to: Position): IO[String, Unit] =
        proxy(MovePiece, from, to)

      def movePieceById(id: String, to: Position): IO[String, Unit] =
        proxy(MovePieceById, id, to)

     def removePieceById(id: String): IO[String, Position] =
        proxy(RemovePieceById, id)
    }
  }
}
