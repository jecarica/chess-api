
package chess.api

import chess._
import sttp.model.StatusCode
import sttp.tapir.json.zio._
import sttp.tapir.ztapir._
import sttp.tapir.generic.auto._
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import zio._
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

case class AddPieceRequest(pieceType: String, position: Position)

object AddPieceRequest {
  implicit val jsonDecoder: JsonDecoder[AddPieceRequest] = DeriveJsonDecoder.gen[AddPieceRequest]
  implicit val jsonEncoder: JsonEncoder[AddPieceRequest] = DeriveJsonEncoder.gen[AddPieceRequest]
}

case class MovePieceRequest(from: Position, to: Position)
object MovePieceRequest {
  implicit val jsonDecoder: JsonDecoder[MovePieceRequest] = DeriveJsonDecoder.gen[MovePieceRequest]
  implicit val jsonEncoder: JsonEncoder[MovePieceRequest] = DeriveJsonEncoder.gen[MovePieceRequest]
}

case class RemovePieceRequest(id: String)
object RemovePieceRequest {
  implicit val jsonDecoder: JsonDecoder[RemovePieceRequest] = DeriveJsonDecoder.gen[RemovePieceRequest]
  implicit val jsonEncoder: JsonEncoder[RemovePieceRequest] = DeriveJsonEncoder.gen[RemovePieceRequest]
}

class ChessApi(chessGameService: ChessGameService) {

  // Endpoint to add a piece
  val addPieceEndpoint: ZServerEndpoint[Any, Any] =
    endpoint.post
      .in("pieces" / "add")
      .in(jsonBody[AddPieceRequest]) // Use the case class for input
      .out(jsonBody[Piece]) // Return the added piece
      .errorOut(stringBody) // Return an error as a string
      .zServerLogic { request =>
        chessGameService.addPiece(request.pieceType, request.position).mapError(identity) // Handle errors
      }

  // Endpoint to move a piece
  val movePieceEndpoint: ZServerEndpoint[Any, Any] =
    endpoint.put
      .in("pieces" / "move")
      .in(jsonBody[MovePieceRequest])
      .out(statusCode(StatusCode.Ok))
      .errorOut(stringBody)
      .zServerLogic { request =>
        chessGameService.movePiece(request.from, request.to).mapError(identity)
      }

  // Endpoint to remove a piece
  val removePieceEndpoint: ZServerEndpoint[Any, Any] =
    endpoint.delete
      .in("pieces" / "remove")
      .in(jsonBody[RemovePieceRequest])
      .out(jsonBody[Position])
      .errorOut(stringBody)
      .zServerLogic { request =>
        chessGameService.removePieceById(request.id).mapError(identity)
      }

  val swaggerEndpoints = SwaggerInterpreter().fromEndpoints[Task](List(addPieceEndpoint.endpoint, movePieceEndpoint.endpoint, removePieceEndpoint.endpoint), "Chess Game API", "1.0")

  val swaggerUIRoutes = ZioHttpInterpreter().toHttp(swaggerEndpoints)

  val apiRoutes = ZioHttpInterpreter().toHttp(List(addPieceEndpoint, movePieceEndpoint, removePieceEndpoint))

  val combinedRoutes = apiRoutes ++ swaggerUIRoutes

}

object ChessApi {
  def live: ZLayer[ChessGameService, Nothing, ChessApi] = ZLayer {
    for {
      chessService <- ZIO.service[ChessGameService]
    } yield new ChessApi(chessService)
  }
}
