package org.goobs.classify;

import java.io.Serializable;
import java.util.*;

/**
 * Stores the parameters. Usually we have a set of parameters per feature
 * template. This data structure is tailored towards the Perceptron algorithm,
 * allowing efficient maintenance of averaged weights. Usage: modify the
 * parameters only between beginUpdate() and endUpdate()
 */
public class FlatParameterVector<FeatureType> implements Serializable {
	private static final long serialVersionUID = 42;

	private HashMap<FeatureType, WeightInfo> weightInfoMap; // feature -> weight
	private int currTime;

	public FlatParameterVector() {
		this.weightInfoMap = new HashMap<FeatureType, WeightInfo>();
		this.currTime = 0;
	}

	public void clear() {
		weightInfoMap.clear();
		currTime = 0;
	}

	public void beginUpdate() {
		currTime++;
	}

	public void endUpdate() {
	}

	public boolean containsFeature(FeatureType feature) {
		return weightInfoMap.containsKey(feature);
	}

	public double getWeight(FeatureType feature, boolean useAveraged) {
		WeightInfo wi = weightInfoMap.get(feature);
		if (wi == null)
			return 0;
		if (useAveraged) {
			wi.refresh(currTime);
			// fig.basic.LogInfo.logs("getWeight " + feature + " = " +
			// (wi.averagedWeight/(currTime+1)));
			// Note: we average in the initial parameters
			return wi.averagedWeight / (currTime + 1);
		} else
			return wi.weight;
	}

	// Create a new weight info if it doesn't exist
	private WeightInfo getWeightInfo(FeatureType feature) {
		WeightInfo wi = weightInfoMap.get(feature);
		if (wi == null)
			weightInfoMap.put(feature, wi = new WeightInfo());
		return wi;
	}

	public void incrWeight(FeatureType feature, double incr) {
		// fig.basic.LogInfo.logs("incrWeight " + feature + " " + incr);
		WeightInfo wi = getWeightInfo(feature);
		wi.refresh(currTime);
		wi.weight += incr;
		wi.averagedWeight += incr;
	}

	public void setWeight(FeatureType feature, double weight) {
		WeightInfo wi = getWeightInfo(feature);
		wi.weight = weight;
		wi.averagedWeight = weight;
		wi.lastUpdatedTime = currTime;
	}

	public Iterable<FeatureType> getFeatures() {
		return weightInfoMap.keySet();
	}

	public static class WeightInfo implements Serializable {
		private static final long serialVersionUID = 42;

		public void refresh(int currTime) {
			averagedWeight += (currTime - lastUpdatedTime) * weight;
			lastUpdatedTime = currTime;
		}

		public double get(boolean useAveraged) {
			return useAveraged ? averagedWeight : weight;
		}

		private WeightInfo() {
		}

		public WeightInfo clone() {
			WeightInfo info = new WeightInfo();
			info.weight = weight;
			info.averagedWeight = averagedWeight;
			info.lastUpdatedTime = lastUpdatedTime;
			return info;
		}

		public double weight = 0;
		public double averagedWeight = 0;
		public int lastUpdatedTime = 0;
	}

	public FlatParameterVector<FeatureType> clone() {
		FlatParameterVector<FeatureType> pv = new FlatParameterVector<FeatureType>();
		pv.weightInfoMap = new HashMap<FeatureType, WeightInfo>();
		for (Map.Entry<FeatureType, WeightInfo> e : weightInfoMap.entrySet())
			pv.weightInfoMap.put(e.getKey(), e.getValue().clone());
		pv.currTime = currTime;
		return pv;
	}

}
