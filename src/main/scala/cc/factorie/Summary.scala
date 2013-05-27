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
import cc.factorie.generative._
import cc.factorie.la.Outer1Tensor2

/** The result of inference: a collection of Marginal objects.
    @author Andrew McCallum */
trait Summary[+M<:Marginal] {
  /** The collection of all Marginals available in this Summary */
  def marginals: Iterable[M]
  /** If this Summary has a univariate Marginal for variable v, return it; otherwise return null. */
  def marginal(v:Var): M
  /** If this summary has a univariate Marginal for variable v, return it in an Option; otherwise return None. */
  def getMarginal(v:Var): Option[M] = { val m = marginal(v); if (m eq null) None else Some(m) }
  /** If this Summary has a Marginal that touches all or a subset of the neighbors of this factor
      return the Marginal with the maximally-available subset. */
  def marginal(factor:Factor): M
  def setToMaximize(implicit d:DiffList): Unit = marginals.foreach(_.setToMaximize(d)) // Note that order may matter here if Marginals overlap with each other!
  def logZ: Double = throw new Error("Summary class "+getClass+" does not provide logZ: "+getClass.getName)
  /** The Model factors used to calculate this Summary.  Not all Summary instances provide this, however. */
  def factors: Option[Iterable[Factor]] = None
  // /** The variables that are varying in this summary. */
  // def variables: Iterable[Variable] // TODO Should we also have a method like this?
}

/** A Summary that can be used to gather weighted samples into its Marginals. */
// TODO Consider the relationship between this and Accumulator
// TODO Consider removing this
trait IncrementableSummary[+M<:Marginal] extends Summary[M] {
  def incrementCurrentValues(weight:Double): Unit
}

/** A Summary that contains multiple Marginals of type M, each a marginal distribution over a single variable. */
class Summary1[V<:Var,M<:Marginal] {
  protected val _marginals = new scala.collection.mutable.HashMap[V,M]
  def marginals = _marginals.values
  def variables = _marginals.keys
  def marginal(v:Var): M = _marginals(v.asInstanceOf[V]) // We don't actually care that this type check does nothing because only Vs could be added to the HashMap
  def marginal(f:Factor): M = f match {
    case f:Factor1[_] => _marginals(f._1.asInstanceOf[V])
    //case f:Factor2[_,_] => {}
    case _ => null.asInstanceOf[M]
  }
  def +=(marginal:M) = {
    val vars = marginal.variables
    require(vars.size == 1)
    val v = vars.head.asInstanceOf[V]
    if (_marginals.contains(v)) throw new Error("Marginal already present for variable "+v)
    _marginals(v) = marginal
  }
}

/** A Summary containing only one Marginal. */
class SingletonSummary[M<:Marginal](val marginal:M) extends Summary[M] {
  def marginals = Seq(marginal)
  // TODO In the conditional below, order shouldn't matter!
  def marginal(v:Var): M = if (Seq(v) == marginal.variables) marginal else null.asInstanceOf[M]
  def marginal(f:Factor): M = null.asInstanceOf[M]
}

/** A Summary with all its probability on one variable-value Assignment.  Note that Assignment inherits from Marginal. */
class AssignmentSummary(val assignment:Assignment) extends Summary[Marginal] {
  def marginals = assignment.variables.map(v=> new Marginal {
    def variables = Seq(v)
    def setToMaximize(implicit d: DiffList) = v match { case vv:MutableVar[Any] => vv.set(assignment(vv)) }
  })
  def marginal(v:Var): Marginal = null
  def marginal(f:Factor): Marginal = null.asInstanceOf[Marginal]
  override def setToMaximize(implicit d:DiffList): Unit = assignment.globalize(d)
}

/** A summary with a separate Proportions distribution for each of its DiscreteVars */
// TODO Consider renaming FullyFactorizedDiscreteSummary or IndependentDiscreteSummary or PerVariableDiscreteSummary
// TODO Consider making this inherit from Summary1
class DiscreteSummary1[V<:DiscreteVar] extends IncrementableSummary[DiscreteMarginal] {
  def this(vs:Iterable[V]) = { this(); ++=(vs) }
  //val variableClass = m.erasure
  val _marginals1 = new scala.collection.mutable.HashMap[V,DiscreteMarginal1[V]]
  def marginals = _marginals1.values
  def variables = _marginals1.keys
  lazy val variableSet = variables.toSet
  def marginal(v1:Var) = _marginals1(v1.asInstanceOf[V])
  def marginal2(vs:Var*): DiscreteMarginal = vs match {
    case Seq(v:V) => _marginals1(v) // Note, this doesn't actually check for a type match on V, because of erasure, but it shoudn't matter
    case Seq(v:V, w:V) => new DiscreteMarginal2[V,V](v, w, new NormalizedTensorProportions2(new Outer1Tensor2(_marginals1(v).proportions,_marginals1(w).proportions), false))
    case _ => null
  }
  def marginal(f:Factor): DiscreteMarginal = null
  def +=(m:DiscreteMarginal1[V]): Unit = _marginals1(m._1) = m
  def +=(v:V): Unit = this += new DiscreteMarginal1(v, null) // but not yet initialized marginal proportions
  def ++=(vs:Iterable[V]): Unit = vs.foreach(+=(_))
  //def ++=(ms:Iterable[DiscreteMarginal1[V]]): Unit = ms.foreach(+=(_))
  def incrementCurrentValues(weight:Double): Unit = for (m <- marginals) m.incrementCurrentValue(weight)
  //def maximize(implicit d:DiffList): Unit = for (m <- marginals) m._1.asInstanceOf[DiscreteVariable].set(m.proportions.maxIndex)
}
