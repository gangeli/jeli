package org.goobs.util;

public class PrettyPrinter {
	private boolean newLine = true;
	private int depth = 0;
	private int printCellWidth;
	
	public PrettyPrinter(int printCellWidth){
		this.printCellWidth = printCellWidth;
	}

	public void logRight(String str, double prob) {
		//(handle newline)
		if(newLine){
			for(int d=0; d<depth; d++){
				System.out.print("       "); // adjust for prob terms
				for(int i=0; i<printCellWidth+3; i++){
					System.out.print(" ");
				}
			}
		}
		newLine = false;
		//(handle prob)
		java.text.DecimalFormat df = new java.text.DecimalFormat("0.000");
		System.out.print("(");	System.out.print(df.format(prob)); System.out.print(")");
		//(handle str)
		String toPrint = null;
		if(str.length() > printCellWidth-1) 
			toPrint = str.substring(0, printCellWidth-1);
		else 
			toPrint = str;
		System.out.print(toPrint);
		//(handle width normalization)
		for(int i=toPrint.length(); i<printCellWidth+3; i++){
			System.out.print(" ");
		}
		depth += 1;
	}

	public void logRight(String str) { logRight(str, 1.0); }
	
	public void backup() {
		if(depth == 0) throw new java.lang.IllegalStateException("Backing up from depth 0");
		depth -= 1;
	}
	
	public void newline() {
		System.out.print("\n");
		newLine = true;
	}
}
