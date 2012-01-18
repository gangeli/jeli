package org.goobs.util;


public interface MemoryAccountable {
	public int OBJ_OVERHEAD = 4;
	public int REF_SIZE = 4;
	public int INT_SIZE = 4;
	public int DBL_SIZE = 8;
	public int CHR_SIZE = 1;
	
	public static class MemoryReport{
		private int size;
		private String name;
		public MemoryReport(int size, String name){
			this.size = size;
			this.name = name;
		}
		@Override
		public String toString(){
			return "" + size + "\t" + name;
		}
	}
	
	public int estimateMemoryUsage();
	public Tree <MemoryReport> dumpMemoryUsage(int minUse);
}
