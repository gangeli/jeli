package org.goobs.search;

import org.goobs.foreign.Counter;

public interface SearchState {
	public Counter <SearchState> children(double needToBeat);
	public boolean isStopState();
}
