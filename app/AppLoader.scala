import play.api.ApplicationLoader.Context

/**
 * @author zepeng.li@gmail.com
 */
class AppLoader
  extends play.api.ApplicationLoader {

  def load(context: Context) = {
    new Components(context).application
  }
}