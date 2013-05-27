/* Copyright (C) 2008-2010 University of Massachusetts Amherst,
   Department of Computer Science.
   This file is part of "FACTORIE" (Factor graphs, Imperative, Extensible)
   http://factorie.cs.umass.edu, http://code.google.com/p/factorie/
   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at
    http://www.apache.org/licenses/LICENSE-2.0
   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package cc.factorie

import cc.factorie

/** Typically Variable instances hold their value internally.
    Alternatively, variable values can be stored in an Assignment: a
    mapping from variables to their values.
    
    An Assignment is also a Summary, able to give , with all its probability on one set of values.

    Alex: an Assignment shouldn't be a Marginal, it should be able to give you marginals
    of some of its variables. I imagine Assignments as "global" things and Marginals as "local". Let's talk.
    
    
    Note that this trait doesn't inherit directly from scala.collection.Map
    because we need a special type signature for 'apply' and 'get'.
    @author Andrew McCallum */
// TODO Yes, make Assignment extend Summary. -akm 
trait Assignment {
  /** All variables with values in this Assignment */
  def variables: Iterable[Var]
  /** Return the value assigned to variable v, or throw an Error if the variable is not in this Assignment. */
  def apply[B<:Var](v:B): B#Value
  /** Return the an Option for the value assigned to variable v.  If v is not contained in this Assignment return None. */
  def get[B<:Var](v:B): Option[B#Value]
  /** Return true if this Assignment has a value for variable v. */
  def contains(v:Var): Boolean
  def getOrElse[B<:Var](v:B, default: => B#Value): B#Value = if (contains(v)) apply(v) else default
  /** Set variables to the values specified in this assignment */
  // TODO Rename this to "set" or "setVariables" -akm
  def globalize(implicit d:DiffList): Unit = {
    for (v <- variables) v match {
      case v:MutableVar[Any] => v.set(this.apply(v))(d)
      case _ => throw new Error
    }
  }
  // For Marginal trait
  // TODO Now that Assignment is no longer a Marginal, remove this.
  final def setToMaximize(implicit d:DiffList): Unit = this.globalize
}

/** An Assignment in which variable-value mappings can be changed.
    @author Andrew McCallum */
trait MutableAssignment extends Assignment {
  def update[V<:Var, U <: V#Value](variable:V, value:U): Unit
}

/** For LabeledVar return the targetValue, otherwise return the current global assignment. */
object TargetAssignment extends Assignment {
  def variables = throw new Error("Cannot list all variables of the TargetAssignment.")
  def apply[V<:Var](v:V): V#Value = v match {
    case v:LabeledVar => v.targetValue.asInstanceOf[V#Value]
    case v:Var => v.value.asInstanceOf[V#Value]
  }
  def get[V<:Var](v:V): Option[V#Value] = Some(apply(v))
  def contains(v:Var) = true
  override def globalize(implicit d:DiffList): Unit = throw new Error("Cannot set a TargetAssignment.  Instead use variables.setToTarget(DiffList).")
}

/** A MutableAssignment backed by a HashMap.
    @author Andrew McCallum */
class HashMapAssignment extends MutableAssignment {
  private val map = new scala.collection.mutable.HashMap[Var, Any]
  def this(variables:Var*) = { this(); variables.foreach(v => update(v, v.value.asInstanceOf[v.Value])) }
  def this(variables:Iterable[Var]) = { this(); variables.foreach(v => update(v, v.value.asInstanceOf[v.Value])) }
  def variables = map.keys
  def apply[V<:Var](v:V): V#Value = { val a = map(v); if (null != a) a.asInstanceOf[V#Value] else throw new Error("Variable not present: "+v) }
  def get[V<:Var](v:V): Option[V#Value] = map.get(v).map(_.asInstanceOf[V#Value])
  def update[V<:Var, U <: V#Value](variable:V, value:U): Unit = map(variable) = value
  def contains(v:Var) = map.contains(v)
}

/** An efficient abstract Assignment of one variable.
    Values for variables not in this assigment are taken from those variables themselves (the "global" assignment).
    @author Andrew McCallum */
trait AbstractAssignment1[A<:Var] extends Assignment {
  def _1: A
  def value1: A#Value
  def variables = Seq(_1) // TODO Consider making this a Set.
  def apply[B<:Var](v:B): B#Value = if (v eq _1) value1.asInstanceOf[B#Value] else v.value.asInstanceOf[B#Value] // throw new Error("Variable not present: "+v)
  def get[B<:Var](v:B): Option[B#Value] = if (v eq _1) Some(value1.asInstanceOf[B#Value]) else None
  def contains(v:Var): Boolean = if (v eq _1) true else false
  override def globalize(implicit d:DiffList): Unit = _1 match { case v:MutableVar[_] => v.set(value1.asInstanceOf[v.Value]) }
}

/** An efficient Assignment of one variable.
    @author Andrew McCallum */
class Assignment1[A<:Var](val _1:A, var value1:A#Value) extends AbstractAssignment1[A]

/** An efficient Assignment of one DiscreteVar. */
class DiscreteAssignment1[A<:DiscreteVar](override val _1:A, initialIntValue1:Int) extends AbstractAssignment1[A] with MutableAssignment {
  def this(variable:A, initialValue:A#Value) = this(variable, initialValue.intValue)
  private var _intValue1 = initialIntValue1
  def intValue1: Int = _intValue1
  def intValue1_=(i:Int): Unit = _intValue1 = i
  def value1: A#Value = _1.domain(_intValue1).asInstanceOf[A#Value]
  def value1_=(v:A#Value): Unit = _intValue1 = v.intValue
  def update[V<:Var, U<:V#Value](variable:V, value:U): Unit = if (variable eq _1) _intValue1 = value.asInstanceOf[DiscreteValue].intValue else throw new Error("Cannot update DiscreteAssignment1 value for variable not present.")
}

/** An efficient abstract Assignment of two variables.
    Values for variables not in this assigment are taken from those variables themselves (the "global" assignment).
    @author Andrew McCallum */
trait AbstractAssignment2[A<:Var,B<:Var] extends Assignment {
  def _1: A
  def _2: B
  def value1: A#Value
  def value2: B#Value
  def variables = Seq(_1, _2)
  def apply[C<:Var](v:C): C#Value = if (v eq _1) value1.asInstanceOf[C#Value] else if (v eq _2) value2.asInstanceOf[C#Value] else v.value.asInstanceOf[C#Value] // throw new Error("Variable not present: "+v)
  def get[C<:Var](v:C): Option[C#Value] = if (v eq _1) Some(value1.asInstanceOf[C#Value]) else if (v eq _2) Some(value2.asInstanceOf[C#Value]) else None
  def contains(v:Var): Boolean = if ((v eq _1) || (v eq _2)) true else false
  override def globalize(implicit d:DiffList): Unit = {
    _1 match { case v:MutableVar[_] => v.set(value1.asInstanceOf[v.Value]) }
    _2 match { case v:MutableVar[_] => v.set(value2.asInstanceOf[v.Value]) }
  }
}
/** An efficient Assignment of two variables.
    @author Andrew McCallum */
class Assignment2[A<:Var,B<:Var](val _1:A, var value1:A#Value, val _2:B, var value2:B#Value) extends AbstractAssignment2[A,B]

/** An efficient abstract Assignment of three variables.
    @author Andrew McCallum */
trait AbstractAssignment3[A<:Var,B<:Var,C<:Var] extends Assignment {
  def _1: A
  def _2: B
  def _3: C
  def value1: A#Value
  def value2: B#Value
  def value3: C#Value
  def variables = Seq(_1, _2, _3)
  def apply[X<:Var](v:X): X#Value = if (v eq _1) value1.asInstanceOf[X#Value] else if (v eq _2) value2.asInstanceOf[X#Value] else if (v eq _3) value3.asInstanceOf[X#Value] else v.value.asInstanceOf[X#Value] // throw new Error("Variable not present: "+v)
  def get[C<:Var](v:C): Option[C#Value] = if (v eq _1) Some(value1.asInstanceOf[C#Value]) else if (v eq _2) Some(value2.asInstanceOf[C#Value]) else if (v eq _3) Some(value3.asInstanceOf[C#Value]) else None
  def contains(v:Var): Boolean = if ((v eq _1) || (v eq _2) || (v eq _3)) true else false
  override def globalize(implicit d:DiffList): Unit = {
    _1 match { case v:MutableVar[_] => v.set(value1.asInstanceOf[v.Value]) }
    _2 match { case v:MutableVar[_] => v.set(value2.asInstanceOf[v.Value]) }
    _3 match { case v:MutableVar[_] => v.set(value3.asInstanceOf[v.Value]) }
  }
}
/** An efficient Assignment of three variables.
    @author Andrew McCallum */
class Assignment3[A<:Var,B<:Var,C<:Var](val _1:A, var value1:A#Value, val _2:B, var value2:B#Value, val _3:C, var value3:C#Value) extends AbstractAssignment3[A,B,C]

/** An efficient abstract Assignment of three variables.
    @author Andrew McCallum */
trait AbstractAssignment4[A<:Var,B<:Var,C<:Var,D<:Var] extends Assignment {
  def _1: A
  def _2: B
  def _3: C
  def _4: D
  def value1: A#Value
  def value2: B#Value
  def value3: C#Value
  def value4: D#Value
  def variables = Seq(_1, _2, _3, _4)
  def apply[X<:Var](v:X): X#Value = if (v eq _1) value1.asInstanceOf[X#Value] else if (v eq _2) value2.asInstanceOf[X#Value] else if (v eq _3) value3.asInstanceOf[X#Value] else if (v eq _4) value4.asInstanceOf[X#Value] else v.value.asInstanceOf[X#Value] // throw new Error("Variable not present: "+v)
  def get[C<:Var](v:C): Option[C#Value] = if (v eq _1) Some(value1.asInstanceOf[C#Value]) else if (v eq _2) Some(value2.asInstanceOf[C#Value]) else if (v eq _3) Some(value3.asInstanceOf[C#Value]) else if (v eq _4) Some(value4.asInstanceOf[C#Value]) else None
  def contains(v:Var): Boolean = if ((v eq _1) || (v eq _2) || (v eq _3) || (v eq _4)) true else false
  override def globalize(implicit d:DiffList): Unit = {
    _1 match { case v:MutableVar[_] => v.set(value1.asInstanceOf[v.Value]) }
    _2 match { case v:MutableVar[_] => v.set(value2.asInstanceOf[v.Value]) }
    _3 match { case v:MutableVar[_] => v.set(value3.asInstanceOf[v.Value]) }
    _4 match { case v:MutableVar[_] => v.set(value4.asInstanceOf[v.Value]) }
  }
}
/** An efficient Assignment of three variables.
    @author Andrew McCallum */
class Assignment4[A<:Var,B<:Var,C<:Var,D<:Var](val _1:A, var value1:A#Value, val _2:B, var value2:B#Value, val _3:C, var value3:C#Value, val _4:D, var value4:D#Value) extends AbstractAssignment4[A,B,C,D]




/** An Assignment whose values are those stored inside the variables themselves. 
    @author Andrew McCallum */
object GlobalAssignment extends Assignment {
  def variables = throw new Error("Cannot list all variables of the global Assignment.")
  def apply[V<:Var](v:V): V#Value = v.value.asInstanceOf[V#Value]
  def get[V<:Var](v:V): Option[V#Value] = Some(v.value.asInstanceOf[V#Value])
  def contains(v:Var) = true
  override def globalize(implicit d:DiffList): Unit = {}
}

/** An Assignment backed by a sequence of assignments.  
    The returned value will be from the first Assignment in the sequence to contain the variable. 
    @author Andrew McCallum */
class AssignmentStack(val assignment:Assignment, val next:AssignmentStack = null) extends Assignment {
  def variables = assignment.variables ++ next.variables
  protected def apply[V<:Var](v:V, s:AssignmentStack): V#Value =
    if (s.next eq null) s.assignment.apply(v)
    else s.assignment.getOrElse(v, apply(v, s.next))
  def apply[V<:Var](v:V): V#Value = apply(v, this)
  protected def get[V<:Var](v:V, s:AssignmentStack): Option[V#Value] = {
    val o = assignment.get(v)
    if (o != None) o
    else if (s.next ne null) s.next.get(v)
    else None
  }
  def get[V<:Var](v:V): Option[V#Value] = get(v, this)
  def contains(v:Var) = if (assignment.contains(v)) true else next.contains(v)
  /** Returns a new Assignment stack, the result of prepending Assignment a. */
  def +:(a:Assignment): AssignmentStack = new AssignmentStack(a, this)
}

/**
 * Allows an iterator over the assignments to the neighbors of a factor (optionally specifying the variables that should vary)
 * @author Sameer
 */
object AssignmentIterator {
  def assignments1[N1 <: Var](f1: Factor1[N1], varying: Set[Var]): Iterator[Assignment] = assignments1(f1._1, varying)

  def assignments1[N1 <: Var](v1:N1, varying: Set[Var]): Iterator[Assignment] = {
    if (varying(v1)) 
      //v1.domain.iterator.map(value => new Assignment1(v1, value.asInstanceOf[v1.Value]))
      v1.domain match { case d: DiscreteDomain => d.iterator.map(value => new Assignment1(v1, value.asInstanceOf[v1.Value])) }
    else Iterator.empty
  }

  def assignments2[N1 <: Var, N2 <: Var](f2: Factor2[N1, N2], varying: Set[Var]): Iterator[Assignment] = assignments2(f2._1, f2._2, varying)

  def assignments2[N1 <: Var, N2 <: Var](v1:N1, v2:N2, varying: Set[Var]): Iterator[Assignment] = {
    val values1 = if (varying.contains(v1)) v1.domain.asInstanceOf[DiscreteDomain] else Seq(v1.value.asInstanceOf[DiscreteValue])
    val values2 = if (varying.contains(v2)) v2.domain.asInstanceOf[DiscreteDomain] else Seq(v2.value.asInstanceOf[DiscreteValue])
    (for (val1 <- values1; val2 <- values2) yield new Assignment2(v1, val1.asInstanceOf[v1.Value], v2, val2.asInstanceOf[v2.Value])).iterator
  }

  def assignments3[N1 <: Var, N2 <: Var, N3 <: Var](f3: Factor3[N1, N2, N3], varying: Set[Var]): Iterator[Assignment] = assignments3(f3._1, f3._2, f3._3, varying)

  def assignments3[N1 <: Var, N2 <: Var, N3 <: Var](v1:N1, v2:N2, v3:N3, varying: Set[Var]): Iterator[Assignment] = {
    val values1 = if (varying.contains(v1)) v1.domain.asInstanceOf[DiscreteDomain] else Seq(v1.value.asInstanceOf[DiscreteValue])
    val values2 = if (varying.contains(v2)) v2.domain.asInstanceOf[DiscreteDomain] else Seq(v2.value.asInstanceOf[DiscreteValue])
    val values3 = if (varying.contains(v3)) v3.domain.asInstanceOf[DiscreteDomain] else Seq(v3.value.asInstanceOf[DiscreteValue])
    (for (val1 <- values1; val2 <- values2; val3 <- values3) yield new Assignment3(v1, val1.asInstanceOf[v1.Value], v2, val2.asInstanceOf[v2.Value], v3, val3.asInstanceOf[v3.Value])).iterator
  }

  def assignments4[N1 <: Var, N2 <: Var, N3 <: Var, N4 <: Var](f4: Factor4[N1, N2, N3, N4], varying: Set[Var]): Iterator[Assignment] = assignments4(f4._1, f4._2, f4._3, f4._4, varying)

  def assignments4[N1 <: Var, N2 <: Var, N3 <: Var, N4 <: Var](v1:N1, v2:N2, v3:N3, v4:N4, varying: Set[Var]): Iterator[Assignment] = {
    val values1 = if (varying.contains(v1)) v1.domain.asInstanceOf[DiscreteDomain] else Seq(v1.value.asInstanceOf[DiscreteValue])
    val values2 = if (varying.contains(v2)) v2.domain.asInstanceOf[DiscreteDomain] else Seq(v2.value.asInstanceOf[DiscreteValue])
    val values3 = if (varying.contains(v3)) v3.domain.asInstanceOf[DiscreteDomain] else Seq(v3.value.asInstanceOf[DiscreteValue])
    val values4 = if (varying.contains(v4)) v4.domain.asInstanceOf[DiscreteDomain] else Seq(v4.value.asInstanceOf[DiscreteValue])
    (for (val1 <- values1; val2 <- values2; val3 <- values3; val4 <- values4) yield new Assignment4(v1, val1.asInstanceOf[v1.Value], v2, val2.asInstanceOf[v2.Value], v3, val3.asInstanceOf[v3.Value], v4, val4.asInstanceOf[v4.Value])).iterator
  }

  def assignments(vars: Seq[Var]): Iterator[Assignment] = {
    if(vars.length == 1) assignments1(vars.head, vars.toSet)
    else if(vars.length == 2) assignments2(vars(0), vars(1), vars.toSet)
    else if(vars.length == 3) assignments3(vars(0), vars(1), vars(2), vars.toSet)
    else if(vars.length == 4) assignments4(vars(0), vars(1), vars(2), vars(3), vars.toSet)
    else throw new Error ("To many variables to iterate over (>4): " + vars.length)
  }

  def assignments(f: Factor, varying: Set[Var]): Iterator[Assignment] = {
    f match {
      // Factor 1
      case f1: Factor1[_] => assignments1(f1, varying)
      // Factor 2
      case f2: Factor2[_, _] => assignments2(f2, varying)
      // Factor 3
      case f3: Factor3[_, _, _] => assignments3(f3, varying)
      // Factor 4
      case f4: Factor4[_, _, _, _] => assignments4(f4, varying)
    }
  }

}
