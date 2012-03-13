package edu.stanford.nlp.ling.tokensregex

import java.util.Properties

import scala.collection.JavaConversions._

import edu.stanford.nlp.pipeline._
import edu.stanford.nlp.ie.NumberNormalizer
import edu.stanford.nlp.pipeline.StanfordCoreNLP
import edu.stanford.nlp.ling.CoreAnnotations._
import edu.stanford.nlp.ling._
import edu.stanford.nlp.util._
import edu.stanford.nlp.ling.tokensregex.SequencePattern._

/**
	Node Operations:
		className("string")    match annotation className with string "string"
		className("regexp")    match annotation className with regexp "regexp"
		className [op] num     ensure annotation className satisfies [op]
		                         [op] can be &lt;, &gt;, &lt;=, &gt;=, ==
		className              ensure annotation className exists on token
		!className             ensure annotation className does not exist
		!node                  negate that node (e.g. !className("string"))
	Sequence Operations:
		seq :: seq             concatenate two sequences
		                         (in normal regexes, the :: is omitted)
		seq*                   any number of matches for seq, maybe zero
		                         (might need parens: (seq*) )
		seq+                   similar to above; 1 or more matches for seq
		seq(num)               exactly num matches of seq
		seq(min,max)           between min and max matches of seq
		seqA | seqB            seqA or seqB
		seq.g                  create a group out of a sequence
		                         (for example, (node :: (node | node)).g
	Misc:
		num(className...)      Backref to group num matching annotations 
		                         { className1,className2, ... }

*/

//case class Pattern(base:PatternExpr){ //TODO :: and |  could be more efficient
//
//	def ::(first:Pattern):Pattern = { 
//		Pattern(new SequencePatternExpr(
//			Array[PatternExpr](first.base,this.base):_*))
//	}
//
//	def |(other:Pattern):Pattern = { 
//		Pattern(new OrPatternExpr(
//			Array[PatternExpr](this.base,other.base):_*))
//	}
//	def star:Pattern = {
//		Pattern(new RepeatPatternExpr(this.base,0,Int.MaxValue))
//	}
//	def * :Pattern = star
//	def plus:Pattern = {
//		Pattern(new RepeatPatternExpr(this.base,1,Int.MaxValue))
//	}
//	def + :Pattern = plus
//	def ? :Pattern = {
//		Pattern(new RepeatPatternExpr(this.base,0,1))
//	}
//	def apply(min:Int,max:Int):Pattern = {
//		Pattern(new RepeatPatternExpr(this.base,min,max))
//	}
//	def apply(num:Int):Pattern = {
//		Pattern(new RepeatPatternExpr(this.base,num,num))
//	}
//
//	def group:Pattern = {
//		Pattern(new GroupPatternExpr(this.base))
//	}
//	def g:Pattern = group
//
//}
//
//case class Node(base:NodePattern[CoreMap]) {
//	def |(other:Node):Node = {
//		new Node(new NodePattern.DisjNodePattern(
//			DBList[NodePattern[CoreMap]](this.base,other.base)))
//	}
//	def unary_! :Node = {
//		new Node(new NodePattern.NegateNodePattern(this.base))
//	}
//	
//	override def toString:String = base.toString
//}
//
//case class AnnotationWrapper[A](ann:Class[_<:CoreAnnotation[A]]){
//	type AClass = Class[_<:Any]
//	import CoreMapNodePattern.NumericAnnotationPattern.CmpType
//	//--NullPrior Apply
//	def exists:Node = {
//		val p = new CoreMapNodePattern.NotNilAnnotationPattern
//		new Node(new CoreMapNodePattern(Map[AClass,NodePattern[_]]( (ann,p) )))
//	}
//	def apply():Node = exists
//	def e:Node = exists
//	//--String
//	def apply(toMatch:String):Node = {
//		val p = 
//			new CoreMapNodePattern.StringAnnotationPattern(toMatch)
//		new Node(new CoreMapNodePattern(Map[AClass,NodePattern[_]]( (ann,p) )))
//	}
//	def apply(toMatch:scala.util.matching.Regex):Node = {
//		val p = 
//			new CoreMapNodePattern.StringAnnotationRegexPattern(toMatch.pattern)
//		new Node(new CoreMapNodePattern(Map[AClass,NodePattern[_]]( (ann,p) )))
//	}
//	//--Numeric
//	private def cmp(toMatch:Number,mode:CmpType):Node = {
//		val p = 
//			new CoreMapNodePattern.NumericAnnotationPattern(
//				toMatch.doubleValue,
//				CoreMapNodePattern.NumericAnnotationPattern.CmpType.EQ
//			)
//		new Node(new CoreMapNodePattern(Map[AClass,NodePattern[_]]( (ann,p) )))
//	}
//	def ==(toMatch:Number):Node = cmp(toMatch,CmpType.EQ)
//	def <(toMatch:Number):Node = cmp(toMatch,CmpType.LT)
//	def >(toMatch:Number):Node = cmp(toMatch,CmpType.GT)
//	def <=(toMatch:Number):Node = cmp(toMatch,CmpType.LE)
//	def >=(toMatch:Number):Node = cmp(toMatch,CmpType.GE)
//	def num:Node = cmp(Double.NaN,CmpType.IS_NUM)
//	def unary_! :Node = {
//		val p = new CoreMapNodePattern.NilAnnotationPattern
//		new Node(new CoreMapNodePattern(Map[AClass,NodePattern[_]]( (ann,p) )))
//	}
//}
//	
//case class Backref(
//		groupID:Int,
//		clauses:DBList[Class[_<:CoreAnnotation[_<:Any]]]){
//	def this(groupID:Int) 
//		= this(groupID,DBList[Class[_<:CoreAnnotation[_<:Any]]]())
//	def apply(toAdd:Class[_<:CoreAnnotation[_<:Any]]*):Backref = {
//		Backref(groupID,clauses ::: toAdd.toList)
//	}
//}
//
//
//object ScalaInterface {
//	// -- IMPLICITS --
//	//(type raises)
//	implicit def class2wrapper[A]
//			(ann:Class[_<:CoreAnnotation[A]]):AnnotationWrapper[A] = {
//		AnnotationWrapper(ann)
//	}
//	implicit def wrapper2node[A](ann:AnnotationWrapper[A]):Node = {
//		ann.apply()
//	}
//	implicit def product2node(prod:Product):Node = {
//		val lst:java.util.DBList[NodePattern[CoreMap]]
//			= prod.productIterator.map{ (n:Any) => 
//				n match {
//					case (x:Class[CoreAnnotation[_]]) => 
//						wrapper2node(class2wrapper(x)).base
//					case (x:AnnotationWrapper[_]) => wrapper2node(x).base
//					case (x:Node) => x.base
//					case (x:Any) => 
//						throw new IllegalArgumentException("Invalid element for coremap")
//				} }.toList
//		new Node(new NodePattern.ConjNodePattern(lst))
//	}
//	implicit def node2pattern(node:Node):Pattern = {
//		Pattern(new NodePatternExpr(node.base))
//	}
//	implicit def pattern2expr(p:Pattern):PatternExpr = {
//		p.base
//	}
//	//(backreferencing)
//	implicit def int2backref(groupID:Int):Backref = new Backref(groupID)
//	implicit def backref2pattern(b:Backref):Pattern
//		= Pattern(
//				new BackRefPatternExpr(
//					new CoreMapNodePattern.AttributesEqualMatchChecker(b.clauses:_*),
//					b.groupID
//					))
//	//(shortcuts)
////	implicit def class2node[A] //DON'T USE
////			(ann:Class[_<:CoreAnnotation[A]]):Node
////		= wrapper2node(class2wrapper(ann))
////	implicit def class2pattern[A] //DON'T USE
////			(ann:Class[_<:CoreAnnotation[A]]):Pattern
////		= node2pattern(wrapper2node(class2wrapper(ann)))
//	implicit def class2expr[A]
//			(ann:Class[_<:CoreAnnotation[A]]):PatternExpr
//		= pattern2expr(node2pattern(wrapper2node(class2wrapper(ann))))
//	implicit def wrapper2pattern[A](ann:AnnotationWrapper[A])
//		= node2pattern(wrapper2node(ann))
//	implicit def wrapper2expr[A](ann:AnnotationWrapper[A])
//		= pattern2expr(node2pattern(wrapper2node(ann)))
//	implicit def product2pattern(prod:Product):Pattern
//		= node2pattern(product2node(prod))
//	implicit def product2expr(prod:Product):PatternExpr
//		= pattern2expr(node2pattern(product2node(prod)))
//	implicit def node2expr(node:Node):PatternExpr
//		= pattern2expr(node2pattern(node))
//}
//
//
//
////object Main {
////	def main(args:Array[String]) = {
////		import ScalaInterface._
////		val w = classOf[CoreAnnotations.TextAnnotation]
////		val pos = classOf[CoreAnnotations.PartOfSpeechAnnotation]
////		val offset = classOf[CoreAnnotations.CharacterOffsetBeginAnnotation]
////		val n = classOf[NumericCompositeValueAnnotation]
////		val pat = pos("DT").g :: w("hotter") :: 1(pos)
////		println(pat)
////	
////	
////		val props = new Properties
////		props.setProperty("annotators","tokenize, ssplit, pos, lemma")
////		val pipeline = new StanfordCoreNLP(props)
////		val input:Annotation = new Annotation("the hotter the summer")
////		pipeline.annotate(input)
////		val tokens = input.get[java.util.DBList[CoreLabel],TokensAnnotation](
////			classOf[TokensAnnotation])
////	
////		val pattern = TokenSequencePattern.compile(pat);
////		val matcher = pattern.getMatcher(tokens);
////	
////		println("Matching...")
////		while (matcher.find()) {
////			val matchedTokens = matcher.groupNodes();
////			println(matchedTokens)
////		}
////	}
////}
