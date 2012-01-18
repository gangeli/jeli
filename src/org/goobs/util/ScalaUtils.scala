package org.goobs.utils

import scala.util.matching.Regex
import scala.collection.immutable.Set
import scala.collection.Iterator

class Def[C](implicit desired : Manifest[C]) {
	def unapply[X](c : X)(implicit m : Manifest[X]) : Option[C] = {
		def sameArgs = desired.typeArguments.zip(m.typeArguments).forall{
				case (desired,actual) => desired >:> actual
			}
		if(desired >:> m && sameArgs) {
			Some(c.asInstanceOf[C])
		} else {
			None
		}
	}
}

case class SingletonIterator[A](elem:A) extends Iterator[A] {
	private var exhausted:Boolean = false
	override def hasNext:Boolean = !exhausted
	override def next:A = {
		if(exhausted){ throw new NoSuchElementException }
		exhausted = true
		elem
	}
}

object Static {
	case class RichRegex(base:Regex){
		def apply(str:String):Boolean = {
			base.pattern.matcher(str).matches;
			}
	}
	case class RichString(str:String){
		def matches(r:Regex) = r(str)
	}
	implicit def regex2RichRegex(r:Regex):RichRegex = RichRegex(r)
	implicit def string2RichString(str:String) = RichString(str);
}
