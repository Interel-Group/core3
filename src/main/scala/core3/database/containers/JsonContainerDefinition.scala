package core3.database.containers

import play.api.libs.json.JsValue

/**
  * Definition trait for containers supporting JSON data handling.
  */
trait JsonContainerDefinition extends ContainerDefinition {
  /**
    * Converts the supplied container to a JSON value.
    *
    * @param container the container to be converted
    * @return the container as a JSON value
    */
  def toJsonData(container: Container): JsValue

  /**
    * Converts the supplied JSON value to a container.
    *
    * @param data the JSON value to be converted
    * @return the converted container
    */
  def fromJsonData(data: JsValue): Container
}
