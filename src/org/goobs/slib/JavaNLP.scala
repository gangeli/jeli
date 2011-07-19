package org.goobs.slib

import edu.stanford.nlp.util.CoreMap
import edu.stanford.nlp.util.ArrayCoreMap
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation
import edu.stanford.nlp.ling.CoreAnnotations.LemmaAnnotation

import scala.collection.JavaConversions._

object JavaNLP {
	val WORD  = classOf[TextAnnotation]
	val POS   = classOf[PartOfSpeechAnnotation]
	val LEMMA = classOf[LemmaAnnotation]


	def word2coremap(word:String):CoreMap = {
		val token = new ArrayCoreMap(1)
		token.set(WORD,word)
		token
	}
	def word2coremap(word:String,pos:String):CoreMap = {
		val token = new ArrayCoreMap(2)
		token.set(WORD,word)
		token.set(POS,pos)
		token
	}
	def word2coremap(word:String,pos:String,lemma:String):CoreMap = {
		val token = new ArrayCoreMap(2)
		token.set(WORD,word)
		token.set(POS,pos)
		token.set(LEMMA,lemma)
		token
	}
	def sent2coremaps(sent:Array[String]):java.util.List[CoreMap] = {
		sent.map{ (word:String) => word2coremap(word) }.toList
	}
	def sent2coremaps(sent:Array[String],pos:Array[String]
			):java.util.List[CoreMap] = {
		if(sent.length != pos.length){ 
			throw new IllegalArgumentException("Length mismatch")
		}
		sent.zip(pos).map{case (w:String,pos:String) => word2coremap(w,pos) }.toList
	}
	def sent2coremaps(sent:Array[String],pos:Array[String],lemma:Array[String]
			):java.util.List[CoreMap] = {
		if(sent.length != pos.length || sent.length != lemma.length){ 
			throw new IllegalArgumentException("Length mismatch")
		}
		sent.zip(pos).zip(lemma).map{ case ((w:String,pos:String),lemma:String) => 
			word2coremap(w,pos,lemma) }.toList
	}
}
