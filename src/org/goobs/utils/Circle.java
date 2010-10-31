package org.goobs.utils;

public class Circle {
	
	private double x, y, radius;
	
	private Object[] points = null;
	private int numPoints = -1;
	
	public Circle(){}
	
	public Circle(double x, double y, double radius){
		this.x = x; this.y = y; this.radius = radius;
	}
	
	public Circle equidistantPoints(int points){
		if(this.points != null) throw new IllegalArgumentException("Circle already has points defined on it");
		this.numPoints = points;
		return this;
	}
	
	public <Type> Circle equidistantPoints(Type[] points){
		if(numPoints >= 0) throw new IllegalArgumentException("Circle already has points defined on it");
		this.points = (Object[]) points;
		return this;
	}
	
	private final int numPoints(){
		if(points == null) return numPoints;
		else return points.length;
	}
	
	public <Type> int pointIndex(Type point){
		if(points == null) return -1;
		for(int i=0; i<points.length; i++){
			if(points[i].equals(point)) return i;
		}
		return -1;
	}
	
	public double radiansFrom(int pointA, int pointB){
		//(get the values)
		double total = Math.PI * 2.0;
		int numPoints = numPoints();
		int dist = pointB-pointA;
		if(dist <= 0) dist = numPoints - pointA + pointB;
		//(get the distance)
		return total * ((double) dist) / ((double) numPoints);
	}
	
	public <Type> double radiansFrom(Type a, Type b){
		if(a.equals(b)) return 0.0;
		int numPoints = numPoints();
		double incr = Math.PI * 2.0 * 1.0 / ((double) numPoints);

		double loopedDist = 0.0;
		double rollingSum = 0.0;
		boolean seenOne = false;
		for(int i=0; i<numPoints; i++){
			if(points[i].equals(a)){
				if(seenOne){
					return loopedDist + ((double) (numPoints-i))*incr;
				}else{
					rollingSum = 0.0;
				}
				seenOne = true;
			}else if(points[i].equals(b)){
				if(seenOne){
					return rollingSum;
				}else{
					loopedDist = rollingSum;
				}
				seenOne = true;
			}
			rollingSum += incr;
		}
		throw new IllegalArgumentException("At least one of the elements does not appear in the circle: "
			+ a + " or " + b);
	}
	
	public boolean isClockwiseFrom(int a, int b){
		double radians = radiansFrom(a,b);
		if(radians <= Math.PI && radians > 0) return true;
		else return false;
	}
	public <Type> boolean isClockwiseFrom(Type a, Type b){
		double radians = radiansFrom(a,b);
		if(radians <= Math.PI && radians > 0) return true;
		else return false;
	}
	public boolean isCounterClockwiseFrom(int a, int b){
		double radians = radiansFrom(a,b);
		if(radians > Math.PI && radians > 0) return true;
		else return false;
	}
	public <Type> boolean isCounterClockwiseFrom(Type a, Type b){
		double radians = radiansFrom(a,b);
		if(radians > Math.PI && radians > 0) return true;
		else return false;
	}
	
	public double getX(){ return x; }
	public double getY(){ return y; }
	public double getR(){ return radius; }
	
	public double area(){
		return Math.PI*radius*radius;
	}
	
}
