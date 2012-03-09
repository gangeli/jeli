package org.goobs.util

import scala.util.Random
import scala.collection.mutable.Map
import scala.collection.mutable.HashMap

import org.goobs.nlp.SearchState
import org.goobs.nlp.Search


case class Term(b:Long,e:Long) extends Intersectable {
	override def begin:Long = b
	override def end:Long = e
	override def toString:String = "["+begin+","+end+"]"
}

class RepeatedTerm(norm:Long,jump:Long,begin:Long
		) extends ProvidesIntersectables[Term] {
	override def has(offset:Long):Boolean = {
		try{ Thread.sleep(1); } catch { case _ => }
		true
	}
	override def intersectable(offset:Long):Term = {
		try{ Thread.sleep(1); } catch { case _ => }
		Term(begin+jump*offset,begin+jump*offset+norm)
	}
	override def toString:String = ""+norm + " every " + jump
}

object RepeatedTerm {
	def apply():RepeatedTerm
		= new RepeatedTerm(Random.nextInt(10),Random.nextInt(10000000),0L)
}


//------------------------------------------------------------------------------
// SOME UTILITIES
//------------------------------------------------------------------------------
class IteratorMap[A <: {def index:Int}](
		iterForward:Iterator[A],iterBackward:Iterator[A]) extends Map[Int,A] {
	private val mapImpl:HashMap[Int,A] = new HashMap[Int,A]
	override def += (kv:(Int,A)) = {
		throw new UnsupportedOperationException("Cannot add to iterable map")
	}
	override def -= (k:Int) = {
		throw new UnsupportedOperationException("Cannot remove from iterable map")
	}

	override def get (key:Int):Option[A] = {
		var containsKey = mapImpl.contains(key)
		while(!containsKey &&
				((key>=0 && iterForward.hasNext) || (key<0 && iterBackward.hasNext))) {
			val value = if(key >= 0){ iterForward.next } else { iterBackward.next }
			mapImpl(value.index) = value
			if(value.index == key){containsKey = true}
		}
		mapImpl.get(key)
	}

	override def iterator:Iterator[(Int,A)] = {
		(1 until Int.MaxValue).iterator.map{ case (i:Int) =>
			if(i % 2 == 0){ (i/2, apply( i/2 )) }
			else { ((i-1)/2, apply( -(i-1)/2 )) }
		}
	}
}


//------------------------------------------------------------------------------
// INTERSECT FUNCTIONALITY
//------------------------------------------------------------------------------
trait Intersectable {
	def begin:Long
	def end:Long
}

trait ProvidesIntersectables[A <: Intersectable] {
	def has(offset:Long):Boolean
	def intersectable(offset:Long):A
}

case class Intersection(index:Int,a:Long,b:Long,origin:(Long,Long)){
	override def toString:String = {
		"["+index+"] ("+a+","+b+") originating from " + origin
	}
}

object Intersect {

	def intersectForward[A <: Intersectable](
			sourceA:ProvidesIntersectables[A],
			sourceB:ProvidesIntersectables[A]):Iterator[Intersection]
		= intersect(sourceA,sourceB,1)
	def intersectBackward[A <: Intersectable](
			sourceA:ProvidesIntersectables[A],
			sourceB:ProvidesIntersectables[A]):Iterator[Intersection]
		= intersect(sourceA,sourceB,-1)
	def intersect[A <: Intersectable](
			sourceA:ProvidesIntersectables[A],
			sourceB:ProvidesIntersectables[A]):Iterator[Intersection]
		= intersect(sourceA,sourceB,0)

	private def intersect[A <: Intersectable](
			sourceA:ProvidesIntersectables[A],
			sourceB:ProvidesIntersectables[A],
			initialDir:Int):Iterator[Intersection] = {
		//--Pruning State
		var min:Long = Long.MaxValue
		var max:Long = Long.MinValue
		var zeroDiff:Long = 0L
		zeroDiff = (new TermSearchState(0L,0L)).offsetBetween //compiler hax
		//--Search State
		case class TermSearchState(a:Long,b:Long,step:Long,dir:Int,moving:Symbol
				) extends SearchState {
			def this(a:Long,b:Long) = this(a,b,1L,0,'None)
			def this(a:Long,b:Long,dir:Int) = this(a,b,1L,dir,'None)

			var origin:Option[(Long,Long)] = None
			private var cachedA:A = null.asInstanceOf[A]
			private var cachedB:A = null.asInstanceOf[A]
			private lazy val isMatch = this.isEndState

			private def ensureTerms:Boolean = {
				if(cachedA == null){
					if(!sourceA.has(a)){ return false }
					cachedA = sourceA.intersectable(a)
				}
				if(cachedB == null){
					if(!sourceB.has(b)){ return false }
					cachedB = sourceB.intersectable(b)
				}
				true
			}
			override def children:List[SearchState] = {
				if(!ensureTerms){ return List[SearchState]() }
				def sameDiff(d1:Long,d2:Long):Boolean = {
					((d1 <= 0 && d2 <= 0) || (d1 >= 0 && d2 >= 0)) && 
					math.abs(d1-d2) < (0.05*math.abs(d1)).toLong &&
					math.abs(d1-d2) < (0.05*math.abs(d2)).toLong
				}
				//--Invalid State
				//(already past here)
				if(  (dir > 0 && cachedA.begin < max && cachedB.begin < max) ||
				     (dir < 0 && cachedA.end   > min && cachedB.end   > min)    ) {
					return List[SearchState]()
				}
				//(insufficient progress)
				if(!isMatch && a == b && a != 0L && sameDiff(offsetBetween,zeroDiff)){
					return List[SearchState]()
				}
				//(jumped into the middle)
				if(isValidIntersect && step > 1){
					return List[SearchState]()
				}
				//--Propose Child
				//(propose function)
				def propose(aI:Long,bI:Long,theStep:Long,dir:Int,moving:Symbol
						):Option[TermSearchState] = {
					//(target exists)
					val exists = sourceA.has(aI) && sourceB.has(bI) && theStep > 0
					//(moving in impossible direction)
					val isImpossible = moving match {
							case 'A =>
								if(dir > 0){ cachedA.begin >= cachedB.end }
								else if(dir < 0){ cachedA.end <= cachedB.begin }
								else { false }
							case 'B =>
								if(dir > 0){ cachedB.begin >= cachedA.end }
								else if(dir < 0){ cachedB.end <= cachedA.begin }
								else { false }
							case _ => throw new IllegalArgumentException
						}
					if(exists && !isImpossible){
						//(create candidate)
						val cand = TermSearchState(aI,bI,theStep,dir,moving)
						if(cand.isMatch){ cand.origin = origin.orElse(Some(aI,bI)) }
//							if(isMatch){
//								TermSearchState(aI,bI,theStep,dir,moving,origin.orElse(Some(aI,bI)))
//							} else {
//								TermSearchState(aI,bI,theStep,dir,moving,None)
//							}
						if(cand.ensureTerms){
							//(check if candidate jumps too far)
							val jumpedOver = moving match {
									case 'A =>
										if(dir > 0){ 
											cachedA.end < cachedB.begin &&
											cand.cachedA.begin > cand.cachedB.end
										} else if(dir < 0) {
											cachedA.begin > cachedB.end &&
											cand.cachedA.end < cand.cachedB.begin
										} else { false }
									case 'B =>
										if(dir > 0){ 
											cachedB.end < cachedA.begin &&
											cand.cachedB.begin > cand.cachedA.end 
										} else if(dir < 0){ 
											cachedB.begin > cachedA.end &&
											cand.cachedB.end < cand.cachedA.begin 
										} else { false }
									case _ => throw new IllegalArgumentException
								}
							if(jumpedOver && false){
								None
							} else {
								Some(cand) // finally, it's ok!
							}
						} else {
							None
						}
					} else {
						None
					}
				}
				//--Create Children
				var lst = List[Option[TermSearchState]]()
				if(dir >= 0){
					//(forwards)
					lst = propose(a+1,b,1L,1,'A) :: lst
					if(moving == 'A){
						lst = propose(a+(step*2),b,step*2,1,'A) :: lst
					}
					lst = propose(a,b+1,1L,1,'B) :: lst
					if(moving == 'B){
						lst = propose(a,b+(step*2),step*2,1,'B) :: lst
					}
				}
				if(dir <= 0){
					//(backward)
					lst = propose(a-1,b,1L,-1,'A) :: lst
					if(moving == 'A){
						lst = propose(a-(step*2),b,step*2,-1,'A) :: lst
					}
					lst = propose(a,b-1,1L,-1,'B) :: lst
					if(moving == 'B){
						lst = propose(a,b-(step*2),step*2,-1,'B) :: lst
					}
				}
				//--Return
				val rtn = lst.filter{ _ match{ case None => false case _ => true } }
				rtn.map{ _.orNull }
			}
			private def isValidIntersect:Boolean = {
				//(check intersect cases)
				val aInB:Boolean = 
					cachedA.begin >= cachedB.begin &&
					cachedA.end <= cachedB.end
				val bInA:Boolean = 
					cachedA.begin <= cachedB.begin &&
					cachedA.end >= cachedB.end
				val bTailsA:Boolean =
					cachedA.begin <= cachedB.begin &&
					cachedB.begin < cachedA.end &&
					cachedA.end <= cachedB.end
				val aTailsB:Boolean =
					cachedB.begin <= cachedA.begin &&
					cachedA.begin < cachedB.end &&
					cachedB.end <= cachedA.end
				//(return)
				aInB || bInA || bTailsA || aTailsB
			}
			override def isEndState:Boolean = {
				//(ensure terms)
				if(!ensureTerms){ return false }
				val isEnd = isValidIntersect && step == 1
				//(update cache)
				if(isEnd){
					var posCand:Long = Long.MinValue
					if(cachedA.begin > posCand){ posCand = cachedA.begin }
					if(cachedB.begin > posCand){ posCand = cachedB.begin }
					var negCand:Long = Long.MaxValue
					if(cachedA.end < negCand){ negCand = cachedA.end }
					if(cachedB.end < negCand){ negCand = cachedB.end }
					if(negCand < 0){
						min = math.min(min,negCand)
					}
					if(posCand > 0){
						max = math.max(max,posCand)
					}
				}
				//(return)
				isEnd
			}
			
			def distanceBetween:Long = {
				if(!ensureTerms){ return Long.MaxValue/2 }
				if(isEndState) { 0L }
					else if(cachedA.end < cachedB.begin) { cachedB.begin - cachedA.end }
					else if(cachedB.end < cachedA.begin) { cachedA.begin - cachedB.end }
					else { 0L }
			}
			def offsetBetween:Long = {
				if(!ensureTerms){ return Long.MaxValue/2 }
				cachedB.begin - cachedA.begin
			}
			def distanceOffset:Long = {
				if(!ensureTerms){ return Long.MaxValue/2 }
				math.max(
					//((distance from origin to A))
					if(cachedA.begin > 0) { cachedA.begin }
					else if(cachedA.end < 0){ -cachedA.end }
					else { 0L },
					//((distance from origin to B))
					if(cachedB.begin > 0) { cachedB.begin }
					else if(cachedB.end < 0){ -cachedB.end }
					else { 0L })
			}
			override def cost:Double = (distanceBetween+distanceOffset).toDouble
			override def assertEnqueueable:Boolean = {
				step > 0
			}
			override def assertDequeueable:Boolean = {
				ensureTerms
				val beginA = if(cachedA != null){ cachedA.begin } else { -42 }
				val endA = if(cachedA != null){ cachedA.end } else { -42 }
				val beginB = if(cachedB != null){ cachedB.begin } else { -42 }
				true
			}
			override def toString:String = {
				"{"+a+","+b+":"+step+","+
				{if(dir > 0) " -> " else if(dir < 0) " <- " else " <-> "}+"}"
			}
			override def hashCode:Int = (a ^ b).toInt
			override def equals(o:Any):Boolean = {
				o match {
					case (s:TermSearchState) => s.a == a && s.b == b && s.step == step
					case _ => false
				}
			}
		}
		//--Iterable
		var isFirst = true
		var matchesPos = 0
		var matchesNeg = 0
		Search[TermSearchState](Search.cache(Search.UNIFORM_COST))
			.iterable(new TermSearchState(0L,0L,initialDir),1000).iterator
			.map{ case (state:TermSearchState,count:Int) =>
				val index 
					= if(isFirst) { isFirst = false; 0 }
					  else if(state.dir >= 0) { matchesPos += 1; matchesPos }
					  else { matchesNeg -= 1; matchesNeg }
				Intersection(index,state.a,state.b,state.origin.getOrElse((state.a, state.b)))
			}
	}
}
