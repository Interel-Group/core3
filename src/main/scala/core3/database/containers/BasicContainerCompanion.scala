package core3.database.containers

/**
  * Basic implementation & usage trait for containers.
  */
trait BasicContainerCompanion {
  /**
    * Retrieves the container's database name.
    *
    * @return the requested database name
    * @throws IllegalArgumentException if the data type is not supported
    */
  def getDatabaseName: String

  /**
    * Checks if the supplied container matches the specified query name and parameters.
    *
    * @param queryName   the name of the query to use for the check
    * @param queryParams the parameters to use for the check
    * @param container   the container to work with
    * @return true, if the container matches the query
    * @throws NotImplementedError      if the container does not support custom query matching
    * @throws IllegalArgumentException if an invalid query name is specified
    */
  def matchCustomQuery(queryName: String, queryParams: Map[String, String], container: Container): Boolean
}
