package org.goobs.stanford;


import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.ArrayCoreMap;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.TypesafeMap;

import java.util.Properties;
import java.util.Set;

public class JavaNLPTasks {

	private static class MyAnnotation extends Annotation {
		private CoreMap impl;

		@SuppressWarnings({"deprecation"})
    public MyAnnotation(CoreMap impl){ super(); this.impl = impl; }

		@Override public <VALUE, KEY extends TypesafeMap.Key<CoreMap, VALUE>> boolean has(Class<KEY> keyClass) { return impl.has(keyClass); }
		@Override public <VALUE, KEY extends TypesafeMap.Key<CoreMap, VALUE>> VALUE get(Class<KEY> keyClass) { return impl.get(keyClass); }
		@Override public <VALUEBASE, VALUE extends VALUEBASE, KEY extends TypesafeMap.Key<CoreMap, VALUEBASE>> VALUE set(Class<KEY> keyClass, VALUE value) { return impl.set(keyClass, value); }
		@Override public <VALUE, KEY extends TypesafeMap.Key<CoreMap, VALUE>> VALUE remove(Class<KEY> keyClass) { return impl.remove(keyClass); }
		@Override public Set<Class<?>> keySet() { return impl.keySet(); }
		@Override public <VALUE, KEY extends TypesafeMap.Key<CoreMap, VALUE>> boolean containsKey(Class<KEY> keyClass) { return impl.containsKey(keyClass); }
		@Override public int size() { return impl.size(); }
		@Override public String toString() { return impl.toString(); }
	}

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
					pipeline.annotate(new MyAnnotation(example));
				}
		}
		@Override public String name() { return "JavaNLP-"+this.getClass().getSimpleName(); }
		@SuppressWarnings({"unchecked"})
    @Override public Class<? extends Task>[] dependencies() { return new Class[0]; }

		protected abstract String getAnnotators();
	}

	public static class Core extends JavaNLPTask {
		@Override protected String getAnnotators() { return "tokenize, ssplit, pos, lemma"; }
	}

	public static class NER extends JavaNLPTask {
		@Override protected String getAnnotators() { return "tokenize, ssplit, pos, lemma, ner"; }
		@SuppressWarnings({"unchecked"})
    @Override public Class<? extends Task>[] dependencies() { return new Class[]{Core.class}; }
	}
}
