package org.goobs.tests

import scala.util.Random


import org.scalatest.Spec
import org.scalatest.matchers.ShouldMatchers
import org.goobs.nlp._
import java.io._
import org.goobs.util.{TrackedObjectOutputStream, SingletonIterator}

object Grammars {
	def TOY:Array[GrammarRule] = {
		Array[GrammarRule](
			BinaryGrammarRule(NodeType.defaultFactory.ROOT, NodeType.defaultFactory.make("S")),
			BinaryGrammarRule(NodeType.defaultFactory.make("S"), NodeType.defaultFactory.make("NP"), NodeType.defaultFactory.make("VP")),
			BinaryGrammarRule(NodeType.defaultFactory.make("NP"), NodeType.defaultFactory.makePreterminal("NN")),
			BinaryGrammarRule(NodeType.defaultFactory.make("VP"), NodeType.defaultFactory.makePreterminal("VB"), NodeType.defaultFactory.make("NP")),
		  LexGrammarRule(NodeType.defaultFactory.makePreterminal("NN")),
			LexGrammarRule(NodeType.defaultFactory.makePreterminal("VB"))
		)
	}
	def MATH_TYPE:Array[GrammarRule] = {
		Array[GrammarRule](
			BinaryGrammarRule(NodeType.defaultFactory.ROOT, NodeType.defaultFactory.make("double")),
			BinaryGrammarRule(NodeType.defaultFactory.make("double"), NodeType.defaultFactory.make("int")),
			BinaryGrammarRule(NodeType.defaultFactory.make("int"), NodeType.defaultFactory.makePreterminal("int_tag")),
			BinaryGrammarRule(NodeType.defaultFactory.make("int"), NodeType.defaultFactory.make("int"), NodeType.defaultFactory.make("int")),
			BinaryGrammarRule(NodeType.defaultFactory.make("double"), NodeType.defaultFactory.make("int"), NodeType.defaultFactory.make("double")),
			BinaryGrammarRule(NodeType.defaultFactory.make("double"), NodeType.defaultFactory.make("double"), NodeType.defaultFactory.make("int")),
			BinaryGrammarRule(NodeType.defaultFactory.make("double"), NodeType.defaultFactory.make("double"), NodeType.defaultFactory.make("double")),
			LexGrammarRule(NodeType.defaultFactory.makePreterminal("int_tag"))
		)
	}

	def MATH_PLUS:Array[GrammarRule] = {
		Array[GrammarRule](
			GrammarRule((x:Any) => x, NodeType.defaultFactory.ROOT, NodeType.defaultFactory.make("double")),
			GrammarRule((x:Int) => x.asInstanceOf[Double], NodeType.defaultFactory.make("double"), NodeType.defaultFactory.make("int")),
			GrammarRule((x:Any) => x, NodeType.defaultFactory.make("int"), NodeType.defaultFactory.makePreterminal("int_")),
			GrammarRule((x:Int,plus:Any,y:Int) => {x + y}, NodeType.defaultFactory.make("int"), NodeType.defaultFactory.make("int"), NodeType.defaultFactory.makePreterminal("+_"), NodeType.defaultFactory.make("int")),
			GrammarRule((sent:Option[Sentence],index:Int) => sent.get.asNumber(index), NodeType.defaultFactory.makePreterminal("int_")),
			GrammarRule((sent:Option[Sentence],index:Int) => 'plus, NodeType.defaultFactory.makePreterminal("+_"))
		)
	}

	def MATH_MINUS:Array[GrammarRule] = {
		Array[GrammarRule](
			GrammarRule((x:Any) => x, NodeType.defaultFactory.ROOT, NodeType.defaultFactory.make("double")),
			GrammarRule((x:Int) => x.asInstanceOf[Double], NodeType.defaultFactory.make("double"), NodeType.defaultFactory.make("int")),
			GrammarRule((x:Any) => x, NodeType.defaultFactory.make("int"), NodeType.defaultFactory.makePreterminal("int_")),
			GrammarRule((x:Int,minus:Any,y:Int) => {x - y}, NodeType.defaultFactory.make("int"), NodeType.defaultFactory.make("int"), NodeType.defaultFactory.makePreterminal("-_"), NodeType.defaultFactory.make("int")),
			GrammarRule((sent:Option[Sentence],index:Int) => sent.get.asNumber(index), NodeType.defaultFactory.makePreterminal("int_")),
			GrammarRule((sent:Option[Sentence],index:Int) => 'minus, NodeType.defaultFactory.makePreterminal("-_"))
		)
	}

	def MATH:Array[GrammarRule] = {
		Array[GrammarRule](
			GrammarRule((x:Any) => x, NodeType.defaultFactory.ROOT, NodeType.defaultFactory.make("double")),
			GrammarRule((x:Int) => x.asInstanceOf[Double], NodeType.defaultFactory.make("double"), NodeType.defaultFactory.make("int")),
			GrammarRule((x:Any) => x, NodeType.defaultFactory.make("int"), NodeType.defaultFactory.makePreterminal("int_")),
			GrammarRule((x:Int,plus:Any, y:Int) => {x + y}, NodeType.defaultFactory.make("int"), NodeType.defaultFactory.make("int"), NodeType.defaultFactory.makePreterminal("+_"), NodeType.defaultFactory.make("int")),
			GrammarRule((x:Int,minus:Any,y:Int) => {x - y}, NodeType.defaultFactory.make("int"), NodeType.defaultFactory.make("int"), NodeType.defaultFactory.makePreterminal("-_"), NodeType.defaultFactory.make("int")),
			GrammarRule((x:Int,times:Any,y:Int) => {x * y}, NodeType.defaultFactory.make("int"), NodeType.defaultFactory.make("int"), NodeType.defaultFactory.makePreterminal("*_"), NodeType.defaultFactory.make("int")),
			GrammarRule((x:Int,div:Any,  y:Int) => {if(y==0){ 0 } else {x / y}}, NodeType.defaultFactory.make("int"), NodeType.defaultFactory.make("int"), NodeType.defaultFactory.makePreterminal("/_"), NodeType.defaultFactory.make("int")),
			GrammarRule((x:Int,pow:Any,  y:Int) => {math.pow(x,y).toInt}, NodeType.defaultFactory.make("int"), NodeType.defaultFactory.make("int"), NodeType.defaultFactory.makePreterminal("^_"), NodeType.defaultFactory.make("int")),
			GrammarRule((l:Any,x:Int,y:Any) => { x }, NodeType.defaultFactory.make("int"), NodeType.defaultFactory.makePreterminal("(_"), NodeType.defaultFactory.make("int"), NodeType.defaultFactory.makePreterminal(")_")),
			GrammarRule((sent:Option[Sentence],index:Int) => sent.get.asNumber(index), NodeType.defaultFactory.makePreterminal("int_")).restrict(_ == math2str.indexOf("#")),
			GrammarRule((sent:Option[Sentence],index:Int) => 'plus, NodeType.defaultFactory.makePreterminal("+_")),
			GrammarRule((sent:Option[Sentence],index:Int) => 'minus, NodeType.defaultFactory.makePreterminal("-_")),
			GrammarRule((sent:Option[Sentence],index:Int) => 'timex, NodeType.defaultFactory.makePreterminal("*_")),
			GrammarRule((sent:Option[Sentence],index:Int) => 'div, NodeType.defaultFactory.makePreterminal("/_")),
			GrammarRule((sent:Option[Sentence],index:Int) => 'pow, NodeType.defaultFactory.makePreterminal("^_")),
			GrammarRule((sent:Option[Sentence],index:Int) => 'lparen, NodeType.defaultFactory.makePreterminal("(_")),
			GrammarRule((sent:Option[Sentence],index:Int) => 'rparen, NodeType.defaultFactory.makePreterminal(")_"))
		)
	}
	
	val w2str:Array[String] = Array[String](
	  "I",
		"like",
		"sugar",
		"NLP"
		)
	
	val mathtyp2str:Array[String] = Array[String](
		"0",
		"1",
		"2",
		"3",
		"4"
	)
	
	val math2str:Array[String] = Array[String](
		"#",
		"+",
		"-",
		"*",
		"/",
		"^",
		"(",
		")"
	)
}

object Trees {
	/**
	 * (S (NP (NN I)) (VP (VB like) (NP (NN sugar))))
	 * (S (NP (NN I)) (VP (VB like) (NP (NN nlp))))
	 */
	def TOY:Array[ParseTree] = Array[ParseTree](
		ParseTree(NodeType.defaultFactory.make("S"), Array[ParseTree](
			ParseTree(NodeType.defaultFactory.make("NP"),Array[ParseTree](
				ParseTree(NodeType.defaultFactory.makePreterminal("NN"), Grammars.w2str.indexOf("I"))), 1.0, NodeType.defaultFactory, true ),
			ParseTree(NodeType.defaultFactory.make("VP"),Array[ParseTree](
				ParseTree(NodeType.defaultFactory.makePreterminal("VB"), Grammars.w2str.indexOf("like")),
				ParseTree(NodeType.defaultFactory.make("NP"),Array[ParseTree](
					ParseTree(NodeType.defaultFactory.makePreterminal("NN"), Grammars.w2str.indexOf("sugar") ) ), 1.0, NodeType.defaultFactory, true)), 1.0, NodeType.defaultFactory, true )), 1.0, NodeType.defaultFactory, true ),
		ParseTree(NodeType.defaultFactory.make("S"), Array[ParseTree](
			ParseTree(NodeType.defaultFactory.make("NP"),Array[ParseTree](
				ParseTree(NodeType.defaultFactory.makePreterminal("NN"), Grammars.w2str.indexOf("I"))), 1.0, NodeType.defaultFactory, true ),
			ParseTree(NodeType.defaultFactory.make("VP"),Array[ParseTree](
				ParseTree(NodeType.defaultFactory.makePreterminal("VB"), Grammars.w2str.indexOf("like")),
				ParseTree(NodeType.defaultFactory.make("NP"),Array[ParseTree](
					ParseTree(NodeType.defaultFactory.makePreterminal("NN"), Grammars.w2str.indexOf("NLP") ) ), 1.0, NodeType.defaultFactory, true)), 1.0, NodeType.defaultFactory, true )), 1.0, NodeType.defaultFactory, true )
	)
	
	def UNARIES:ParseTree 
		= ParseTree(NodeType.defaultFactory.make("A"), Array[ParseTree](
		  	ParseTree(NodeType.defaultFactory.make("B"), Array[ParseTree](
					ParseTree(NodeType.defaultFactory.makePreterminal("C_"), Grammars.w2str.indexOf("NLP"))))))
}

class CKYParserSpec extends Spec with ShouldMatchers {
	import Grammars._
	
	describe("A Node Type"){
		it("should be createable"){
			//(simple equality)
			NodeType.defaultFactory.make("S") should be (NodeType.defaultFactory('S))
			NodeType.defaultFactory.make("S") should be (NodeType.defaultFactory.make('S))
			//(with flags)
			NodeType.defaultFactory.make("NN",NodeType.SYM_PRETERMINAL) should be (NodeType.defaultFactory('NN))
			//(constants)
			NodeType.defaultFactory.ROOT
			NodeType.defaultFactory.WORD
			NodeType.defaultFactory.WORD should be (NodeType.defaultFactory.makeWord())
			//(duplicate create)
			intercept[IllegalArgumentException]{
				NodeType.defaultFactory.make("S", NodeType.SYM_PRETERMINAL)
			}
		}
		it("should remember itself"){
			NodeType.defaultFactory.make("somerandomnode")
			NodeType.defaultFactory('somerandomnode)
			NodeType.defaultFactory("somerandomnode")
		}
		it("should recognize flags"){
			NodeType.defaultFactory.WORD.isWord should be (true)
			NodeType.defaultFactory.WORD.isPreterminal should be (false)
			NodeType.defaultFactory.makeWord().isWord should be (true)
			NodeType.defaultFactory.make("NN",NodeType.SYM_PRETERMINAL).isWord should be (false)
			NodeType.defaultFactory.make("NN",NodeType.SYM_PRETERMINAL).isPreterminal should be (true)
		}
		it("should hash well"){
			def randomNode:NodeType = NodeType.defaultFactory.make(scala.util.Random.nextString(10))
			scala.util.Random.setSeed(42);
			for(iter <- 0 until 100){
				//(variables)
				val bucket = 10 + scala.util.Random.nextInt(100);
				bucket should be >= 10
				bucket should be < 1010
				val histogram = new Array[Int](bucket)
				//(hash into histogram)
				for(i <- 0 until (bucket*100)){
					histogram(randomNode.hashCode.abs % bucket) += 1
				}
				//(check uniform hashing)
				histogram.forall{ (count:Int) => count < 200} should be (true)
			}
		}
	}
	
	describe("A sentence"){
		it("should be creatable"){
			val sent:Sentence = Sentence(w2str,"I like sugar")
			//(integer gets)
			sent(0) should be (0)
			sent(1) should be (1)
			sent(2) should be (2)
			//(string gets)
			sent.gloss(0) should be ("I")
			sent.gloss(1) should be ("like")
			sent.gloss(2) should be ("sugar")
			//(object overrides)
			sent.toString should be ("I like sugar")
			sent should be (sent)
			sent.hashCode should be (sent.hashCode)
			//(in depth equality check)
			val sent2:Sentence = Sentence(w2str,"I like sugar")
			sent should be (sent2)
			sent.hashCode should be (sent2.hashCode)
		}
		it("should be creatable from a corpus"){
			val corpus:Array[Sentence] = Sentence(Array[String]("I like sugar", "I like NLP"))
			corpus.length should be (2)
			corpus(0)(2) should be (2)
			corpus(1)(2) should be (3)
			corpus(0) should not be (corpus(1))
		}
	}

	describe("A grammar rule"){
		it("should have consistent equals and hash code"){
			import Grammars._
			//--Simple Rules
			//(trivial equality)
			TOY(0) should be (TOY(0))
			TOY(0).hashCode should be (TOY(0).hashCode)
			//(instance different equality)
			val rootA = BinaryGrammarRule(NodeType.defaultFactory.ROOT, NodeType.defaultFactory.make("S"))
			val rootB = BinaryGrammarRule(NodeType.defaultFactory.ROOT, NodeType.defaultFactory.make("S"))
			val rootC = new SimpleGrammarRule(NodeType.defaultFactory.ROOT, NodeType.defaultFactory.make("S"))
			rootA should be (rootB)
			rootA should be (rootC)
			rootB should be (rootC)
			rootC should be (rootA)
			//--All Types
			//(unaries)
			val simpleUnary = new SimpleGrammarRule(NodeType.defaultFactory.ROOT, NodeType.defaultFactory.make("X"))
			val binaryUnary = BinaryGrammarRule(NodeType.defaultFactory.ROOT, NodeType.defaultFactory.make("X"))
			val ckyUnary = new CKYUnary(NodeType.defaultFactory.ROOT, NodeType.defaultFactory.make("X"));
			simpleUnary should be (binaryUnary)
			simpleUnary should be (ckyUnary)
			binaryUnary should be (simpleUnary)
			binaryUnary should be (ckyUnary)
			ckyUnary should be (simpleUnary)
			ckyUnary should be (binaryUnary)
			simpleUnary.hashCode should be (binaryUnary.hashCode)
			binaryUnary.hashCode should be (ckyUnary.hashCode)
			//(binaries)
			val simpleBinary = new SimpleGrammarRule(NodeType.defaultFactory.ROOT, NodeType.defaultFactory.make("X"), NodeType.defaultFactory.make("X"))
			val binaryBinary = BinaryGrammarRule(NodeType.defaultFactory.ROOT, NodeType.defaultFactory.make("X"), NodeType.defaultFactory.make("X"))
			val ckyBinary = new CKYBinary(NodeType.defaultFactory.ROOT, NodeType.defaultFactory.make("X"), NodeType.defaultFactory.make("X"));
			simpleBinary should be (binaryBinary)
			simpleBinary should be (ckyBinary)
			binaryBinary should be (simpleBinary)
			binaryBinary should be (ckyBinary)
			ckyBinary should be (simpleBinary)
			ckyBinary should be (binaryBinary)
			simpleBinary.hashCode should be (binaryBinary.hashCode)
			binaryBinary.hashCode should be (ckyBinary.hashCode)
			//--Not Equals
			val simpleTrinary2 = new SimpleGrammarRule(NodeType.defaultFactory.ROOT, NodeType.defaultFactory.make("X"), NodeType.defaultFactory.make("X"), NodeType.defaultFactory.make("X"))
			val simpleBinary2 = new SimpleGrammarRule(NodeType.defaultFactory.ROOT, NodeType.defaultFactory.make("X"), NodeType.defaultFactory.make("X"))
			val binaryBinary2 = BinaryGrammarRule(NodeType.defaultFactory.ROOT, NodeType.defaultFactory.make("X"), NodeType.defaultFactory.make("Y"))
			val ckyBinary2 = new CKYBinary(NodeType.defaultFactory.ROOT, NodeType.defaultFactory.make("X"), NodeType.defaultFactory.make("Z"));
			val ckyUnary2 = new CKYUnary(NodeType.defaultFactory.ROOT, NodeType.defaultFactory.make("X"))
			simpleTrinary2 should not be (simpleBinary2)
			simpleTrinary2 should not be (binaryBinary2)
			simpleTrinary2 should not be (ckyBinary2)
			simpleTrinary2 should not be (ckyUnary2)
			simpleBinary2 should not be (simpleTrinary2)
			simpleBinary2 should not be (binaryBinary2)
			simpleBinary2 should not be (ckyBinary2)
			simpleBinary2 should not be (ckyUnary2)
			binaryBinary2 should not be (simpleBinary2)
			binaryBinary2 should not be (simpleTrinary2)
			binaryBinary2 should not be (ckyBinary2)
			binaryBinary2 should not be (ckyUnary2)
			ckyBinary2 should not be (simpleBinary2)
			ckyBinary2 should not be (binaryBinary2)
			ckyBinary2 should not be (simpleTrinary2)
			ckyBinary2 should not be (ckyUnary2)
			ckyUnary2 should not be (simpleBinary2)
			ckyUnary2 should not be (binaryBinary2)
			ckyUnary2 should not be (ckyBinary2)
			ckyUnary2 should not be (simpleTrinary2)
		}
		it("should hash well"){
			def randomNode:NodeType = NodeType.defaultFactory.make(scala.util.Random.nextString(1))
			scala.util.Random.setSeed(42);
			for(iter <- 0 until 100){
				//(variables)
				val bucket = scala.util.Random.nextInt(100);
				val simple = new Array[Int](bucket)
				val cky = new Array[Int](bucket)
				//(hash into histogram)
				for(i <- 0 until (bucket*bucket)){
					val ruleSimple = new SimpleGrammarRule(randomNode, randomNode, randomNode);
					val ruleCKY = new CKYUnary(randomNode, randomNode);
					simple(ruleSimple.hashCode.abs % bucket) += 1
					cky(ruleCKY.hashCode.abs % bucket) += 1
				}
				//(check uniform hashing)
				simple.forall{ (count:Int) => count < bucket*5} should be (true)
				cky.forall{ (count:Int) => count < bucket*5} should be (true)
			}
		}
		it("should respect closure equality/hashcode"){
			//--Variables
			val top = new CKYUnary(NodeType.defaultFactory.make('A), NodeType.defaultFactory.make('B))
			val mid = new CKYUnary(NodeType.defaultFactory.make('B), NodeType.defaultFactory.make('C))
			val midClone = new CKYUnary(NodeType.defaultFactory.make('B), NodeType.defaultFactory.make('C))
			val bottom = new CKYUnary(NodeType.defaultFactory.make('C), NodeType.defaultFactory.make('D))
			val bottomWrong = new CKYUnary(NodeType.defaultFactory.make('C), NodeType.defaultFactory.make('E))
			//--Closure Tests
			//(equality)
			new CKYClosure(top,mid,bottom) should be (new CKYClosure(top,mid,bottom))
			new CKYClosure(top,mid,bottom) should be (new CKYClosure(top,midClone,bottom))
			new CKYClosure(top,mid,bottom) should not be (new CKYClosure(top,mid,bottomWrong))
			//(hash code)
			new CKYClosure(top,mid,bottom).hashCode should be (new CKYClosure(top,mid,bottom).hashCode)
			new CKYClosure(top,mid,bottom).hashCode should be (new CKYClosure(top,midClone,bottom).hashCode)
			new CKYClosure(top,mid,bottom).hashCode should not be (new CKYClosure(top,mid,bottomWrong).hashCode)
			//--Unary Tests
			//(equality)
			new CKYClosure(top,mid,bottom) should be (new CKYUnary(NodeType.defaultFactory('A),NodeType.defaultFactory('D)))
			new CKYClosure(top,mid,bottom) should be (new SimpleGrammarRule(NodeType.defaultFactory('A),NodeType.defaultFactory('D)))
			//(hash code)
			new CKYClosure(top,mid,bottom).hashCode should be (new CKYUnary(NodeType.defaultFactory('A),NodeType.defaultFactory('D)).hashCode)
			new CKYClosure(top,mid,bottom).hashCode should be (new SimpleGrammarRule(NodeType.defaultFactory('A),NodeType.defaultFactory('D)).hashCode)
		}
	}

	describe("A tree"){
		it("should represent unaries"){
			Trees.UNARIES
		}
		it("should scrape constituent rules (no closure)"){
			val (rules,lex) = CKYParser.scrapeGrammar(Array[ParseTree](Trees.UNARIES))
			//(existence)
			rules.keys.toArray.contains(GrammarRule(NodeType.defaultFactory.make("A"), NodeType.defaultFactory.make("B"))) should be (true)
			rules.keys.toArray.contains(GrammarRule(NodeType.defaultFactory.make("B"), NodeType.defaultFactory.makePreterminal("C_"))) should be (true)
			rules.keys.toArray.contains(GrammarRule(NodeType.defaultFactory.make("A"), NodeType.defaultFactory.makePreterminal("C_"))) should be (false)
			//(probabilities)
			rules(new CKYUnary(NodeType.defaultFactory.ROOT, NodeType.defaultFactory.make("A"))) should be (1.0 plusOrMinus 0.000001)
			rules(new CKYUnary(NodeType.defaultFactory.make("A"), NodeType.defaultFactory.make("B"))) should be (1.0 plusOrMinus 0.000001)
			rules(new CKYUnary(NodeType.defaultFactory.make("B"), NodeType.defaultFactory.makePreterminal("C_"))) should be (1.0 plusOrMinus 0.000001)
		}
	}
	
	describe("A toy binarize(NodeType.defaultFactory)d grammar") {
		it("can be created"){
			val toyGrammar = TOY;
		}
		it("should binarize(NodeType.defaultFactory)"){
			val topRule = TOY(0);
			topRule.binarize(NodeType.defaultFactory).foreach{ (rule:CKYRule) =>
				//(parent)
				rule.parent should  be (NodeType.defaultFactory.ROOT)
				//(unary check)
				assert(rule.isUnary)
				assert(rule.isInstanceOf[CKYUnary])
				rule.child should be (NodeType.defaultFactory("S"))
				//(not binary check)
				intercept[AssertionError]{
					rule.leftChild;
				}
				intercept[AssertionError]{
					rule.rightChild
				}
			}
		}
		it("should know about its lexical elements"){
			assert(!TOY(0).binarize(NodeType.defaultFactory).next().isLex)
			assert(!TOY(1).binarize(NodeType.defaultFactory).next().isLex)
			assert(!TOY(2).binarize(NodeType.defaultFactory).next().isLex)
			assert(!TOY(3).binarize(NodeType.defaultFactory).next().isLex)
			assert(TOY(4).binarize(NodeType.defaultFactory).next().isLex)
			assert(TOY(5).binarize(NodeType.defaultFactory).next().isLex)
		}
		it("should be passable to a parser"){
			CKYParser(w2str.length, TOY) should not be (null)
		}
		it("should have a proper rule distribution"){
			val parser = CKYParser(w2str.length, TOY);
			//(should have probabilities)
			parser.ruleProb(new CKYBinary(NodeType.defaultFactory('S), NodeType.defaultFactory('NP), NodeType.defaultFactory('VP))) should be (1.0)
			parser.ruleProb(new CKYBinary(NodeType.defaultFactory('VP), NodeType.defaultFactory('VB), NodeType.defaultFactory('NP))) should be (1.0)
			parser.ruleProb(new CKYUnary(NodeType.defaultFactory.ROOT, NodeType.defaultFactory('S))) should be (1.0)
			parser.ruleProb(new CKYUnary(NodeType.defaultFactory('NP), NodeType.defaultFactory('NN))) should be (1.0)
			//(should not have probabilities)
			intercept[NoSuchElementException]{
				parser.ruleProb(new CKYUnary(NodeType.defaultFactory('NN), NodeType.defaultFactory.WORD))
			}
			intercept[NoSuchElementException]{
				parser.ruleProb(new CKYUnary(NodeType.defaultFactory('VB), NodeType.defaultFactory.WORD))
			}
		}
		it("should have a proper lex distribution"){
			val parser = CKYParser(w2str.length, TOY);
			(0 until w2str.length).foreach{ (w:Int) =>
				//(should have probabilities)
				parser.lexProb(new CKYUnary(NodeType.defaultFactory('NN), NodeType.defaultFactory.WORD), w) should be (1.0 / w2str.length.asInstanceOf[Double])
				parser.lexProb(new CKYUnary(NodeType.defaultFactory('VB), NodeType.defaultFactory.WORD), w) should be (1.0 / w2str.length.asInstanceOf[Double])
				//(should not have probabilities)
				intercept[NoSuchElementException]{
					parser.lexProb(new CKYUnary(NodeType.defaultFactory.ROOT, NodeType.defaultFactory('S)), w)
				}
				intercept[NoSuchElementException]{
					parser.lexProb(new CKYUnary(NodeType.defaultFactory('NP), NodeType.defaultFactory('NN)), w)
				}
			}
		}
		it("should parse simple sentences"){
			val parser = CKYParser.apply(w2str.length, TOY.map{ (_,0.0) }, paranoid=true);
			val sent:Sentence = Sentence(w2str, "I like sugar")
			//(parse beam 1)
			val parse1 = parser.parse(sent,1);
			parse1 should not be (null)
			parse1.length should be (1)
			parse1(0).parent should be (NodeType.defaultFactory.ROOT);
			parse1(0).asParseString() should not be (null)
			parser.parse(sent) should not be (null)
			//(parse another sentence)
			val sent2:Sentence = Sentence(w2str, "I like NLP")
			val parseOther = parser.parse(sent2,1)
			parseOther.length should be (1)
			parseOther.foreach{ (p:ParseTree) =>
				p.parent should be (NodeType.defaultFactory.ROOT)
			}
		}
		it("should define a single parse tree"){
			val parser = CKYParser.apply(w2str.length, TOY.map{ (_,0.0) }, paranoid=true);
			val sent:Sentence = Sentence(w2str, "I like like sugar")
			parser.parse(sent,1).length should be (0)
		}
		it("should learn a distribution"){
			//(variables)
			val parser = CKYParser.apply(w2str.length, TOY.map{ (_,0.0) }, paranoid=true);
			val sentA:Sentence = Sentence(w2str, "I like sugar")
			val sentB:Sentence = Sentence(w2str, "I like NLP")
			//(get parses)
			val parses:Array[ParseTree] = Array[ParseTree](
				parser.parse(sentA),
				parser.parse(sentB)
			)
			//(update parser)
			val learnedParser = parser.update(parses)
			//(check Output)
			learnedParser.ruleProb(TOY(0).binarize(NodeType.defaultFactory).next.asInstanceOf[CKYRule]) should be (1.0 plusOrMinus 0.00001)
			learnedParser.ruleProb(TOY(1).binarize(NodeType.defaultFactory).next.asInstanceOf[CKYRule]) should be (1.0 plusOrMinus 0.00001)
			learnedParser.ruleProb(TOY(2).binarize(NodeType.defaultFactory).next.asInstanceOf[CKYRule]) should be (1.0 plusOrMinus 0.00001)
			learnedParser.ruleProb(TOY(3).binarize(NodeType.defaultFactory).next.asInstanceOf[CKYRule]) should be (1.0 plusOrMinus 0.00001)
			learnedParser.lexProb( TOY(4).binarize(NodeType.defaultFactory).next.asInstanceOf[CKYUnary], w2str.indexOf("I")) should be (0.5 plusOrMinus 0.00001)
			learnedParser.lexProb( TOY(4).binarize(NodeType.defaultFactory).next.asInstanceOf[CKYUnary], w2str.indexOf("sugar")) should be (0.25 plusOrMinus 0.00001)
			learnedParser.lexProb( TOY(4).binarize(NodeType.defaultFactory).next.asInstanceOf[CKYUnary], w2str.indexOf("NLP")) should be (0.25 plusOrMinus 0.00001)
			learnedParser.lexProb( TOY(5).binarize(NodeType.defaultFactory).next.asInstanceOf[CKYUnary], w2str.indexOf("like")) should be (1.0 plusOrMinus 0.00001)
		}
		it("should be creatable from trees"){
			val parser = CKYParser(Trees.TOY);
			//--Simple Sentence
			val sentA:Sentence = Sentence(w2str, "I like sugar")
			//(parse beam 1)
			val parse1 = parser.parse(sentA,1);
			parse1 should not be (null)
			parse1.length should be (1)
			parse1(0).parent should be (NodeType.defaultFactory.ROOT);
			parse1(0).asParseString() should not be (null)
			parser.parse(sentA) should not be (null)
			//(parse another sentence)
			val sentB:Sentence = Sentence(w2str, "I like NLP")
			val parseOther = parser.parse(sentB,1)
			parseOther.length should be (1)
			parseOther.foreach{ (p:ParseTree) =>
				p.parent should be (NodeType.defaultFactory.ROOT)
			}
			//--Update Parser
			//(get parses)
			val parses:Array[ParseTree] = Array[ParseTree](
				parser.parse(sentA),
				parser.parse(sentB)
			)
			//(update parser)
			val learnedParser = parser.update(parses)
			//(check output)
			learnedParser.ruleProb(TOY(0).binarize(NodeType.defaultFactory).next.asInstanceOf[CKYRule]) should be (1.0 plusOrMinus 0.00001)
			learnedParser.ruleProb(TOY(1).binarize(NodeType.defaultFactory).next.asInstanceOf[CKYRule]) should be (1.0 plusOrMinus 0.00001)
			learnedParser.ruleProb(TOY(2).binarize(NodeType.defaultFactory).next.asInstanceOf[CKYRule]) should be (1.0 plusOrMinus 0.00001)
			learnedParser.ruleProb(TOY(3).binarize(NodeType.defaultFactory).next.asInstanceOf[CKYRule]) should be (1.0 plusOrMinus 0.00001)
			learnedParser.lexProb( TOY(4).binarize(NodeType.defaultFactory).next.asInstanceOf[CKYUnary], w2str.indexOf("I")) should be (0.5 plusOrMinus 0.00001)
			learnedParser.lexProb( TOY(4).binarize(NodeType.defaultFactory).next.asInstanceOf[CKYUnary], w2str.indexOf("sugar")) should be (0.25 plusOrMinus 0.00001)
			learnedParser.lexProb( TOY(4).binarize(NodeType.defaultFactory).next.asInstanceOf[CKYUnary], w2str.indexOf("NLP")) should be (0.25 plusOrMinus 0.00001)
			learnedParser.lexProb( TOY(5).binarize(NodeType.defaultFactory).next.asInstanceOf[CKYUnary], w2str.indexOf("like")) should be (1.0 plusOrMinus 0.00001)
		}
	}
	
	describe("a general grammar"){
		it("can be created"){
			val grammar = MATH_TYPE;
		}
		it("should binarize(NodeType.defaultFactory)"){
			MATH_TYPE.foreach{ (rule:GrammarRule) =>
				rule.binarize(NodeType.defaultFactory).forall{ _.isInstanceOf[CKYRule] } should be (true)
			}
		}
		it("should know about its lexical elements"){
			assert(!MATH_TYPE(0).binarize(NodeType.defaultFactory).next().isLex)
			assert(!MATH_TYPE(1).binarize(NodeType.defaultFactory).next().isLex)
			assert(!MATH_TYPE(2).binarize(NodeType.defaultFactory).next().isLex)
			assert(!MATH_TYPE(3).binarize(NodeType.defaultFactory).next().isLex)
			assert(!MATH_TYPE(4).binarize(NodeType.defaultFactory).next().isLex)
			assert(!MATH_TYPE(5).binarize(NodeType.defaultFactory).next().isLex)
			assert(!MATH_TYPE(6).binarize(NodeType.defaultFactory).next().isLex)
			assert( MATH_TYPE(7).binarize(NodeType.defaultFactory).next().isLex)
		}
		it("should be passable to a parser"){
			CKYParser(w2str.length, MATH_TYPE) should not be (null)
		}
		it("should have a proper rule distribution"){
			val parser = CKYParser(w2str.length, MATH_TYPE);
			//(should have probabilities)
			parser.ruleProb(new CKYBinary(NodeType.defaultFactory('int), NodeType.defaultFactory('int), NodeType.defaultFactory('int))) should be (0.5)
			parser.ruleProb(new CKYUnary(NodeType.defaultFactory('int), NodeType.defaultFactory('int_tag))) should be (0.5)
			parser.ruleProb(new CKYBinary(NodeType.defaultFactory('double), NodeType.defaultFactory('int), NodeType.defaultFactory('double))) should be (0.25)
			parser.ruleProb(new CKYBinary(NodeType.defaultFactory('double), NodeType.defaultFactory('double), NodeType.defaultFactory('int))) should be (0.25)
			parser.ruleProb(new CKYBinary(NodeType.defaultFactory('double), NodeType.defaultFactory('double), NodeType.defaultFactory('double))) should be (0.25)
			parser.ruleProb(new CKYUnary(NodeType.defaultFactory('double), NodeType.defaultFactory('int))) should be (0.25)
			intercept[NoSuchElementException]{
				parser.ruleProb(new CKYUnary(NodeType.defaultFactory('double), NodeType.defaultFactory('int_tag)))
			}
			parser.ruleProb(new CKYUnary(NodeType.defaultFactory.ROOT, NodeType.defaultFactory('double))) should be (1.0 plusOrMinus 0.000001)
			intercept[NoSuchElementException]{
				parser.ruleProb(new CKYUnary(NodeType.defaultFactory.ROOT, NodeType.defaultFactory('int)))
			}
			intercept[NoSuchElementException]{
				parser.ruleProb(new CKYUnary(NodeType.defaultFactory.ROOT, NodeType.defaultFactory('int_tag)))
			}
			//(should not have probabilities)
			intercept[NoSuchElementException]{
				parser.ruleProb(new CKYUnary(NodeType.defaultFactory('int), NodeType.defaultFactory.WORD))
			}
		}
		it("should have a proper lex distribution"){
			val parser = CKYParser(mathtyp2str.length, MATH_TYPE);
			(0 until mathtyp2str.length).foreach{ (w:Int) =>
			//(should have probabilities)
				parser.lexProb(new CKYUnary(NodeType.defaultFactory('int_tag), NodeType.defaultFactory.WORD), w) should be (1.0 / mathtyp2str.length.asInstanceOf[Double])
				//(should not have probabilities)
				intercept[NoSuchElementException]{
					parser.lexProb(new CKYUnary(NodeType.defaultFactory.ROOT, NodeType.defaultFactory('double)), w)
				}
				intercept[NoSuchElementException]{
					parser.lexProb(new CKYUnary(NodeType.defaultFactory('double), NodeType.defaultFactory('int)), w)
				}
			}
		}
		it("should parse simple sentences"){
			val parser = CKYParser.apply(mathtyp2str.length, MATH_TYPE.map{ (_,0.0) }, paranoid=true);
			val sent:Sentence = Sentence(mathtyp2str, "0 1 2 3 4")
			//(parse beam 1)
			val parse1 = parser.parse(sent,1);
			parse1 should not be (null)
			parse1.length should be (1)
			parse1(0).parent should be (NodeType.defaultFactory.ROOT);
			parse1(0).asParseString() should not be (null)
			parser.parse(sent) should not be (null)
			//(parse with a beam)
			val parse2 = parser.parse(sent,100)
			parse2 should not be (null)
			parse2.length should be > 1
			parse2.length should be <= 100
			parse2.foreach{ (p:ParseTree) =>
				p.parent should be (NodeType.defaultFactory.ROOT)
			}
		}
	}

	describe("Arithmetic"){
		case class MSent(gloss:String) extends Sentence {
			val words = gloss.split("""\s+""").map{ (s:String) =>
				try {
					s.toDouble
					math2str.indexOf("#")
				} catch {
					case (e:Exception) => math2str.indexOf(s)
				}
			}
			val nums = gloss.split("""\s+""").map{ (s:String) =>
				try {
					s.toDouble
				} catch {
					case (e:Exception) => Double.NaN
				}
			}
			override def apply(i:Int) = words(i)
			override def length:Int = words.length
			override def gloss(i:Int) = math2str(words(i))
			override def asNumber(i:Int) = nums(i).toInt
			override def asDouble(i:Int) = nums(i)
		}

		it("can be created"){
			val grammar = MATH;
		}
		it("should binarize(NodeType.defaultFactory)"){
			MATH.foreach{ (rule:GrammarRule) =>
				rule.binarize(NodeType.defaultFactory).forall{ _.isInstanceOf[CKYRule] } should be (true)
			}
		}
		it("should create a parser"){
			CKYParser.apply(math2str.length, MATH.map{ (_,0.0) }, paranoid=true);
		}
		it("should parse addition if forced to"){
			val parser = CKYParser.apply(math2str.length, MATH_PLUS.map{ (_,0.0) }, paranoid=true);
			val parse = parser.parse(MSent("1 + 2"));
			parse should not be (null)
			parse.evaluate should be (3.toDouble)
			parse.logProb should be > (Double.NegativeInfinity)
		}
		it("should parse subtraction if forced to"){
			val parser = CKYParser.apply(math2str.length, MATH_MINUS.map{ (_,0.0) }, paranoid=true);
			val parse = parser.parse(MSent("1 - 2"));
			parse should not be (null)
			parse.evaluate should be (-1.toDouble)
			parse.logProb should be > (Double.NegativeInfinity)
		}
		it("should parse ambiguously"){
			//--Easy Case
			//(construct parses)
			var parser = CKYParser.apply(math2str.length, MATH.map{ (_,0.0) }, paranoid=true);
			var parse = parser.parse(MSent("6 + 2"), 100);
			parse.length should be (5)
			var values:Array[Int] = parse.map{ _.evaluate.asInstanceOf[Double].toInt }
			//(evaluate ambiguity)
			values should contain (8)  // +
			values should contain (4)  // -
			values should contain (12) // *
			values should contain (3)  // /
			values should contain (36) // ^
			//--Medium Case
			//(construct parses)
			parser = CKYParser.apply(math2str.length, MATH.map{ (_,0.0) }, paranoid=true);
			parse = parser.parse(MSent("( 6 + 2 )"), 100);
			parse.length should be (5)
			values = parse.map{ _.evaluate.asInstanceOf[Double].toInt }
			//(evaluate ambiguity)
			values should contain (8)  // +
			values should contain (4)  // -
			values should contain (12) // *
			values should contain (3)  // /
			values should contain (36) // ^
			//--Hard Case
			//(construct parses)
			parser = CKYParser.apply(math2str.length, MATH.map{ (_,0.0) }, paranoid=true);
			parse = parser.parse(MSent("( 34532 + 2656456 ) / 4"), 1000);
			parse.length should be > 0
			parse.length should be <= 1000
			values = parse.map{ _.evaluate.asInstanceOf[Double].toInt }
			parse.forall{ _.logProb > Double.NegativeInfinity } should be (true)
			//(evaluate ambiguity)
			values should contain (672747)     // + /
			values should contain (-655481)    // - /
			values should contain (10763952)   // + *
			values should contain (-10487696)  // - *
			values should contain (2690992)    // + +
		}
		it("should be learnable"){
			Random.setSeed(42)
			val dataSize = 100
			val expressionLength = 5
			//--Helper Functions
			val functions = Array[((Int,Int)=>Int,String)](
				(_ + _, "+"),
				(_ - _, "-"),
				(_ * _, "*"),
				((i:Int,j:Int) => if(j == 0){ 0 } else {i / j}, "/"),
			  (math.pow(_,_).toInt, "^")
			)
			def randomExpression(numOps:Int):(Int,String) = {
				if(numOps == 0){
					val i = Random.nextInt(20) - 10
					(i.toInt, i.toString)
				} else {
					val (left, leftStr) = randomExpression((numOps-1) / 2)
					val (right, rightStr) = randomExpression((numOps-1) / 2 + (numOps-1) % 2)
					val (op,name) = functions(Random.nextInt(functions.length))
					(op(left,right), leftStr + " " + name + " " + rightStr)
				}
			}
			//--Basic
			randomExpression(0)._2.length should be < 3
			val (ans,str) = randomExpression(0)
			ans should be (str.toInt)
			//--Run Experiment
			//(make parser)
			val parser = CKYParser.apply(math2str.length, MATH.map{ (_,0.0) }, paranoid=true);
			//(make dataset)
			val dataset = (0 until dataSize).map{ (i:Int) =>
				randomExpression(Random.nextInt(expressionLength))
			}.toArray
			//(parse)
			val goodTrees:Array[ParseTree] = dataset.flatMap{ case (answer:Int,input:String) =>
				parser.parse(MSent(input), 1000).filter{ _.evaluate.asInstanceOf[Double].toInt == answer }
			}
			//(update)
			val learned = parser.update(goodTrees, 0.0, 0.0 )
			//(check)
			learned.parse(MSent("6 + 2")).evaluate.asInstanceOf[Double].toInt should be (8)
			learned.parse(MSent("6 - 2")).evaluate.asInstanceOf[Double].toInt should be (4)
			learned.parse(MSent("6 * 2")).evaluate.asInstanceOf[Double].toInt should be (12)
			learned.parse(MSent("6 / 2")).evaluate.asInstanceOf[Double].toInt should be (3)
			learned.parse(MSent("6 ^ 2")).evaluate.asInstanceOf[Double].toInt should be (36)
		}
	}

	describe("A CKY parser"){
		it("should serialize"){
			val file = File.createTempFile("scalatest", ".ser");
			//--Write
			//(pre-parse)
			val toSave = CKYParser(w2str.length, TOY);
			var out = new TrackedObjectOutputStream(new FileOutputStream(file));
			try {
				out.writeObject(toSave);
				out.close();
			} catch {
				case (e:Exception) => assert(false, "Could not serialize: " + out.getStack.toArray.mkString(" --> "))
			}
			//(post-parse)
			val sent:Sentence = Sentence(w2str, "I like sugar")
			val parseToMatch = toSave.parse(sent)
			out = new TrackedObjectOutputStream(new FileOutputStream(file));
			try {
				out.writeObject(toSave);
				out.close();
			} catch {
				case (e:Exception) => assert(false, "Could not serialize: " + out.getStack.toArray.mkString(" --> "))
			}
			//--Read
			//(read parser)
			val in = new ObjectInputStream(new FileInputStream(file));
			val parser:CKYParser = in.readObject().asInstanceOf[CKYParser];
			//(check parse)
			val reReadParse = parser.parse(sent)
			reReadParse should be (parseToMatch)
			
		}
	}
}
