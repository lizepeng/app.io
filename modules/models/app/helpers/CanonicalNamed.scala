package helpers

/**
 * @author zepeng.li@gmail.com
 */
trait CanonicalNamed extends Any {

  /**
   * if empty specified then leave package name as full module name
   *
   * @return module name
   */
  def basicName: String

  def packageName: String = this.getClass.getPackage.getName

  def canonicalName: String =
    packageName + (if (basicName.isEmpty) "" else s".$basicName")
}

trait PackageNameAsCanonicalName extends CanonicalNamed {

  def basicName: String = ""
}

trait ClassNameAsCanonicalName extends CanonicalNamed {

  //@see regular-expressions "Look around"
  val namePattern = """\w+(?=\$|$)""".r

  val basicName = namePattern.findAllIn(this.getClass.getName).mkString(".")
}