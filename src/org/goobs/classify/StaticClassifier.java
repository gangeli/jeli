package org.goobs.classify;


public interface StaticClassifier<I,L> {
  L getLabel(I instance);
}
