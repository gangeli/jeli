package org.goobs.tests

import org.scalatest.Spec
import org.scalatest.matchers.ShouldMatchers
import org.goobs.nlp._

object Grammars {
	def TOY:Array[GrammarRule] = {
		Array[GrammarRule](
			BinaryGrammarRule(NodeType.ROOT, NodeType.make("S")),
			BinaryGrammarRule(NodeType.make("S"), NodeType.make("NP"), NodeType.make("VP")),
			BinaryGrammarRule(NodeType.make("NP"), NodeType.makePreterminal("NN")),
			BinaryGrammarRule(NodeType.make("VP"), NodeType.makePreterminal("VB"), NodeType.make("NP")),
		  LexGrammarRule(NodeType.makePreterminal("NN")),
			LexGrammarRule(NodeType.makePreterminal("VB"))
		)
	}
	def w2str:Array[String] = Array[String](
	  "I",
		"like",
		"sugar",
		"NLP"
		)
}

object Trees {
	/**
	 * (S (NP (NN I)) (VP (VB like) (NP (NN sugar))))
	 * (S (NP (NN I)) (VP (VB like) (NP (NN nlp))))
	 */
	def TOY:Array[ParseTree] = Array[ParseTree](
		ParseTree(NodeType.make("S"), Array[ParseTree](
			ParseTree(NodeType.make("NP"),Array[ParseTree](
				ParseTree(NodeType.makePreterminal("NN"), Grammars.w2str.indexOf("I"))), 1.0, true ),
			ParseTree(NodeType.make("VP"),Array[ParseTree](
				ParseTree(NodeType.makePreterminal("VB"), Grammars.w2str.indexOf("like")),
				ParseTree(NodeType.make("NP"),Array[ParseTree](
					ParseTree(NodeType.makePreterminal("NN"), Grammars.w2str.indexOf("sugar") ) ), 1.0, true)), 1.0, true )) ),
		ParseTree(NodeType.make("S"), Array[ParseTree](
			ParseTree(NodeType.make("NP"),Array[ParseTree](
				ParseTree(NodeType.makePreterminal("NN"), Grammars.w2str.indexOf("I"))), 1.0, true ),
			ParseTree(NodeType.make("VP"),Array[ParseTree](
				ParseTree(NodeType.makePreterminal("VB"), Grammars.w2str.indexOf("like")),
				ParseTree(NodeType.make("NP"),Array[ParseTree](
					ParseTree(NodeType.makePreterminal("NN"), Grammars.w2str.indexOf("NLP") ) ), 1.0, true)), 1.0, true )) )
	)
}

class CKYParserSpec extends Spec with ShouldMatchers {
	import Grammars._
	
	describe("A Node Type"){
		it("should be createable"){
			//(simple equality)
			NodeType.make("S") should be (NodeType('S))
			NodeType.make("S") should be (NodeType.make('S))
			//(with flags)
			NodeType.make("NN",NodeType.SYM_PRETERMINAL) should be (NodeType('NN))
			//(constants)
			NodeType.ROOT
			NodeType.WORD
			NodeType.WORD should be (NodeType.makeWord())
			//(duplicate create)
			intercept[IllegalArgumentException]{
				NodeType.make("S", NodeType.SYM_PRETERMINAL)
			}
		}
		it("should recognize flags"){
			NodeType.WORD.isWord should be (true)
			NodeType.WORD.isPreterminal should be (false)
			NodeType.makeWord().isWord should be (true)
			NodeType.make("NN",NodeType.SYM_PRETERMINAL).isWord should be (false)
			NodeType.make("NN",NodeType.SYM_PRETERMINAL).isPreterminal should be (true)
		}
		it("should hash well"){
			def randomNode:NodeType = NodeType.make(scala.util.Random.nextString(10))
			scala.util.Random.setSeed(42);
			for(iter <- 0 until 10){
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
			val rootA = BinaryGrammarRule(NodeType.ROOT, NodeType.make("S"))
			val rootB = BinaryGrammarRule(NodeType.ROOT, NodeType.make("S"))
			val rootC = SimpleGrammarRule(NodeType.ROOT, NodeType.make("S"))
			rootA should be (rootB)
			rootA should be (rootC)
			rootB should be (rootC)
			rootC should be (rootA)
			//--All Types
			//(unaries)
			val simpleUnary = SimpleGrammarRule(NodeType.ROOT, NodeType.make("X"))
			val binaryUnary = BinaryGrammarRule(NodeType.ROOT, NodeType.make("X"))
			val ckyUnary = new CKYUnary(NodeType.ROOT, NodeType.make("X"));
			simpleUnary should be (binaryUnary)
			simpleUnary should be (ckyUnary)
			binaryUnary should be (simpleUnary)
			binaryUnary should be (ckyUnary)
			ckyUnary should be (simpleUnary)
			ckyUnary should be (binaryUnary)
			simpleUnary.hashCode should be (binaryUnary.hashCode)
			binaryUnary.hashCode should be (ckyUnary.hashCode)
			//(binaries)
			val simpleBinary = SimpleGrammarRule(NodeType.ROOT, NodeType.make("X"), NodeType.make("X"))
			val binaryBinary = BinaryGrammarRule(NodeType.ROOT, NodeType.make("X"), NodeType.make("X"))
			val ckyBinary = new CKYBinary(NodeType.ROOT, NodeType.make("X"), NodeType.make("X"));
			simpleBinary should be (binaryBinary)
			simpleBinary should be (ckyBinary)
			binaryBinary should be (simpleBinary)
			binaryBinary should be (ckyBinary)
			ckyBinary should be (simpleBinary)
			ckyBinary should be (binaryBinary)
			simpleBinary.hashCode should be (binaryBinary.hashCode)
			binaryBinary.hashCode should be (ckyBinary.hashCode)
			//--Not Equals
			val simpleTrinary2 = SimpleGrammarRule(NodeType.ROOT, NodeType.make("X"), NodeType.make("X"), NodeType.make("X"))
			val simpleBinary2 = SimpleGrammarRule(NodeType.ROOT, NodeType.make("X"), NodeType.make("X"))
			val binaryBinary2 = BinaryGrammarRule(NodeType.ROOT, NodeType.make("X"), NodeType.make("Y"))
			val ckyBinary2 = new CKYBinary(NodeType.ROOT, NodeType.make("X"), NodeType.make("Z"));
			val ckyUnary2 = new CKYUnary(NodeType.ROOT, NodeType.make("X"))
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
			def randomNode:NodeType = NodeType.make(scala.util.Random.nextString(1))
			scala.util.Random.setSeed(42);
			for(iter <- 0 until 10){
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
				println(bucket + " " + simple.max + " " + cky.max)
				simple.forall{ (count:Int) => count < bucket*5} should be (true)
				cky.forall{ (count:Int) => count < bucket*5} should be (true)
			}
		}
	}
	
	describe("A toy binarized grammar") {
		it("should be definable"){
			val toyGrammar = TOY;
		}
		it("should binarize"){
			val topRule = TOY(0);
			topRule.binarize.foreach{ (rule:CKYRule) =>
				//(parent)
				rule.parent should  be (NodeType.ROOT)
				//(unary check)
				assert(rule.isUnary)
				assert(rule.isInstanceOf[CKYUnary])
				rule.child should be (NodeType("S"))
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
			assert(!TOY(0).binarize.next().isLex)
			assert(!TOY(1).binarize.next().isLex)
			assert(!TOY(2).binarize.next().isLex)
			assert(!TOY(3).binarize.next().isLex)
			assert(TOY(4).binarize.next().isLex)
			assert(TOY(5).binarize.next().isLex)
		}
		it("should be passable to a parser"){
			CKYParser(w2str.length, TOY) should not be (null)
			CKYParser(w2str.length, TOY:_*) should not be (null)
		}
		it("should have a proper rule distribution"){
			val parser = CKYParser(w2str.length, TOY);
			//(should have probabilities)
			parser.ruleProb(new CKYBinary(NodeType('S), NodeType('NP), NodeType('VP))) should be (1.0)
			parser.ruleProb(new CKYBinary(NodeType('VP), NodeType('VB), NodeType('NP))) should be (1.0)
			parser.ruleProb(new CKYUnary(NodeType.ROOT, NodeType('S))) should be (1.0)
			parser.ruleProb(new CKYUnary(NodeType('NP), NodeType('NN))) should be (1.0)
			//(should not have probabilities)
			intercept[NoSuchElementException]{
				parser.ruleProb(new CKYUnary(NodeType('NN), NodeType.WORD))
			}
			intercept[NoSuchElementException]{
				parser.ruleProb(new CKYUnary(NodeType('VB), NodeType.WORD))
			}
		}
		it("should have a proper lex distribution"){
			val parser = CKYParser(w2str.length, TOY);
			(0 until w2str.length).foreach{ (w:Int) =>
				//(should have probabilities)
				parser.lexProb(new CKYUnary(NodeType('NN), NodeType.WORD), w) should be (1.0 / w2str.length.asInstanceOf[Double])
				parser.lexProb(new CKYUnary(NodeType('VB), NodeType.WORD), w) should be (1.0 / w2str.length.asInstanceOf[Double])
				//(should not have probabilities)
				intercept[NoSuchElementException]{
					parser.lexProb(new CKYUnary(NodeType.ROOT, NodeType('S)), w)
				}
				intercept[NoSuchElementException]{
					parser.lexProb(new CKYUnary(NodeType('NP), NodeType('NN)), w)
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
			parse1(0).parent should be (NodeType.ROOT);
			parse1(0).asParseString(sent) should not be (null)
			parser.parse(sent) should not be (null)
			//(parse another sentence)
			val sent2:Sentence = Sentence(w2str, "I like NLP")
			val parseOther = parser.parse(sent2,1)
			parseOther.length should be (1)
			parseOther.foreach{ (p:ParseTree) =>
				p.parent should be (NodeType.ROOT)
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
			learnedParser.ruleProb(TOY(0).binarize.next.asInstanceOf[CKYRule]) should be (1.0 plusOrMinus 0.00001)
			learnedParser.ruleProb(TOY(1).binarize.next.asInstanceOf[CKYRule]) should be (1.0 plusOrMinus 0.00001)
			learnedParser.ruleProb(TOY(2).binarize.next.asInstanceOf[CKYRule]) should be (1.0 plusOrMinus 0.00001)
			learnedParser.ruleProb(TOY(3).binarize.next.asInstanceOf[CKYRule]) should be (1.0 plusOrMinus 0.00001)
			learnedParser.lexProb( TOY(4).binarize.next.asInstanceOf[CKYUnary], w2str.indexOf("I")) should be (0.5 plusOrMinus 0.00001)
			learnedParser.lexProb( TOY(4).binarize.next.asInstanceOf[CKYUnary], w2str.indexOf("sugar")) should be (0.25 plusOrMinus 0.00001)
			learnedParser.lexProb( TOY(4).binarize.next.asInstanceOf[CKYUnary], w2str.indexOf("NLP")) should be (0.25 plusOrMinus 0.00001)
			learnedParser.lexProb( TOY(5).binarize.next.asInstanceOf[CKYUnary], w2str.indexOf("like")) should be (1.0 plusOrMinus 0.00001)
		}
		it("should be creatable from trees"){
			val parser = CKYParser(Trees.TOY);
			//--Simple Sentence
			val sentA:Sentence = Sentence(w2str, "I like sugar")
			//(parse beam 1)
			val parse1 = parser.parse(sentA,1);
			parse1 should not be (null)
			parse1.length should be (1)
			parse1(0).parent should be (NodeType.ROOT);
			parse1(0).asParseString(sentA) should not be (null)
			parser.parse(sentA) should not be (null)
			//(parse another sentence)
			val sentB:Sentence = Sentence(w2str, "I like NLP")
			val parseOther = parser.parse(sentB,1)
			parseOther.length should be (1)
			parseOther.foreach{ (p:ParseTree) =>
				p.parent should be (NodeType.ROOT)
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
			learnedParser.ruleProb(TOY(0).binarize.next.asInstanceOf[CKYRule]) should be (1.0 plusOrMinus 0.00001)
			learnedParser.ruleProb(TOY(1).binarize.next.asInstanceOf[CKYRule]) should be (1.0 plusOrMinus 0.00001)
			learnedParser.ruleProb(TOY(2).binarize.next.asInstanceOf[CKYRule]) should be (1.0 plusOrMinus 0.00001)
			learnedParser.ruleProb(TOY(3).binarize.next.asInstanceOf[CKYRule]) should be (1.0 plusOrMinus 0.00001)
			learnedParser.lexProb( TOY(4).binarize.next.asInstanceOf[CKYUnary], w2str.indexOf("I")) should be (0.5 plusOrMinus 0.00001)
			learnedParser.lexProb( TOY(4).binarize.next.asInstanceOf[CKYUnary], w2str.indexOf("sugar")) should be (0.25 plusOrMinus 0.00001)
			learnedParser.lexProb( TOY(4).binarize.next.asInstanceOf[CKYUnary], w2str.indexOf("NLP")) should be (0.25 plusOrMinus 0.00001)
			learnedParser.lexProb( TOY(5).binarize.next.asInstanceOf[CKYUnary], w2str.indexOf("like")) should be (1.0 plusOrMinus 0.00001)
		}
	}
	
	describe("a grammar with closures"){
		//TODO make me
	}
	
	describe("a grammar with ambiguous parses"){
		//TODO make me
	}
}
