package helpers

/**
 * @author zepeng.li@gmail.com CanonicalNamed
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