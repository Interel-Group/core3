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
package core3.database.containers

/**
  * Container for containers.
  * <br><br>
  * Note: Only useful in caching / memory optimization scenarios and should probably be avoided for anything else.
  *
  * @param container the container to be referenced
  * @tparam A concrete container type/class
  */
final class ContainerRef[A](private var container: Option[A]) {
  private var removed = false

  /**
    * Directly retrieves the container from the underlying Option.
    *
    * @return the stored container
    * @throws NoSuchElementException if the underlying option is empty
    */
  def get: A = container.get

  /**
    * Drops the reference to the stored container, if any.
    *
    * @param containerRemoved set to true, if the container has been deleted;
    *                         set to false if it exists in a database and has only been evicted from a cache.
    */
  def drop(containerRemoved: Boolean): Unit = {
    container = None
    removed = containerRemoved
  }

  /**
    * Replaces the stored container, if any, with the supplied container.
    *
    * @param replacement the container to use as a replacement
    */
  def replace(replacement: A): Unit = {
    container = Some(replacement)
    removed = false
  }

  def flatMap[B](f: A => ContainerRef[B]): ContainerRef[B] = {
    container match {
      case Some(x) => f(x)
      case None => ContainerRef[B](None)
    }
  }

  def map[B](f: A => B): ContainerRef[B] = {
    container match {
      case Some(x) => ContainerRef[B](f(x))
      case None => ContainerRef[B](None)
    }
  }

  def toIterable: Iterable[A] = {
    container match {
      case Some(x) => Iterable[A](x)
      case None => Iterable.empty[A]
    }
  }

  def iterator: Iterator[A] =
    container match {
      case Some(x) => collection.Iterator.single(x)
      case None => collection.Iterator.empty
    }

  def isEmpty: Boolean = container.isEmpty

  def isDefined: Boolean = !isEmpty

  def nonEmpty: Boolean = !isEmpty

  def contains[B >: A](x: B): Boolean = !isEmpty && container.get == x

  def exists(f: A => Boolean): Boolean = !isEmpty && f(container.get)

  def getOrElse[B >: A](default: => B): B = if (isEmpty) default else container.get

  def wasRemoved: Boolean = removed
}

object ContainerRef {

  import scala.language.implicitConversions

  implicit def ref2Iterable[A](x: ContainerRef[A]): Iterable[A] = x.toIterable

  def empty[A]: ContainerRef[A] = ContainerRef[A](None)

  def apply[A](x: A): ContainerRef[A] = new ContainerRef(Some(x))

  def apply[A](x: Option[A]): ContainerRef[A] = new ContainerRef[A](x)
}