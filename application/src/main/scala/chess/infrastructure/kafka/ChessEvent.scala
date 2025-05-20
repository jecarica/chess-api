package chess.infrastructure.kafka

import chess.domain._
import chess.domain.model.{Piece, Position}
import org.apache.kafka.clients.producer.ProducerRecord
import zio.{Task, ZIO}
import zio.json.{DecoderOps, DeriveJsonDecoder, DeriveJsonEncoder, EncoderOps, JsonDecoder, JsonEncoder}
import zio.kafka.producer._
import zio.kafka.serde.Serde

sealed trait ChessEvent{
  def serializeEvent: String

  val topic = "events"

  def sendEventToKafka(producer: Producer, id: String): Task[Unit] = {
    val message = this.serializeEvent
    val record = new ProducerRecord[String, String](topic, id, message)
    producer.produce(record, Serde.string, Serde.string).unit
  }

}

case class PieceAdded(piece: Piece, position: Position) extends ChessEvent {
  override def serializeEvent: String =
    s"""{"action": "added", "pieceId": "${piece.id}", "position": {"x": ${position.x}, "y": ${position.y}}}"""
}
case class PieceMoved(piece: Piece, from: Position, to: Position) extends ChessEvent {
  override def serializeEvent: String =
    s"""{"action": "added", "pieceId": "${piece.id}", "from": {"x": ${from.x}, "y": ${from.y}}, "to": {"x": ${to.x}, "y": ${to.y}}}"""
}
case class PieceRemoved(piece: Piece, position: Position) extends ChessEvent {
  override def serializeEvent: String =
    s"""{"action": "removed", "pieceId": "${piece.id}", "position": {"x": ${position.x}, "y": ${position.y}}}"""
}

object ChessEvent {

  implicit val encoder: JsonEncoder[ChessEvent] =
    DeriveJsonEncoder.gen[ChessEvent]

  implicit val decoder: JsonDecoder[ChessEvent] =
    DeriveJsonDecoder.gen[ChessEvent]


  val chessEventSerde: Serde[Any, ChessEvent] =
    Serde.string.inmapM[Any, ChessEvent](s =>
      ZIO.fromEither(s.fromJson[ChessEvent])
        .mapError(e => new RuntimeException(e))
    )(r => ZIO.succeed(r.toString))
}

