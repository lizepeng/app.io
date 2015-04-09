package helpers

import com.datastax.driver.core.utils.UUIDs

/**
 * @author zepeng.li@gmail.com
 */
trait SysConfig {
  self: ModuleLike =>

  def getUUID(key: String) = {
    models.sys.SysConfig
      .getOrElseUpdate(fullModuleName, key, UUIDs.timeBased())
  }
}