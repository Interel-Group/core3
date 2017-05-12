package core3.database.containers

/**
  * Implementation & usage trait for containers supporting search (Solr & ElasticSearch) data handling.
  */
trait SearchContainerCompanion extends JsonContainerCompanion {
  /**
    * Retrieves the container's supported search field and their corresponding data types.
    *
    * @return the requested search field
    */
  def getSearchFields: Map[String, String]
}
