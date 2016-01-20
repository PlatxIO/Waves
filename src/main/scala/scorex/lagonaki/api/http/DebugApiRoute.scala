package scorex.lagonaki.api.http

import javax.ws.rs.Path

import akka.actor.ActorRefFactory
import com.wordnik.swagger.annotations._
import play.api.libs.json.Json
import scorex.api.http._
import scorex.block.Block.BlockId
import scorex.crypto.encode.Base58
import scorex.lagonaki.server.LagonakiApplication
import spray.routing.Route

@Api(value = "/debug", description = "Debug methods", position = 1)
case class DebugApiRoute(application: LagonakiApplication)(implicit val context: ActorRefFactory)
  extends ApiRoute with CommonTransactionApiFunctions {

  implicit lazy val transactionModule = application.transactionModule
  lazy val wallet = application.wallet

  override lazy val route = pathPrefix("debug") {
    state ~ stateAt
  }

  @Path("/state")
  @ApiOperation(value = "State", notes = "Get current state", httpMethod = "GET")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Json state")
  ))
  def state: Route = {
    path("state") {
      jsonRoute {
        application.blockStorage.state.toString
      }
    }
  }

  @Path("/state/{blockId}")
  @ApiOperation(value = "State at block", notes = "Get state at specified block", httpMethod = "GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "blockId", value = "Id of block", required = true, dataType = "String", paramType = "path")
  ))
  def stateAt: Route = {
    path("state" / Segment) { case blockId =>
      jsonRoute {
        val id: BlockId = Base58.decode(blockId).getOrElse(Array.empty)
        application.blockStorage.state(Some(id)) match {
          case None => Json.obj("error" -> "wrong block id").toString
          case Some(b) => b.toString
        }
      }
    }
  }

}