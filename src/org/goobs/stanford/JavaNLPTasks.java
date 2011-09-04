package org.goobs.stanford;


import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.TypesafeMap;

import java.util.Properties;
import java.util.Set;

public class JavaNLPTasks {

	public static abstract class JavaNLPTask implements Task<DBCoreMap> {
		private final StanfordCoreNLP pipeline;
		private final Properties props = new Properties();
		public JavaNLPTask(){
			//(make pipeline)
			props.setProperty("annotators", getAnnotators());
			this.pipeline = new StanfordCoreNLP(props);
		}
		@Override
		public void perform(org.goobs.testing.Dataset<DBCoreMap> data) {
			for(int i=0; i<data.numExamples(); i++){
				CoreMap example = data.get(i);
					pipeline.annotate(new NestedElement.MyAnnotation(example));
				}
		}
		@Override public String name() { return "JavaNLP-"+this.getClass().getSimpleName(); }
		@SuppressWarnings({"unchecked"})
    @Override public Class<? extends Task<?>>[] dependencies() { return new Class[0]; }

		protected abstract String getAnnotators();
	}

	public static class Core extends JavaNLPTask {
		@Override protected String getAnnotators() { return "tokenize, ssplit"; }
	}

	public static class NER extends JavaNLPTask {
		@Override protected String getAnnotators() { return "tokenize, ssplit, pos, lemma"; }
		@SuppressWarnings({"unchecked"})
    @Override public Class<? extends Task<?>>[] dependencies() { return new Class[]{Core.class}; }
	}
}
