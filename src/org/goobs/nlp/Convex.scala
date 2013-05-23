package org.goobs.nlp

import breeze.linalg._
import breeze.numerics._
import scala.util.Random

trait ObjectiveFn extends Function1[DenseVector[Double],Option[Double]] {
	def cardinality:Int

	def gradient(x:DenseVector[Double]):Option[DenseVector[Double]] = None
	def hessian(x:DenseVector[Double]):Option[DenseMatrix[Double]] = None
	
	def differentiableAt(x:DenseVector[Double]):Boolean =  gradient(x).isDefined
	def twiceDifferentiableAt(x:DenseVector[Double]):Boolean =  hessian(x).isDefined

//	def plot(x:DenseVector[Double],hold:Boolean=false){
//		if(cardinality > 1){
//			throw new IllegalStateException("Cannot plot function of cardinality > 1")
//		}
//		val y = x.map{ (v:Double) =>
//			this(DenseVector(v)) match {
//				case Some(y) =>
//					if(y.isInfinite){ Double.NaN } else { y };
//				case None => Double.NaN
//			}
//		}
//		breeze.plot.plot(x, y)
//	}
	
//	def plot(begin:Double,end:Double,step:Double){
//		val dim:Int = ((end-begin)/step).toInt + 1
//		val x = (DenseVector(Array.range(0,dim).map(_.toDouble)) :* step) :+ begin
//		plot(x)
//	}
}

case class OptimizerProfile(optimalX:DenseVector[Double],optimalValue:Double,guessProfile:DenseVector[Double]) {
//	def plotObjective(name:String="objective"){
//		breeze.plot.plot(
//			x=DenseVector(Array.range(0,guessProfile.length).map(_.toDouble)),
//			y=guessProfile,
//			name=name
//		)
//	}
//	def plotConvergence(name:String="convergence",optimal:Double=optimalValue){
//		breeze.plot.plot(
//			x=DenseVector(Array.range(0,guessProfile.length).map(_.toDouble)),
//			y=guessProfile :- optimal,
//			name=name
//		)
//	}
//	def plotLogConvergence(nm:String="log convergence",optimal:Double=optimalValue){
//		breeze.plot.plot(
//			x = DenseVector(Array.range(0,guessProfile.length).map(_.toDouble)),
//			y = (guessProfile :- optimal).map{ (v:Double) => if(v == 0){ Double.NaN } else { log(v) } },
//			name = nm
//		)
//	}
}

trait Optimizer {
	def minimize(fn:ObjectiveFn, initialValue:DenseVector[Double]):OptimizerProfile
}

object Optimizer {
	def apply(tolerance:Double=0.25,lineStep:Double=0.5):Optimizer
		= new NewtonOptimizer(1e-5,0,tolerance,lineStep)
	def newton(lambdaTolerance:Double=1e-5,lambdaCacheTime:Int=0,tolerance:Double=0.25,lineStep:Double=0.5):NewtonOptimizer
		= new NewtonOptimizer(lambdaTolerance,lambdaCacheTime,tolerance,lineStep)
}

abstract class DescentOptimizer(tolerance:Double, lineStep:Double) extends Optimizer{
	if(tolerance >= 0.5 || tolerance < 0.0){
		throw new IllegalArgumentException("Invalid tolerance: " + tolerance)
	}
	if(lineStep >= 1.0 || lineStep < 0){
		throw new IllegalArgumentException("Invalid line step: " + lineStep)
	}


	def converged(fnValue:Double,grad:DenseVector[Double],hessian:()=>DenseMatrix[Double]):Boolean
	def delta(fnValue:Double,gradient:DenseVector[Double],hessian:()=>DenseMatrix[Double]):DenseVector[Double]

	protected def safeMultiply(v:DenseVector[Double], t:Double):DenseVector[Double] = {
		if(t == 0.0){
			DenseVector.zeros[Double](v.length)
		} else if(t < 1e-5){
			val logT = log(t)
			val cand = v.map{ (value:Double) =>
				val polarity = {if(value > 0.0){ 1.0 } else { -1.0 }}
				if(value == 0.0){ 0.0 } else { polarity*exp(log(abs(value)) + logT) }
			}
			if(!cand.forallValues{ !_.isNaN }){
				throw new IllegalStateException("NaN vector:\n"+cand)
			}
			cand
		} else {
			v :* t
		}
	}
	protected def safeMultiply(args:Double*):Double = {
		val (v,polarity,isLog) = args.foldLeft((1.0,1.0,false)){ case ((soFar:Double,polarity:Double,isLog:Boolean), arg:Double) =>
			val argPolarity = {if(arg > 0.0){ 1.0 } else { -1.0 }}
			if(arg == 0.0){
				(0.0, 0.0, false)
			} else if(isLog){
				(soFar + log(abs(arg)), polarity*argPolarity, true)
			} else if(soFar != 0.0 && (soFar < 1e-5 || abs (arg) < 1e-5)){
				(log(soFar) + log(abs(arg)), polarity*argPolarity, true)
			} else {
				(soFar * abs(arg), polarity*argPolarity, false)
			}
		}
		if(v.isNaN){ throw new IllegalStateException("NaN multiplication") }
		if(isLog){
			polarity*exp(v)
		} else {
			polarity*v
		}
	}
	
	private def moveDeltaT(x:DenseVector[Double], delta:DenseVector[Double], t:Double):DenseVector[Double] = {
		x :+ safeMultiply(delta,t)
	}

	private def lineSearch(fn:ObjectiveFn, x:DenseVector[Double], delta:DenseVector[Double], gradient:DenseVector[Double]):Double = {
		var t:Double = 1.0
		def check(t:Double):Boolean = {
			fn(x).flatMap{ (fnValue:Double) =>
				fn( moveDeltaT(x,delta,t) ).flatMap{ (stepped:Double) =>
					if(stepped <= (fnValue + safeMultiply(gradient dot delta, tolerance, t)) ){
						Some(true)
					} else {
						None
					}
				}
			}.isDefined
		}
		while(!check(t)){
			t = t * lineStep
		}
		if((t < 0.0001) && !gradient.forallValues{ _ < 0.01 }){
			throw new IllegalStateException("Line search failed (bad gradient?)")
		}
		t
	}

	def minimize(fn:ObjectiveFn, initialValue:DenseVector[Double]):OptimizerProfile = {
		//--Initialize
		//(variables)
		var x = initialValue
		var fnValue = fn(initialValue)
		var iter = 0
		var guesses:List[Double] = List[Double](fnValue.get)
		//(error checks)
		if(!fnValue.isDefined){
			throw new IllegalArgumentException("Bad starting point:\n" + initialValue)
		}
		//--Descent
		while(true){
			//(get gradient)
			val grad = fn.gradient(x) match {
				case (Some(grad)) => grad
				case None => throw new IllegalArgumentException("Gradient is not defined at:\n" + x)
			}
			//(promise hessian)
			var hessianImpl:Option[DenseMatrix[Double]] = None
			val hessian = () => {
				if(!hessianImpl.isDefined){
					hessianImpl = fn.hessian(x);
				}
				if(!hessianImpl.isDefined){
					throw new IllegalArgumentException("Minimizer needs a hessian defined")
				}
				hessianImpl.get
			}
			//(check for convergence)
			if(converged(fnValue.get,grad,hessian)){
				println("x=\n"+x)
				println("Converged to " + fnValue.get + " [|grad|=" + grad.map{ _.abs }.sum + "]")
				return OptimizerProfile(x, fnValue.get, DenseVector(guesses.reverse.toArray))
			}
			//(iterative step)
			val deltaX = delta(fnValue.get,grad,hessian)
			val t:Double = lineSearch(fn,x,deltaX, grad)
			//(debug)
			println("Iteration "+iter+" [value=" +fnValue.get+"] [|grad|=" +grad.map{ _.abs }.sum+"] [t="+t+"]")
			//(update)
			val savedX = x;
			x = moveDeltaT(x, deltaX, t)
			if( !(deltaX :* t).forallValues{ _ == 0.0 } && x == savedX){
				throw new IllegalStateException("Numeric precision underflow: x+t*deta == x even if t*delta > 0")
			}
			fnValue = fn(x)
			//(update overhead)
			if(!fnValue.isDefined){
				throw new IllegalArgumentException("Function optimized out of bounds: " + x)
			}
			iter += 1
			guesses = fnValue.get :: guesses
		}
		throw new IllegalStateException("Exited a while(true) loop? true=false now?")
	}
}

class GradientDescentOptimizer(gradientTolerance:Double,tolerance:Double,lineStep:Double
																	) extends DescentOptimizer(tolerance, lineStep) {
	override def converged(fnValue:Double,grad:DenseVector[Double],hessian:()=>DenseMatrix[Double]):Boolean
		= grad.forallValues{ _.abs <= gradientTolerance}

	override def delta(fnValue:Double,grad:DenseVector[Double],hessian:()=>DenseMatrix[Double]):DenseVector[Double]
		= -grad
}

class NewtonOptimizer(lambdaTolerance:Double,hessianInterval:Int,tolerance:Double,lineStep:Double) extends DescentOptimizer(tolerance, lineStep) {
	var hessianInverseCache:Option[DenseMatrix[Double]] = None
	var cacheCond:()=>DenseMatrix[Double] = null
	var timeSinceUpdate:Int = 0

	def inv(fn:()=>DenseMatrix[Double]):DenseMatrix[Double] = {
		if(timeSinceUpdate > hessianInterval || !hessianInverseCache.isDefined || cacheCond == null){
			hessianInverseCache = Some(LinearAlgebra.inv(fn()))
			cacheCond = fn
			timeSinceUpdate = 0
		}
		timeSinceUpdate += 1
		hessianInverseCache.get
	}

	private def lambdaSquared(grad:DenseVector[Double],hessian:()=>DenseMatrix[Double]):Double = grad dot (hessian() \ grad)
	override def converged(fnValue:Double,grad:DenseVector[Double],hessian:()=>DenseMatrix[Double]):Boolean
		= lambdaSquared(grad,hessian) / 1.0 <= lambdaTolerance
	override def delta(fnValue:Double,grad:DenseVector[Double],hessian:()=>DenseMatrix[Double]):DenseVector[Double]
		= -inv(hessian)*grad
}

/*
object Convex {

	def main(args:Array[String]) {
		//--Parameters
		val random = new Random(42)
		val outputDir = "/home/gabor/tmp"

		val m:Int = 200
		val n:Int = 100
		var diagonal:Boolean = false
		var hessianCache = 0
		val epsilon= 1e-10
		val eta = 1e-6
		val alpha = 0.25
		val beta = 0.5
		val A:DenseMatrix[Double] = DenseMatrix.rand(m,n, random)

		//--Objective Function
		val fn:ObjectiveFn = new ObjectiveFn {
			def cardinality:Int = n
			def apply(x: DenseVector[Double]):Option[Double] = {
				val value = sum(log1p(-A * x)) +
					x.map{ (v) => log(1-v*v) }.sum
				if(value.isNaN){
					None
				} else {
					Some(-value)
				}
			}
			override def gradient(x:DenseVector[Double]):Option[DenseVector[Double]] = {
				apply(x).flatMap{ (fnValue:Double) =>
					val termA = (0 until m).map{ (i:Int) =>
						val numer = A.t(::,i)
						val denom = 1.0 - (numer dot x)
						numer :/ denom
					}.foldLeft(DenseVector.zeros[Double](cardinality)){
						case (soFar:DenseVector[Double], term:DenseVector[Double]) => soFar :+ term
					}
					val termB = (x :* 2.0) :/ ( (x :^ 2.0) :- 1.0)
					val deriv = termA :- termB
					if(deriv.forallValues( (v:Double) => !v.isNaN )){
						Some(deriv)
					} else {
						None
					}
				}
			}
			override def hessian(x:DenseVector[Double]):Option[DenseMatrix[Double]] = {
				val hessian:DenseMatrix[Double] =
					if(diagonal){
						val hessian = DenseMatrix.eye[Double](x.length)
						(0 until x.length).foreach{ (i:Int) =>
							val term1 = A(::,i).sum
							val term2 = (2.0*x(i)*x(i)+2.0) / (1-x(i)*x(i))*(1-x(i)*x(i))
							hessian(i,i) = term1 + term2
						}
						hessian
					} else {
						//--Term 1
						val term1 = (0 until m).map{ (i:Int) =>
							val numer1:DenseMatrix[Double] = A(i,::).t*A(i,::)
							val denom1:Double = (1.0 - (A.t(::,i) dot x))
							numer1 :/ (denom1*denom1)
						}.foldLeft(DenseMatrix.zeros[Double](cardinality,cardinality)){
							case (soFar:DenseMatrix[Double], term:DenseMatrix[Double]) => soFar :+ term
						}
						//--Term 2
						val term2 = x.map{ (x:Double) =>
							(2.0*x*x + 2) / ((1-x*x)*(1-x*x))
						}
						(0 until cardinality).foreach{ (i:Int) =>
							term1(i,i) += term2(i)
						}
						//--Return
						term1
					}
				Some(hessian)
			}
		}


		//--Experiments
		val optimal = new NewtonOptimizer(1e-15, 0, alpha, beta).minimize(fn, DenseVector.zeros[Double](fn.cardinality)).optimalValue
		def runTest[A](name:String,file:String,vals:Array[A],fn:A=>Any){
                        val f = breeze.plot.Figure()
                        val plot = f.subplot(0)
			plot.title = name
			plot.legend = true
			vals.foreach{ (v:A) =>
				fn(v)
				Thread.sleep(1000)
			}
			Thread.sleep(1000)
			f.saveas(outputDir + "/"+file)
			Thread.sleep(1000)
			Thread.sleep(1000)
			f.clear()
			Thread.sleep(5000)
		}
		def changeEta(etas:Double*) {
			runTest("Changing Eta", "eta-converge.png", etas.toArray,
				(eta:Double) =>
					new GradientDescentOptimizer(eta, alpha, beta).minimize(fn, DenseVector.zeros[Double](fn.cardinality)).plotLogConvergence("eta = " + eta,optimal))
		}
		def changeAlpha(alphas:Double*) {
			runTest("Convergence vs Alpha", "alpha-converge.png", alphas.toArray,
				(alpha:Double) =>
					new GradientDescentOptimizer(eta, alpha, beta).minimize(fn, DenseVector.zeros[Double](fn.cardinality)).plotLogConvergence("alpha = " + alpha,optimal))
			runTest("Objective vs Alpha", "alpha-objective.png", alphas.toArray,
				(alpha:Double) =>
					new GradientDescentOptimizer(eta, alpha, beta).minimize(fn, DenseVector.zeros[Double](fn.cardinality)).plotObjective("alpha = " + alpha))
		}
		def changeBeta(betas:Double*) {
			runTest("Convergence vs Beta", "beta-converge.png", betas.toArray,
				(beta:Double) =>
					new GradientDescentOptimizer(eta, alpha, beta).minimize(fn, DenseVector.zeros[Double](fn.cardinality)).plotLogConvergence("beta = " + beta,optimal))
			runTest("Objective vs Beta", "beta-objective.png", betas.toArray,
				(beta:Double) =>
					new GradientDescentOptimizer(eta, alpha, beta).minimize(fn, DenseVector.zeros[Double](fn.cardinality)).plotObjective("beta = " + beta))
		}
		def changeDiagonalization {
			runTest("Convergence vs Diagonal", "diag-converge.png", Array[Boolean](true,false),
				(d:Boolean) => {
					diagonal = d
					new NewtonOptimizer(epsilon, hessianCache, alpha, beta).minimize(fn, DenseVector.zeros[Double](fn.cardinality))
							.plotLogConvergence(if(diagonal){ "diagonal" } else { "full" }, optimal) })
			runTest("Objective vs Diagonal", "diag-objective.png", Array[Boolean](true,false),
				(d:Boolean) => {
					diagonal = d
					new NewtonOptimizer(epsilon, hessianCache, alpha, beta).minimize(fn, DenseVector.zeros[Double](fn.cardinality))
							.plotObjective(if(diagonal){ "diagonal" } else { "full" }) })
			diagonal = false
		}
		def changeEpsilon(epsilons:Double*) {
			runTest("Changing Epsilon", "epsilon-converge.png", epsilons.toArray,
				(epsilon:Double) =>
					new NewtonOptimizer(epsilon, hessianCache, alpha, beta).minimize(fn, DenseVector.zeros[Double](fn.cardinality))
							.plotLogConvergence("epsilon = " + epsilon,optimal) )
		}
		def changeCache(caches:Int*) {
			runTest("Convergence vs Hessian Cache", "cache-converge.png", caches.toArray,
				(hessianCache:Int) =>
					new NewtonOptimizer(epsilon, hessianCache, alpha, beta).minimize(fn, DenseVector.zeros[Double](fn.cardinality))
							.plotLogConvergence("cache = " + hessianCache, optimal) )
			runTest("Objective vs Hessian Cache", "cache-objective.png", caches.toArray,
				(hessianCache:Int) =>
					new NewtonOptimizer(epsilon, hessianCache, alpha, beta).minimize(fn, DenseVector.zeros[Double](fn.cardinality))
							.plotObjective("cache = " + hessianCache) )
			hessianCache = 0
		}

//		runTest("Gradient Decent", "gradient.png", Array[Double](eta),
//			(eta:Double) =>
//				new GradientDescentOptimizer(eta, alpha, beta).minimize(fn, DenseVector.zeros[Double](fn.cardinality))
//						.plotObjective("Objective value") )
				runTest("Newton's Method", "newton.png", Array[Double](eta),
					(eta:Double) =>
						new NewtonOptimizer(epsilon, hessianCache,alpha, beta).minimize(fn, DenseVector.zeros[Double](fn.cardinality))
								.plotObjective("Objective value") )
//		changeAlpha(0.05,0.1,0.25,0.4,0.49)
//		changeBeta(0.1,0.25,0.5,0.75,0.9)
//		changeDiagonalization
//		changeCache(1,15,30)


//		fn.plot(-5.0,1.0,0.01)
//
//		val goldMin = new GradientDescentOptimizer(eta, alpha, beta).minimize(fn, DenseVector.zeros[Double](fn.cardinality)).optimalValue
//		println("-----------------\n\n")
//
//		val minimizer = Optimizer.newton(epsilon, hessianCache, alpha, beta)
//		val profile = minimizer.minimize(fn, DenseVector.zeros[Double](fn.cardinality))
//		println("Guess=" + profile.optimalValue + " gold=" + goldMin)
//		profile.plotLogConvergence()

	}
}
*/
