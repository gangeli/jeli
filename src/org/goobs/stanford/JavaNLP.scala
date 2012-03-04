package org.goobs.stanford

import java.util.AbstractList

import scala.collection.JavaConversions._

import edu.stanford.nlp.util.CoreMap
import edu.stanford.nlp.util.ArrayCoreMap
import edu.stanford.nlp.ling.Word
import edu.stanford.nlp.ling.HasWord
import edu.stanford.nlp.ling.CoreLabel
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation
import edu.stanford.nlp.ling.CoreAnnotations.LemmaAnnotation
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation
import edu.stanford.nlp.util.logging.Redwood
import collection.generic.CanBuildFrom


object JavaNLP {
	val WORD      = classOf[TextAnnotation]
	val TEXT      = classOf[TextAnnotation]
	val POS       = classOf[PartOfSpeechAnnotation]
	val LEMMA     = classOf[LemmaAnnotation]
	val ANSWER    = classOf[AnswerAnnotation]
	val TOKENS    = classOf[TokensAnnotation]
	val SENTENCES = classOf[SentencesAnnotation]

	def word2corelabel(word:String):CoreLabel = {
		val token = new CoreLabel(2)
		token.set(WORD,word)
		token
	}
	def word2corelabel(word:String,pos:String):CoreLabel = {
		val token = new CoreLabel(3)
		token.set(WORD,word)
		token.set(POS,pos)
		token
	}
	def word2corelabel(word:String,pos:String,lemma:String):CoreLabel = {
		val token = new CoreLabel(4)
		token.set(WORD,word)
		token.set(POS,pos)
		token.set(LEMMA,lemma)
		token
	}
	def sent2corelabels(sent:Array[String]):java.util.List[CoreLabel] = {
		sent.map{ (word:String) => word2corelabel(word) }.toList
	}
	def sent2corelabels(sent:Array[String],pos:Array[String]
			):java.util.List[CoreLabel] = {
		if(sent.length != pos.length){ 
			throw new IllegalArgumentException("Length mismatch")
		}
		val retArray:Array[CoreLabel] 
			= sent.zip(pos).map{ 
				case (word:String,pos:String) => word2corelabel(word,pos) }
		retArray.toList
	}
	def sent2corelabels(sent:Array[String],pos:Array[String],lemma:Array[String]
			):java.util.List[CoreLabel] = {
		if(sent.length != pos.length || sent.length != lemma.length){ 
			throw new IllegalArgumentException("Length mismatch")
		}
		sent.zip(pos).zip(lemma).map{ case ((w:String,pos:String),lemma:String) => 
			word2corelabel(w,pos,lemma) }.toList
	}
	def sent2coremaps(sent:Array[String]):java.util.List[CoreMap] = {
		sent2corelabels(sent).map{ x => x }
	}
	def sent2coremaps(sent:Array[String],pos:Array[String]
			):java.util.List[CoreMap] = {
		sent2corelabels(sent,pos).map{ x => x }
	}
	def sent2coremaps(sent:Array[String],pos:Array[String],lemma:Array[String]
			):java.util.List[CoreMap] = {
		sent2corelabels(sent,pos,lemma).map{ x => x }
	}

	def sent2coremap(sent:Array[CoreLabel]):CoreMap = {
		val tokens:java.util.List[CoreLabel] = sent.toList
		sent2coremap(tokens)
	}
	def sent2coremap(tokens:java.util.List[CoreLabel]):CoreMap = {
		val rtn = new ArrayCoreMap(1)
		rtn.set(TOKENS,tokens)
		rtn
	}

	def sentences(sents:Array[Array[CoreLabel]]):CoreMap = {
		val lst:Array[CoreMap] = sents.map{ sent2coremap(_) }
		sentences(lst)
	}
	def sentences(sents:Array[java.util.List[CoreLabel]]):CoreMap = {
		val lst:java.util.List[CoreMap] = new java.util.ArrayList[CoreMap]()
		sents.foreach{ (term:java.util.List[CoreLabel]) =>
			lst.add(sent2coremap(term)) }
		sentences(lst)
	}
	def sentences(sents:Array[CoreMap]):CoreMap = {
		val lst:java.util.List[CoreMap] = new java.util.ArrayList[CoreMap]()
		sents.foreach{ lst.add(_) }
		sentences(lst)
		
	}
	def sentences(sents:java.util.List[CoreMap]):CoreMap = {
		val rtn = new ArrayCoreMap(1)
		rtn.set(SENTENCES,sents)
		rtn
	}
	def sentences(sents:Array[Array[String]]):CoreMap = {
		sentences( sents.map{ case (sent:Array[String]) =>
				sent2corelabels(sent)
			} )
	}

	def setAnswers[A <: CoreMap](sent:java.util.List[A],answers:Array[String]
			):java.util.List[A] = {
		//(check)
		if(sent.size != answers.length){ 
			throw new IllegalArgumentException("Length mismatch")
		}
		//(tag)
		var i:Int = 0
		val iter:java.util.Iterator[A] = sent.iterator
		while(iter.hasNext){
			iter.next.set(ANSWER,answers(i))
			i += 1
		}
		sent
	}

	def setAnswers(sent:CoreMap,answers:Array[String]):CoreMap = {
		val labels:java.util.List[CoreMap]
			= sent.get[java.util.List[CoreMap],SentencesAnnotation](SENTENCES)
		setAnswers(labels, answers)
		sent
	}

	//--Implicits Magic--
	implicit def coremapList2words(lst:AbstractList[_<:CoreMap]):Array[String] = {
		lst.map{ (term:CoreMap) => 
			term.get[String,TextAnnotation](WORD)
		}.toArray
	}

	implicit def string2HasWord(str:String):HasWord = new Word(str)
	implicit def list2HasWord(lst:List[String]):java.util.List[_ <: HasWord] = {
		val labels:java.util.List[Word] = new java.util.ArrayList[Word]
		lst.foreach{ case (w:String) => labels.add(new Word(w)) }
		labels
	}
	implicit def array2HasWord(lst:Array[String]):java.util.List[_ <: HasWord] = {
		val labels:java.util.List[Word] = new java.util.ArrayList[Word]
		lst.foreach{ case (w:String) => labels.add(new Word(w)) }
		labels
	}





	def threadAndMap[A,B,That](in:Iterable[A], fn:A=>B, numThreads:Int=Runtime.getRuntime.availableProcessors(), name:String="Threaded Map")(implicit bf:CanBuildFrom[Seq[A],B,That]):That = {
		//--Variables
		val writeLock = new scala.concurrent.Lock
		val builder = bf()
		//--Thread
		Redwood.Util.threadAndRun(name, asJavaCollection( in.map{ (term:A) =>
			new Runnable{
				override def run{
					val b:B = fn(term)
					writeLock.acquire()
					builder += b
					writeLock.release()
				}
			}
		}), numThreads)
		//--Return
		builder.result()
	}
}

