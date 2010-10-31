package org.goobs.utils;

import java.util.List;

public class StringUtils {
	           
	public static int editDistance(String a, String b){
		//--Variables
		char[] s = a.toCharArray();
		char[] t = b.toCharArray();
		int m = s.length;
		int n = t.length;
		int[][] d = new int[m][n];
		
		//--Base Case
		for(int i=0; i<m; i++){
			d[i][0] = i;
		}
		for(int j=0; j<n; j++){
			d[0][j] = j;
		}
		
		//--Recursive Case
		for(int j=1; j<n; j++){
			for(int i=1; i<m; i++){
				//(substitution cost)
				int cost = 0;
				if(s[i] != t[j]){
					cost = 1;
				}
				//(recursive update)
				//   insertion
				//   deletion
				//   substitution
				d[i][j] = Math.min( d[i-1][j] + 1,
						Math.min( d[i][j-1] + 1,
								d[i-1][j-1] + cost ) );
				
			}
		}
		
		return d[m-1][n-1];
	}
	
	public static String listString(List <? extends Object> lst){
		StringBuilder b = new StringBuilder();
		b.append("( ");
		for(Object o : lst){
			b.append(o.toString()).append(" ");
		}
		b.append(")");
		return b.toString();
	}

	
	public static String stringBetween(String raw, String start, String stop){
		int front = 0;
		if(start != null){
			front = raw.indexOf(start) + start.length();
		}
		int back = raw.length();
		if(stop != null){
			back = raw.indexOf(stop, front);
		}
		if(front < 0 || back < 0){
			return null;
		}
		return raw.substring(front,back);
	}
}
