package chess.api

import chess.api.dto.Request._
import chess.domain.error.ChessError._
import chess.domain.error.ChessError
import chess.api.dto.JsonCodecs._
import chess.domain.model.{Piece, PieceType, Position, Rook}
import chess.service.game.ChessGameService
import sttp.model.StatusCode
import sttp.tapir.generic.auto._
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.tapir.json.zio._
import sttp.tapir.ztapir._
import sttp.tapir.Schema
import zio._
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}


case class BoardState(pieces: Map[Position, Piece])

object BoardState {
  import chess.api.dto.JsonCodecs._
  import chess.api.dto.Schemas._
  implicit val jsonDecoder: JsonDecoder[BoardState] = DeriveJsonDecoder.gen[BoardState]
  implicit val jsonEncoder: JsonEncoder[BoardState] = DeriveJsonEncoder.gen[BoardState]
  implicit val schema: Schema[BoardState] = Schema.derived[BoardState]
    .description("Current state of the chess board")
    .modify(_.pieces)(_.description("Map of positions to pieces currently on the board"))
}

class ChessApi(chessGameService: ChessGameService) {

  // Endpoint to add a piece
  val addPieceEndpoint: ZServerEndpoint[Any, Any] =
    endpoint.post
      .in("api" / "v1" / "pieces")
      .in(jsonBody[AddPieceRequest]
        .description("Request to add a new piece")
        .example(AddPieceRequest(PieceType.Rook, Position(1, 1))))
      .out(jsonBody[Piece]
        .description("The added piece with its ID")
        .example(Rook("1")))
      .errorOut(
        oneOf[ChessError](
          oneOfVariant(StatusCode.BadRequest, jsonBody[ChessError.InvalidPosition]
            .example(ChessError.InvalidPosition(Position(9, 9), "Position must be within 8x8 board"))),
          oneOfVariant(StatusCode.BadRequest, jsonBody[ChessError.InvalidPieceType]
            .example(ChessError.InvalidPieceType("This piece has been removed and cannot be added back"))),
          oneOfVariant(StatusCode.Conflict, jsonBody[ChessError.PositionOccupied]
            .example(ChessError.PositionOccupied(Position(1, 1))))
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
          oneOfVariant(StatusCode.NotFound, jsonBody[ChessError.PieceNotFound]
            .example(ChessError.PieceNotFound("non-existent-id"))),
          oneOfVariant(StatusCode.BadRequest, jsonBody[ChessError.InvalidMove]
            .example(ChessError.InvalidMove(Position(1, 1), Position(2, 2), "Invalid move for this piece type")))
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
        .description("The position where the piece was removed from")
        .example(Position(1, 1)))
      .errorOut(
        oneOf[ChessError](
          oneOfVariant(StatusCode.NotFound, jsonBody[ChessError.PieceNotFound]
            .example(ChessError.PieceNotFound("non-existent-id")))
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
        .description("Current state of the board")
        .example(BoardState(Map(Position(1, 1) -> Rook("1")))))
      .errorOut(
        oneOf[ChessError](
          oneOfVariant(StatusCode.BadRequest, jsonBody[ChessError.InvalidPosition]
            .example(ChessError.InvalidPosition(Position(0, 0), "Position coordinates must be between 1 and 8")))
        )
      )
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
