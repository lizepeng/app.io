package controllers

import security._

/**
 * @author zepeng.li@gmail.com
 */
object Secured {

  object Modules {

    def names: Seq[String] = modules.map(_.CheckedModuleName.name)

    lazy val modules: Seq[PermCheckable] =
      Seq(
        AccessControls,
        EmailTemplates,
        Files,
        Groups,
        Users
      )
  }

  object Actions {

    def names: Seq[String] = actions.map(_.name)

    lazy val actions: Seq[CheckedAction] = CheckedActions.ALL
  }

}