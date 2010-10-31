package org.goobs.slib

import java.util.Iterator

import org.goobs.functional.Function
import org.goobs.functional.Function2
import org.goobs.functional.Function3

object JMaps {
	def toFn[A,O]( fn:A=>O ):Function[A,O] = {
		new Function[A,O](){
			override def eval(a:A):O = fn(a)
		}
	}
	
	def toFn[A,B,O]( fn:(A,B)=>O ):Function2[A,B,O] = {
		new Function2[A,B,O](){
			override def eval(a:A, b:B):O = fn(a,b)
		}
	}
	
	def toFn[A,B,C,O]( fn:(A,B,C)=>O ):Function3[A,B,C,O] = {
			new Function3[A,B,C,O](){
				override def eval(a:A, b:B, c:C):O = fn(a,b,c)
			}
		}

	def toIter[E]( iter:Iterator[E] ):Iterable[E] = {
		new Iterable[E](){
			override def elements:scala.Iterator[E] = {
				new scala.Iterator[E](){
					override def next:E = iter.next
					override def hasNext:Boolean = iter.hasNext
				}
			}
		}
	}
}
