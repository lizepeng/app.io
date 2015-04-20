package controllers

import security._

/**
 * @author zepeng.li@gmail.com
 */
object Secured {

  object Modules {

    def names: Seq[String] = modules.map(_.CheckedModuleName.name)

    lazy val modules: Seq[PermissionCheckable] =
      Seq(
        Files,
        Groups,
        Users,
        EmailTemplates,
        AccessControls,
        controllers.api.Groups,
        controllers.api.Users
      )
  }

  object Actions {

    def names: Seq[String] = actions.map(_.name)

    lazy val actions: Seq[CheckedAction] = CheckedActions.ALL
  }

}