package controllers

import java.util.UUID

import com.gilt.apidoc.models.{GeneratorCreateForm, GeneratorUpdateForm, Generator, Visibility, Domain}
import models._
import play.api.data.Forms._
import play.api.data._
import play.api.mvc._

import scala.concurrent.Future

object Generators extends Controller {

  implicit val context = scala.concurrent.ExecutionContext.Implicits.global

  def list() = Authenticated.async { implicit request =>
    request.api.Generators.get().recover {
      case ex: Exception => Seq.empty
    }.map { generators =>
      Ok(views.html.generators.index(
        MainTemplate(
          user = Some(request.user),
          generators = generators,
          title = s"Generators"
        )
      ))
    }
  }

  def update(generatorGuid: UUID, visibility: String, enabled: Boolean) = Authenticated { implicit request =>
    val filledForm = generatorUpdateForm.fill(GeneratorUpdateData(visibility, enabled))
    Ok(views.html.generators.update(
      MainTemplate(
        user = Some(request.user),
        title = s"Update Generator"
      ),
      generatorGuid,
      filledForm
    ))
  }

  def postUpdate(generatorGuid: java.util.UUID) = Authenticated.async { implicit request =>
    val tpl = MainTemplate(
      user = Some(request.user),
      title = s"Add Generator"
    )
    val boundForm = generatorUpdateForm.bindFromRequest
    boundForm.fold (

      errors => Future {
        Ok(views.html.generators.update(tpl, generatorGuid, errors))
      },

      valid => {
        request.api.Generators.putByGuid(GeneratorUpdateForm(
          visibility = Some(Visibility(valid.visibility)),
          enabled = Some(valid.enabled)
        ), generatorGuid).map { d =>
          Redirect(routes.Generators.list()).flashing("success" -> s"Generator updated")
        }.recover {
          case response: com.gilt.apidoc.error.ErrorsResponse => {
            Ok(views.html.generators.update(tpl, generatorGuid, boundForm, response.errors.map(_.message)))
          }
        }
      }
    )
  }

  def create() = Authenticated { implicit request =>
    Ok(views.html.generators.form(
      MainTemplate(
        user = Some(request.user),
        title = s"Add Generator"
      ),
      1,
      generatorCreateForm
    ))
  }

  def postCreate() = Authenticated.async { implicit request =>
    val tpl = MainTemplate(
      user = Some(request.user),
      title = s"Add Generator"
    )
    val boundForm = generatorCreateForm.bindFromRequest
    boundForm.fold (

      errors => Future {
        Ok(views.html.generators.form(tpl, 1, errors))
      },

      (valid: GeneratorCreateData) => {
        valid.details.headOption.map { _ =>
          val futures: List[Future[Generator]] = valid.details.filter(_.selected).map { detail =>
            request.api.Generators.post(GeneratorCreateForm(
              key = detail.key,
              uri = valid.uri,
              visibility = Visibility(detail.visibility)
            ))
          }
          Future.sequence(futures).map { d =>
            Redirect(routes.Generators.list()).flashing("success" -> s"Generator(s) added")
          }.recover {
            case response: com.gilt.apidoc.error.ErrorsResponse => {
              Ok(views.html.generators.form(tpl, 1, boundForm, response.errors.map(_.message)))
            }
          }
        }.getOrElse {
          new com.gilt.apidocgenerator.Client(valid.uri).generators.get().map { gens =>
            val d = gens.toList.map(gen => GeneratorDetails(gen.key, Visibility.Public.toString, true))
            Ok(views.html.generators.form(tpl, 2, generatorCreateForm.fill(valid.copy(details = d))))
          }.recover {
            case response: com.gilt.apidoc.error.ErrorsResponse => {
              Ok(views.html.generators.form(tpl, 1, boundForm, response.errors.map(_.message)))
            }
          }
        }
      }
    )
  }

  def postRemove(guid: java.util.UUID) = Authenticated.async { implicit request =>
    for {
      response <- request.api.Generators.deleteByGuid(guid)
    } yield {
      Redirect(routes.Generators.list()).flashing("success" -> s"Generator removed")
    }
  }

  case class GeneratorDetails(key: String, visibility: String, selected: Boolean)

  case class GeneratorCreateData(uri: String, details: List[GeneratorDetails])
  private val generatorCreateForm = Form(
    mapping(
      "uri" -> nonEmptyText,
      "details" -> Forms.list(
        mapping(
          "key" -> nonEmptyText,
          "visibility" -> nonEmptyText,
          "selected" -> boolean
        )(GeneratorDetails.apply)(GeneratorDetails.unapply)
      )
    )(GeneratorCreateData.apply)(GeneratorCreateData.unapply)
  )

  case class GeneratorUpdateData(visibility:  String, enabled:  Boolean)
  private val generatorUpdateForm = Form(
    mapping(
      "visibility" -> nonEmptyText,
      "enabled" -> boolean
    )(GeneratorUpdateData.apply)(GeneratorUpdateData.unapply)
  )
}
