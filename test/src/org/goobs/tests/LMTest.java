package org.goobs.tests;
import static org.junit.Assert.*;
import org.junit.*;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Random;
import java.util.UUID;

import org.goobs.lm.*;

public class LMTest{

	private String[] createWords(int num){
		String[] rtn = new String[num];
		for(int i=0; i<rtn.length; i++){
			rtn[i] = UUID.randomUUID().toString();
		}
		return rtn;
	}
	
	private String[] randomSentence(String[] words, int len){
		String[] rtn = new String[len];
		Random rand = new Random();
		for(int i=0; i<rtn.length; i++){
			rtn[i] = words[rand.nextInt(rtn.length)];
		}
		return rtn;
	}
	
	@Test
	public void testKnLM(){
		LanguageModel lm = new KnesserNeyLM(3);
		lm.addSentence("the cat in the hat".split("\\s+"));
		lm.addSentence("the cat wears a hat".split("\\s+"));
		lm.addSentence("the cat in something".split("\\s+"));
		lm.addSentence("the dog is in the hat".split("\\s+"));
		lm.addSentence("the dog is in the hat".split("\\s+"));
		lm.train();
		
		lm.generate();
		lm.infer();
		assertTrue( Arrays.deepEquals(lm.infer(), "the cat in the hat".split("\\s+")));
		
	}
	
	@Test
	public void testKNSummation(){
		//--Create Language Model
		LanguageModel lm = new KnesserNeyLM(3);
		String[] words = createWords(100);
		Random rand = new Random();
		lm.addSentence(words);
		for(int i=0; i<10000; i++){
			lm.addSentence(randomSentence(words, 5+rand.nextInt(15)));
		}
		lm.train();
		
		//--Test Summation
		//(unigram)
		double sum = 0.0;
		for(int word=0; word<words.length; word++){
			sum += lm.nextWordProb(new String[0], words[word]);
		}
		sum += lm.stopProb(new String[0]);
		assertTrue("Unigram probabilities should sum to 1.0: " + sum , Math.abs(sum-1.0) < 0.01 );
		//(bigram)
		String[] tail = new String[1];
		for(int first=0; first<words.length; first++){
			tail[0] = words[first];
			sum = 0.0;
			for(int word=0; word<words.length; word++){
				sum += lm.nextWordProb(tail, words[word]);
			}
			sum += lm.stopProb(tail);
			assertTrue("Bigram probabilities should sum to 1.0: " + sum , Math.abs(sum-1.0) < 0.01 );
		}
		//(trigram)
		tail = new String[2];
		for(int first=0; first<words.length; first++){
			tail[0] = words[first];
			for(int second=0; second<words.length; second++){
				tail[1] = words[second];
				sum = 0.0;
				for(int word=0; word<words.length; word++){
					sum += lm.nextWordProb(tail, words[word]);
				}
				sum += lm.stopProb(tail);
				assertTrue("Trigram probabilities should sum to 1.0: " + sum , Math.abs(sum-1.0) < 0.01 );
			}
		}
	}
	
	@Test
	public void testKNConsistency(){
		//--Create Language Model
		LanguageModel lm = new KnesserNeyLM(3);
		String[] words = createWords(100);
		Random rand = new Random();
		lm.addSentence(words);
		for(int i=0; i<10000; i++){
			lm.addSentence(randomSentence(words, 5+rand.nextInt(15)));
		}
		lm.train();

		for(int i=0; i<1000; i++){
			String[] sent = randomSentence(words, 10);
			LinkedList<String> tail = new LinkedList<String>();
			double fullProb = lm.getSentenceLogProb(sent);
			double sumProb = 0.0;
			for(String str : sent){
				sumProb += Math.log( lm.nextWordProb(tail.toArray(new String[tail.size()]), str) );
				tail.addLast(str);
			}
			sumProb += Math.log( lm.stopProb(tail.toArray(new String[tail.size()])));
			assertEquals(fullProb, sumProb, 0.0001);
		}
	}
	
	@Test
	public void testKNUnk(){
		assertTrue("I know for a fact UNKs are handled poorly", false);
	}
}
