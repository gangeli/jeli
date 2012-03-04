package org.goobs.stanford;

import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.TypesafeMap;
import edu.stanford.nlp.util.logging.Redwood;
import org.goobs.testing.Datum;

import java.io.Serializable;
import java.util.Set;

public class CoreMapDatum implements Datum, CoreMap, Serializable {
	private static final long serialVersionUID = 1L;

	public final CoreMap impl;
	public final int id;

	public CoreMapDatum(CoreMap impl, int id) {
		this.impl = impl;
		this.id = id;
	}

	public CoreMap implementation(){ return impl; }

		@Override public <VALUE, KEY extends TypesafeMap.Key<CoreMap, VALUE>> boolean has(Class<KEY> keyClass) { return impl.has(keyClass); }
		@Override public <VALUE, KEY extends TypesafeMap.Key<CoreMap, VALUE>> VALUE get(Class<KEY> keyClass) { return impl.get(keyClass); }
		@Override public <VALUEBASE, VALUE extends VALUEBASE, KEY extends TypesafeMap.Key<CoreMap, VALUEBASE>> VALUE set(Class<KEY> keyClass, VALUE value) { return impl.set(keyClass, value); }
		@Override public <VALUE, KEY extends TypesafeMap.Key<CoreMap, VALUE>> VALUE remove(Class<KEY> keyClass) { return impl.remove(keyClass); }
		@Override public Set<Class<?>> keySet() { return impl.keySet(); }
		@Override public <VALUE, KEY extends TypesafeMap.Key<CoreMap, VALUE>> boolean containsKey(Class<KEY> keyClass) { return impl.containsKey(keyClass); }
		@Override public int size() { return impl.size(); }
		@Override public String toString() { return impl.toString();}
  	@Override public boolean equals(Object obj) { return impl.equals(obj); }
		@Override public int hashCode(){ return impl.hashCode(); }
		@Override public int getID() { return this.id; }

	@Override
	public void prettyLog(Redwood.RedwoodChannels redwoodChannels, String s) {
		redwoodChannels.prettyLog(s, impl);
	}
}
