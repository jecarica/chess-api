import chess.api.ChessApi
import zio._
import zio.http._
import zio.kafka.producer.{Producer, ProducerSettings}
import chess._

object ChessServer extends ZIOAppDefault {

  override def run: ZIO[Scope, Throwable, Unit] = {
    for {
      // Create a producer instance (this should be your actual Kafka producer configuration)
      producer <- Producer.make(ProducerSettings(List("localhost:9092")))
      // Initialize state references for the chess game
      gameStateRef <- Ref.make(Map.empty[Position, Piece])
      removedPiecesRef <- Ref.make(Map.empty[String, Position])
      // Create an instance of the chess game service
      chessGameService = new ChessGameService(producer, gameStateRef, removedPiecesRef)
      // Create the Chess API
      chessApi = new ChessApi(chessGameService)


      // Create and serve the HTTP server
      _ <- Server.serve(chessApi.combinedRoutes).provide(
        Server.defaultWith(
          _.port(8080).enableRequestStreaming
        )
      )
    } yield ()
  }
}
