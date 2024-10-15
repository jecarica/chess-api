package chess

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
        piece <- ChessGameService.addPiece("Rook", Position(3, 3))
        board <- ChessGameService.getBoard
      } yield assertTrue(board(Position(3, 3)) == piece)
    },
    test("Fail to add a piece to an occupied position") {
      for {
        chessGame <- ZIO.service[ChessGameService]
        _ <- chessGame.addPiece("Rook", Position(1, 1))
        result <- chessGame.addPiece("Bishop", Position(1, 1)).either
      } yield assert(result)(isLeft(equalTo("Position already occupied!")))
    },
    test("Fail to add a piece that was removed") {
      for {
        chessGame <- ZIO.service[ChessGameService]
        piece <- chessGame.addPiece("Rook", Position(2, 2))
        _ <- chessGame.removePieceById(piece.id)
        result <- chessGame.addPiece("Rook", Position(2, 2), Some(piece.id)).either
      } yield assert(result)(isLeft(equalTo("This piece has been removed and cannot be added back!")))
    },
    test("Should emit Kafka event when piece is added") {
      for {
        chessGame <- ZIO.service[ChessGameService]
        _ <- ChessGameService.addPiece("Rook", Position(5, 1))

        records <- Consumer
          .plainStream(Subscription.Topics(Set("events")), Serde.string, Serde.string)
          .take(5)
          .runCollect
          .provideSome[Kafka](
            // Comes from `KafkaTestUtils`
            consumer(clientId = "client", groupId = Some("groupId"))
          )
        consumed = records.map(r => (r.record.key, r.record.value)).toList
        // Assertions about Kafka records can go here (e.g., checking a Kafka consumer)
      } yield assert(consumed.head._2)(containsString("added"))
    },

    test("Should move rook successfully when path is clear and update game state") {
      for {
        chessGame <- ZIO.service[ChessGameService]
        piece <- chessGame.addPiece("Rook", Position(7, 1)) // Add a rook to the board
        _ <- chessGame.movePieceById(piece.id, Position(7, 7)) // Move rook
        gameState <- chessGame.getBoard // Get the current game state
      } yield {
        assertTrue(gameState(Position(7, 7)).id == piece.id) && assertTrue(!gameState.contains(Position(7, 1)))
      }
    },
    test("Should move bishop successfully when path is clear and update game state") {
      for {
        chessGame <- ZIO.service[ChessGameService]
        piece <- chessGame.addPiece("Bishop", Position(6, 1)) // Add a bishop to the board
        _ <- chessGame.movePieceById(piece.id, Position(7, 2)) // Move bishop
        gameState <- chessGame.getBoard // Get the current game state
      } yield {
        assertTrue(gameState(Position(7, 2)).id == piece.id) && assertTrue(!gameState.contains(Position(6, 1)))
      }
    },
    test("should remove a piece from the board") {
      for {
        piece <- ChessGameService.addPiece("Rook", Position(7, 8), Some("1"))
        result <- ChessGameService.removePiece("1")
        position <- ChessGameService.getLastPositionOfRemovedPiece("1")
      } yield {
        assertTrue(result == position)
      }
    }
  )
    .provideSomeShared[Scope](
    // Combine the TestEnvironment with Mock Kafka Producer and ChessGameService
    Kafka.embedded, producer,
      ChessGameService.live
  )
}
