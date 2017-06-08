/**
  * Copyright 2017 Interel
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  * http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */
package core3

package object database {
  type ObjectID = java.util.UUID
  type RevisionID = java.util.UUID
  type RevisionSequenceNumber = Int
  type ContainerType = String
  type ContainerViewType = String

  /**
    * Generates a new random object ID.
    *
    * @return the generated ID
    */
  def getNewObjectID: ObjectID = java.util.UUID.randomUUID()

  /**
    * Generates a new random object revision ID.
    *
    * @return the generated ID
    */
  def getNewRevisionID: RevisionID = java.util.UUID.randomUUID()

  /**
    * Generates an initial object revision number.
    *
    * @return the generated revision number
    */
  def getInitialRevisionSequenceNumber: RevisionSequenceNumber = 1

  /**
    * Generates a new object revision number, based on the current one.
    * <br><br>
    * Note: Revision numbers are always sequential.
    *
    * @param currentNumber the current revision number used by the object
    * @return the generated revision number
    */
  def getNewRevisionSequenceNumber(currentNumber: RevisionSequenceNumber): RevisionSequenceNumber = currentNumber + 1
}
