package chess

import zio.kafka.consumer.{Consumer, ConsumerSettings, Subscription}
import zio.{ZIO, Scope, _}
import zio.kafka.producer._
import zio.kafka.serde.{Serde => KafkaSerde}
import chess.api.{PieceType, ChessError}
import java.util.UUID

trait GameService {
  def addPiece(pieceType: PieceType, position: Position, pieceId: Option[String] = None): IO[ChessError, Piece]
  def movePiece(from: Position, to: Position): IO[ChessError, Unit]
  def movePieceById(id: String, to: Position): IO[ChessError, Unit]
  def removePieceById(id: String): IO[ChessError, Position]
}

class ChessGameService(producer: Producer, gameStateRef: Ref[Map[Position, Piece]], removedPiecesRef: Ref[Map[String, Position]]) extends GameService {

  private def isValidPosition(position: Position): Boolean =
    position.x >= 1 && position.x <= 8 && position.y >= 1 && position.y <= 8

  private def generateId: String = UUID.randomUUID().toString

  // Add a new piece with a unique ID to the board and emit an event to Kafka
  def addPiece(pieceType: PieceType, position: Position, pieceId: Option[String] = None): IO[ChessError, Piece] = {
    if (!isValidPosition(position)) {
      ZIO.fail(ChessError.InvalidPosition(position, "Position coordinates must be between 1 and 8"))
    } else {
      for {
        board <- gameStateRef.get
        removedPieces <- removedPiecesRef.get
        // Check if the position is already occupied or if the piece ID has been removed previously
        _ <- ZIO.when(board.contains(position))(ZIO.fail(ChessError.PositionOccupied(position)))
        id = pieceId.getOrElse(generateId)
        piece <- pieceType match {
          case PieceType.Rook => ZIO.succeed(Rook(id))
          case PieceType.Bishop => ZIO.succeed(Bishop(id))
        }
        _ <- ZIO.fail(ChessError.InvalidPieceType("Invalid piece type. Must be either Rook or Bishop"))
          .when(removedPieces.contains(piece.id)) // Prevent re-adding removed pieces
        _ <- gameStateRef.update(_ + (position -> piece)) // Add piece to the board
        event = PieceAdded(piece, position) // Emit the "piece added" event
        _ <- event.sendEventToKafka(producer, id).mapError(err => throw new RuntimeException(err))
      } yield piece
    }
  }

  def movePiece(from: Position, to: Position): IO[ChessError, Unit] = {
    for {
      gameState <- gameStateRef.get
      piece <- gameState.get(from) match {
        case Some(p) => ZIO.succeed(p)
        case None => ZIO.fail(ChessError.PieceNotFound(s"No piece found at position $from"))
      }
      _ <- ZIO.fail(ChessError.PositionOccupied(to)).when(gameState.contains(to))
      _ <- ZIO.fail(ChessError.InvalidMove(from, to, "Invalid move for this piece type"))
        .when(!piece.moveIsValid(from, to, gameState))
      _ <- gameStateRef.update(state => state - from + (to -> piece))
      event = PieceMoved(piece, from, to)
      _ <- event.sendEventToKafka(producer, piece.id).mapError(err => throw new RuntimeException(err))
    } yield ()
  }

  // Move a piece by its unique ID to a new position and emit an event to Kafka
  def movePieceById(id: String, to: Position): IO[ChessError, Unit] = {
    if (!isValidPosition(to)) {
      ZIO.fail(ChessError.InvalidPosition(to, "Position must be within 8x8 board."))
    } else {
      for {
        board <- gameStateRef.get
        foundPiece <- findPieceById(id, board)
        (from, piece) = foundPiece
        _ <- ZIO.fail(ChessError.PositionOccupied(to)).when(board.contains(to))
        _ <- ZIO.fail(ChessError.InvalidMove(from, to, "Invalid move for this piece type"))
          .when(!piece.moveIsValid(from, to, board))
        _ <- gameStateRef.update(_ - from + (to -> piece))
        event = PieceMoved(piece, from, to)
        _ <- event.sendEventToKafka(producer, id).mapError(err => throw new RuntimeException(err))
      } yield ()
    }
  }

  // Remove a piece by its unique ID and emit an event to Kafka
  def removePieceById(id: String): IO[ChessError, Position] = {
    for {
      board <- gameStateRef.get
      foundPiece <- findPieceById(id, board)
      (position, piece) = foundPiece
      _ <- gameStateRef.update(_ - position)
      lastPosition = position
      _ <- removedPiecesRef.update(_ + (id -> lastPosition))
      event = PieceRemoved(piece, position)
      _ <- event.sendEventToKafka(producer, id).mapError(err => throw new RuntimeException(err))
    } yield lastPosition
  }

  private def findPieceById(id: String, board: Map[Position, Piece]): IO[ChessError, (Position, Piece)] =
    board.collectFirst { case (pos, piece) if piece.id == id => (pos, piece) } match {
      case Some(result) => ZIO.succeed(result)
      case None         => ZIO.fail(ChessError.PieceNotFound(id))
    }

  private def pieceExists(id: String, board: Map[Position, Piece]) =
    board.collectFirst { case (pos, piece) if piece.id == id => (pos, piece) } match {
      case Some(_) => true
      case None => false
    }

  def getBoard = gameStateRef.get

  def getLastPositionOfRemovedPiece(id: String): IO[ChessError, Position] = {
    for {
      removedPieces <- removedPiecesRef.get
      position <- removedPieces.get(id) match {
        case Some(pos) => ZIO.succeed(pos)
        case None      => ZIO.fail(ChessError.PieceNotFound(id))
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

  def addPiece(pieceType: PieceType, position: Position, pieceId: Option[String] = None): ZIO[ChessGameService, ChessError, Piece] =
    ZIO.serviceWithZIO[ChessGameService](_.addPiece(pieceType, position, pieceId))

  def movePiece(from: Position, to: Position): ZIO[ChessGameService, ChessError, Unit] =
    ZIO.serviceWithZIO[ChessGameService](_.movePiece(from, to))

  def movePieceById(id: String, to: Position): ZIO[ChessGameService, ChessError, Unit] =
    ZIO.serviceWithZIO[ChessGameService](_.movePieceById(id, to))

  def getBoard = ZIO.serviceWithZIO[ChessGameService](_.getBoard)

  def removePiece(pieceId: String): ZIO[ChessGameService, ChessError, Position] =
    ZIO.serviceWithZIO[ChessGameService](_.removePieceById(pieceId))

  def getLastPositionOfRemovedPiece(id: String) =
    ZIO.serviceWithZIO[ChessGameService](_.getLastPositionOfRemovedPiece(id))
}
