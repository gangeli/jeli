package org.goobs.tests;

import static org.junit.Assert.*;
import org.junit.*;
import org.goobs.testing.MTScorer;

public class MTScorerTest{

	
	@Test
	public void testBasic(){
		//--Sanity Checks
		//(trivial case)
		MTScorer.Factory fact = new MTScorer.Factory();
		MTScorer score = fact.addSentencePair("x", "x")
			.score();
		assertEquals(1.0, score.getBleu(), 0.0001);
		//(multi-word case)
		fact = new MTScorer.Factory();
		score = fact.addSentencePair("a sentence that is somewhat long", "a sentence that is somewhat long")
			.score();
		assertEquals(1.0, score.getBleu(), 0.0001);
		//(middle case)
		fact = new MTScorer.Factory();
		score = fact.addSentencePair("a sentence that is somewhat long", "a sentence that is somewhat the same")
			.score();
		assertTrue(score.getBleu() < 1.0);
		assertTrue(score.getBleu() > 0.0);
		
		//--Example from cortex.BleuScorer
		fact = new MTScorer.Factory();
		score = fact.addSentencePair("a0 a1 a2 a3 a1 a4 a5 a6 a7 a8 a9 a10", "a0 a2391 a14 a1384 a14 a45 a7 a8 a9 a10")
			.score();
		assertEquals(0.2274, score.getBleu(), 0.0001);
		assertEquals(1.5581, score.getNist(), 0.0001);		
	}
	
	//TODO more thorough test cases
}
