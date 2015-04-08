package controllers

import security._

/**
 * @author zepeng.li@gmail.com
 */
object Secured {

  object Modules {

    def apply(): Seq[PermCheckable] = modules

    def names: Seq[String] = modules.map(_.CheckedModuleName.name)

    lazy val modules: Seq[PermCheckable] =
      Seq(
        AccessControls,
        EmailTemplates,
        Files
      )
  }

  object Actions {

    def apply(): Seq[CheckedAction] = actions

    def names: Seq[String] = actions.map(_.name)

    lazy val actions: Seq[CheckedAction] = CommonActions
  }

}