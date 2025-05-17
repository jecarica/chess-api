package chess

import chess.api.{ChessError, PieceType}
import zio._
import zio.test._
import zio.test.Assertion._
import zio.kafka.serde.Serde
import zio.kafka.consumer.{ Consumer, Subscription }
import zio.kafka.testkit.KafkaTestUtils.{ consumer, producer }
import zio.kafka.testkit._


object ChessGameServiceSpec extends ZIOSpecDefault {

  override def spec = suite("ChessGameService Tests")(

    test("Add a piece to an empty position") {
      for {
        piece <- ChessGameService.addPiece(PieceType.Rook, Position(3, 3))
        board <- ChessGameService.getBoard
      } yield assertTrue(board(Position(3, 3)) == piece)
    },
    test("Fail to add a piece to an occupied position") {
      for {
        chessGame <- ZIO.service[ChessGameService]
        _ <- chessGame.addPiece(PieceType.Rook, Position(1, 1))
        result <- chessGame.addPiece(PieceType.Bishop, Position(1, 1)).either
      } yield assertTrue(
        result.isLeft,
        result.left.get.isInstanceOf[ChessError.PositionOccupied]
      )
    },
    test("Fail to add a piece outside the board boundaries") {
      for {
        chessGame <- ZIO.service[ChessGameService]
        result1 <- chessGame.addPiece(PieceType.Rook, Position(0, 1)).either
        result2 <- chessGame.addPiece(PieceType.Rook, Position(1, 0)).either
        result3 <- chessGame.addPiece(PieceType.Rook, Position(9, 1)).either
        result4 <- chessGame.addPiece(PieceType.Rook, Position(1, 9)).either
      } yield {
        assertTrue(
          result1.isLeft && result1.left.get.isInstanceOf[ChessError.InvalidPosition],
          result2.isLeft && result2.left.get.isInstanceOf[ChessError.InvalidPosition],
          result3.isLeft && result3.left.get.isInstanceOf[ChessError.InvalidPosition],
          result4.isLeft && result4.left.get.isInstanceOf[ChessError.InvalidPosition]
        )
      }
    },
    test("Fail to add a piece that was removed") {
      for {
        chessGame <- ZIO.service[ChessGameService]
        piece <- chessGame.addPiece(PieceType.Rook, Position(2, 2))
        _ <- chessGame.removePieceById(piece.id)
        result <- chessGame.addPiece(PieceType.Rook, Position(2, 2), Some(piece.id)).either
      } yield assertTrue(
        result.isLeft,
        result.left.get.isInstanceOf[ChessError.InvalidPieceType]
      )
    },
    test("Should emit Kafka event when piece is added") {
      for {
        chessGame <- ZIO.service[ChessGameService]
        _ <- ChessGameService.addPiece(PieceType.Rook, Position(5, 1))

        records <- Consumer
          .plainStream(Subscription.Topics(Set("events")), Serde.string, Serde.string)
          .take(5)
          .runCollect
          .provideSome[Kafka](
            consumer(clientId = "client", groupId = Some("groupId"))
          )
        consumed = records.map(r => (r.record.key, r.record.value)).toList
      } yield assert(consumed.head._2)(containsString("added"))
    },

    test("Should move rook successfully when path is clear and update game state") {
      for {
        chessGame <- ZIO.service[ChessGameService]
        piece <- chessGame.addPiece(PieceType.Rook, Position(7, 1))
        _ <- chessGame.movePieceById(piece.id, Position(7, 7))
        gameState <- chessGame.getBoard
      } yield {
        assertTrue(gameState(Position(7, 7)).id == piece.id) && assertTrue(!gameState.contains(Position(7, 1)))
      }
    },
    test("Should move bishop successfully when path is clear and update game state") {
      for {
        chessGame <- ZIO.service[ChessGameService]
        piece <- chessGame.addPiece(PieceType.Bishop, Position(6, 1))
        _ <- chessGame.movePieceById(piece.id, Position(7, 2))
        gameState <- chessGame.getBoard
      } yield {
        assertTrue(gameState(Position(7, 2)).id == piece.id) && assertTrue(!gameState.contains(Position(6, 1)))
      }
    },
    test("Should fail to move non-existent piece by ID") {
      for {
        chessGame <- ZIO.service[ChessGameService]
        result <- chessGame.movePieceById("non-existent-id", Position(1, 1)).either
      } yield assertTrue(
        result.isLeft,
        result.left.get.isInstanceOf[ChessError.PieceNotFound]
      )
    },
    test("should remove a piece from the board") {
      for {
        service <- ZIO.service[ChessGameService]
        piece <- service.addPiece(PieceType.Rook, Position(7, 8), Some("1"))
        result <- service.removePieceById("1")
        position <- service.getLastPositionOfRemovedPiece("1")
      } yield {
        assertTrue(result == position)
      }
    },
    test("addPiece should fail when position is invalid") {
      for {
        service <- ZIO.service[ChessGameService]
        result <- service.addPiece(PieceType.Rook, Position(9, 9)).either
      } yield assertTrue(
        result.isLeft,
        result.left.get.isInstanceOf[ChessError.InvalidPosition]
      )
    },
    test("movePieceById should fail when the piece ID doesn't exist") {
      for {
        service <- ZIO.service[ChessGameService]
        result <- service.movePieceById("non-existent-id", Position(2, 2)).either
      } yield assertTrue(
        result.isLeft,
        result.left.get.isInstanceOf[ChessError.PieceNotFound]
      )
    },
    test("removePieceById should fail when the piece ID doesn't exist") {
      for {
        service <- ZIO.service[ChessGameService]
        result <- service.removePieceById("non-existent-id").either
      } yield assertTrue(
        result.isLeft,
        result.left.get.isInstanceOf[ChessError.PieceNotFound]
      )
    },
    test("getLastPositionOfRemovedPiece should fail when the piece ID doesn't exist") {
      for {
        service <- ZIO.service[ChessGameService]
        result <- service.getLastPositionOfRemovedPiece("non-existent-id").either
      } yield assertTrue(
        result.isLeft,
        result.left.get.isInstanceOf[ChessError.PieceNotFound]
      )
    }
  )
    .provideSomeShared[Scope](
    // Combine the TestEnvironment with Mock Kafka Producer and ChessGameService
    Kafka.embedded, producer,
      ChessGameService.live
  )
}
