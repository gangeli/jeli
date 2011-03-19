package org.goobs.slib

import java.util.Iterator

import org.goobs.functional.Function
import org.goobs.functional.Function2
import org.goobs.functional.Function3

import scala.collection.mutable.ListBuffer

object JMaps {
//TODO MAKE ME WORK WITH 2.8
//	def toFn[A,O]( fn:A=>O ):Function[A,O] = {
//		new Function[A,O](){
//			override def eval(a:A):O = fn(a)
//		}
//	}
//	
//	def toFn[A,B,O]( fn:(A,B)=>O ):Function2[A,B,O] = {
//		new Function2[A,B,O](){
//			override def eval(a:A, b:B):O = fn(a,b)
//		}
//	}
//	
//	def toFn[A,B,C,O]( fn:(A,B,C)=>O ):Function3[A,B,C,O] = {
//			new Function3[A,B,C,O](){
//				override def eval(a:A, b:B, c:C):O = fn(a,b,c)
//			}
//		}
//
//	def toIter[E]( iter:Iterator[E] ):Iterable[E] = {
//		new Iterable[E](){
//			override def elements:scala.Iterator[E] = {
//				new scala.Iterator[E](){
//					override def next:E = iter.next
//					override def hasNext:Boolean = iter.hasNext
//				}
//			}
//		}
//	}
//
//	def toLst[E]( lst:List[E] ):java.util.List[E] = {
//		val rtn:java.util.List[E] = new java.util.LinkedList[E]
//		lst.foreach( (term:E) => {
//			rtn.add(term);
//		})
//		return rtn;
//	}
//	def toList[E]( lst:List[E] ):java.util.List[E] = toLst(lst)
//	
//	def toLst[E]( lst:Array[E] ):java.util.List[E] = {
//		val rtn:java.util.List[E] = new java.util.LinkedList[E]
//		lst.foreach( (term:E) => {
//			rtn.add(term);
//		})
//		return rtn;
//	}
//	def toList[E]( lst:Array[E] ):java.util.List[E] = toLst(lst)
//
//	def fromLst[E]( lst:java.util.List[E]):List[E] = {
//		val ret = new ListBuffer[E]()
//		val iter:java.util.Iterator[E] = lst.iterator
//		while(iter.hasNext){
//			ret += iter.next
//		}
//		return ret.toList
//	}
//	def fromList[E]( lst:java.util.List[E]):List[E] = fromLst(lst)
//	
//	def lst2array[E]( lst:java.util.List[E]):Array[E] = {
//		val ret = new ListBuffer[E]()
//		val iter:java.util.Iterator[E] = lst.iterator
//		while(iter.hasNext){
//			ret += iter.next
//		}
//		return ret.toArray
//	}
}
