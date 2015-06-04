package helpers

/**
 * @author zepeng.li@gmail.com CanonicalNamed
 */
trait CanonicalNamed {

  /**
   * if empty specified then leave package name as full module name
   *
   * @return module name
   */
  def basicName: String = ""

  def canonicalName: String =
    this.getClass.getPackage.getName +
      (if (basicName.isEmpty) "" else s".$basicName")
}