package chess.api

import chess._
import sttp.model.StatusCode
import sttp.tapir.generic.auto._
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.tapir.codec.enumeratum._
import sttp.tapir.json.zio._
import sttp.tapir.ztapir._
import sttp.tapir.Schema
import zio._
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder, JsonFieldDecoder, JsonFieldEncoder}
import enumeratum._

sealed trait ChessError extends Throwable {
  def message: String
}

object ChessError {
  import Schemas._
  case class InvalidPosition(position: Position, reason: String) extends ChessError {
    override def message: String = s"Invalid position $position: $reason"
  }
  
  case class InvalidPieceType(pieceType: String) extends ChessError {
    override def message: String = s"Invalid piece type: $pieceType"
  }
  
  case class PositionOccupied(position: Position) extends ChessError {
    override def message: String = s"Position $position is already occupied"
  }
  
  case class PieceNotFound(id: String) extends ChessError {
    override def message: String = s"Piece with id $id not found"
  }
  
  case class InvalidMove(from: Position, to: Position, reason: String) extends ChessError {
    override def message: String = s"Invalid move from $from to $to: $reason"
  }

  // JSON encoders/decoders for error types
  implicit val invalidPositionEncoder: JsonEncoder[InvalidPosition] = DeriveJsonEncoder.gen[InvalidPosition]
  implicit val invalidPositionDecoder: JsonDecoder[InvalidPosition] = DeriveJsonDecoder.gen[InvalidPosition]
  
  implicit val invalidPieceTypeEncoder: JsonEncoder[InvalidPieceType] = DeriveJsonEncoder.gen[InvalidPieceType]
  implicit val invalidPieceTypeDecoder: JsonDecoder[InvalidPieceType] = DeriveJsonDecoder.gen[InvalidPieceType]
  
  implicit val positionOccupiedEncoder: JsonEncoder[PositionOccupied] = DeriveJsonEncoder.gen[PositionOccupied]
  implicit val positionOccupiedDecoder: JsonDecoder[PositionOccupied] = DeriveJsonDecoder.gen[PositionOccupied]
  
  implicit val pieceNotFoundEncoder: JsonEncoder[PieceNotFound] = DeriveJsonEncoder.gen[PieceNotFound]
  implicit val pieceNotFoundDecoder: JsonDecoder[PieceNotFound] = DeriveJsonDecoder.gen[PieceNotFound]
  
  implicit val invalidMoveEncoder: JsonEncoder[InvalidMove] = DeriveJsonEncoder.gen[InvalidMove]
  implicit val invalidMoveDecoder: JsonDecoder[InvalidMove] = DeriveJsonDecoder.gen[InvalidMove]

  // Schema for error types
  implicit val invalidPositionSchema: Schema[InvalidPosition] = Schema.derived[InvalidPosition]
  implicit val invalidPieceTypeSchema: Schema[InvalidPieceType] = Schema.derived[InvalidPieceType]
  implicit val positionOccupiedSchema: Schema[PositionOccupied] = Schema.derived[PositionOccupied]
  implicit val pieceNotFoundSchema: Schema[PieceNotFound] = Schema.derived[PieceNotFound]
  implicit val invalidMoveSchema: Schema[InvalidMove] = Schema.derived[InvalidMove]
}

sealed trait PieceType extends EnumEntry

object PieceType extends Enum[PieceType] {
  case object Rook extends PieceType
  case object Bishop extends PieceType
  
  val values = findValues
  
  implicit val schema: Schema[PieceType] = Schema.string[PieceType]
  implicit val jsonEncoder: JsonEncoder[PieceType] = JsonEncoder[String].contramap(_.entryName)
  implicit val jsonDecoder: JsonDecoder[PieceType] = JsonDecoder[String].mapOrFail { str =>
    values.find(_.entryName == str)
      .toRight(s"Invalid piece type: $str")
  }
}

object JsonCodecs {
  implicit val positionFieldDecoder: JsonFieldDecoder[Position] = JsonFieldDecoder[String].map { str =>
    val parts = str.split(",")
    Position(parts(0).toInt, parts(1).toInt)
  }
  
  implicit val positionFieldEncoder: JsonFieldEncoder[Position] = JsonFieldEncoder[String].contramap { pos =>
    s"${pos.x},${pos.y}"
  }

  implicit val mapJsonDecoder: JsonDecoder[Map[Position, Piece]] = JsonDecoder.map[Position, Piece]
  implicit val mapJsonEncoder: JsonEncoder[Map[Position, Piece]] = JsonEncoder.map[Position, Piece]
}

object Schemas {
  // Define Position schema manually since it's a case class with primitive types
  implicit val positionSchema: Schema[Position] = Schema.derived[Position]
  
  // Define Piece schema
  implicit val pieceSchema: Schema[Piece] = Schema.derived[Piece]

  // Define Map schema using schemaForMap
  implicit val mapPositionPieceSchema: Schema[Map[Position, Piece]] = 
    Schema.schemaForMap[Position, Piece](pos => s"${pos.x},${pos.y}")
}

case class AddPieceRequest(pieceType: PieceType, position: Position)

object AddPieceRequest {
  import Schemas._
  implicit val jsonDecoder: JsonDecoder[AddPieceRequest] = DeriveJsonDecoder.gen[AddPieceRequest]
  implicit val jsonEncoder: JsonEncoder[AddPieceRequest] = DeriveJsonEncoder.gen[AddPieceRequest]
  implicit val schema: Schema[AddPieceRequest] = Schema.derived[AddPieceRequest]
}

case class MovePieceRequest(to: Position)

object MovePieceRequest {
  import Schemas._
  implicit val jsonDecoder: JsonDecoder[MovePieceRequest] = DeriveJsonDecoder.gen[MovePieceRequest]
  implicit val jsonEncoder: JsonEncoder[MovePieceRequest] = DeriveJsonEncoder.gen[MovePieceRequest]
  implicit val schema: Schema[MovePieceRequest] = Schema.derived[MovePieceRequest]
}

case class BoardState(pieces: Map[Position, Piece])

object BoardState {
  import JsonCodecs._
  import Schemas._
  implicit val jsonDecoder: JsonDecoder[BoardState] = DeriveJsonDecoder.gen[BoardState]
  implicit val jsonEncoder: JsonEncoder[BoardState] = DeriveJsonEncoder.gen[BoardState]
  implicit val schema: Schema[BoardState] = Schema.derived[BoardState]
}

class ChessApi(chessGameService: ChessGameService) {
  private def handleError(error: ChessError): (StatusCode, String) = error match {
    case _: ChessError.InvalidPosition => (StatusCode.BadRequest, error.message)
    case _: ChessError.InvalidPieceType => (StatusCode.BadRequest, error.message)
    case _: ChessError.PositionOccupied => (StatusCode.Conflict, error.message)
    case _: ChessError.PieceNotFound => (StatusCode.NotFound, error.message)
    case _: ChessError.InvalidMove => (StatusCode.BadRequest, error.message)
  }

  // Endpoint to add a piece
  val addPieceEndpoint: ZServerEndpoint[Any, Any] =
    endpoint.post
      .in("api" / "v1" / "pieces")
      .in(jsonBody[AddPieceRequest]
        .description("Request to add a new piece")
        .example(AddPieceRequest(PieceType.Rook, Position(1, 1))))
      .out(jsonBody[Piece]
        .description("The added piece with its ID"))
      .errorOut(
        oneOf[ChessError](
          oneOfVariant(StatusCode.BadRequest, jsonBody[ChessError.InvalidPosition]),
          oneOfVariant(StatusCode.BadRequest, jsonBody[ChessError.InvalidPieceType]),
          oneOfVariant(StatusCode.Conflict, jsonBody[ChessError.PositionOccupied])
        )
      )
      .description("Add a new piece to the board")
      .tag("Pieces")
      .zServerLogic { request =>
        chessGameService.addPiece(request.pieceType, request.position)
      }

  // Endpoint to move a piece
  val movePieceEndpoint: ZServerEndpoint[Any, Any] =
    endpoint.patch
      .in("api" / "v1" / "pieces" / path[String]("id"))
      .in(jsonBody[MovePieceRequest]
        .description("Request to move a piece")
        .example(MovePieceRequest(Position(2, 2))))
      .out(statusCode(StatusCode.NoContent))
      .errorOut(
        oneOf[ChessError](
          oneOfVariant(StatusCode.NotFound, jsonBody[ChessError.PieceNotFound]),
          oneOfVariant(StatusCode.BadRequest, jsonBody[ChessError.InvalidMove])
        )
      )
      .description("Move a piece to a new position")
      .tag("Pieces")
      .zServerLogic { case (id: String, request: MovePieceRequest) =>
        chessGameService.movePieceById(id, request.to)
      }

  // Endpoint to remove a piece
  val removePieceEndpoint: ZServerEndpoint[Any, Any] =
    endpoint.delete
      .in("api" / "v1" / "pieces" / path[String]("id"))
      .out(jsonBody[Position]
        .description("The position where the piece was removed from"))
      .errorOut(
        oneOf[ChessError](
          oneOfVariant(StatusCode.NotFound, jsonBody[ChessError.PieceNotFound])
        )
      )
      .description("Remove a piece from the board")
      .tag("Pieces")
      .zServerLogic { id =>
        chessGameService.removePieceById(id)
      }

  // Endpoint to get the current board state
  val getBoardEndpoint: ZServerEndpoint[Any, Any] =
    endpoint.get
      .in("api" / "v1" / "board")
      .out(jsonBody[BoardState]
        .description("Current state of the board"))
      .errorOut(stringBody)
      .description("Get the current state of the board")
      .tag("Board")
      .zServerLogic { _ =>
        chessGameService.getBoard.map(board => BoardState(board))
      }

  val swaggerEndpoints = SwaggerInterpreter()
    .fromEndpoints[Task](
      List(
        addPieceEndpoint.endpoint,
        movePieceEndpoint.endpoint,
        removePieceEndpoint.endpoint,
        getBoardEndpoint.endpoint
      ),
      "Chess Game API",
      "1.0"
    )

  val swaggerUIRoutes = ZioHttpInterpreter().toHttp(swaggerEndpoints)

  val apiRoutes = ZioHttpInterpreter().toHttp(
    List(
      addPieceEndpoint,
      movePieceEndpoint,
      removePieceEndpoint,
      getBoardEndpoint
    )
  )

  val combinedRoutes = apiRoutes ++ swaggerUIRoutes
}

object ChessApi {
  def live: ZLayer[ChessGameService, Nothing, ChessApi] = ZLayer {
    for {
      chessService <- ZIO.service[ChessGameService]
    } yield new ChessApi(chessService)
  }
}
