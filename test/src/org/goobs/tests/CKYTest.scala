package org.goobs.tests

import org.scalatest.Spec
import org.scalatest.matchers.ShouldMatchers
import org.goobs.nlp._

object Grammars {
	lazy val TOY:Array[GrammarRule] = {
		Array[GrammarRule](
			BinaryGrammarRule(NodeType.ROOT, NodeType.make("S")),
			BinaryGrammarRule(NodeType.make("S"), NodeType.make("NP"), NodeType.make("VP")),
			BinaryGrammarRule(NodeType.make("NP"), NodeType.makePreterminal("NN")),
			BinaryGrammarRule(NodeType.make("VP"), NodeType.makePreterminal("VB"), NodeType.make("NP")),
		  LexGrammarRule(NodeType.makePreterminal("NN")),
			LexGrammarRule(NodeType.makePreterminal("VB"))
		)
	}
	lazy val w2str:Array[String] = Array[String](
	  "I",
		"like",
		"sugar",
		"NLP"
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
		it("should learn a distribution")(pending)
	}
	
	describe("a grammar with closures"){
		//TODO make me
	}
	
	describe("a grammar with ambiguous parses"){
		//TODO make me
	}
	
	describe("A parser with learned parameters"){
		//TODO write me
	}
}
