package org.goobs.nlp

import java.text.DecimalFormat
import java.lang.IllegalStateException

import scala.collection.JavaConversions._
import scala.collection.mutable.PriorityQueue
import scala.collection.mutable.HashMap
import scala.collection.mutable.ArrayBuffer
import scala.collection.immutable.Set
import scala.collection.mutable.HashSet

import org.goobs.stats._
import org.goobs.util.SingletonIterator

//------------------------------------------------------------------------------
// GRAMMAR
//------------------------------------------------------------------------------

case class NodeType(name:Symbol,id:Int,flag:Set[Symbol]) {
	def isPreterminal:Boolean = flag(NodeType.SYM_PRETERMINAL)
	def isWord:Boolean = flag(NodeType.SYM_WORD)
	//--Default Overrides
	override def equals(o:Any):Boolean = {
		o match {
			case NodeType(otherName,otherId,flags) =>
				if(name == otherName){
					assert(id == otherId, "Names match but IDs don't: " +
						this + "("+this.id+") " + o +"("+otherId+")")
					true
				} else {
					assert(id != otherId, "Names don't match but IDs do")
					false
				}
			case _ => false
		}
	}
	override def hashCode:Int = id
	override def toString:String = name.name
}


object NodeType {
	//--State
	private var nextId = -1
	private val valueMap = new HashMap[Symbol,NodeType]
	private var values:Option[Array[NodeType]] = None
	private val valuesBuilder = new ArrayBuffer[NodeType]
	//--Constants //<--must be after State
	val SYM_WORD = '_Word
	val SYM_PRETERMINAL = '_Preterminal
	lazy val ROOT:NodeType = make('_ROOT)
	lazy val WORD:NodeType = makeWord()

	def count:Int = {
		assert(valuesBuilder.length == nextId+1, "Sizes don't match")
		nextId+1
	}
	def all:Array[NodeType] = {
		if(!values.isDefined){
			values = Some(valuesBuilder.toArray)
		}
		values.get
	}
	def register(head:NodeType):NodeType = {
		//(update array)
		valuesBuilder += head
		values = None
		//(update map)
		valueMap(head.name) = head
		//(return)
		head
	}
	def apply(i:Int):NodeType = {
		if(!values.isDefined){
			values = Some(valuesBuilder.toArray)
		}
		values.get.apply(i)
	}
	def apply(name:Symbol):NodeType = {
		if(!valueMap.contains(name)){
			//(case: no such element)
			throw new NoSuchElementException("No such nonterminal: " + name)
		} else {
			//(case: return nonterminal)
			valueMap(name)
		}
	}
	def apply(name:String):NodeType = {
		apply(Symbol(name))
	}
	def makePreterminal(name:Symbol, flags:Symbol*):NodeType 
		= make(name,Set[Symbol](flags:_*)+SYM_PRETERMINAL)
	def makePreterminal(name:String, flags:Symbol*):NodeType 
		= make(Symbol(name),Set[Symbol](flags:_*)+SYM_PRETERMINAL)
	def makeWord(flags:Symbol*):NodeType 
		= make(SYM_WORD, Set[Symbol](flags:_*)+SYM_WORD)
	def make(name:String, flags:Symbol*):NodeType=make(Symbol(name),Set(flags:_*))
	def make(name:Symbol, flags:Symbol*):NodeType = make(name,Set(flags:_*))
	def make(name:Symbol, flags:Set[Symbol]):NodeType = {
		//--Error Check
		assert(valueMap != null, "Object was never initialized")
		if(valueMap.contains(name)){
			val cand:NodeType = valueMap(name)
			if(cand.flag != flags){
				throw new IllegalArgumentException("Incompatable NodeType: " + name)
			} else {
				return cand
			}
		}
		//--Create
		//(create class)
		nextId += 1
		val term = NodeType(name,nextId,flags)
		//(register class)
		register(term)
	}
}

object GrammarRule {
	def assertValidity(rule:CKYRule):Boolean = {
		//--Preterminal -> Terminal
		if(rule.parent.isPreterminal){
			rule match {
				case (unary:CKYUnary) =>
					assert(unary.isLex, 
						"Rule goes from preterminal to non-lex: " + rule)
				case _ => assert(false, "Preterminal from non-unary: " + rule)
			}
		}
		//--Lex Unary Only
		rule.children.foreach{ (term:NodeType) =>
			assert(!term.isWord || rule.isUnary, "Binary rule makes Lex: " + rule)
		}
		//--All Good
		true
	}

	def apply(_head:NodeType,_children:NodeType*):GrammarRule = {
		new GrammarRule {
			override def parent:NodeType = _head
			override def children:Array[NodeType] = _children.toArray
		}
	}

}

trait GrammarRule extends Serializable {
	//<<abstract>>
	def parent:NodeType
	def children:Array[NodeType]
	//<<optional override>
	def binarize:Iterator[CKYRule] = {
		if(children.length == 0){
			throw new IllegalStateException("Rule with no children: " + this);
		} else if(children.length == 1){
			Array[CKYUnary](new CKYUnary(parent, children(0))).iterator
		} else {
			//--Cycle Over Children
			val (rules,lastNode,lastPrefix) = children.zipWithIndex.foldLeft(
					( List[CKYRule](),
						parent,
						(new StringBuilder).append("@").append(parent)
					)){
					case ((soFar:List[CKYRule],p:NodeType,prefix:StringBuilder),
						(child:NodeType,index:Int))=>
				//(create intermediate node)
				val intermNode:NodeType = 
					NodeType.make(prefix.append("_").append(child).toString)
				if(index < children.length-1){
					//(case: binary rule)
					(new CKYBinary(p,child,intermNode) :: soFar, intermNode, prefix)
				} else {
					//(case: last unary)
					(new CKYUnary(p,child) :: soFar, intermNode, prefix)
				}
					case _ => throw new IllegalStateException("Match error")
			}
			rules.iterator
		}
	}
	//<<Object Overrides>>
	override def toString:String = parent.toString+"->"+children.mkString(",")
	override def hashCode:Int = {
		children.foldLeft(parent.hashCode){ case (code:Int,child:NodeType) =>
			(code ^ child.hashCode * 7)
		}
	}
	override def equals(o:Any):Boolean = {
		o match {
			case (r:GrammarRule) =>
		 		if(r.children.length == this.children.length){
					this.parent == r.parent && 
						children.zip(r.children).forall{ case (a:NodeType,b:NodeType) =>
							a == b
						}
				} else {
					false
				}
			case _ => false
		}
	}
}

case class SimpleGrammarRule(parent:NodeType, _children:NodeType*
		) extends GrammarRule {
	assert(children.length > 0, "Grammar rule with no children");
	override def children:Array[NodeType] = _children.toArray
	//<<Object overrides>>
	override def toString:String = super.toString
	override def equals(o:Any):Boolean = super.equals(o)
	override def hashCode:Int = super.hashCode
}

case class BinaryGrammarRule(parent:NodeType, _children:NodeType*
		) extends GrammarRule {
	assert(children.length > 0, "Grammar rule with no children");
	assert(children.length < 3, "Binary rule with >2 children");
	override def children:Array[NodeType] = _children.toArray
	override def binarize:Iterator[CKYRule] = {
		if(children.length == 0){
			//(case: no children)
			throw new IllegalStateException("Rule with no children: " + this);
		} else if(children.length == 1){
			//(case: unary)
			Array[CKYUnary](new CKYUnary(parent, children(0))).iterator
		} else if(children.length == 2){
			//(case: binary)
			Array[CKYBinary](new CKYBinary(parent,children(0),children(1))).iterator
		} else {
			//(case: more than 2 children)
			throw new IllegalStateException("Binary rule with >2 children: " + this);
		}
	}
	//<<Object overrides>>
	override def toString:String = super.toString
	override def equals(o:Any):Boolean = super.equals(o)
	override def hashCode:Int = super.hashCode
}

case class LexGrammarRule(parent:NodeType) extends GrammarRule {
	assert(children.length > 0, "Grammar rule with no children");
	override def children:Array[NodeType] = Array[NodeType](NodeType.WORD)
	//<<Object overrides>>
	override def toString:String = super.toString
	override def equals(o:Any):Boolean = super.equals(o)
	override def hashCode:Int = super.hashCode
}


trait CKYRule extends GrammarRule {
	//<<Construction>>
	assert(GrammarRule.assertValidity(this))
	//<<Overrides>>
	def isUnary:Boolean
	def isClosure:Boolean = false
	//<<Utilities>>
	def isLex:Boolean = {
		val isTrue:Boolean = isUnary && child.isWord
		assert(!isTrue || parent.isPreterminal, 
			"Lex rule not tagged as preterminal")
		isTrue
	}
	def child:NodeType = {
		assert(isUnary, "Non-unary rules don't have a single child")
		children.iterator.next
	}
	def leftChild:NodeType = {
		assert(!isUnary, "Unary rules don't have a left child")
		children.iterator.next
	}
	def rightChild:NodeType = {
		assert(!isUnary, "Unary rules don't have a right child")
		val iter = children.iterator
		iter.next
		iter.next
	}
	//<<Object Overrides>>
	override def toString:String = {
		if(isUnary) { parent.toString + "->" + child }
		else { parent.toString + "->" + leftChild + "," + rightChild }
	}
	override def hashCode:Int = {
		val code:Int = if(isUnary) { 
			parent.hashCode ^ (child.hashCode * 7)
		} else { 
			parent.hashCode ^
				(leftChild.hashCode * 7) ^
				(rightChild.hashCode * 7) 
		}
		assert(code == super.hashCode, "Hash code inconsistency")
		code
	}
	override def equals(o:Any):Boolean = {
		o match {
			case (r:CKYRule) =>
		 		if(r.isUnary && this.isUnary){
					//(case: both unaries)
					this.parent == r.parent && this.child == r.child
				} else if(!r.isUnary && !this.isUnary) {
					//(case: both binaries)
					this.parent == r.parent &&
						this.leftChild == r.leftChild &&
						this.rightChild == r.rightChild 
				} else {
					//(case: binary and unary)
					false
				}
			case (r:GrammarRule) =>
				super.equals(r)
			case _ => false
		}
	}
}

class CKYUnary(_parent:NodeType,_child:NodeType) extends CKYRule {
	//--Overrides
	//(required)
	override def binarize:Iterator[CKYRule] = new SingletonIterator(this)
	override def parent:NodeType = _parent
	override def children:Array[NodeType] = Array[NodeType](child)
	override def isUnary:Boolean = true
	//(for efficiency)
	override def child:NodeType = _child
	//--Possible Overrides
	def validLexApplication(w:Int):Boolean = true
}

class CKYClosure(val chain:CKYUnary*) 
		extends CKYUnary(chain(0).parent,chain.last.child) {
	//--Error Checks
	if(chain.length <= 1){ 
		throw new IllegalArgumentException("Closure must have >1 rule")
	}
	//--Overrides
	override def isClosure:Boolean = true
}

class CKYBinary(_parent:NodeType,
		_leftChild:NodeType,_rightChild:NodeType) extends CKYRule {
	//--Overrides
	//(required)
	override def parent:NodeType = _parent
	override def binarize:Iterator[CKYRule] = new SingletonIterator(this)
	override def children:Array[NodeType] 
		= Array[NodeType](leftChild,rightChild)
	override def isUnary:Boolean = false
	//(for efficiency)
	override def leftChild:NodeType = _leftChild
	override def rightChild:NodeType = _rightChild
}

//------------------------------------------------------------------------------
// AUXILLIARY
//------------------------------------------------------------------------------
trait Sentence extends Serializable{
	def apply(i:Int):Int
	def length:Int
	def gloss(i:Int):String
}

class SimpleSentence(w2str:Int=>String,words:Int*) extends Sentence{
	//<<basic overrides>>
	override def apply(i:Int):Int = words(i)
	override def length:Int = words.length
	override def gloss(i:Int):String = w2str(words(i))
	//<<object overrides>>
	override def toString:String = words.map{ w2str(_) }.mkString(" ")
	override def hashCode:Int
		= words.foldLeft(0){ (soFar:Int,term:Int) => soFar ^ term << 1 }
	override def equals(o:Any):Boolean = {
		o match {
			case (sent:Sentence) =>
				sent.length == length &&
					(0 until length).forall{ (i:Int) => this(i) == sent(i) }
			case _ => false
		}
	}
	
}

object Sentence {
	/**
		Cannonically make a simple sentence
	*/
	def apply(w2str:Int=>String,words:Int*):Sentence 
		= new SimpleSentence(w2str,words:_*)
	/**
		Make a sentence from a vocabulary and a sentence (as a String)
	*/
	def apply(dict:Seq[String], sentence:String):Sentence = {
		new SimpleSentence(
			(i:Int) => dict(i),
			sentence.split("""\s+""").map{ (str:String) => dict.indexOf(str) }:_* )
	}
	/**
		Make a sentence from a vocabulary and a list of words (Strings)
	*/
	def apply(dict:Seq[String], sentence:String*):Sentence = {
		new SimpleSentence(
			(i:Int) => dict(i),
			sentence.map{ (str:String) => dict.indexOf(str) }:_* )
	}
	/**
		Make a sentence from a collection of sentences (as Strings)
	*/
	def apply(sentences:Array[String]):Array[Sentence] = {
		//--Get Vocab
		val words = new ArrayBuffer[String]()
		val wordSet = new HashSet[String]
		sentences.foreach{ (sent:String) =>
			sent.split("""\s+""").foreach{ (str:String) =>
				if(!wordSet.contains(str)){
					words += str
					wordSet += str
				}
			}
		}
		val vocab = words.toArray
		//--Make Corpus
		sentences.map{ apply(vocab,_) }.toArray
	}
	/**
		Make a sentence from a collection of sentences (as String arrays)
	*/
	def apply(sentences:Array[String]*):Array[Sentence] = {
		//--Get Vocab
		val words = new ArrayBuffer[String]()
		val wordSet = new HashSet[String]
		sentences.foreach{ (sent:Array[String]) =>
			sent.foreach{ (str:String) =>
				if(!wordSet.contains(str)){
					words += str
					wordSet += str
				}
			}
		}
		val vocab = words.toArray
		//--Make Corpus
		sentences.map{ (s:Array[String]) => apply(vocab,s:_*) }.toArray
	}
}

trait Tree[A] extends Serializable {
	//<<Overrides>>
	def parent:A
	def children:Array[_<:Tree[A]]
	def isLeaf:Boolean = (children.length == 0)
	//<<Utilities>>
	private def prettyPrintAppend(printer:A=>String,b:StringBuilder):Unit = {
		b.append("(").append(printer(parent))
		children.foreach( (tree:Tree[A]) => {
			b.append(" ")
			tree.prettyPrintAppend(printer,b)
		})
		b.append(")")
	}
	def prettyPrint(printer:A=>String = _.toString):String = {
		val b = new StringBuilder
		prettyPrintAppend(printer,b)
		b.toString
	}
	//<<Object Overrides>>
	override def toString:String = prettyPrint()
	override def equals(o:Any):Boolean = o match {
		case (tree:Tree[A]) =>
			parent == tree.parent && children.length == tree.children.length &&
				children.zip(tree.children).forall{ case (a,b) => a == b }
		case _ => false
	}
	override def hashCode:Int = parent.hashCode ^ children.hashCode << 3
}

object ParseTree {
	def apply(head:NodeType, rules:Array[ParseTree]):ParseTree 
		= apply(head,rules,1.0)
	def apply(head:NodeType,word:Int):ParseTree 
		= apply(head,word,1.0)
	def apply(head:NodeType, word:Int, prob:Double):ParseTree  = {
		//(variables)
		val logProb = math.log(prob)
		val headRule:CKYUnary = new CKYUnary(head,NodeType.WORD)
		//(tree)
		new ParseTree {
			override def logProb:Double = math.log(prob)
			override def parent:NodeType = head
			override def children:Array[ParseTree] = Array[ParseTree]()
			override def traverse(ruleFn:(CKYRule)=>Any,lexFn:(CKYUnary,Int)=>Any
					):Unit = {
				lexFn(headRule,word)
			}
		}
	}
	def apply(head:NodeType, rules:Array[ParseTree], prob:Double, 
			binary:Boolean=false):ParseTree = {
		//(variables)
		val logProb = math.log(prob)
		val headRule:GrammarRule = 
			if(binary) { BinaryGrammarRule(head, rules.map{ _.parent }:_*) }
			else { GrammarRule(head,rules.map{ _.parent }:_*) }
		val ckyRules:Iterator[CKYRule] = headRule.binarize
		//(tree)
		new ParseTree {
			override def logProb:Double = math.log(prob)
			override def parent:NodeType = head
			override def children:Array[ParseTree] = rules
			override def traverse(ruleFn:(CKYRule)=>Any,lexFn:(CKYUnary,Int)=>Any
					):Unit = {
				ckyRules.foreach{ ruleFn(_) }
				rules.foreach{ _.traverse(ruleFn,lexFn) }
			}
		}
	}
}

trait ParseTree extends Tree[NodeType] {
	//<<Overrides>>
	override def children:Array[ParseTree] //for type check
	def traverse(ruleFn:(CKYRule)=>Any,lexFn:(CKYUnary,Int)=>Any):Unit 
	def logProb:Double
	//<<Common Methods>>
	private def cleanParseString(
			headType:Symbol,
			indent:Int,b:StringBuilder,sent:Sentence,i:Int):Int = {
		//(util)
		val df = new DecimalFormat("0.000")
		//(begin)
		for(i <- 0 until indent){ b.append("  ") }
		//(head type)
		val head:String = headType match {
				case 'String => parent.toString
				case 'Probability => {
					val prob = math.exp(logProb)
					assert(!prob.isNaN, "Rule has NaN probability")
					assert(prob >= 0.0 && prob <= 1.0, "Rule has malformed probability")
					df.format(prob)
				}
			}
		//(route)
		if(isLeaf){
			//--Case: Leaf
			val tail = sent.gloss(i)
			if(tail != null && !tail.equals("")){
				b.append("(").append(parent.toString.replaceAll(" ","_")).append(" ")
				b.append(tail.replaceAll(" ","_").replaceAll("'","\\'")).append(")")
			} else {
				b.append(parent.toString.replaceAll(" ","_"))
			}
			i+1
		} else {
			//--Case: Not Leaf
			//(overhead)
			var retI = i
			//(write)
			b.append("(").append(parent.toString.replaceAll(" ","_"))
			children.foreach{ (child:ParseTree) =>
				b.append("\n")
				retI = child.cleanParseString(headType,indent+1,b,sent,retI)
			}
			b.append(")")
			retI
		}
	}
	def asParseString(sent:Sentence):String = {
		val b = new StringBuilder
		cleanParseString('String,0,b,sent,0)
		b.toString
	}
	def asParseProbabilities(sent:Sentence):String = {
		val b = new StringBuilder
		cleanParseString('Probability,0,b,sent,0)
		b.toString
	}
	def lexRules:Array[CKYUnary] = {
		var terms = List[CKYUnary]()
		traverse( 
			(rid:CKYRule) => {}, 
			(rid:CKYUnary,w:Int) => { terms = rid :: terms } )
		terms.reverse.toArray
	}
}

//------------------------------------------------------------------------------
// PARSER FACTORY
//------------------------------------------------------------------------------
object CKYParser {
	// -- Constants --
	val UNARY:Int = 0
	val BINARY:Int = 1
	
	//-----
	// Multinomial Count Store
	//-----
	private def intStore(capacity:Int):CountStore[Int] = {
		//(data structure)
		val counts:Array[Double] = new Array[Double](capacity)
		//(interface)
		new CountStore[Int] {
			var totalCnt:Double = 0.0
			override def getCount(key:Int):Double = counts(key)
			override def setCount(key:Int,count:Double):Unit = { 
				totalCnt += count - counts(key)
				counts(key) = count 
			}
			override def emptyCopy:CountStore[Int] = intStore(capacity)
			override def clone:CountStore[Int] = {
				super.clone
				val copy = emptyCopy
				counts.zipWithIndex.foreach{ case (count:Double,i:Int) =>
					copy.setCount(i,count)
				}
				copy
			}
			override def clear:CountStore[Int] = {
				(0 until counts.length).foreach{ (i:Int) => counts(i) = 0 }
				this
			}
			override def iterator:java.util.Iterator[Int] = {
				var nextIndex = 0
				new java.util.Iterator[Int] {
					override def hasNext:Boolean = nextIndex < counts.length
					override def next:Int = {
						if(nextIndex >= counts.length){ throw new NoSuchElementException }
						nextIndex += 1
						nextIndex - 1
					}
					override def remove:Unit = {}
				}
			}
			override def totalCount:Double = totalCnt
		}
	}

	//-----
	// Closures
	//-----
	private def computeClosures(raw:Iterable[(CKYRule,Double)]
			):Array[(CKYUnary,Double)] = {
		//--Construct Graph
		case class Node(parent:NodeType,var neighbors:List[(Node,CKYUnary,Double)]){
			def this(parent:NodeType) = this(parent,List[(Node,CKYUnary,Double)]())
			def addNeighbor(n:Node,rule:CKYUnary,count:Double) 
				= {neighbors = (n,rule,count) :: neighbors}
			def search(seen:HashSet[NodeType],backtrace:List[(CKYUnary,Double)],
					tick:(NodeType,List[(CKYUnary,Double)])=>Any):Unit = {
				//(overhead)
				if(seen(parent)){ 
					throw new IllegalStateException("Cyclic unaries for: " + parent)
				}
				seen(parent) = true
				//(report path)
				if(backtrace.length > 0){ tick(parent,backtrace) }
				//(continue searching)
				neighbors.foreach{ case (node,rule,count) =>
					assert(rule.parent == this.parent, "graph constructed badly (head)")
					assert(rule.child == node.parent,  "graph constructed badly (ch)")
					node.search(seen, (rule,count) :: backtrace,tick)
				}
				//(pop up)
				seen(parent) = false
			}
		}
		//(populate graph)
		val graph = Map( NodeType.all.zip(NodeType.all.map{ new Node(_) }):_* )
		raw.foreach{ case (rule:CKYRule,count:Double) => 
			//(asserts)
			assert(!rule.parent.isWord, "Unary headed by a Word")
			assert(rule.isUnary, "Computing closures for non-unary rules")
			assert(rule.isInstanceOf[CKYUnary], "Unary doesn't inherit CKYUnary")
			//(add neighbor)
			if(!rule.isLex){ //don't add lex rules
				graph(rule.parent)
					.addNeighbor(graph(rule.child), rule.asInstanceOf[CKYUnary], count) 
			}
		}
		//--Search Graph
		//(variables)
		var closures = HashSet[(CKYUnary,Double)]()
		var closureCount:Int = 0
		//(search)
		graph.foreach{ case (startType:NodeType, start:Node) => 
			start.search(new HashSet[NodeType], List[(CKYUnary,Double)](),
				(child:NodeType,backtrace:List[(CKYUnary,Double)]) => {
					//(variables)
					val rules:Array[(CKYUnary,Double)] = backtrace.reverse.toArray
					//(error checks)
					assert(rules.length > 0, "backtrace of length 0 returned")
					assert(rules(0)._1.parent == start.parent, "bad head")
					assert(rules.last._1.child == child, "bad child")
					//(add closure)
					val toAdd:(CKYUnary,Double) = 
						if(rules.length == 1){ 
							rules(0) 
						} else{ 
							val ruleList = rules.map{ _._1 }
							val minCount = rules.map{ _._2 }.min
							(new CKYClosure(ruleList:_*), minCount)
						}
					closures += toAdd
					closureCount += 1
				})
		}
		//(return)
		assert(closures.size == closureCount, "Duplicate rules extracted")
		closures.toArray
	}
	
	//-----
	// Scrape Grammar
	//-----
	def scrapeGrammar(trees:Iterable[ParseTree]
			):(HashMap[CKYRule,Double],HashMap[(CKYUnary,Int),Double]) = {
		//--Variables
		val ruleMap = new HashMap[CKYRule,Double]
		val lexMap = new HashMap[(CKYUnary,Int),Double]
		//--Scrape
		trees.foreach{ (tree:ParseTree) =>
			//(variables)
			val prob = math.exp(tree.logProb)
			assert(!prob.isNaN && prob <= 1.0 && prob >= 0.0,
				"Invalid probability for tree: " + tree.logProb)
			//(head rule)
			if(tree.parent != NodeType.ROOT){
				val rootRule = new CKYUnary(NodeType.ROOT, tree.parent)
				if(ruleMap.contains(rootRule)){
					ruleMap(rootRule) = ruleMap(rootRule) + prob
				} else {
					ruleMap(rootRule) = prob
				}
			}
			//(other rules)
			tree.traverse( 
				//(rules)
				(rule:CKYRule) => 
					if(ruleMap.contains(rule)){ 
						ruleMap(rule) = ruleMap(rule) + prob
					} else {
						ruleMap(rule) = prob
					},
				//(lex)
				(rule:CKYUnary,w:Int) => 
					if(lexMap.contains((rule,w))){ 
						lexMap((rule,w)) = lexMap(rule,w) + prob
					} else {
						lexMap((rule,w)) = prob
					}
			)
		}
		//--Return
		(ruleMap,lexMap)
	}

	//-----
	// Constructors
	//-----
	/**
	*  Define a parser from a set of trees
	*/
	def apply(dataset:Iterable[ParseTree]):CKYParser = {
		val (rules:HashMap[CKYRule,Double],lex:HashMap[(CKYUnary,Int),Double]) 
			= CKYParser.scrapeGrammar(dataset)
		apply( 
			lex.keys.map{ _._2 }.max+1,
			rules.keys ++ lex.keys.map{ _._1 } 
		)
	}
	/**
	*  Define a parser from a lexicon and fixed grammar rules; fast version
	*/
	def apply(numWords:Int, grammar:GrammarRule*):CKYParser
		= apply(numWords,grammar.toArray)
	/**
	*  Define a parser from a lexicon and fixed grammar rules
	*/
	def apply(numWords:Int, grammar:Iterable[GrammarRule]):CKYParser 
		= apply(numWords,grammar.map{(_,0.0)})
	/**
	*  Define a parser from a lexicon and a grammar with counts
	*/
	def apply(numWords:Int, grammar:Iterable[(GrammarRule,Double)],
			lexPrior:Prior[Int,Multinomial[Int]] = Dirichlet.SYMMETRIC(0.0),
			rulePrior:Prior[Int,Multinomial[Int]] = Dirichlet.SYMMETRIC(0.0),
			paranoid:Boolean=false,algorithm:Int=3):CKYParser = {
		//--Binarize Grammar
		//(binarize)
		val rawBinaryGrammar = new HashSet[(CKYRule,Double)]
		grammar.foreach{ case (rule:GrammarRule,count:Double) => 
			rawBinaryGrammar ++= rule.binarize.map{ (_,count) }
		}
		//(compute closures)
		val closures:Array[(CKYUnary,Double)] =
			computeClosures(rawBinaryGrammar.filter{ 
					case (rule:GrammarRule,count:Double) =>
				rule.isUnary 
			})
		val binaryGrammar = new HashSet[(CKYRule,Double)] ++=
				rawBinaryGrammar.filter( !_._1.isUnary ) ++=
				closures
		//--Collect Stats
		//(variables)
		val nodeTypes = new HashSet[NodeType]
		val lexicalEntries = new HashSet[CKYUnary]
		val rules = new HashMap[NodeType,HashMap[CKYRule,Double]]
		//(non-lexical entries)
		binaryGrammar.foreach{ case (rule:CKYRule,count:Double) =>
			//((collect types))
			nodeTypes += rule.parent
			nodeTypes ++= rule.children
			//((collect rules))
			assert(!rule.isLex, "Lexical rule in top-level grammar")
			if(!rules.contains(rule.parent)){
				rules(rule.parent) = HashMap[CKYRule,Double]()
			}
			assert(!rules(rule.parent).contains(rule), "duplicate rule detected")
			rules(rule.parent)(rule) = count
		}
		//(lexical entries)
		rawBinaryGrammar.map{ _._1}.filter{ _.isLex }.foreach{ 
			case (rule:CKYUnary) =>
				nodeTypes += rule.parent += rule.child
				lexicalEntries += rule
			case _ => throw new IllegalArgumentException("Lexical rule is not unary")
		}
		val numNodeTypes:Int = nodeTypes.size
		//--Node Type
		//(node type indexing)
		val index2NodeType:Array[NodeType] = nodeTypes.toArray
		val nodeTypeIndex:Map[NodeType,Int] = Map(index2NodeType.zipWithIndex:_*)
		//(rule prob domain)
		val ruleProbDomain:Array[Array[CKYRule]] = index2NodeType
			.map{ (elem:NodeType) =>
				if(rules.contains(elem)){
					rules(elem).keys.toArray
				} else {
					new Array[CKYRule](0) //case: no rules with given head
				}
			}
		//--Rule Distribution
		//(rule prob index)
		val ruleProbIndex:Map[CKYRule,(Int,Int)] = Map({
			ruleProbDomain.zipWithIndex.foldLeft(List[(CKYRule,(Int,Int))]()){ 
					(soFar,args) =>
				val (someRules:Array[CKYRule],i1:Int) = args
				someRules.zipWithIndex.foldLeft(List[(CKYRule,(Int,Int))]()){
						(innerSoFar,args) =>
					val (arule:CKYRule,i2:Int) = args
					(arule,(i1,i2)) :: innerSoFar
				} ::: soFar
			}
		}:_*)
		//(rule prob)
		val ruleProb = ruleProbDomain.map{ (rules:Array[CKYRule]) => 
			new Multinomial[Int](intStore(rules.length)).initUniform
		}
		//(fill rule prob)
		ruleProbDomain.zipWithIndex.foreach{ case (ruleArray:Array[CKYRule],i:Int)=> 
			val ess = ruleProb(i).newStatistics(rulePrior)
			ruleArray.zipWithIndex.foreach{ case (rule:CKYRule,j:Int) =>
				val parent = rule.parent
				val prob = rules(rule.parent)(rule)
				ess.updateEStep(j,prob)
			}
			ruleProb(i) = ess.runMStep
		}

		//--Lex Distribution
		//(lex prob domain)
		val lexProbDomain:Array[CKYUnary] = lexicalEntries.toArray
		//(lex prob index)
		val lexProbIndex:Map[CKYUnary,Int] = Map(lexProbDomain.zipWithIndex:_*)
		//(lex prob create/fill)
		val lexProb = lexProbDomain.map{ (rule:CKYRule) => 
			(new Multinomial[Int](intStore(numWords)))
				.newStatistics(lexPrior).distribution
		}
		//--Create Parser
		new CKYParser(
			//(sizes)
			numWords,
			numNodeTypes,
			//(parameters)
			ruleProb,
			lexProb,
			//(indexing)
			index2NodeType,
			nodeTypeIndex,
			ruleProbDomain,
			lexProbDomain,
			ruleProbIndex,
			lexProbIndex,
			//(extra checks)
			paranoid,
			algorithm
		)
	}
}

//------------------------------------------------------------------------------
// PARSER
//------------------------------------------------------------------------------
class CKYParser(
		//(sizes)
		numWords:Int,
		numNodeTypes:Int,
		//(probabilities -- data)
		ruleProb:Array[Multinomial[Int]],
		lexProb:Array[Multinomial[Int]],
		//(indexing)
		index2NodeType:Array[NodeType],
		nodeTypeIndex:Map[NodeType,Int],
		ruleProbDomain:Array[Array[CKYRule]],
		lexProbDomain:Array[CKYUnary],
		ruleProbIndex:Map[CKYRule,(Int,Int)],
		lexProbIndex:Map[CKYUnary,Int],
		//(misc)
		paranoid:Boolean = false,
		kbestCKYAlgorithm:Int = 3
			) extends Serializable {

	//-----
	// Declarations
	//-----
	type RuleList = Array[Beam]
	type RulePairList = Array[RuleList]
	type Chart = Array[Array[RulePairList]]
	type LazyStruct = (CKYRule,Beam,Beam,(ChartElem,ChartElem)=>Double)
	
	//-----
	// Values
	//-----
	private val binaryRules:Array[CKYRule] 
		= ruleProbIndex.keys.filter{ !_.isUnary }.toArray
	private val unaryRules:Array[CKYRule]  
		= ruleProbIndex.keys.filter{ _.isUnary }.toArray

	
	//-----
	// Chart Element
	//-----
	class ChartElem(
			var logScore:Double, 
			var term:CKYRule, 
			var left:ChartElem,
			var right:ChartElem,
			sent:Option[Sentence] = None) extends ParseTree with Cloneable{
		
		// -- CKY Usage --
		def apply(logScore:Double,term:CKYRule,left:ChartElem,right:ChartElem
				):ChartElem = {
			assert(logScore <= 0.0, "Setting ChartElem with bad log score: "+logScore)
			this.logScore = logScore
			this.term = term
			this.left = left
			this.right = right
			this
		}
		def apply(logScore:Double,term:CKYRule,left:ChartElem):ChartElem = {
			assert(term.isUnary, "Invalid apply for arity 1 rule")
			apply(logScore,term,left,null)
		}
		def apply(other:ChartElem):ChartElem = {
			assert(!other.isNil, "Setting to nil chart element")
			apply(other.logScore,other.term,other.left,other.right)
		}
		def nilify:Unit = { logScore = Double.NaN; term = null }
		def isNil:Boolean = (term == null)

		// -- ParseTree Overrides --
		override def parent:NodeType = {
			assert(term != null,"taking head of null rule"); 
			term.parent
		}
		override def isLeaf:Boolean = {
			left == null && right == null
		}
		override def children:Array[ParseTree] = {
			assert(term != null,"taking children of null rule")
			if(left == null && right == null) { //leaf
				return Array[ParseTree]()
			} else if(term.isUnary) {
				assert(right == null, "closure with 2 children")
				Array[ParseTree](left)
			} else if(!term.isUnary) {	
				Array[ParseTree](left,right)
			} else {
				throw new IllegalStateException("Bad cky term: " + term)
			}
		}
		override def traverse(
				ruleFn:(CKYRule)=>Any,
				lexFn:(CKYUnary,Int)=>Any):Unit = {
			traverseHelper(0,ruleFn,lexFn,()=>{})
		}
		override def logProb:Double = logScore
		def topRuleLogProb:Double = {
			val subProbs:Double = 
				if(term.isLex) { 0.0 }
				else if(term.isUnary) { left.logScore }
				else { left.logScore + right.logScore }
			logScore - subProbs
		}
		
		// -- ParseTree Optional Overrides --
		override def asParseString(sent:Sentence):String 
			= cleanParseString('String,sent)
		override def asParseProbabilities(sent:Sentence):String
			= cleanParseString('Probability,sent)
		// -- Object Overrides --
		override def clone:ChartElem = {
			new ChartElem(logScore,term,left,right)
		}
		def deepclone:ChartElem = deepclone(None)
		def deepclone(sent:Sentence):ChartElem = deepclone(Some(sent))
		private def deepclone(sent:Option[Sentence]):ChartElem = {
			val leftClone = if(left == null) null else left.deepclone(sent)
			val rightClone = if(right == null) null else right.deepclone(sent)
			new ChartElem(logScore,term,leftClone,rightClone,sent)
		}
		override def equals(a:Any) = {
			a match {
				case (elem:ChartElem) => {
					elem.term == term && elem.left == left && elem.right == right
				}
				case (_:Any) => false
			}
		}
		override def hashCode:Int = {
			term.hashCode ^ left.hashCode
		}
		override def toString:String = {
			val df = new DecimalFormat("0.000")
			"[" + df.format(logScore) + "] " + 
				term + " -> (" + children.map{ _.parent }.mkString(", ") + ")"
		}
		// -- Helpers --
		private def traverseHelper(i:Int,
				ruleFn:(CKYRule)=>Any,lexFn:(CKYUnary,Int)=>Any,up:()=>Any):Int = {
			//--Traverse
			assert(term != null, "evaluating null rule")
			var stackDepth:Int = 0
			val pos = if(term.isUnary) {
				//(case: unary rule)
				assert(right == null, "binary rule on closure ckyI")
				if(this.isLeaf) {
					assert(!term.isClosure, "closure used as lex tag")
					term match {
						case (unary:CKYUnary) =>
							sent match {
								case Some(sentence) => lexFn(unary,sentence(i))
								case None => assert(false, "No sentence attached")
							}
						case _ => throw new IllegalStateException("Not a unary: " + term)
					}
					i + 1 //return
				} else {
					term match {
						case (closure:CKYClosure) => 
							closure.chain.foreach{ (unary:CKYUnary) =>
								stackDepth+=1 
								ruleFn(unary) 
							}
						case (unary:CKYUnary) =>
							stackDepth+=1 
							ruleFn(unary) 
						case _ => throw new IllegalStateException("Unknown rule: " + term)
					}
					left.traverseHelper(i,ruleFn,lexFn,up) //return
				}
			}else if(!term.isUnary){
				//(case: binary rule)
				stackDepth+=1
				ruleFn(term) 
				val leftI = left.traverseHelper(i,ruleFn,lexFn,up)
				right.traverseHelper(leftI,ruleFn,lexFn,up) //return
			}else{
				throw new IllegalStateException("Invalid cky term")
			}
			//(pop stack and return)
			(0 until stackDepth).foreach{ (stack:Int) => up() }
			pos
		}
		private def cleanParseString(headType:Symbol,sent:Sentence):String = {
			val b = new StringBuilder
			//(clean string)
			def clean(str:String) = {
				"\"" + str.replaceAll("\"","\\\\\"").replaceAll("'","\\\\'") + "\""
			}
			val df = new DecimalFormat("0.000")
			//(traverse)
			traverseHelper(0,
				(rule:CKYRule) => {
					val head:String = headType match {
						case 'String => clean(rule.toString)
						case 'Probability => df.format(ruleProb(rule))
					}
					b.append("(").append(head).append(" ")
				},
				(rule:CKYUnary,w:Int) => {
					val head:String = headType match {
						case 'String => clean(rule.toString)
						case 'Probability => df.format(lexProb(rule,w))
					}
					b.append("( ").append(head).append(" ").
						append(clean(sent.gloss(w))).append(" ) ")
				},
				() => {
					b.append(") ")
				})
			b.toString
		}
	}

	//-----
	// K-Best List
	//-----
	class Beam(val values:Array[ChartElem],var capacity:Int) {
		private var deferred:List[LazyStruct] = null
		private var lazyNextFn:Unit=>Boolean = null
		var length = 0
		var wasReset = false
		
		// -- Lazy Eval --

		def markLazy = { 
			assert(deferred == null, "marking as lazy twice")
			deferred = List[LazyStruct]() 
		}
		def markEvaluated = { deferred = null }
		def isLazy:Boolean = (deferred != null)
		def ensureEvaluated = { 
			if(isLazy){
				while(lazyNext){ }
				markEvaluated 
			}
			if(paranoid){
				val (ok,str) = check(false); assert(ok,"ensureEvaluated: " +str)
			}
		}

		// -- Structure --
		def apply(i:Int) = {
			if(isLazy) {
				while(i >= length){
					if(!lazyNext){ throw new ArrayIndexOutOfBoundsException(""+i) }
				}
			} else {
				if(i > length){ throw new ArrayIndexOutOfBoundsException(""+i) }
			}
			values(i)
		}
		def has(i:Int):Boolean = {
			if(isLazy){
				while(i >= length){
					if(!lazyNext){ return false }
				}
				return true
			} else {
				return i < length
			}
		}
		def reset(newCapacity:Int):Unit = {
			length = 0
			capacity = newCapacity
			markEvaluated
			lazyNextFn = null
			wasReset = true
		}
		def foreach(fn:ChartElem=>Any):Unit = {
			ensureEvaluated
			for(i <- 0 until length){ fn(values(i)) }
		}
		def map[A : Manifest](fn:ChartElem=>A):Array[A] = {
			ensureEvaluated
			val rtn = new Array[A](length)
			for(i <- 0 until length){
				rtn(i) = fn(values(i))
			}
			rtn
		}
		def zipWithIndex = {
			ensureEvaluated
			values.slice(0,length).zipWithIndex
		}
		def toArray:Array[ChartElem] = {
			ensureEvaluated
			values.slice(0,length)
		}
		override def clone:Beam = {
			ensureEvaluated
			val rtn = new Beam(values.clone,capacity)
			rtn.length = this.length
			rtn
		}
		def deepclone:Beam = {
			ensureEvaluated
			val rtn = new Beam(values.map{ _.clone },capacity)
			rtn.length = this.length
			rtn
		}
		

		// -- As Per (Huang and Chiang 2005) --
		//<Paranoid Checks>
		private def check(nonempty:Boolean=true):(Boolean,String) = {
			//(non-empty)
			if(nonempty && length == 0){ return (false,"empty") }
			//(non-null)
			for(i <- 0 until this.length){
				if(values(i).isNil){ return (false,"nil element at " + i) }
			}
			//(acceptable score)
			for(i <- 0 until this.length){
				if(values(i).logScore > 0 || values(i).logScore.isNaN ){ 
					return (false,"bad score for element " + i + " " + values(i).logScore)
				}
			}
			//(sorted)
			var last:Double = Double.PositiveInfinity
			for(i <- 0 until this.length){
				if(last < values(i).logScore){ return (false,"not sorted") }
				last = values(i).logScore
			}
			//(unique)
			for(i <- 0 until this.length) {
				for(j <- (i+1) until this.length) {
					if(values(i).equals(values(j))){ 
						return (false,"not unique: " + 
							values(i) + " versus " + values(j) + " (length: " + length + ")") 
					}
				}
			}
			//(ok)
			return (true,"")
		}

		//<Algorithm 0>
		private def mult0(term:CKYRule, left:Beam, right:Beam,
				score:(ChartElem,ChartElem)=>Double
				):Array[(Double,ChartElem,ChartElem)]= {
			//--Create Combined List
			val combined:Array[(Double,ChartElem,ChartElem)] = if(right != null){
				//(case: binary rule)
				assert(left.length > 0 && right.length > 0, "bad length")
				val out 
					= new Array[(Double,ChartElem,ChartElem)](left.length*right.length)
				for( lI <- 0 until left.length ){
					for(rI <- 0 until right.length ){
						out(right.length*lI + rI) 
							= (left(lI).logScore+right(rI).logScore+score(left(lI),right(rI)),
							   left(lI),
								 right(rI))
					}
				}
				out
			} else {
				//(case: unary rule)
				assert(left.length > 0, "bad length")
				left.map{ elem => 
					(elem.logScore+score(elem,null), elem, null)
				}
			}
			//--Sort List
			val sorted = combined.sortBy( - _._1 )
			assert(sorted.length > 0, "empty combined vector")
			if(paranoid){
				//(check)
				var highest:Double = Double.PositiveInfinity
				sorted.foreach{ case (score:Double,left,right) => 
					assert(!score.isNaN, "NaN score found")
					assert(score <= highest, 
						"mult0 output not sorted: " + score + " > " + highest)
					highest = score
				}
			}
			sorted
		}
		private def merge0(term:CKYRule, 
				input:Array[(Double,ChartElem,ChartElem)]):Unit = {
			assert(term != null, "Merging bad rule")
			assert(capacity > 0 && (this.length > 0 || input.length > 0),
				"bad precondition to merge")
			var defendP = 0
			var candP = 0
			var index:Int = 0
			val defender = this.deepclone
			//--Merge
			while(index < capacity && 
					(defendP < this.length ||
					candP < input.length) ){
				val takeNew = 
					if(defendP < defender.length && candP < input.length){
						//(case: either element valid)
						if(defender(defendP).logScore >= input(candP)._1){
							false
						} else {
							true
						}
					} else if(defendP < defender.length) { false //(case: only defender)
					} else if(candP < input.length) { true //(case: only candidate)
					} else { throw new IllegalStateException() }
				if(takeNew){
					//(case: take candidate)
					val (score,left,right) = input(candP)
					assert(!score.isNaN, "setting to NaN score")
					if(right == null) {
						assert(left != null, "setting to null rule")
						values(index)(score,term,left)
					} else {
						assert(left != null, "setting to null rules")
						values(index)(score,term,left,right)
					}
					index += 1; candP += 1;
				} else {
					//(case: keep defender)
					assert(!defender(defendP).logScore.isNaN, "setting to NaN score")
					values(index)(defender(defendP))
					index += 1; defendP += 1;
				}
			}
			//--Cleanup
			//(set length)
			length = index
			assert(length != 0, "Merge returned length 0")
		}
		private def algorithm0(term:CKYRule, left:Beam, right:Beam,
				score:(ChartElem,ChartElem)=>Double):Unit = {
			assert(left.length > 0, "precondition for algorithm0")
			merge0(term,mult0(term, left, right, score))
		}
		
		//<Algorithm 1>
		private def mult1(term:CKYRule, left:Beam, right:Beam,
				score:(ChartElem,ChartElem)=>Double
				):Array[(Double,ChartElem,ChartElem)] = {
			val combined:Array[(Double,ChartElem,ChartElem)] = if(term.isUnary) {
				//--Unary Rule
				left.map{ elem => 
					(elem.logScore+score(elem,null), elem, null)
				}
			} else if(!term.isUnary) {
				//--Binary Rule
				//(setup queue)
				val pq = new PriorityQueue[(Double,Int,Int)]
				val seen = new Array[Boolean](left.length*right.length)
				def enqueue(lI:Int,rI:Int) = {
					if(	lI < left.length && 
							rI < right.length && 
							!seen(lI*right.length+rI)){
						val s 
							= left(lI).logScore+right(rI).logScore+score(left(lI),right(rI))
						pq.enqueue( (s,lI,rI) )
						seen(lI*right.length+rI) = true
					}
				}
				enqueue(0,0)
				var out = List[(Double,ChartElem,ChartElem)]()
				//(uniform cost search)
				assert(right != null, "no right child for binary rule")
				assert(left.capacity == right.capacity, "k differs between children")
				while(out.length <= left.capacity && !pq.isEmpty) {
					//(dequeue)
					val (s,lI,rI) = pq.dequeue
					out = (s,left(lI),right(rI)) :: out
					//(add neighbors)
					enqueue(lI+1,rI)
					enqueue(lI,rI+1)
				}
				//(pass value up)
				assert(!pq.isEmpty || left.length*right.length <= left.capacity,
					"priority queue is prematurely empty: " + out.length)
				out.reverse.toArray
			} else {
				throw new IllegalStateException("Arity > 2 rule")
			}
			//--Sanity Checks
			assert(combined.length > 0, "empty combined vector")
			if(paranoid){
				//(check sorted)
				var highest:Double = Double.PositiveInfinity
				combined.foreach{ case (score:Double,left,right) => 
					assert(!score.isNaN, "NaN score found")
					assert(score <= highest, 
						"mult0 output not sorted: " + score + " > " + highest)
					highest = score
				}
				//(matches algorithm 0)
				assert(
					mult0(term, left, right, score).
						zip(combined).forall{ case ((s0,l0,r0),(s1,l1,r1)) => s0 == s1 },
					"mult0 should match up with mult1")
				//(unique elements)
				for(i <- 0 until combined.length){
					for(j <- (i+1) until combined.length){
						val (sA,lA,rA) = combined(i)
						val (sB,lB,rB) = combined(j)
						assert(!(sA == sB && lA == lB && rA == rB), 
							"duplicate in mult1: " + i + ", " + j)
					}
				}
			}
			combined
		}
		private def algorithm1(term:CKYRule, left:Beam, right:Beam,
				score:(ChartElem,ChartElem)=>Double):Unit = {
			assert(left.length > 0, "precondition for algorithm1")
			merge0(term,mult1(term, left, right, score))
		}
		
		//<Algorithm 2>
		private def lazyNext:Boolean = {
			assert(isLazy, "Lazy next called on non-lazy structure")
			if(lazyNextFn == null){ lazyNextFn = mkLazyNext }
			lazyNextFn(Unit)
		}
		private def mkLazyNext:Unit=>Boolean = {
			assert(isLazy, "mkLazy called on non-lazy structure")
			//--State
			//(bookeeping)
			var lazyArray:Array[LazyStruct] = deferred.toArray
			//(check)
			if(paranoid){
				for(i <- 0 until lazyArray.length){
					val (rl1,l1,r1,fn1) = lazyArray(i)
					for(j <- (i+1) until lazyArray.length){
						val (rl2,l2,r2,fn1) = lazyArray(j)
						assert(rl1 != rl2 || l1 != l2 || r1 != r2,"duplicates in lazyArray")
					}
				}
			}
			assert(length == 0, "mklazy called with existing length")
			//(priority queue)
			case class DataSource(score:Double,source:Int,leftI:Int,rightI:Int
					) extends Ordered[DataSource] {
				def compare(that:DataSource):Int = {
					if(this.score < that.score) -1
					else if(this.score > that.score) 1
					else 0
				}
				override def toString:String = "DataSource(score="+score+
					",source="+source+",leftI="+leftI+",rightI="+rightI+")"
			}
			val pq = new PriorityQueue[DataSource]
			val seen = new HashSet[Int]
			val seenUnary = new HashSet[Int]
			//(enqueue method)
			def enqueue(source:Int,lI:Int,rI:Int) = {
				val (rule,left,right,score) = lazyArray(source)
				if(	left.has(lI) &&
						( ( right == null && 
						    !seenUnary( source*capacity + lI ) ) ||
							( ( right != null && right.has(rI) &&
						      !seen(	source * capacity * capacity + 
						              lI * capacity +
									  	    rI			) ) ) )
							){
					//(score from cfg)
					val cfgScore:Double = if(right == null) {
							seenUnary( source*capacity + lI ) = true
							left(lI).logScore+score(left(lI),null)
						} else {
							seen(	source * capacity * capacity + 
								        lI * capacity +
												rI			) = true
							left(lI).logScore+right(rI).logScore+score(left(lI),right(rI))
						}
						pq.enqueue( DataSource(cfgScore,source,lI,rI) )
				}
			}
			//(initialize queue)
			for(i <- 0 until lazyArray.length) { enqueue(i,0,0) }
			//--Function
			(Unit) => {
				if(length >= capacity) {
					//(too long)
					false
				} else if(pq.isEmpty) {
					//(no more terms to evaluate)
					if(paranoid){
						val potentialSize = lazyArray.foldLeft(0){ 
							case (sizeSoFar, (term,left,right,score)) => 
								left.ensureEvaluated
								var size = left.length
								if(right != null){
									right.ensureEvaluated
									size *= right.length
								}
								sizeSoFar + size }
						assert(potentialSize == length, "pq did not exhaust options: " + 
							potentialSize + ", used " + length + " rules " + lazyArray.length)
					}
					false
				} else {
					//(dequeue)
					val datum = pq.dequeue
					//(process datum)
					val (rule,left,right,score) = lazyArray(datum.source)
					val lI = datum.leftI
					val rI = datum.rightI
					if(rule.isUnary){
						assert(datum.score <= 0, "Log probability > 0: " + datum.score)
						values(length)(datum.score,rule,left(lI))
					} else {
						assert(datum.score <= 0, "Log probability > 0: " + datum.score)
						values(length)(datum.score,rule,left(lI),right(rI))
					}
					length += 1
					//(add neighbors) //note: checks are done in enqueue
					assert(datum.source < lazyArray.length, "source out of bounds")
					enqueue(datum.source,lI+1,rI)
					enqueue(datum.source,lI,rI+1)
					//(return)
					true
				}
			}
		}

		private def algorithm2(term:CKYRule, left:Beam, right:Beam,
				score:(ChartElem,ChartElem)=>Double):Unit = {
			algorithm3(term,left,right,score)
		}

		//<Algorithm 3>
		private def algorithm3(term:CKYRule, left:Beam, right:Beam,
				score:(ChartElem,ChartElem)=>Double):Unit = {
			if(!isLazy){ this.markLazy }
			deferred = (term,left,right,score) :: deferred
		}

		//<Top Level>
		def combine(term:CKYRule, left:Beam, right:Beam,
				score:(ChartElem,ChartElem)=>Double):Unit = {
			assert(!term.isUnary || right == null, "unary rule has 2 children")
			assert(term.isUnary || right != null, "binary rule has 1 child")
			kbestCKYAlgorithm match{
				case 0 => if(left.length > 0 && (right == null || right.length > 0)) {
					if(paranoid){val (ok,str) = check(false); assert(ok,"pre: " + str)}
					this.algorithm0(term, left, right, score)
					if(paranoid){val (ok,str) = check(); assert(ok,"post: " + str)}
				}
				case 1 => if(left.length > 0 && (right == null || right.length > 0)) {
					if(paranoid){val (ok,str) = check(false); assert(ok,"pre: " + str)}
					this.algorithm1(term, left, right, score)
					if(paranoid){val (ok,str) = check(); assert(ok,"post: " + str)}
				}
				case 2 => this.algorithm2(term, left, right, score)
				case 3 => this.algorithm3(term, left, right, score)
				case _ => throw new IllegalArgumentException(
						"bad algorithm: " + kbestCKYAlgorithm)
			}
		}
		def combine(term:CKYRule, left:Beam,
				score:(ChartElem,ChartElem)=>Double):Unit = {
			assert(term.isUnary, "must be arity 1 rule")
			combine(term, left, null, score)
		}

		// -- Standard Methods --
		def add(term:CKYRule,score:Double,left:ChartElem,right:ChartElem) = {
			//(checks)
			assert(!term.isUnary, "must be arity 2 rule")
			if(paranoid){
				(0 until length).foreach{ (i:Int) =>
					assert(values(i).term != term || values(i).left != left ||
						values(i).right != right,
						"Adding duplicate chart entry (binary)")
				}
			}
			//(add)
			values(length)(score,term,left,right)
			length += 1
		}
		def add(term:CKYRule,score:Double,left:ChartElem) = {
			//(checks)
			assert(term.isUnary, "must be arity 1 rule")
			if(paranoid){
				(0 until length).foreach{ (i:Int) =>
					assert(values(i).term != term || values(i).left != left,
						"Adding duplicate chart entry (unary -- lex?): " + term + 
						"; was reset? " + wasReset)
				}
			}
			//(add)
			values(length)(score,term,left)
			length += 1
		}
		def suggest(term:CKYRule,score:Double,left:ChartElem,right:ChartElem) = {
			if(length < capacity){ add(term,score,left,right) }
		}
		def suggest(term:CKYRule,score:Double,left:ChartElem) = {
			if(length < capacity){ add(term,score,left) }
		}
		def suggest(term:CKYRule,score:Double) = {
			if(length < capacity){ add(term,score,null) }
		}
	}

	//-----
	// Utilities
	//-----
	private def safeLn(d:Double):Double = {
		assert(!d.isNaN, "Taking the log of NaN")
		assert(d >= 0, "Taking the log of a negative number")
		if(d == 0.0){ 
			Double.NegativeInfinity
		} else { 
			scala.math.log(d) 
		}
	}
	def ruleProb(rule:CKYRule):Double = {
		val (head,index) = ruleProbIndex(rule)
		assert(!ruleProb(head).prob(index).isNaN,"NaN rule probability")
		ruleProb(head).prob(index)
	}
	def ruleLogProb(rule:CKYRule):Double = safeLn(ruleProb(rule))
	def lexProb(rule:CKYUnary,w:Int):Double = {
		if(w >= 0 && w < numWords) {
			assert(!lexProb(lexProbIndex(rule)).prob(w).isNaN, "NaN lex probability")
			lexProb(lexProbIndex(rule)).prob(w)
		} else {
			throw new IllegalArgumentException("Unknown word: " + w + " (W=" + numWords + ")");
		}
	}
	def lexLogProb(rule:CKYUnary,w:Int):Double = safeLn(lexProb(rule,w))

	//-----
	// Parsing
	//-----
	/**
		Chart creation / cache function
	*/
	val makeChart:Long=>((Int,Int)=>Chart) = {
		val chartMap = new HashMap[Long,(Int,Int)=>Chart]
		(thread:Long) =>
			if(chartMap.contains(thread)){
				chartMap(thread)
			} else {
				var largestChart = new Chart(0)
				var largestBeam = 0
				val fn = (inputLength:Int,inputBeam:Int) => {
					//--Make Chart
					val chart = if(inputLength > largestChart.length || 
							inputBeam > largestBeam){ 
						val len = math.max(inputLength, largestChart.length)
						val beam = math.max(inputBeam, largestBeam)
						//(create)
						largestChart = (0 until len).map{ (start:Int) =>            //begin
							assert(len-start > 0,"bad length end on start "+start+" len "+len)
							(0 until (len-start)).map{ (length:Int) =>                //length
								(0 to 1).map{ (arity:Int) =>                            //arity
									index2NodeType.map{ (term:NodeType) =>                //rules
										assert(beam > 0, "bad kbest end")
										new Beam((0 until beam).map{ (kbestItem:Int) => //kbest
											new ChartElem(Double.NegativeInfinity,null,null,null)
										}.toArray, beam) //convert to arrays
									}.toArray
								}.toArray
							}.toArray
						}.toArray
						//(return)
						largestBeam = inputBeam
						largestChart
					} else {
						//(cached)
						largestChart
					}
					//--Reset Chart
					for(start <- 0 until largestChart.length){
						for(len <- 0 until chart(start).length){
							for(head <- 0 until chart(start)(len)(CKYParser.UNARY).length){
								chart(start)(len)(CKYParser.UNARY)(head).reset(inputBeam)
								assert(chart(start)(len)(CKYParser.UNARY)(head).length == 0)
							}
							for(head <- 0 until chart(start)(len)(CKYParser.BINARY).length){
								chart(start)(len)(CKYParser.BINARY)(head).reset(inputBeam)
								assert(chart(start)(len)(CKYParser.BINARY)(head).length == 0)
							}
						}
					}
					//--Return
					chart
					}
			chartMap(thread) = fn
			fn
		}
	}
	
	/**
		Access a chart element
	*/
	private def gram(chart:Chart,begin:Int,end:Int,parent:NodeType,t:Int
			):Beam = {
		val head:Int = nodeTypeIndex(parent)
		if(end == begin+1){ return lex(chart,begin,parent,t) }
		//(asserts)
		assert(end > begin+1, "Chart access error: bad end: " + begin + ", " + end)
		assert(begin >= 0, "Chart access error: negative values: " + begin)
		assert(head >= 0, "Chart access error: bad head: " + head)
		assert(head < index2NodeType.length, "Chart access error: bad head: "+head)
		assert(t == 0 || t == 1, "must be one of UNARY/BINARY")
		//(access)
		chart(begin)(end-begin-1)(t)(head)
	}
	/**
		Access a lexical element
	*/
	private def lex(chart:Chart,elem:Int,parent:NodeType,t:Int=CKYParser.BINARY
			):Beam = {
		val head:Int = nodeTypeIndex(parent)
		//(asserts)
		assert(elem >= 0, "Chart access error: negative value: " + elem)
		assert(head >= 0, "Chart access error: bad head: " + head)
		assert(head < index2NodeType.length, "Chart access error: bad head: "+head)
		chart(elem)(0)(t)(head)
	}


	/**
		k-best CKY Algorithm implementation
	*/
	def parse(sent:Sentence, beam:Int):Array[ParseTree] = {
		//--Asserts
		assert(sent.length > 0, "Sentence of length 0 cannot be parsed")
		//--Get Lexical Entries
		def klex(elem:Int):Array[(CKYRule,Double)] = {
			val word:Int = sent(elem)
			lexProbDomain
				.filter{ (term:CKYUnary) => term.validLexApplication(word) }
				.map{ (term:CKYUnary) => (term, lexProb(term,word))  }
				.sortWith{ case ((rA,pA),(rB,pB)) => pA > pB }
				.map{ (pair:(CKYUnary,Double)) => (pair._1,math.log(pair._2)) }
				.toArray
		}
		//--Create Chart
		val chart = makeChart(Thread.currentThread.getId)(sent.length,beam)
		assert(chart.length >= sent.length, "Chart is too small")
		//--Lex
		var lastProb = Double.PositiveInfinity
		(0 until sent.length).foreach{ (elem:Int) =>
			assert(klex(elem).length > 0, "No lexical terms for " + sent.gloss(elem))
			klex(elem).foreach{ case (rule:CKYUnary,logProb:Double) =>
				//(add term)
				val lst:Beam = lex(chart,elem,rule.parent)
				if(lst.length < beam){
					lst.suggest(rule,logProb)
				}
				//(error checks)
				assert(lex(chart,elem,rule.parent).length > 0, "Didn't add?")
				assert(lastProb >= logProb, "Lexical probabilities are not sorted")
				lastProb = logProb
			}
		}
		//--Grammar
		for(length <- 1 to sent.length) {                      // length
			for(begin <- 0 to sent.length-length) {              // begin
				val end:Int = begin+length
				assert(end <= sent.length, "end is out of bounds")
				//(update chart)
				binaryRules.foreach{ (term:CKYRule) =>             // rules [binary]
					val ruleLProb:Double = ruleLogProb(term)
					assert(ruleLProb <= 0.0, "Log probability of >0: " + ruleLProb)
					assert(!term.isUnary, "Binary rule should be binary")
					val ruleLeft = term.leftChild
					val ruleRight = term.rightChild
					for(split <- (begin+1) to (end-1)){              // splits
						//((get variables))
						val leftU:Beam
							= gram(chart, begin,split,ruleLeft, CKYParser.UNARY)
						val rightU:Beam
							= gram(chart,split,end,   ruleRight,CKYParser.UNARY)
						val leftB:Beam
							= gram(chart, begin,split,ruleLeft, CKYParser.BINARY)
						val rightB:Beam
							= gram(chart,split,end,   ruleRight,CKYParser.BINARY)
						assert(leftU != leftB && rightU != rightB, ""+begin+" to "+end)
						val output = gram(chart,begin,end,term.parent,CKYParser.BINARY)
						val score = (left:ChartElem,right:ChartElem) => { ruleLProb }
						//((combine))
						output.combine(term,leftU,rightU,score)
						output.combine(term,leftU,rightB,score)
						output.combine(term,leftB,rightU,score)
						output.combine(term,leftB,rightB,score)
						//((error checks))
						assert( 
							(!(leftU.length > 0 && rightU.length > 0) &&       //no unaries
							 !(leftB.length > 0 && rightB.length > 0)    )  || //+ no binaries
							output.length > 0,                                 //or have out
								"Beam did not combine on rule : " + term)
					}
				}
				unaryRules.foreach{ (term:CKYRule) =>              // rules [unary]
					val ruleLProb = ruleLogProb(term)
					assert(ruleLProb <= 0.0, "Log probability of >0: " + ruleLProb)
					assert(term.isUnary, "Unary rule should be unary")
					val child:Beam = gram(chart,begin,end,term.child,CKYParser.BINARY)
					gram(chart,begin,end,term.parent,CKYParser.UNARY).combine(term,child,
						(left:ChartElem,right:ChartElem) => { ruleLProb })
				}
				//(post-update tasks)
				if(kbestCKYAlgorithm < 3) {
					index2NodeType.foreach { (parent:NodeType) => 
						gram(chart,begin,end,parent,CKYParser.BINARY).ensureEvaluated
						gram(chart,begin,end,parent,CKYParser.UNARY).ensureEvaluated
					}
				}
			}
		}
		//--Return
		Array.concat(
			gram(chart,0,sent.length,NodeType.ROOT,CKYParser.UNARY).toArray,
			gram(chart,0,sent.length,NodeType.ROOT,CKYParser.BINARY).toArray
			).map{ x => x.deepclone(sent) }
	}
	
	def parse(sent:Sentence):ParseTree = {
		val candidateTrees:Array[ParseTree] = parse(sent,1)
		if(candidateTrees.length == 0){
			throw new IllegalArgumentException(
				"Sentence not recognized by grammar: " + sent)
		} else if(candidateTrees.length == 1){
			candidateTrees(0)
		} else {
			throw new IllegalStateException("Returned multiple results for parse")
		}
	}
	
	//-----
	// EM
	//-----
	def update(trees:Iterable[ParseTree],
			lexPrior:Prior[Int,Multinomial[Int]] = Dirichlet.SYMMETRIC(0.0),
			rulePrior:Prior[Int,Multinomial[Int]] = Dirichlet.SYMMETRIC(0.0)
				):CKYParser = {
		//--Extract Grammar
		val (rules:HashMap[CKYRule,Double],lex:HashMap[(CKYUnary,Int),Double]) 
			= CKYParser.scrapeGrammar(trees)
		//--Update Probabilities
		//(get ESS)
		val ruleESS = ruleProb.map{ _.newStatistics(rulePrior) }
		val lexESS = lexProb.map{ _.newStatistics(lexPrior) }
		//(update rule ESS)
		rules.foreach{ case (rule:CKYRule,count:Double) =>
			val (i:Int,j:Int) = ruleProbIndex(rule)
			ruleESS(i).updateEStep(j,count)
		}
		//(update lex ESS)
		lex.foreach{ case ((rule:CKYUnary,w:Int),count:Double) =>
			val i:Int = lexProbIndex(rule)
			lexESS(i).updateEStep(w,count)
		}
		//(create new probabilities)
		val newRuleProb = ruleESS.map{ _.runMStep }
		val newLexProb  = lexESS.map { _.runMStep }
		//--Return Parser
		return new CKYParser(
				//(sizes)
				numWords,
				numNodeTypes,
				//(probabilities -- data)
				newRuleProb, //<--changed
				newLexProb,  //<--changed
				//(indexing)
				index2NodeType,
				nodeTypeIndex,
				ruleProbDomain,
				lexProbDomain,
				ruleProbIndex,
				lexProbIndex,
				//(misc)
				paranoid,
				kbestCKYAlgorithm
			)
	}

}
