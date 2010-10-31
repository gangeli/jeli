package org.goobs.utils;

public class ProgressBar {

	private int count;
	private int totalCount;
	private int mod;
	private int printedCount = 0;
	private int printLength;
	
	public ProgressBar(int totalCount){
		this(totalCount, 20);
	}
	
	public ProgressBar(int totalCount, int length){
		this.count = 0;
		this.totalCount = totalCount;
		this.mod = totalCount / length;
		this.printLength = length;
		System.out.print("          |");
		for(int i=0; i<length; i++){
			System.out.print(" ");
		}
		System.out.println("|");
		System.out.print("Progress: |");
		if(totalCount == 0){
			finish();
		}
	}
	
	public boolean tick(){
		count++;
		boolean rtn = false;
		if(count % mod == 0){
			if(printedCount < printLength){
				System.out.print("-");
				printedCount++;
				rtn = true;
			}
		}
		if(count == totalCount){
			finish();
		}
		return rtn;
	}
	
	private void finish(){
		while(printedCount < printLength){
			System.out.print("-");
			printedCount++;
		}
		System.out.println("|");
	}
}
