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

import sbt.State
import sbtrelease.ReleasePlugin.autoImport.{ReleaseStep, releaseVcs}
import sbtrelease.Utilities._
import sbtrelease.{Git, Utilities}

object ReleaseHelpers {
  def checkout(fromBranch: String, toBranch: String): (State) => State = {
    state: State =>
      state.extract.get(releaseVcs) match {
        case Some(git: Git) =>
          if (git.currentBranch == fromBranch) {
            git.cmd("checkout", toBranch) ! state.log
            git.cmd("pull") ! state.log
            state
          } else {
            state.log.error(s"Unexpected branch encountered: [${git.currentBranch}]; must be on [$fromBranch].")
            state.fail
          }
        case _ =>
          state.log.error("Unexpected VCS found.")
          state.fail
      }
  }

  def merge(withBranch: String, fastForward: String): (State) => State = {
    state: State =>
      state.extract.get(releaseVcs) match {
        case Some(git: Git) =>
          git.cmd("merge", withBranch, fastForward) ! state.log
          state
        case _ =>
          state.log.error("Unexpected VCS found.")
          state.fail
      }
  }

  lazy val checkoutMaster = ReleaseStep(checkout("develop", "master"))
  lazy val checkoutDevelop = ReleaseStep(checkout("master", "develop"))
  lazy val mergeWithDevelop = ReleaseStep(merge("develop", "--no-ff"))
  lazy val mergeWithMaster = ReleaseStep(merge("master", "--ff"))
}
