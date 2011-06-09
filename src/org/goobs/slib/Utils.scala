package org.goobs.slib

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
