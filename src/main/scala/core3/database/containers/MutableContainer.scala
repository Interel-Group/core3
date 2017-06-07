package core3.database.containers

import core3.database.{RevisionID, RevisionSequenceNumber}
import core3.utils.Timestamp

/**
  * Container trait for update-able objects with support for revisions.
  * <br><br>
  * Note: The container itself does NOT update revision IDs and/or numbers upon field changes.
  */
trait MutableContainer extends Container {
  val created: Timestamp
  var updated: Timestamp
  var updatedBy: String
  var revision: RevisionID
  var revisionNumber: RevisionSequenceNumber
}
