package core3.database.containers

/**
  * Definition trait for containers supporting search (Solr & ElasticSearch) data handling.
  */
trait SearchContainerDefinition {
  /**
    * Retrieves the container's supported search field and their corresponding data types.
    *
    * @return the requested search field
    */
  def getSearchFields: Map[String, String]
}
