package chess

import zio.kafka.consumer.{Consumer, ConsumerSettings, Subscription}
import zio.{ZIO, Scope, _}
import zio.kafka.producer._
import zio.kafka.serde.{Serde => KafkaSerde}

import java.util.UUID

trait GameService {
  def addPiece(pieceType: String, position: Position, pieceId: Option[String] = None): IO[String, Piece]
  def movePiece(from: Position, to: Position): IO[String, Unit]
  def movePieceById(id: String, to: Position): IO[String, Unit]
  def removePieceById(id: String): IO[String, Position]
}

class ChessGameService(producer: Producer, gameStateRef: Ref[Map[Position, Piece]], removedPiecesRef: Ref[Map[String, Position]]) extends GameService {

  private def isValidPosition(position: Position): Boolean =
    position.x >= 1 && position.x <= 8 && position.y >= 1 && position.y <= 8

  private def generateId: String = UUID.randomUUID().toString

  // Add a new piece with a unique ID to the board and emit an event to Kafka
  def addPiece(pieceType: String, position: Position, pieceId: Option[String] = None): IO[String, Piece] = {
    if (!isValidPosition(position)) {
      ZIO.fail(s"Invalid position: $position. Position must be within 8x8 board.")
    } else {
      for {
        board <- gameStateRef.get
        removedPieces <- removedPiecesRef.get
        // Check if the position is already occupied or if the piece ID has been removed previously
        _ <- ZIO.fail("Position already occupied!").when(board.contains(position))
        id = pieceId.getOrElse(generateId)
        piece <- pieceType match {
          case "Rook" => ZIO.succeed(Rook(id))
          case "Bishop" => ZIO.succeed(Bishop(id))
          case _ => ZIO.fail("Invalid piece type!")
        }
        _ <- ZIO.fail("This piece has been removed and cannot be added back!")
          .when(removedPieces.contains(piece.id)) // Prevent re-adding removed pieces
        _ <- gameStateRef.update(_ + (position -> piece)) // Add piece to the board
        event = PieceAdded(piece, position) // Emit the "piece added" event
        _ <- event.sendEventToKafka(producer, id).mapError(err => throw new RuntimeException(err))
      } yield piece
    }
  }

  def movePiece(from: Position, to: Position): ZIO[Any, String, Unit] = {
    for {
      gameState <- gameStateRef.get
      piece <- ZIO.fromOption(gameState.get(from)).orElseFail("No piece at the given position")
      _ <- ZIO.fail("Position already occupied").when(gameState.contains(to))
      _ <- ZIO.fail(s"Invalid move.").when(!piece.moveIsValid(from, to, gameState))
      _ <- gameStateRef.update(state => state - from + (to -> piece))
      event = PieceMoved(piece, from, to)
      _ <- event.sendEventToKafka(producer, piece.id).mapError(err => throw new RuntimeException(err))
    } yield ()
  }


  // Move a piece by its unique ID to a new position and emit an event to Kafka
  def movePieceById(id: String, to: Position): IO[String, Unit] = {
    if (!isValidPosition(to)) {
      ZIO.fail(s"Invalid position: $to. Position must be within 8x8 board.")
    } else {
      for {
        board <- gameStateRef.get
        _ <- ZIO.fail(s"Piece with id=$id doesn't exist on the board").when(!pieceExists(id, board))
        foundPiece <- findPieceById(id, board)
        (from, piece) = foundPiece
        _ <- if (board.contains(to)) ZIO.fail(s"Position $to is already occupied.")
        else if (!piece.moveIsValid(from, to, board)) ZIO.fail(s"Invalid move.")
        else gameStateRef.update(_ - from + (to -> piece))
        event = PieceMoved(piece, from, to)
        _ <- event.sendEventToKafka(producer, id).mapError(err => throw new RuntimeException(err))// Emit the move event to Kafka
      } yield ()
    }
  }

  // Remove a piece by its unique ID and emit an event to Kafka
  def removePieceById(id: String): IO[String, Position] = {
    for {
      board <- gameStateRef.get
      foundPiece <- findPieceById(id, board)
      (position, piece) = foundPiece
      _ <- gameStateRef.update(_ - position)
      lastPosition = position
      _ <- removedPiecesRef.update(_ + (id -> lastPosition))
      event = PieceRemoved(piece, position)
      _ <- event.sendEventToKafka(producer, id).mapError(err => throw new RuntimeException(err))  // Emit the remove event to Kafka
    } yield lastPosition
  }

  private def findPieceById(id: String, board: Map[Position, Piece]): IO[String, (Position, Piece)] =
    board.collectFirst { case (pos, piece) if piece.id == id => (pos, piece) } match {
      case Some(result) => ZIO.succeed(result)
      case None         => ZIO.fail(s"No piece with ID: $id found on the board.")
    }

  private def pieceExists(id: String, board: Map[Position, Piece]) =
    board.collectFirst { case (pos, piece) if piece.id == id => (pos, piece) } match {
      case Some(_) => true
      case None => false
    }


  def getBoard= gameStateRef.get

  def getLastPositionOfRemovedPiece(id: String): IO[String, Position] = {
    for {
      removedPieces <- removedPiecesRef.get
      position <- removedPieces.get(id) match {
        case Some(pos) => ZIO.succeed(pos)
        case None      => ZIO.fail(s"No removed piece with ID: $id found.")
      }
    } yield position
  }

  // this method can be used for the game state recovery
  def consumeGameEvents: ZIO[Scope, Throwable, Unit] = {
    val consumerSettings = ConsumerSettings(List("localhost:9092"))
      .withGroupId("chess-game-service")
      .withClientId("chess-game-service-client")
      .withProperty("auto.offset.reset", "earliest")

    for {
      consumer <- Consumer.make(consumerSettings)
      _ <- consumer
        .plainStream(Subscription.topics("events"), KafkaSerde.string, ChessEvent.chessEventSerde)
        .tap(record => ZIO.log(s"Received event: ${record.value}"))
        .mapZIO { record =>
          recoverGameState(record.value)
        }
        .runDrain
    } yield ()
  }

  // Method to update the game state based on the consumed event
  private def recoverGameState(event: ChessEvent): UIO[Unit] = {
    event match {
      case PieceAdded(piece, position) =>
        gameStateRef.update(_ + (position -> piece))
      case PieceMoved(piece, from, to) =>
        gameStateRef.update(state => state - from + (to -> piece))
      case PieceRemoved(pieceId, position) =>
        gameStateRef.update(state => state - position)
    }
  }
}

object ChessGameService {
  val live: ZLayer[Producer, Throwable, ChessGameService] =
    ZLayer {
      for {
        producer <- ZIO.service[Producer]
        gameStateRef <- Ref.make(Map.empty[Position, Piece])
        removedPiecesRef <- Ref.make(Map.empty[String, Position])
      } yield new ChessGameService(producer, gameStateRef, removedPiecesRef)
    }

  def addPiece(pieceType: String, position: Position, pieceId: Option[String] = None): ZIO[ChessGameService, String, Piece] =
    ZIO.serviceWithZIO[ChessGameService](_.addPiece(pieceType, position, pieceId))

  def movePiece(from: Position, to: Position): ZIO[ChessGameService, String, Unit] =
    ZIO.serviceWithZIO[ChessGameService](_.movePiece(from, to))

  def movePieceById(id: String, to: Position): ZIO[ChessGameService, String, Unit] =
    ZIO.serviceWithZIO[ChessGameService](_.movePieceById(id, to))

  def getBoard = ZIO.serviceWithZIO[ChessGameService](_.getBoard)

  def removePiece(pieceId: String): ZIO[ChessGameService, String, Position] =
    ZIO.serviceWithZIO[ChessGameService](_.removePieceById(pieceId))

  def getLastPositionOfRemovedPiece(id: String) =
    ZIO.serviceWithZIO[ChessGameService](_.getLastPositionOfRemovedPiece(id))
}
