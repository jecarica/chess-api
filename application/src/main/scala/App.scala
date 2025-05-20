import chess.api.ChessApi
import zio._
import zio.http._
import zio.kafka.producer.{Producer, ProducerSettings}
import chess._
import chess.domain._
import chess.domain.model.{Piece, Position}
import chess.service.game.ChessGameService

object ChessServer extends ZIOAppDefault {

  override def run: ZIO[Scope, Throwable, Unit] = {
    for {
      producer <- Producer.make(ProducerSettings(List("localhost:9092")))
      // Initialize state references for the chess game
      gameStateRef <- Ref.make(Map.empty[Position, Piece])
      removedPiecesRef <- Ref.make(Map.empty[String, Position])
      chessGameService = new ChessGameService(producer, gameStateRef, removedPiecesRef)
      chessApi = new ChessApi(chessGameService)

      consumerFiber <- chessGameService.consumeGameEvents.fork

      // Create and serve the HTTP server
      serverFiber <- Server.serve(chessApi.combinedRoutes).provide(
        Server.defaultWith(
          _.port(8080).enableRequestStreaming
        )
      ).fork

      // Wait for either the consumer or server to complete
      _ <- Seq(
        consumerFiber.join,
        serverFiber.join
      ).reduce(_ raceFirst _)
    } yield ()
  }
}
